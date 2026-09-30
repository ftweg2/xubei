#!/usr/bin/env python3
"""
Local mock of the three Komari endpoints the 续杯 (Xubei) app uses (standard library only).

    GET /api/version
    GET /api/nodes
    GET /api/recent/{uuid}

The data lives in mock_data.json next to this script and is re-read on every request, so editing
the file simulates traffic growth, a counter reset or a node disappearing without a restart.

Modes (--mode):
    normal  plain guest access (default)
    auth    private site: every request needs "Authorization: Bearer <api_key from mock_data.json>",
            otherwise 401 like Komari ("Private site is enabled, please login first.")
    html    every request answers 200 with an HTML page (reverse proxy / Cloudflare challenge)
    slow    every response is delayed by --delay seconds (default 20, beyond the app's 15 s read timeout)

The request log never prints the Authorization header; it only says whether one was present
and whether it matched.
"""

import argparse
import json
import math
import os
import sys
import time
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import unquote, urlparse

HERE = os.path.dirname(os.path.abspath(__file__))
STARTED = time.time()
# --freeze-at: every request answers as if it were this moment (comparable screenshots).
FROZEN_AT = None


def now_seconds():
    return FROZEN_AT if FROZEN_AT is not None else time.time()
DATA_FILE = os.path.join(HERE, "mock_data.json")

HTML_PAGE = """<!DOCTYPE html>
<html><head><title>Just a moment...</title></head>
<body><h1>Checking your browser before accessing the site.</h1></body></html>
"""


def load_data():
    with open(DATA_FILE, encoding="utf-8") as f:
        return json.load(f)


def rfc3339_nanos(dt):
    """Go-style RFC 3339 with nanoseconds, like Komari 1.5.1 (e.g. 2026-09-28T12:16:58.554845943Z)."""
    dt = dt.astimezone(timezone.utc)
    nanos = dt.microsecond * 1000 + 943
    return dt.strftime("%Y-%m-%dT%H:%M:%S") + ".%09dZ" % nanos


REPORT_EVERY_SECONDS = 3
REPORTS_PER_MINUTE = 20


def _seed(uuid):
    """A stable small number per node, so every node gets its own rhythm."""
    return sum(ord(c) for c in uuid) % 97


