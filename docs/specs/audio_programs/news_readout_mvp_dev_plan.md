# 跑步音频节目系统：新闻全文播报（MVP）开发计划与进度跟踪

基于：`docs/specs/audio_programs/news_readout_mvp.md`
创建日期：2026-02-27
维护约定：后续每完成一个功能点，都在本文档中更新勾选项与“进度更新日志”。

---

## 0. 状态标记约定

- 任务状态：`[ ]` 未开始 / `[~]` 进行中 / `[x]` 已完成 / `[!]` 阻塞
- 每次更新至少补充两处：
  1) 对应任务条目状态（勾选/改状态）
  2) “进度更新日志”新增一条记录（日期 + 变更摘要 + 关联 PR/commit 可选）

---

## 1. MVP 交付清单（来自规格文档第 12 节）

- [x] 跑步页 Now Playing UI（标题/来源/时间/状态 + 控制按钮）
- [x] 语音指令：开启/暂停/继续/换词/跳过/停止（规则解析）
- [x] NewsProgram：列表->全文->句子队列->朗读
- [x] Orchestrator：陪跑打断与恢复（句子级 cursor）
- [x] RunSessionNewsHistory：跑后回看链接列表
- [x] Provider：只接入 1 个 preset（feed + content），可通过配置替换 endpoint 与映射

---

## 2. 里程碑与推荐实现顺序（可并行但建议按依赖推进）

### M0：合规门禁与失败策略（必须先做）

- [x]（合规）新增“全文播报授权”开关/白名单校验（无授权：拒绝开启或明确降级策略）
- [x]（失败策略）provider 未配置/鉴权失败/feed 无结果/content 失败的用户提示与状态机落点

**验收要点**
- 未配置/未授权时，无法进入“Running”，UI 显示明确原因。

### M1：节目抽象与语音输出通道（SpeechChannel）

目标：把“内容生产/节目控制”与“音频输出”解耦，保证后续可切到云端单通道方案。

- [x] 定义 `Program`/`NewsProgram` 的最小接口：`start/pause/resume/skip/stop`
- [x] 定义 `SpeechChannel` 抽象：`speak/stop/onDone/onError`（或等价事件回调）
- [x] `LocalTtsChannel`（Android `TextToSpeech`）：ready 状态 + Utterance 回调驱动推进
- [~]（兼容点）预留 `RemotePcmChannel` 接口形态（复用 `BailianTtsClient` + `AudioStreamPlayer` 的可能性）

**验收要点**
- 在不接入 provider 的情况下，可用“假数据文本”跑通：按句提交、完成回调推进、stop 后可从 cursor 续播。

### M2：Provider（先 1 个 preset 跑通）

- [x] 设计 provider 配置结构（feedUrlTemplate/contentUrlTemplate/apiKey/header/params/少量字段映射）
- [x] 实现 feed 拉取：`keyword -> articles[]`（含 id/title/source/publishedAt/url）
- [x] 实现 content 拉取：`url/id -> fullText`（若 feed 自带全文则可跳过）
- [x]（文本最小清洗）HTML 标签/实体/控制符/空白规范化

**验收要点**
- 给定 keyword，可稳定拿到“可播报全文文本”，并能输出必要元数据。

### M3：NewsProgram（句子切分 + 连续播报）

- [x] 句子切分：中英标点（。！？；!?）与换行边界
- [x] 播报模板：每篇开始播报前先读“来源+时间+标题”
- [x] 连播策略：一篇读完自动下一篇；无内容时进入“无可播报内容”并可定时重试
- [x] 控制：pause（stop+保留 cursor）、resume（从 cursor 继续）、skip（跳下一篇）、stop（结束并清理）

**验收要点**
- 跑步中可持续播放多篇文章；暂停/继续/跳过/停止行为一致且不崩溃。

### M4：Orchestrator（陪跑打断与恢复）

目标：陪跑音频出现时打断新闻；陪跑结束后从“下一句”恢复新闻。

- [x] 定义仲裁层（Orchestrator）与优先级：用户指令 > 陪跑 > 新闻
- [x] 接入陪跑事件：以 `AIRealtimeProviderEvent.AssistantAudioDelta` 作为“陪跑开始”的触发点（仅触发一次），以 `AssistantCompleted` 作为“陪跑结束”
- [x] 打断策略：陪跑开始对 `LocalTtsChannel.stop()`；记录 `cursor(articleId + sentenceIndex)`
- [x] 恢复策略：陪跑结束从 cursor 下一句继续
- [x]（建议）AudioFocus 策略：新闻申请 focus，陪跑以更高优先级抢占并触发新闻 stop

**验收要点**
- 陪跑播报期间不出现新闻串音；陪跑结束后新闻能恢复且恢复点符合“句子级”要求。

