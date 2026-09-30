#!/usr/bin/env python3
"""
Writes seed_subscriptions.json: an export file of the 续杯 (Xubei) app ("设置 → 导入订阅") with sample
subscriptions for manual testing on an emulator. The VPS entries are linked to the nodes of
mock_data.json, so after importing, pointing the app at the mock panel (http://10.0.2.2:8765)
and pulling to refresh, every traffic state of the detail page can be seen.

Due dates are relative to --today (default: the current date).

    python tools/mock-komari/make_seed.py [--today 2026-09-28]
"""

import argparse
import json
import os
from datetime import date, timedelta

HERE = os.path.dirname(os.path.abspath(__file__))


def sub(name, category, price, currency, unit="MONTH", count=1, due=None, auto=False, **extra):
    entry = {
        "name": name,
        "category": category,
        "price_minor": price,
        "currency": currency,
        "cycle_unit": unit,
        "cycle_count": count,
        "next_due": due.isoformat() if due else None,
        "auto_renew": auto,
    }
    entry.update(extra)
    return entry


def vps(uuid, name, limit_gib=0, limit_type="SUM", mode="KOMARI_CUMULATIVE", reset_day=1, **extra):
    v = {
        "komari_uuid": uuid,
        "komari_name": name,
        "traffic_limit_bytes": limit_gib * (1 << 30),
        "traffic_limit_type": limit_type,
        "counting_mode": mode,
        "reset_day": reset_day,
    }
    v.update(extra)
    return v


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--today", default=date.today().isoformat())
    args = parser.parse_args()
    today = date.fromisoformat(args.today)
    d = lambda n: today + timedelta(days=n)

    subs = [
        sub("Claude Max", "AI", 10000, "USD", due=d(3), auto=True),
        sub("ChatGPT Plus", "AI", 2000, "USD", due=d(9), auto=True),
        sub("Cursor Pro", "AI", 2000, "USD", due=d(24), auto=True),
        sub("Gemini Advanced", "AI", 14000, "CNY", due=d(18)),
        sub("Midjourney", "AI", 9600, "USD", unit="MONTH", count=12, due=d(140)),
        sub("东京 CN2 GIA", "VPS", 4990, "CNY", due=d(20), auto=True,
            vps=vps("mock-0001-tokyo", "东京 CN2 GIA", 1024, "MAX", reset_day=18, komari_region="🇯🇵")),
        sub("法兰克福 年付", "VPS", 36000, "EUR", count=12, due=d(154),
            vps=vps("mock-0002-fra", "法兰克福 年付", 0, "MAX", mode="APP_ACCUMULATED", reset_day=1, komari_region="🇩🇪",
                    agent_month_rotate=True)),
        sub("洛杉矶 9929", "VPS", 12900, "CNY", count=3, due=d(38),
            vps=vps("mock-0003-la", "洛杉矶 9929", 500, "SUM", reset_day=1, komari_region="🇺🇸")),
        sub("香港 离线机", "VPS", 2000, "CNY", due=d(4),
            vps=vps("mock-0004-hk", "香港 离线机", 200, "MAX", komari_region="🇭🇰")),
        sub("甲骨文-eu-92", "VPS", 0, "CNY", cycle_known=False,
            vps=vps("mock-0005-oracle", "甲骨文-eu-92", 0, "MAX", komari_region="🇩🇪")),
        sub("新加坡 终身机", "VPS", 0, "USD", unit="ONCE", count=1, is_free=True, is_permanent=True,
            vps=vps("mock-0006-sg", "新加坡 终身机", 2048, "SUM", komari_region="🇸🇬")),
        sub("搬瓦工 DC6", "VPS", 4999, "USD", count=12, due=d(-2)),
        sub("iCloud+ 200GB", "OTHER", 2100, "CNY", due=d(0), auto=True),
        sub("Spotify", "OTHER", 1500, "CNY", due=d(12), auto=True),
        sub("Netflix", "OTHER", 6800, "CNY", due=d(30), auto=True),
        sub("域名 xubei.app", "OTHER", 9800, "CNY", count=12, due=d(210), archived=True),
    ]
    out = {
        "format": "vluer-subscriptions",
        "version": 1,
        "exported_at": today.isoformat() + "T00:00:00Z",
        "subscriptions": subs,
    }
    path = os.path.join(HERE, "seed_subscriptions.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
    print(path)


if __name__ == "__main__":
    main()