def live_speed(uuid, counters, t):
    """
    Upload and download speed (bytes/s) at unix time t: a base speed per node ("speed_up_bps" /
    "speed_down_bps" in the recent block, or a default derived from the uuid) that rises and falls
    over a couple of minutes, with a faster ripple on top. Deterministic in t, so consecutive
    requests continue the same curve.
    """
    seed = _seed(uuid)
    base_up = float(counters.get("speed_up_bps", 60_000 + seed * 18_000))
    base_down = float(counters.get("speed_down_bps", base_up * 0.6))
    slow = math.sin(t / (40.0 + seed % 17) + seed)
    fast = math.sin(t / (1.6 + (seed % 5) * 0.2) + seed * 1.7)
    # A per-report jitter (stable for the same 3 s slot) like real traffic.
    slot = int(t // REPORT_EVERY_SECONDS)
    jitter = ((slot * 7919 + seed * 104729) % 1000) / 1000.0 - 0.5
    wobble = 1.0 + 0.45 * slow + 0.25 * fast + 0.35 * jitter
    up = max(0.0, base_up * wobble)
    down = max(0.0, base_down * (1.0 + 0.45 * math.sin(t / 33.0 + seed * 0.3) + 0.2 * fast))
    return int(up), int(down)


def recent_reports(uuid, counters):
    """
    About one minute of reports (every 3 s, oldest first, like Komari's /api/recent), built from
    the counters. Speeds change over time (see live_speed); cpu, memory and connections move a
    little too; the counters of earlier reports are smaller by what was transferred since.
    """
    if counters is None or counters.get("offline"):
        return []
    now_ts = now_seconds()
    now = datetime.fromtimestamp(now_ts, timezone.utc)
    elapsed = now_ts - STARTED
    # Optional steady growth ("grow_up_per_min" / "grow_down_per_min", bytes per minute since the
    # mock started) and an uptime that keeps running like a real machine.
    up = int(counters.get("totalUp", 0)) + int(float(counters.get("grow_up_per_min", 0)) * elapsed / 60)
    down = int(counters.get("totalDown", 0)) + int(float(counters.get("grow_down_per_min", 0)) * elapsed / 60)
    uptime = int(counters.get("uptime", 0)) + (int(elapsed) if counters.get("uptime_runs", True) else 0)
    seed = _seed(uuid)
    reports = []
    back_up = 0
    back_down = 0
    # Newest first while walking back in time, then reversed.
    for k in range(REPORTS_PER_MINUTE):
        age = k * REPORT_EVERY_SECONDS
        t = now_ts - age
        speed_up, speed_down = live_speed(uuid, counters, t)
        cpu = 18 + 14 * math.sin(t / 21.0 + seed) + 6 * math.sin(t / 5.0 + seed * 2)
        ram_used = 522440704 + int(40_000_000 * math.sin(t / 50.0 + seed))
        reports.append({
            "uuid": uuid,
            "cpu": {"usage": round(max(0.5, cpu), 2)},
            "ram": {"total": 1003114496, "used": ram_used},
            "swap": {"total": 0, "used": 0},
            "load": {"load1": round(0.2 + cpu / 100, 2), "load5": 0.3, "load15": 0.25},
            "disk": {"total": 48384024064, "used": 26406692352},
            "network": {
                "up": speed_up,
                "down": speed_down,
                "totalUp": max(0, up - back_up),
                "totalDown": max(0, down - back_down),
            },
            "connections": {"tcp": 35 + int(10 * (1 + math.sin(t / 30.0))), "udp": 4},
            "uptime": max(0, uptime - age),
            "process": 143,
            "message": "",
            "updated_at": rfc3339_nanos(now - timedelta(seconds=age)),
        })
        back_up += speed_up * REPORT_EVERY_SECONDS
        back_down += speed_down * REPORT_EVERY_SECONDS
    reports.reverse()
    return reports


class Handler(BaseHTTPRequestHandler):
    # HTTP/1.1 with Content-Length and keep-alive, like Komari's Go server.
    protocol_version = "HTTP/1.1"
    server_version = "MockKomari/1.0"
    mode = "normal"
    delay = 20.0

    def log_message(self, fmt, *args):  # quieter, and never prints headers
        pass

    def _send(self, code, body, content_type="application/json; charset=utf-8"):
        data = body.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _json(self, code, obj):
        self._send(code, json.dumps(obj, ensure_ascii=False))

    def _ok(self, data):
        self._json(200, {"status": "success", "message": "", "data": data})

    def do_GET(self):
        started = time.time()
        auth_state = "no-auth"
        try:
            data = load_data()
        except (OSError, ValueError) as e:
            self._json(500, {"status": "error", "message": "mock_data.json unreadable: %s" % e})
            return
        header = self.headers.get("Authorization")
        if header is not None:
            auth_state = "auth-ok" if header == "Bearer " + str(data.get("api_key", "")) else "auth-wrong"

        path = urlparse(self.path).path.rstrip("/")
        code = 200
        try:
            if self.mode == "slow":
                time.sleep(self.delay)
            if self.mode == "html":
                self._send(200, HTML_PAGE, "text/html; charset=utf-8")
                return
            if self.mode == "auth" and auth_state != "auth-ok":
                code = 401
                self._json(401, {"status": "error", "message": "Private site is enabled, please login first."})
                return

            if path == "/api/version":
                self._ok({"version": data.get("version", "1.5.1"), "hash": "mock"})
            elif path == "/api/nodes":
                nodes = [n for n in data.get("nodes", []) if not n.get("hidden") or auth_state == "auth-ok"]
                self._ok(nodes)
            elif path.startswith("/api/recent/"):
                uuid = unquote(path[len("/api/recent/"):])
                counters = data.get("recent", {}).get(uuid)
                self._ok(recent_reports(uuid, counters))
            else:
                code = 404
                self._send(404, "404 page not found", "text/plain; charset=utf-8")
        except (BrokenPipeError, ConnectionResetError):
            code = -1
        finally:
            print("%s %-6s %-40s %s %s %.0fms" % (
                time.strftime("%H:%M:%S"), self.command, self.path[:40], code, auth_state,
                (time.time() - started) * 1000,
            ), flush=True)


def main():
    parser = argparse.ArgumentParser(description="Mock Komari panel for 续杯 (Xubei)")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--host", default="127.0.0.1", help="bind address (the emulator reaches it as 10.0.2.2)")
    parser.add_argument("--mode", choices=["normal", "auth", "html", "slow"], default="normal")
    parser.add_argument("--delay", type=float, default=20.0, help="seconds, for --mode slow")
    parser.add_argument("--freeze-at", type=float, default=None,
                        help="unix time: answer every request as of this moment (speeds, uptime, report times stand still)")
    args = parser.parse_args()

    global FROZEN_AT, STARTED
    if args.freeze_at is not None:
        FROZEN_AT = args.freeze_at
        STARTED = args.freeze_at

    Handler.mode = args.mode
    Handler.delay = args.delay
    load_data()  # fail fast on a broken file
    httpd = ThreadingHTTPServer((args.host, args.port), Handler)
    print("Mock Komari on http://%s:%d  mode=%s  data=%s" % (args.host, args.port, args.mode, DATA_FILE), flush=True)
    print("Emulator address: http://10.0.2.2:%d" % args.port, flush=True)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        httpd.server_close()


if __name__ == "__main__":
    sys.exit(main())
