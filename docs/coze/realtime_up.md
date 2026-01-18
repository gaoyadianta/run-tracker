Realtime 上行事件
本文介绍扣子智能语音信令事件中的上行事件。智能体进房后才能发送上行事件，具体请参见​集成音视频 Realtime Web SDK。​
事件 ID：每个上行事件 ID 建议不要重复，故障排查场景下便于定位问题。​
事件发送时机：确保在监听到 Realtime SDK 的 onUserJoined 回调智能体进房后再发送上行事件。​
事件发送对象：需要发送给房间内的智能体，需要指定传入的 user_id 为创建房间接口传入的 bot_id，而不是创建房间接口返回的 uid。​
​
更新房间配置​
事件类型：session.update​
事件说明：此事件可更新房间内的配置项。​
传入哪个字段就改哪个字段，填空值也会更新；若不想修改某个字段，则不要传入该字段更新 RTC 房间的配置信息。支持更新智能体音色、语速等配置，也可以修改 meta_data 等对话配置。 所有参数都是可选的，如果不需要修改某个配置，可以省略对应的参数。​
若更新成功，会收到下行事件 "session.updated"，否则，会收到下行事件 "error"。​
事件结构：​
​
参数​
类型​
是否必选​
说明​
id​
String​
必选​
客户端自行生成的事件 ID，方便定位问题。​
event_type​
String​
必选​
必填 session.update。​
data​
Object​
可选​
事件数据，包含会话配置的详细信息。​
data.speech_rate​
Integer​
可选​
模型回复的语速，取值范围 [-50, 100]，默认为 0。-50 表示 0.5 倍速，100 表示 2 倍速。​
data.loudness_rate​
Integer​
可选​
输出音频的音量，取值范围 [-50, 100]，默认为 0。-50 表示 0.5 倍音量，100 表示 2 倍音量。​
data.longest_silence_ms​
Integer​
可选​
当智能体处于长时间沉默状态时，房间将自动解散。此时间以毫秒（ms）为单位计量，默认时长为 180,000 毫秒。​
data.event_subscriptions​
Array<String>​
可选​
需要订阅下行事件的事件类型列表。不设置或者设置为空为订阅所有下行事件。​
data.chat_config​
Object​
可选​
会话配置。​
data.chat_config.meta_data​
Map<String, String>​
可选​
附加信息，通常用于封装一些业务相关的字段。查看对话消息详情时，系统会透传此附加信息。自定义键值对，应指定为 Map 对象格式。长度为 16 对键值对，其中键（key）的长度范围为 1～64 个字符，值（value）的长度范围为 1～512 个字符。​
data.chat_config.custom_variables​
Map<String, String>​
可选​
智能体中定义的变量。在智能体 prompt 中设置变量 {{key}} 后，可以通过该参数传入变量值，同时支持 Jinja2 语法。变量名只支持英文字母和下划线。​
data.chat_config.extra_params​
Map<String, String>​
可选​
附加参数，通常用于特殊场景下指定一些必要参数供模型判断，例如指定经纬度，并询问智能体此位置的天气。自定义键值对格式，其中键（key）仅支持设置为：​
latitude：纬度，此时值（Value）为纬度值，例如 39.9800718。​
longitude：经度，此时值（Value）为经度值，例如 116.309314。​
data.chat_config.plugin_interrupt_mode​
String​
可选​
端插件执行模式，可选值有 blocking / nonblocking，默认为 nonblocking。​
blocking 模式下，遇到端插件执行后，会阻塞后续的对话，直到提交端插件结果。​
nonblocking 模式下，遇到端插件执行后，若未提交端插件结果继续对话，则端插件请求会被丢弃。​
data.chat_config.allow_voice_interrupt​
Boolean​
可选​
是否打开语音打断功能，默认为 true。​
data.chat_config.interrupt_config​
Object​
可选​
语音打断配置，仅在 allow_voice_interrupt 为true时生效​
data.chat_config.interrupt_config.mode​
String​
必选​
语音打断模式，可选值有all / keyword_contains / keyword_prefix，默认为all。​
all模式下，任意内容都可以打断模型回复。​
keyword_contains模式下，说话内容包含关键词才会打断模型回复。例如关键词"扣子"，用户正在说“你好呀扣子......” / “扣子你好呀”，模型回复都会被打断。​
keyword_prefix模式下，说话内容前缀匹配关键词才会打断模型回复。例如关键词"扣子"，用户正在说“扣子你好呀......”，模型回复就会被打断，而用户说“你好呀扣子......”，模型回复不会被打断。​
data.chat_config.interrupt_config.keywords​
Array<String>​
可选​
关键词列表，每个关键词长度不超过8个字，最多10个关键词，仅在keyword_contains/keyword_prefix模式下生效。​
data.chat_config.parameters​
Map<String, Any>​
可选​
设置对话流的自定义输入参数的值并传递给对话流，具体用法和示例代码可参考​为自定义参数赋值。​
data.turn_detection​
Object​
可选​
声音检测配置。​
data.turn_detection.type​
String​
可选​
语音检测模式，默认为 server_vad，可选项包括：​
server_vad：语音活动检测由扣子服务端完成，客户端将音频流持续发送到服务端，服务端在接收到音频后，通过服务端 VAD 检测语音的开始和结束。​
client_vad：客户端使用自己的 VAD 检测语音的开始和结束，并将检测到的语音片段发送到服务器进行识别。​
semantic_vad：采用语义判停的自由对话模式（此功能仅对企业版用户开放），由服务端识别语义来判断是否停止说话。​
详细的检测逻辑请参见​如何设置扣子的语音检测模式？。​
data.turn_detection.prefix_padding_ms​
Integer​
可选​
VAD 检测到语音之前要包含的音频量，单位为 ms。默认为 600ms。​
data.turn_detection.silence_duration_ms​
Integer​
可选​
server_vad 模式下，检测语音停止的静音持续时间，单位为 ms。取值范围为 200~2000，默认为 500ms。​
data.turn_detection.semantic_vad_config​
Object​
可选​
semantic_vad 模式下，配置判定语音停止的语义检测策略。​
data.turn_detection.semantic_vad_config.silence_threshold_ms​
Integer​
可选​
当用户暂停说话时，持续静音多久后，触发语义判停检测。单位为 ms。默认为 300ms。​
data.turn_detection.semantic_vad_config.semantic_unfinished_wait_time_ms​
Integer​
可选​
当语义检测判断该语句未结束时，持续静音多久后，扣子认定语音结束。单位为 ms。默认为 500ms。取值范围为 100~2000。​
data.asr_config.stream_mode​
String​
可选​
ASR 识别的模式。​
output_no_stream：不会逐字返回语音识别结果，而是等整段语音结束后统一输出完整文本。异步语音消息场景中推荐使用该模式，会整合整句音频信息做上下文分析，减少实时截断导致的误差，提升准确率。​
bidirectional_stream（默认值）：逐字的返回语音识别的结果。​
data.asr_config.context​
String​
可选​
上下文，限制 800 tokens，超出后自动截断。​
data.asr_config.enable_itn​
Boolean​
可选​
将语音转为文本时，是否开启文本规范化（ITN）处理，将识别结果转换为更符合书面表达习惯的格式以提升可读性。默认为 true。​
开启后，会将口语化数字转换为标准数字格式，示例：​
将两点十五分转换为 14:15。​
将一百美元转换为 $100。​
data.asr_config.enable_punc​
Boolean​
可选​
将语音转为文本时，是否给文本加上标点符号。默认为 true。​
data.asr_config.enable_ddc​
Boolean​
可选​
将语音转为文本时，是否启用顺滑，默认为 true。​
true：系统在进行语音处理时，会去掉识别结果中诸如 “啊”“嗯” 等语气词，使得输出的文本语义更加流畅自然，符合正常的语言表达习惯，尤其适用于对文本质量要求较高的场景，如正式的会议记录、新闻稿件生成等。​
false：系统不会对识别结果中的语气词进行处理，识别结果会保留原始的语气词。​
data.asr_config.enable_nostream​
Boolean​
可选​
是否开启二次识别模式：​
true：会实时返回逐字识别的文本；当一句话结束时，会结合整句音频进行上下文分析并重新识别，生成优化后的识别结果并返回。这种机制既能满足客户实时上屏的需求，又能确保最终结果的识别准确率。​
false（默认值）：仅进行一次实时识别，逐字返回文本，不会在一句话结束时重新识别分句，可能存在一定的识别误差。​
data.asr_config.enable_emotion​
Boolean​
可选​
识别说话人的情绪。仅在 data.asr_config.stream_mode 为output_no_stream时生效。默认为false。​
支持的情绪标签包括：​
angry：表示情绪为生气​
happy：表示情绪为开心​
neutral：表示情绪为平静或中性​
sad：表示情绪为悲伤​
surprise：表示情绪为惊讶​
data.asr_config.enable_gender​
Boolean​
可选​
是否开启识别说话人的性别（male/female），仅在 data.asr_config.stream_mode 为output_no_stream时生效。默认为false。​
data.asr_config.sensitive_words_filter​
Object​
可选​
敏感词过滤功能，支持以下 3 种过滤方式：​
过滤系统敏感词，并替换为*。​
过滤自定义敏感词，并替换为空。​
过滤自定义敏感词，并替换为*。​
data.asr_config.sensitive_words_filter.system_reserved_filter​
Boolean​
可选​
是否过滤系统自带的敏感词，并将匹配到的敏感词替换为*。（系统自带敏感词主要包含一些限制级词汇）。默认为false。​
data.asr_config.sensitive_words_filter.filter_with_empty​
Array<string>​
可选​
自定义需替换为空的敏感词列表。​
data.asr_config.sensitive_words_filter.filter_with_signed​
Array<string>​
可选​
自定义需替换为 * 的敏感词列表。​
data.voice_print_config​
Object​
可选​
声纹识别配置。​
data.voice_print_config.group_id​
String​
可选​
声纹组 ID。语音通话时，扣子会在该声纹组内进行查找匹配对应的声纹，当声纹匹配度高于 score 阈值，则认为是同一个人的声音。​
你可以通过查看声纹组列表 API 查看声纹组 ID。​
data.voice_print_config.score​
Integer​
可选​
声纹匹配的命中阈值，即声音匹配度的最低标准。当声音匹配度达到或超过该阈值时，扣子才会认定声纹匹配成功。你可以根据应用的安全性要求进行自定义设置。如果匹配了多轮声纹，扣子会取相似度最高的一个。​
取值范围：0~100，默认值：40。​
data.voice_print_config.reuse_voice_info​
Boolean​
可选​
当本轮对话未命中任何声纹时，是否沿用历史声纹信息。​
true：未命中声纹时，智能体将返回上一次命中的声纹。适用于连续对话场景，当收音不好等情况导致声纹没能正确被识别时，保障对话的连贯性。​
false：（默认值）未命中声纹时，智能体返回空的声纹信息。​
data.voice_id​
String​
可选​
音色 ID。​
data.tts_config​
Object​
可选​
配置语音合成的相关参数，用于控制合成语音的整体风格与情感表达，包括辅助信息（如情绪、方言、语气等）、多情感音色的情感类型及强度等。​
data.tts_config.context_texts​
String​
可选​
语音合成的辅助信息，用于控制合成语音的整体情绪（如悲伤、生气）、方言（如四川话、北京话）、语气（如撒娇、暧昧、吵架、夹子音）、语速（快慢）及音调（高低）等。默认为空。​
示例：用低沉沙哑的语气、带着沧桑与绝望地说。​
仅当 voice_id 为豆包语音合成大模型 2.0 音色时才支持该参数，具体支持的音色列表请参见​系统音色列表。​
更多关于豆包语音合成 2.0 的 context_texts 示例和效果可参考语音指令-示例库。​
​
data.tts_config.emotion​
String​
可选​
设置多情感音色的情感类型，仅当 voice_id 为多情感音色时才需要设置。​
不同音色支持的情感范围不同，可以通过系统音色列表查看各音色支持的情感。默认为空。枚举值如下：​
happy-开心​
sad-悲伤​
angry-生气​
surprised-惊讶​
fear-恐惧​
hate-厌恶​
excited-激动​
coldness-冷漠​
neutral-中性​
data.tts_config.emotion_scale​
Float​
可选​
情感值用于量化情感的强度。数值越高，情感表达越强烈，例如： “开心” 的情感值 5 比 1 更显兴奋。​
仅当 voice_id 为多情感音色时才需要设置。​
取值范围：1.0~5.0，默认值：4.0。​
data.voice_processing_config​
Object​
可选​
语音降噪配置。默认不启用降噪。​
仅扣子企业版支持该配置。​
​
data.voice_processing_config.enable_ans​
Boolean​
可选​
主动噪声抑制。自动识别并过滤掉背景环境中的各种噪音（如键盘声、空调声、街道嘈杂声），让说话者的声音更清晰。​
此功能与下面的 enable_pdns（声纹降噪）只能二选一开启，不能同时使用。​
data.voice_processing_config.enable_pdns​
Boolean​
可选​
声纹降噪。专门针对特定说话人的声音进行优化，能更精准地保留目标人声。​
此功能与上面的 enable_ans只能二选一开启，不能同时使用。​
提供两种模式，你可以根据需要选择：​
自动提取：设置简单，开箱即用。默认为该模式。降噪生效稍微有延迟，服务端需要先听你说一会儿话才能提取出你的声纹特征，在此期间降噪效果可能不佳。另外，提取声纹会受到用户说话场景影响，准确性上可能会弱于主动设置。​
主动设置：降噪效果更精准、更快速，在对话开始时就立即生效。不过需要提前录制声纹并在 voice_print_feature_id 中设置声纹 ID。​
data.voice_print_config.feature_id​
String​
可选​
目标说话人的声纹 ID。当你选择开启 enable_pdns（声纹降噪）并希望使用主动设置模式时，需要在此处填入你提前录制好的声纹 ID。​
你可以通过扣子开发平台录制声纹，具体请参见​声纹识别，或通过​创建声纹 API 上传声纹并获取声纹 ID。​
​
事件示例：​
​
{​
    "id": "7446668538246561828",​
    "event_type": "session.update",​
    "data": {​
        "voice_id": "7426720361733046281", // 音色 ID​
        "speech_rate": 0, // [-50, 100]​
        "longest_silence_ms": 180000,​
        "event_subscriptions": ["error"],​
        "chat_config": {​
            "meta_data": {​
                "a": "123"​
            },​
            "custom_variables": {​
                "a": "123"​
            },​
            "extra_params": {​
                "a": "123"​
            },​
            "plugin_interrupt_mode": "nonblocking", // 控制收到端插件执行中断信号后，没有提交端执行请求时的模型​
            // 端插件的执行模型，默认不阻塞，blocking/nonblocking，阻塞的场景如果不提交端执行请求就会让语音链路一直block住​
            "allow_voice_interrupt": true,​
            "interrupt_config": {​
                "mode": "keyword_prefix",​
                "keywords": ["扣子"]​
            },​
            "parameters": {​
                "a": 123,​
                "b": "abc"​
            }​
        },​
        "turn_detection": {​
            "type": "server_vad", // server_vad/client_vad​
            "prefix_padding_ms": 600,​
            "silence_duration_ms": 500​
        },​
        "asr_config": {​
            "hot_words": ["扣子"],​
            "enable_itn": true,​
            "enable_punc": true,​
            "enable_ddc": true​
        }    ​
    }​
}​
​
提交端插件执行结果​
事件类型：conversation.chat.submit_tool_outputs​
事件说明：你可以将需要客户端执行的操作定义为插件，对话中如果触发这个插件，会收到一个 event_type = “conversation.chat.requires_action” 的下行事件，此时需要执行客户端的操作后，通过此上行事件来提交插件执行后的结果。作用等同于​提交工具执行结果 API。​
事件结构：​
​
参数​
类型​
是否必选​
说明​
id​
String​
 必选​
