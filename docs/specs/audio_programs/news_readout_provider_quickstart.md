# 新闻播报 Provider 联调配置（Quickstart）

适用范围：`app/src/main/res/values/strings.xml`

## 1) 快速联调（默认模板：NewsAPI）

当前代码已内置以下模板（可直接联调）：
- `news_program_feed_url_template` 使用 NewsAPI `everything` 接口
- `news_program_api_key_query_name=apiKey`
- `news_program_feed_content_path=content`

你只需要改 2 个值：

1. 填入自己的 key
   - `news_program_api_key_value=YOUR_NEWSAPI_KEY`

2. 打开合规开关（仅在你确认具备授权时）
   - `news_program_fulltext_authorized=true`

> 注意：NewsAPI 的 `content` 往往是正文片段，适合功能联调，不等价于“授权全文”生产方案。

---

## 2) 生产接入（授权全文）

如果你们有已授权的全文 provider，建议这样配置：

- `news_program_feed_url_template`：列表接口（返回标题/来源/时间/url）
- `news_program_feed_content_path`：留空（不依赖列表里的片段）
- `news_program_content_url_template`：正文接口（可用 `{id}` 或 `{url}`）
- `news_program_content_text_path`：正文字段路径（如 `data.content`）
- `news_program_api_key_header_name` 或 `news_program_api_key_query_name`：鉴权方式

---

## 3) 自动启动配置

App 打开后自动启动新闻播报由以下配置控制：

- `news_program_auto_start_on_app_open=true`

自动启动会被以下门禁约束：
- `news_program_enabled=true`
- `news_program_fulltext_authorized=true`
- `news_program_feed_url_template` 非空

任一条件不满足时会安全跳过自动启动。
