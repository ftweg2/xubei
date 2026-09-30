# 本地 Komari 模拟面板

只依赖 Python 标准库，模拟续杯用到的三个接口：`/api/version`、`/api/nodes`、`/api/recent/{uuid}`。
返回格式与 Komari 1.5.1 一致：`{"status":"success","message":"","data":...}`，recent 按时间升序、`updated_at` 带纳秒。
recent 返回最近约一分钟的 20 条上报（每 3 秒一条），每条都有 cpu、ram、swap、load、disk、network{up,down,totalUp,totalDown}、connections、uptime、process；网速随时间起伏，用来测试总览页的“节点实时”。

## 启动

```bash
python tools/mock-komari/mock_komari.py                # 普通访客模式，端口 8765
python tools/mock-komari/mock_komari.py --mode auth    # 私有站点：不带 Key 返回 401
python tools/mock-komari/mock_komari.py --mode html    # 返回 HTML 页面（模拟防火墙质询）
python tools/mock-komari/mock_komari.py --mode slow    # 每个响应延迟 20 秒（超过 app 的 15 秒读取超时）
python tools/mock-komari/mock_komari.py --mode slow --delay 5 --port 9000
```

默认只监听 `127.0.0.1`。控制台日志只显示请求路径、状态码和是否带了正确的 Key，不打印请求头。

## 在模拟器里填写地址

Android 模拟器里 `10.0.2.2` 指向电脑本机，所以在续杯的“设置 → Komari 面板”里填：

```
http://10.0.2.2:8765
```

`auth` 模式需要的 API Key 在 `mock_data.json` 的 `api_key` 字段（是随机生成的假值，只用于本地测试）。
因为是 HTTP 地址，填了 Key 后 app 会显示明文传输警告，这是预期行为。

## 数据文件 `mock_data.json`

每次请求都会重新读取，改完保存即可生效，不用重启：

| 节点 | 说明 |
|---|---|
| 东京 CN2 GIA | 月付，1 TB 限额，按较大者计算，用量约 85% |
| 法兰克福 年付 | 年付，不限流量 |
| 洛杉矶 9929 | 季付，500 GB 限额，按上下行之和计算，已超额（约 112%） |
| 香港 离线机 | `"offline": true`，recent 返回空数组 |
| 甲骨文-eu-92 | 和真实面板一致：价格、周期、到期日、限额都没填（0 或 null，货币为 `$`），开机约 141 天 |
| 新加坡 终身机 | 免费（price -1）、一次性（billing_cycle -1）、到期日为 200 年后（Komari“设置为长期”的写法），2 TB 按上下行之和 |
| 伦敦 家宽 | 英国节点（`"region": "🇬🇧"`），月付；给新增订阅关联节点时测试国旗和实时资源小环 |

常见操作：

- **流量增长**：把某个节点 `recent` 里的 `totalUp` / `totalDown` 调大。
- **计数器归零（重启）**：把 `totalUp` / `totalDown` / `uptime` 改小。
- **节点离线**：设 `"offline": true`。
- **节点消失**：从 `nodes` 里删掉该节点，再在 app 里“立即同步”，订阅会被标记为“面板上已找不到”，不会被删除。

## 实时网速

- 每个节点的上行、下行网速按时间连续起伏（慢波加快速抖动），同一时刻重复请求得到同一条曲线，隔几秒再请求曲线会向前走。
- 在某个节点的 `recent` 里加 `"speed_up_bps": 2000000`、`"speed_down_bps": 800000` 可以设定基准网速（字节/秒）；不填时按 uuid 算一个固定的基准。
- 较早的上报里 `totalUp` / `totalDown` 会减去这段时间传输的量，和网速对得上。

## 自动增长和运行中的 uptime

- `uptime` 会随模拟面板运行的时间自动增加（像真机一样）。某个节点想固定 uptime，在它的 `recent` 里加 `"uptime_runs": false`。
- 在 `recent` 里加 `"grow_up_per_min": 104857600`（字节/分钟）或 `"grow_down_per_min"`，计数器会从模拟面板启动起按这个速度自动增长，适合测试“App 自己累计”。
- 模拟重启：把 `totalUp` / `totalDown` 和 `uptime` 都改小。
- 模拟开启 `--month-rotate`：只把 `totalUp` / `totalDown` 改小，`uptime` 不动（计数器没重启就归零）。
- 模拟开了 `--month-rotate` 的机器重启：`uptime` 改小，计数器保持或变大（计数器在重启后没归零）。

## 示例订阅 `seed_subscriptions.json`

`make_seed.py` 生成一份续杯导出格式的示例订阅（AI、VPS、其他各几条；VPS 已关联上面的节点并带国旗；含已过期、今天到期、待补充、长期有效、免费、已归档）。在“设置 → 导入订阅”里导入后，订阅页和详情页的各种状态都能看到。到期日相对 `--today` 计算：

```bash
python tools/mock-komari/make_seed.py --today 2026-09-28
```