事件 ID，也就是事件的唯一标识。由客户端生成，在故障排查场景下用于定位具体的事件，便于排查问题。​
event_type​
String ​
必选​
固定为 conversation.chat.submit_tool_outputs。​
data​
chat_id​
String​
必选​
对话 ID。​
tool_outputs​
JSON Array​
必选​
工具执行结果。具体的结构定义可参考​提交工具执行结果 API。​
tool_outputs.tool_call_id​
String​
必选​
上报运行结果的 ID。你可以在端插件请求事件的 tool_calls 字段下查看此 ID。​
tool_outputs.output​
String​
必选​
工具的执行结果。​
​
事件示例：​
​
{​
    "id": "7446668538246561829",​
    "event_type": "conversation.chat.submit_tool_outputs",​
    "data": {​
        "chat_id": "7446675275930271785",​
        "tool_outputs": [​
            {​
                "tool_call_id": "BUJJRUUVEhJGERVeEkRDFV5HEkJAXktLQBZeEEAXREpLSxZFR****=",​
                "output": "{\"url\":\"https://lf3-bot-platform-tos-sign.coze.cn/bot-studio-bot-platform/bot_files/323733792754532/image/jpeg/7446661351415529491/blob****\"}"​
            }​
        ]​
    }​
}​
​
中断 Agent 语音输出​
事件类型：conversation.chat.cancel​
事件说明：发送此事件可取消正在进行的对话。​
事件结构：​
​
参数​
类型​
是否必选​
说明​
id​
string​
 必选​
