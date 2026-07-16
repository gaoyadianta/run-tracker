# 新闻简报联调与生产接入

新闻功能只播报简报，不把 Provider 的 `content` 字段视为授权全文。Android 本地 TTS 是默认播放渠道。

## Debug 联调

在 `local.properties` 中按需配置：

```properties
NEWS_PROGRAM_API_KEY=YOUR_NEWSAPI_KEY
AI_BAILIAN_API_KEY=YOUR_BAILIAN_KEY
NEWS_BACKEND_BASE_URL=
```

- 不配置新闻 Key 时，可在 Debug 设置页选择“使用内置测试”，离线验证连续播报。
- 选择“使用 NewsAPI”时，App 用 `X-Api-Key` Header 请求最近 24 小时内的标题、来源、描述和正文片段，再由百炼生成简报。
- 百炼不可用、输出为空、超长或未保留来源和标题时，自动回退到确定性模板。
- Debug Key 只从 Gradle `-P`、环境变量或 `local.properties` 注入，不写入 DataStore。

NewsAPI 仅用于开发验证。它返回的内容可能截断，且开发方案不满足生产内容授权要求。

## Release 接入

Release 新闻链路不包含 NewsAPI Key 或百炼永久 API Key，只允许连接 HTTPS 自有后端（其他实时陪跑服务的既有配置不在本次改造范围内）：

```properties
NEWS_BACKEND_BASE_URL=https://news.example.com
```

后端契约：

- `POST /v1/mobile/session`：接收随机安装 ID 和 App 版本，返回约 15 分钟短期令牌。
- `GET /v1/news/briefs?keyword=&language=&limit=5`：使用 Bearer 短期令牌返回简报批次。
- 后端负责 Provider 与百炼永久 Key、授权内容获取、缓存、限流和不含正文的调用审计。

只有内容源明确允许摘要分发和语音播报后，才能在 Release 中启用新闻功能。默认 Release 新闻和自动启动均关闭。

## 数据与日志安全

- 设置迁移会删除历史明文 `news_program_api_key_value` 和旧全文授权字段。
- 日志不得记录 Key、带查询参数的 URL、原始正文或完整 AI 请求。
- Release 验收必须扫描 APK，确认不存在任何永久 Provider 或模型 Key。
