# 跑步音频节目系统：新闻全文播报（MVP 设计）

状态：Draft

目标：在跑步过程中，用户可通过 UI/语音开启“按关键词持续播报新闻原文”，陪跑语音可随时插播并在结束后恢复新闻播报；跑后提供已播文章列表（可跳转原文链接）。

本文以“次简单方案”为默认实现路径：
- 新闻：本地 TTS（Android TextToSpeech）
- 陪跑：现有云端 TTS（WebSocket realtime 生成 PCM，AudioTrack 播放）

同时，文档明确保留兼容“最简单可跑通方案”的切换点：
- 新闻也走云端 TTS（复用现有 BailianTtsClient + AudioStreamPlayer），实现单音频通道/统一播放链路。

---

## 1. 需求确认（来自讨论）

### 1.1 功能范围
- 新闻来源：第三方新闻 API（可插拔 provider，配置 endpoint + 字段映射）
- 关键词：任意关键词（中文/英文）
- 语言：先支持中文；后续按用户语音输入语言自动切换（中文->中文、英文->英文）
- 播报内容：新闻原文全文播报（不做摘要、改写、要点提炼）
- 播报策略：持续播报，一条播完自动下一条
- 陪跑插播：陪跑语音到来时打断新闻播报；陪跑结束后自动恢复新闻（按句子级别恢复即可）
- 语音控制指令：
  - 暂停新闻 / 继续新闻
  - 换关键词（换行业）
  - 跳过这条
  - 结束播报
- 跑后回看：必须展示文章列表，点击链接跳转原文；每条必须播报来源与时间
- 断网/缓存：暂不考虑（可作为后续增强）

### 1.2 合规前置条件（必须写进产品与实现）
- “原文全文播报”只有在第三方 API 明确授权“全文内容分发 + 终端展示/播报（TTS）”时才可启用。
- 若 provider 不提供全文或不具备授权：MVP 应明确失败策略（拒绝开启并提示，或降级到标题/摘要，降级需业务确认）。

---

## 2. 用户体验与 UI（跑步页面）

### 2.1 跑步页面新增“节目条（Now Playing）”
常驻小卡片/条幅，展示当前新闻播报状态与文章信息：
- 标题：currentTitle
- 来源：currentSource
- 发布时间：currentPublishedAt（HH:mm 或 yyyy-MM-dd HH:mm）
- 状态：播报中 / 暂停 / 陪跑插播中（新闻将自动继续）/ 获取内容中 / 无可播报内容 / 错误
- 进度：第 N 句 / 总句数（可选），或已播时长（可选）

按钮：
- 暂停/继续（切换）
- 下一条（跳过当前文章）
- 停止（结束新闻播报）

### 2.2 设置入口（跑前配置）
最小配置建议：
- 总开关：启用“节目系统”
- 子开关：允许跑步中语音开启新闻播报
- 默认关键词（可选）：跑步开始时自动开启（默认关闭，避免误触）
- provider 配置：选择 provider preset + 填入 key/endpoint 等

### 2.3 跑后回看（Run Detail）
在跑步详情增加“本次播报”列表：
- 每条：标题 + 来源 + 发布时间 + 点击打开原文 URL
- 记录顺序：按播报开始时间排序
- 建议只存元数据与 url，不存原文内容（降低版权与存储风险）

---

## 3. 语音指令与意图识别（MVP）

### 3.1 指令集合
开启（示例）：
- “给我播报点{关键词}行业的新闻资讯”
- “开始播报{关键词}新闻”
- “新闻模式 {关键词}”

控制：
- “暂停新闻”
- “继续新闻”
- “换成{关键词}”
- “跳过这条/下一条”
- “结束播报/停止新闻”

### 3.2 识别策略（建议从简单规则起步）
MVP 建议先用规则/正则实现（可解释、可控、风险低）：
- 关键词提取：匹配“播报/新闻/换成/换到/行业”等后面的短语
- 多节目扩展：未来可换为 LLM 意图分类，但 MVP 不必引入