客户端自行生成的事件 ID，方便定位问题。​
event_type​
string ​
必选​
固定为conversation.chat.cancel。​
​
事件示例：​
​
{​
    "id": "7446668538246561827",​
    "event_type": "conversation.chat.cancel",​
    "data": {}​
}​
​
手动提交对话内容​
事件类型：conversation.message.create​
事件说明：​
若 role=user，提交事件后就会生成语音回复，适合如下的场景，比如帮我解析xx链接，帮我分析这个图片的内容等。​
若 role=assistant，提交事件后会加入到对话的上下文。​
事件结构：​
​
参数​
类型​
是否必选​
说明​
id​
string​
 必​
客户端自行生成的事件 ID，方便定位问题。​
event_type​
string ​
必选​
固定为conversation.message.create。​
data​
role​
string​
​
必选​
发送这条消息的实体。取值： ​
user：代表该条消息内容是用户发送的。 ​
assistant：代表该条消息内容是智能体发送的。​
content_type​
​
string​
必选​
消息内容的类型，支持设置为： ​
text：文本 ​
object_string：多模态内容，即文本和文件的组合、文本和图片的组合，参考​
content​
string​
必选​
消息的内容，支持纯文本、多模态（文本、图片、文件混合输入）、卡片等多种类型的内容。参考 object_string object。​
​
事件示例：​
​
{​
    "id": "7446668538246561826",​
    "event_type": "conversation.message.create",​
    "data": {​
       "role": "user", // user/assistant​
       "content_type":"object_string", // text/object_string​
       "content": "[{\"type\":\"text\",\"text\":\"帮我看看这个PDF里有什么内容？\"},{\"type\":\"file\",\"file_url\":\"https://lf3-appstore-sign.oceancloudapi.com/ocean-cloud-tos/eaafba63-0d96-4ea6-b60c-fbadcf2c25e9.?lk3s=edeb9e45&x-expires=1718296132&x-signature=YtlsUsvSeLJi6x31I%2F4S9X53Y6Y%3D\"}]"​
    }​
}​
​
客户端 VAD 检测​
事件类型：client.vad​
事件说明：客户端侧的 VAD 检测结果，仅在turn_detection.type="client_vad"时生效。​
事件结构：​
​
参数​
类型​
是否必选​
说明​
id​
string​
 必选​
