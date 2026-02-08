## AI Running History - 本地实现方案（无重后端）

### 1. 目标与范围
- 覆盖 PRD 的历史跑步列表、单次详情、AI 复盘、AI 轨迹注释、前端分享卡，尽量不依赖云后端。
- 支持单设备持久化、AI 生成、隐私模糊与本地分享；后续可平滑迁移到服务端。

### 2. 本地模式关键决定
- 数据存储：应用侧 SQLite（Room）或现有本地存储层；周/月汇总可缓存表或视图。
- AI 访问：调用本机 LLM 代理（例：`http://127.0.0.1:<port>/v1/chat/completions`），无队列；失败回退模板。
- 分享生成：前端 Canvas/Compose 渲染成图片；位置模糊在前端完成。
- 隐私：默认模糊起终点 300–500m；AI 数据可删除；AI 使用数据范围提示放本地。

### 3. 数据模型（本地表建议）
- `runs`：`id`(PK), `user_id`, `date`, `type(outdoor/treadmill)`, `distance_m`, `duration_s`, `avg_pace_s`, `route_polyline`, `has_hr`, `has_elev`, `is_pb`, `tags`, `notes`.
- `run_metrics`（可拆表或 JSON 列）：splits、pace_series[{t, pace}], hr_series[{t, bpm}], elev_series[{t, m}], step_cadence_series。
- `ai_artifacts`：`run_id`(PK, FK), `one_liner`(<=20 字), `summary`(4 段结构), `trace_annotations`(json list {km/latlng,text,type}), `story_card_meta`, `quote_card_meta`, `compare_card_meta`, `version`, `updated_at`.
- `aggregates`：缓存 week/month 统计；失效策略：数据更新或 24h。
- 分享控制：`share_mask`（bitset：start_blur/end_blur/disable_precise_route），`blur_radius_m`。

### 4. AI 生成流程（本地）
1) 特征准备：从 pace/hr/elev/splits 提取事件（起步过快、明显掉速、坡段、冲刺）。  
2) 调用 LLM 提示模板输出四段式 summary；再裁剪一条 20 字以内 one-liner。  
3) 轨迹注释：算法先找事件→映射到 polyline 点→LLM 生成人话描述。  
4) 成功落表；失败返回占位文案并允许重试；删除/重算直接操作 `ai_artifacts`。  
5) 安全：本地敏感词/长度校验，避免泄露精确地址；提示“数据仅存本地”。  

### 5. 功能实现要点
- 列表：本地筛选（时间/距离/PB/有AI），虚拟列表；缩略 polyline；AI 一句话显示加载/失败状态。
- 详情：地图（标准/卫星/夜间，自动缩放），注释气泡，图表↔地图联动；AI 复盘卡；标签/笔记编辑。
- 图表：滑动高亮，联动光标到 polyline；标记 AI 注释区间。
- 分享：三种卡片模板（故事卡、路线金句、成长对比）；前端生成图片；保存/系统分享；模糊起终点。
- 性能：列表<500ms 通过索引+缓存；地图用压缩 polyline；AI 调用异步，不阻塞渲染。

### 6. 里程碑（本地版）
- **M1 核心数据**：本地 schema/DAO；列表+详情基础数据与地图渲染；筛选 & 缩略图；骨架屏。
- **M2 AI 能力**：接本地 LLM 代理；one-liner + summary + 注释生成/重试/删除；图表联动；隐私开关。
- **M3 分享与打磨**：三类分享卡前端生成+模糊；成长对比逻辑；性能与文案 A/B 钩子；观测与日志（本地）。 

### 7. 任务拆分（执行序）
1) 现有代码基检查：存储层（Room/Realm/其他）、地图/图表组件、分享管线。  
2) 定义本地 schema 与 DAO；迁移脚本（如 Room Migration）；造若干跑步样例数据。  
3) 列表 & 筛选实现；AI 占位字段与状态管理。  
4) 详情页：地图+图表联动，注释点数据结构打通。  
5) 接入本地 LLM 代理：封装 AI 客户端、提示模板、失败回退与删除/重算逻辑。  
6) 分享卡模板实现 + 模糊处理；系统分享集成。  
7) 性能/体验收尾：缓存、骨架屏、错误提示、埋点（本地日志）。 

### 8. 立即行动（下一步）
- 确认项目使用的存储/DI/地图库，以便对齐 schema 与实现方式。  
- 如无本地 LLM 代理，准备一个可调用的本机 HTTP endpoint 并配置地址/超时。  
- 开一个样例 run 数据集，便于调试地图+图表+AI 生成链路。

### 9. 当前实现进展（截至 2026-01-27）
**已完成**
- 本地数据：`run_ai_artifact`、`run_metrics` 表 + 迁移；`running_table` 增加 `routePoints`。
- 路径存储：保存 `lat,lng,alt,time`，兼容旧格式解码。
- AI 本地生成：one‑liner + 4 段 summary；AI 轨迹注释点生成并落库。
- 详情页：交互地图 + 路径绘制 + 自动适配路线；AI 注释点可点击；图表↔地图联动；回放；地图模式（标准/卫星/夜间）。
- 图表分析：配速/海拔曲线、分段、平滑、配速轴倒序、高亮点；滑动同步地图。
- 分享卡：故事/金句/对比模板；路线叠图；平台角标与品牌块。
- 跑步中地图：用户手势缩放后可暂停自动跟随；支持“回中/跟随”按钮与延时恢复。

**部分完成**
- 心率/步频：数据结构已预留，但采集与图表数据源未接入（心率为空）。
- 列表缩略图：目前仍使用跑步结束时的截图，未渲染“简化轨迹缩略图”。

**未完成**
- 历史列表筛选（时间/距离/PB/AI 高亮）与周/月汇总。
- 跑步标签/笔记编辑与展示。
- 隐私：起终点模糊、精确路线开关、小红书默认模糊。
- AI：本地 LLM 代理接入、重试/删除 UI、引用陪跑 AI 记忆。
- 性能/体验：缓存、骨架屏、错误提示、埋点/日志。

**风险与注意**
- 旧历史数据若无 `routePoints`，详情页无法绘制轨迹（需提示或回退截图）。
- 分享卡隐私未处理，暂不适合公开传播。