语言判定（MVP）：
- 以本条用户指令的语言为准，设置 program.language（中文/英文）
- 后续可按文章 language 自动覆盖

---

## 4. 系统抽象：Program（节目）与播放仲裁

### 4.1 设计目标
新闻只是第一个节目类型，未来可接入音乐/小说等。应把“内容生产”与“播放控制”解耦：
- 内容层：抓取/整理/切分成可播报单元
- 播放层：TTS/播放器输出音频
- 仲裁层：陪跑与节目之间的优先级、打断与恢复

### 4.2 核心接口（概念级）
Program：
- start(params)
- pause()
- resume()
- skip()
- stop()
- onCompanionInterruptStart()
- onCompanionInterruptEnd()

SpeechChannel（兼容两种方案的关键点）：
- speak(text, utteranceId, priority)
- stop(priorityOwner)
- onUtteranceDone(utteranceId)
- onError(...)

两种实现：
- LocalTtsChannel：Android TextToSpeech
- RemotePcmChannel：现有 BailianTtsClient + AudioStreamPlayer

通过 SpeechChannel 抽象，新闻可在本地或云端播报之间切换，而 Program/Orchestrator 不变。

### 4.3 优先级与行为
优先级（高到低）：
1) 用户显式控制指令（暂停/停止/换词/跳过）
2) 陪跑语音（里程碑/提醒/总结/互动）
3) 新闻播报

打断策略（需求确定）：
- 陪跑开始：停止新闻输出，记录 cursor（articleId + sentenceIndex）
- 陪跑结束：从 cursor 的下一句继续

---

## 5. 新闻播报完整流程（MVP 运行时）

### 5.1 流程概览
1) 用户开启新闻播报（keyword、language）
2) provider 拉取文章列表（feed）
3) 选择下一篇 article
4) 播报开头固定模板：
   - “来源：{source}，发布时间：{time}。标题：{title}。”
5) 获取全文 content（content endpoint 或 feed 自带）
6) 最小清洗 + 按句子切分 sentences[]
7) 循环朗读 sentences：
   - 每次提交 1 句（或 1-2 句）给 TTS
   - 监听 utterance 完成事件，推进 sentenceIndex
8) 一篇读完 -> 进入下一篇
9) 陪跑打断 -> 停止新闻 -> 播放陪跑 -> 恢复新闻
10) 跑后回看：每篇开始播报时记录元数据（title/source/time/url）

### 5.2 句子切分（允许的“非加工”）
为满足“打断后按句子恢复”，需要做文本切分，这不改变内容语义：
- 去 HTML 标签、脚本、样式（若 provider 返回 HTML）
- 解码 HTML 实体、移除控制符、规范化空白
- 句子边界：中文/英文标点（。！？；!?）与换行

---

## 6. Provider 可插拔设计（只接 1 个 provider 跑通）

### 6.1 Provider 能力分两段
Feed（列表）能力：keyword -> articles[]
- 必需字段：id（可由 url hash）、title、sourceName、publishedAt、url
- 可选字段：description、language、contentSnippet

Content（全文）能力：url/id -> fullText
- provider 若 feed 已自带全文，可跳过 contentEndpoint

### 6.2 配置与字段映射（建议）
为了“通过配置填 endpoint + 字段映射”，但又不在 MVP 引入过度复杂的通用解析器，建议做两层：

MVP（最小可用）：
- 预置 1 个 provider 类型（例如 ProviderPresetA）
- 配置仅包含：
  - feedUrlTemplate（含 {keyword}、{page}）
  - contentUrlTemplate（含 {url} 或 {id}）
  - apiKey/header/params
  - 字段映射（固定少量 json path：如 response.data[i].title）

后续增强（真正“全通用映射”）：
- 支持 JSONPath/JMESPath 表达式
- 支持多种响应格式与分页策略