客户端自行生成的事件 ID，方便定位问题。​
event_type​
string ​
必选​
固定为client.vad。​
data​
vad​
boolean​
必选​
客户端自行检测的 vad 值，只需在变化时上传。​
​
事件示例：​
​
{​
    "id": "74466685382465618283",​
    "event_type": "client.vad",​
    "data": {​
       "vad": true/false​
    }​
}​
​
更新安抚配置​
事件类型：session.pre_answer.update ​
事件说明：此事件可更新房间内的安抚配置，若更新成功，会收到下行事件 "session.pre_answer.updated"，否则，会收到下行事件 "error"。​
事件结构：​
​
参数​
类型​
是否必选​
说明​
id​
String​
必选​
客户端自行生成的事件 ID，方便定位问题。​
event_type​
String​
必选​
固定为 session.pre_answer.update。​
data​
Object​
必选​
事件数据，包含安抚配置的详细信息。​
data.pre_answer​
Object​
必选​
安抚配置。​
data.pre_answer.type​
String​
必选​
安抚生成类型，可选类型有 none / audio / text / bot。​
none：取消安抚策略，默认值。​
audio：根据用户上传的音频文件作为安抚语。​
text：根据用户输入的文本生成安抚语。​
bot：指定另一个智能体作为安抚语的生成载体。​
data.pre_answer.file_id​
String​
可选​
仅在 audio 模式下生效，用户需要先通过上传文件接口上传一个音频文件，传入该接口返回的 file_id。当前仅支持 wav\mp3 音频文件。​
data.pre_answer.pre_answer_list​
Array<String>​
可选​
仅在 text 模式下生效，用户可以输入一批文本，会在触发安抚策略时随机选取一段文本作为安抚语播放。每段文本的长度上限为 10 个字符。​
data.pre_answer.bot_id​
String​
可选​
仅在 bot 模式下生效，ASR 识别的文本会同时请求这个 BotID，并将该智能体回复的内容作为安抚语。传入的智能体必须是属于当前用户的智能体。该策略会消耗额外的 Token。​
data.trigger​
Object​
可选​
触发相关配置。​
data.trigger.type​
String​
必选​
安抚策略的触发类型，可选类型有 mandatory / time-trigger / event-driven。​
mandatory：必定触发安抚策略，默认值。​
time-trigger：一段时间后模型没有生成回复，就会触发安抚策略。​
event-driven：模型触发 function call 就会触发安抚策略。​
data.trigger.time_after​
Integer​
可选​
仅在 time-trigger 模式下生效，指定在等待多长时间后触发安抚策略，单位为 ms，取值范围 [0, 3000]，默认值为 1500ms。​
​
事件示例：​
​
{​
    "id": "7446668538246561822",​
    "event_type": "session.pre_answer.update",​
    "data": {​
        "pre_answer": {​
            "type": "none",​
            "file_id": "",​
            "pre_answer_list": [""],​
            "bot_id": ""​
        },​
        "trigger": {​
            "type": "mandatory",​
            "time_after": 1500​
        }​
    }​
}​
​
更新房间模式​
事件类型：mode.update ​
事件说明：更新房间的模式，若更新成功，会收到 mode.updated 下行事件，否则，会收到下行事件 "error"。​
事件结构：​
​
 参数 ​
 类型 ​
 是否必选 ​
 说明 ​
 id ​
 String ​
 必选 ​