### M5：跑步页 Now Playing UI + 控制按钮

- [x] 跑步页新增常驻 Now Playing 卡片：标题/来源/时间/状态（播报中/暂停/陪跑插播中/获取中/无内容/错误）
- [x] 按钮：暂停/继续、下一条（跳过）、停止
- [x] 进度展示（可选）：第 N 句/总句数 或 已播时长

**验收要点**
- UI 状态与 Program 状态一致；按钮操作能可靠驱动 Program 行为。

### M6：语音指令（规则/正则）

- [x] 规则解析：开启（含关键词提取）、暂停、继续、换成{关键词}、跳过、结束播报
- [x] 触发来源：使用 `AIRealtimeProviderEvent.UserTranscript(isFinal=true)` 作为指令输入
- [x] 指令优先级：高于陪跑/新闻播报（立即生效）
- [x] 语言判定：以本条用户指令语言作为 program.language（中/英）

**验收要点**
- 用语音说出上述指令，能稳定控制新闻播报；误触发率可控（可通过前缀词/唤醒词约束）。

### M7：跑后回看（Run Detail 展示 + 持久化）

- [x] 数据落库：建议新增 `run_news_history` 表（仅存元数据与 url，不存 fullText）
- [x] 记录时机：每篇开始播报（播报模板之前）写入：runId/title/source/publishedAt/url/playedAt
- [x] Run Detail 增加“本次播报”列表：按 playedAt 排序；点击打开系统浏览器

**验收要点**
- 跑步结束后进入详情页可看到本次播报列表；点击能跳转原文链接。

### M8：兼容点（可选但建议预先打桩）

- [ ] 提供“新闻走云端 TTS 单通道”的切换点（不要求 MVP 默认开启）
- [ ] 长文限制（后续增强）：最大字数/时长阈值配置的预留

---

## 3. 关键实现落点（与现有代码对齐的建议）

以下为“建议落点”，最终以实现时的目录结构为准：

- 陪跑事件入口：`app/src/main/java/com/sdevprem/runtrack/ai/manager/AIRunningCompanionManager.kt`（`startRealtimeEventCollection`）
- 陪跑音频播放：`app/src/main/java/com/sdevprem/runtrack/ai/audio/AudioStreamPlayer.kt`
- 跑步页 UI：`app/src/main/java/com/sdevprem/runtrack/ui/screen/currentrun/CurrentRunScreen.kt`
- 跑后详情页：`app/src/main/java/com/sdevprem/runtrack/ui/screen/rundetail/RunDetailScreen.kt`
- Room/DAO：`app/src/main/java/com/sdevprem/runtrack/data/db/*`

---

## 4. 当前实现进展（截至 2026-02-27）

**已完成**
- [x] M0 合规门禁与失败策略
- [x] M1 节目抽象 + 本地 TTS 通道
- [x] M2 Provider 配置化拉取（feed + content）
- [x] M3 连播 + 无内容定时重试
- [x] M4 陪跑打断与恢复（句子级）
- [x] M5 跑步页 Now Playing UI + 控制按钮
- [x] M6 语音指令（规则解析 + Realtime 输入）
- [x] M7 跑后回看（落库 + Run Detail 展示）

**进行中**
- [~] M8 兼容点（云端单通道新闻 TTS 预留）

**未开始**
- [ ] AudioFocus 抢占策略
- [ ] RemotePcmChannel 统一播放链路切换

---

## 5. 进度更新日志

> 格式建议：`YYYY-MM-DD` - 变更摘要（关联：PR/commit，可选）

- 2026-02-27 - 新增开发计划与进度跟踪文档（初始化）
- 2026-02-27 - 完成 NewsProgram/LocalTtsChannel/Provider 配置化接入，支持 feed->全文->句子播报链路
- 2026-02-27 - 完成陪跑打断恢复、语音指令解析（UserTranscript final）与跑步页 Now Playing 控制
- 2026-02-27 - 完成 run_news_history 落库与 Run Detail “本次播报”列表展示（含原文跳转）
- 2026-02-27 - 完成无内容定时重试（默认 5 分钟）与 AudioFocus 抢占策略（新闻/陪跑焦点管理）
- 2026-02-27 - 支持 App 打开后按配置自动启动新闻播报（默认开启，需满足授权与 provider 配置）
- 2026-02-27 - 补充可直接联调的 NewsAPI provider 模板，并新增 quickstart 配置说明文档
- 2026-02-27 - 补充“跑前设置 UI”：新增新闻节目开关、自动启动、默认关键词、重试间隔与 provider endpoint/key 配置
- 2026-03-01 - 修复 Run Detail 打开崩溃的容错链路（详情/指标/新闻历史查询异常降级 + 非法坐标过滤），并新增“内置测试文章”预设用于边跑边播联调