### 6.3 provider 选择建议（MVP）
MVP 可以采用“列表 + 正文抓取”组合：
- feed：返回 url/title/source/time 的新闻列表
- content：对 url 做正文提取，得到 fullText

注意：正文抓取接口通常会强调版权，需要你们对来源与授权做白名单/校验。

---

## 7. 音频实现：次简单方案（新闻本地 TTS + 陪跑云端 TTS）

### 7.1 本地 TTS（新闻）
关键点：
- Android TextToSpeech 初始化异步，需有 ready 状态
- 通过 UtteranceProgressListener 获取每句的完成回调，以推进 cursor
- “暂停/继续”的实现：TextToSpeech 没有跨设备一致的 pause/resume 语义，MVP 建议：
  - pause：stop() 并保留 cursor
  - resume：从 cursor 继续 speak 后续句子

### 7.2 云端 TTS（陪跑）
复用现有 realtime provider 输出的音频 delta 播放链路。

### 7.3 仲裁（中断/恢复）
陪跑开始：
- 立即对 LocalTtsChannel 执行 stop()（停止新闻）
- 标记状态：NewsProgram Interrupted
- 记录 cursor（articleId + sentenceIndex）

陪跑结束：
- NewsProgram 恢复，从 cursor 继续 speak

### 7.4 AudioFocus（建议）
为避免两路音频争抢系统输出：
- 新闻本地 TTS 开始前申请 AudioFocus（GAIN 或 GAIN_TRANSIENT，按播报连续性决定）
- 陪跑开始申请更高优先级 AudioFocus（GAIN_TRANSIENT），触发新闻 stop
- 结束后按需要恢复新闻 focus

---

## 8. 兼容点：最简单可跑通方案（新闻也走云端 TTS 单通道）

当本地 TTS 体验/兼容性不满足时，可切换为：
- 新闻文本也通过 RemotePcmChannel（云端 TTS）播放
- 好处：统一音色/统一播放器/更可控的打断（停止提交后续音频）
- 代价：成本更高；请求更密集（尤其按句子提交）

保持兼容的关键：
- Orchestrator 只依赖 SpeechChannel 接口，不关心本地/云端实现
- NewsProgram 输出的是“可播报文本片段序列（sentences/chunks）”

---

## 9. 数据记录：跑后回看链接列表

### 9.1 记录时机
- 每篇新闻开始播报（开头模板播报之前）记录一次：
  - runId（或 sessionId）、title、source、publishedAt、url、playedAt

### 9.2 展示
- 跑步详情页展示该列表
- 点击 url 使用系统浏览器打开原文

### 9.3 不存原文
MVP 默认不持久化 fullText，仅在内存/会话内使用，避免版权与存储风险。

---

## 10. 失败策略（MVP 必需）
- provider 配置缺失/鉴权失败：提示“新闻服务未配置/不可用”，不进入 Running
- feed 无结果：提示“暂无相关新闻”，可定时重试（例如 5 分钟）
- content 获取失败：可跳过该条并记录原因；连续失败则提示并停止
- 合规校验失败（无授权/无全文）：提示并拒绝开启（或降级，需业务确认）

---

## 11. 风险与权衡
- 版权与授权：原文全文播报是最大风险点，必须有明确授权链路与可追溯配置。
- 本地 TTS 体验一致性：不同机型/引擎差异大，需提供一键切换到云端 TTS 的兼容路径。
- 长文朗读：文章过长会占用跑步注意力，建议后续加可配置上限（最大字数/最大时长），但 MVP 先不强制。

---

## 12. MVP 交付清单（不含代码实现细节）
1) 跑步页 Now Playing UI（标题/来源/时间/状态 + 控制按钮）
2) 语音指令：开启/暂停/继续/换词/跳过/停止（规则解析）
3) NewsProgram：列表->全文->句子队列->朗读
4) Orchestrator：陪跑打断与恢复（句子级 cursor）
5) RunSessionNewsHistory：跑后回看链接列表
6) provider：只接入 1 个 preset（feed + content），可通过配置替换 endpoint 与映射