客户端自行生成的事件 ID，方便定位问题。​
 event_type ​
 String ​
 必选 ​
固定为 mode.update。 ​
 data ​
 Object ​
 必选 ​
事件数据，包含房间模式和对话设置。 ​
 data.mode ​
 String ​
 必选 ​
房间模式。​
默认为 chat，即对话模式，跟智能体进行聊天。 ​
 data.mode.chat ​
 Object ​
 可选 ​
对话模式下的设置，仅当 mode 为 chat 时生效。 ​
data.chat.user_language ​
 String ​
 可选 ​
用户说话的语种，默认为 common支持中英文、上海话、闽南语，四川、陕西、粤语识别。仅在 data.asr_config.stream_mode 为output_no_stream时可以指定设置语种。当将其设置为下方特定键时，它可以识别指定语言。 ​
英语：en-US​
日语：ja-JP​
印尼语：id-ID​
西班牙语：es-MX​
葡萄牙语：pt-BR​
德语：de-DE​
法语：fr-FR​
韩语：ko-KR​
菲律宾语：fil-PH​
马来语：ms-MY​
泰语：th-TH​
阿拉伯语：ar-SA​
例如，如果输入音频是德语，则此参数传入de-DE​
​
事件示例：​
​
​
{​
    "id": "",​
    "event_type": "mode.update",​
    "data": {​
        "mode": "chat",​
        "chat": {​
            "user_language": "common"​
        }​
    }​
}​
​
语音合成​
事件类型：input_text.generate_audio​
事件说明：你可以主动提交一段文字用来做语音合成，提交的消息不会触发智能体的回复，只会合成音频内容下发到客户端。提交事件的时候如果智能体正在输出语音会被中断输出。适合在和智能体聊天过程中客户端长时间没有响应，智能体可以主动说话暖场的场景。​
事件结构：​
​
参数​
类型​
是否必选​
说明​
id​
String​
必选​
客户端自行生成的事件 ID，方便定位问题。​
event_type​
String​
必选​
固定为 input_text.generate_audio。​
data​
Object​
必选​
事件数据。​
data.mode​
String​
必选​
消息内容的类型，支持设置为：​
text：文本​
data.text​
string​
可选​
当 mode == text 时候必填。长度限制 (0, 1024) 字节​
​
事件示例：​
​
{​
  "id": "744666853824656xxxx",​
  "event_type": "input_text.generate_audio",​
  "data": {​
      "mode": "text",​
      "text": "亲，你怎么不说话了。"​
  }​
}​
​
用户开始说话​
事件类型：input_audio_buffer.start​
事件说明：发送此事件表示用户开始说话。仅在按键说话模式下生效。​
事件结构：​
​
参数​
类型​
是否必选​
说明​
id​
string​
 必选​
客户端自行生成的事件 ID，方便定位问题。​
event_type​
string ​
必选​
固定为 input_audio_buffer.start。​
​
事件示例：​
​
{​
    "id": "7446668538246561827",​
    "event_type": "input_audio_buffer.start",​
    "data": {}​
}​
​
用户结束说话​
事件类型：input_audio_buffer.complete​
事件说明：发送此事件表示用户结束说话。仅在按键说话模式下生效。​
事件结构：​
​
参数​
类型​
是否必选​
说明​
id​
string​
 必选​
客户端自行生成的事件 ID，方便定位问题。​
event_type​
string ​
必选​
固定为 input_audio_buffer.complete。​
​
事件示例：​
​
{​
    "id": "7446668538246561827",​
    "event_type": "input_audio_buffer.complete",​
    "data": {}​
}​
​
​