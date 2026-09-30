# Komari 真实返回样本

取自用户的 Komari 面板，版本 1.5.1，访客身份，采集于 2026-09-28。
节点 uuid 已替换为占位值，其余字段保持原样。

- `nodes-1.5.1.json`：`GET /api/nodes` 的完整返回。
- `recent-1.5.1.json`：`GET /api/recent/{uuid}` 的返回，只保留最后 3 条记录。

要点：
- recent 数组按时间升序排列，最后一条是最新的。
- 离线节点的 recent 返回空数组 `{"data":[],"message":"","status":"success"}`。
- 流量在 `network.totalUp` 和 `network.totalDown`，单位字节。`uptime` 单位秒。
- `updated_at` 是带纳秒的 RFC3339 时间。
- 这些节点在面板里都没有填价格、周期、到期日和流量限额，所以对应字段是 0 或空。
