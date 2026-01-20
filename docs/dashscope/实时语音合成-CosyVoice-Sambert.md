语音合成，又称文本转语音（Text-to-Speech，TTS），是将文本转换为自然语音的技术。该技术基于机器学习算法，通过学习大量语音样本，掌握语言的韵律、语调和发音规则，从而在接收到文本输入时生成真人般自然的语音内容。

核心功能
实时生成高保真语音，支持中英等多语种自然发声

提供声音复刻能力，快速定制个性化音色

支持流式输入输出，低延迟响应实时交互场景

可调节语速、语调、音量与码率，精细控制语音表现

兼容主流音频格式，最高支持48kHz采样率输出

适用范围
支持的地域：仅支持北京地域，需使用该地域的API Key

支持的模型：

CosyVoice：cosyvoice-v3-plus、cosyvoice-v3-flash、cosyvoice-v2、cosyvoice-v1

Sambert：sambert-zhinan-v1、sambert-zhiqi-v1、sambert-zhichu-v1、sambert-zhide-v1、sambert-zhijia-v1、sambert-zhiru-v1、sambert-zhiqian-v1、sambert-zhixiang-v1、sambert-zhiwei-v1、sambert-zhihao-v1、sambert-zhijing-v1、sambert-zhiming-v1、sambert-zhimo-v1、sambert-zhina-v1、sambert-zhishu-v1、sambert-zhistella-v1、sambert-zhiting-v1、sambert-zhixiao-v1、sambert-zhiya-v1、sambert-zhiye-v1、sambert-zhiying-v1、sambert-zhiyuan-v1、sambert-zhiyue-v1、sambert-zhigui-v1、sambert-zhishuo-v1、sambert-zhimiao-emo-v1、sambert-zhimao-v1、sambert-zhilun-v1、sambert-zhifei-v1、sambert-zhida-v1、sambert-camila-v1、sambert-perla-v1、sambert-indah-v1、sambert-clara-v1、sambert-hanna-v1、sambert-beth-v1、sambert-betty-v1、sambert-cally-v1、sambert-cindy-v1、sambert-eva-v1、sambert-donna-v1、sambert-brian-v1、sambert-waan-v1，详情请参见Sambert模型列表

支持的音色：

CosyVoice：参见CosyVoice音色列表

Sambert：参见Sambert音色列表

模型选型




场景

推荐模型

理由

注意事项

品牌形象语音定制/个性化语音克隆服务

cosyvoice-v3-plus

声音复刻能力最强，支持48kHz高音质输出，高音质+声音复刻，打造拟人化品牌声纹

成本较高（2元/万字符），建议用于核心场景

智能客服 / 语音助手

cosyvoice-v3-flash

成本最低（1元/万字符），支持流式交互、情感表达，响应快，性价比高

移动端嵌入式语音合成

CosyVoice全系列

SDK全覆盖，资源优化好，流式支持强，延迟可控

cosyvoice-v1不支持 SSML

方言广播系统

cosyvoice-v3-flash、cosyvoice-v3-plus

支持东北话、闽南语等多种方言，适合地方内容播报

cosyvoice-v3-plus成本较高（2元/万字符）

教育类应用（含公式朗读）

cosyvoice-v2、cosyvoice-v3-flash、cosyvoice-v3-plus

支持LaTeX公式转语音，适合数理化课程讲解

cosyvoice-v2和cosyvoice-v3-plus成本较高（2元/万字符），cosyvoice-v2不支持设置情感

结构化语音播报（新闻/公告）

cosyvoice-v3-plus、cosyvoice-v3-flash、cosyvoice-v2

支持SSML控制语速、停顿、发音等，提升播报专业度

需额外开发 SSML 生成逻辑，不支持设置情感

语音与文本精准对齐（如字幕生成、教学回放、听写训练）

cosyvoice-v3-flash、cosyvoice-v3-plus、cosyvoice-v2/Sambert

支持时间戳输出，可实现合成语音与原文同步

需显式启用时间戳功能，默认关闭，cosyvoice-v2不支持设置情感，Sambert不支持流式输入

多语言出海产品

cosyvoice-v3-flash、cosyvoice-v3-plus、Sambert

支持多语种

Sambert不支持流式输入，价格高于cosyvoice-v3-flash

更多说明请参见模型功能特性对比。

快速开始
下面是调用API的示例代码。更多常用场景的代码示例，请参见GitHub。

您需要已获取与配置 API Key并配置API Key到环境变量。如果通过SDK调用，还需要安装DashScope SDK。

CosyVoice

将合成音频保存为文件将LLM生成的文本实时转成语音并通过扬声器播放
PythonJava
 
# coding=utf-8

import dashscope
from dashscope.audio.tts_v2 import *

# 若没有将API Key配置到环境变量中，需将your-api-key替换为自己的API Key
# dashscope.api_key = "your-api-key"

# 模型
# 不同模型版本需要使用对应版本的音色：
# cosyvoice-v3-flash/cosyvoice-v3-plus：使用longanyang等音色。
# cosyvoice-v2：使用longxiaochun_v2等音色。
model = "cosyvoice-v3-flash"
# 音色
voice = "longanyang"

# 实例化SpeechSynthesizer，并在构造方法中传入模型（model）、音色（voice）等请求参数
synthesizer = SpeechSynthesizer(model=model, voice=voice)
# 发送待合成文本，获取二进制音频
audio = synthesizer.call("今天天气怎么样？")
# 首次发送文本时需建立 WebSocket 连接，因此首包延迟会包含连接建立的耗时
print('[Metric] requestId为：{}，首包延迟为：{}毫秒'.format(
    synthesizer.get_last_request_id(),
    synthesizer.get_first_package_delay()))

# 将音频保存至本地
with open('output.mp3', 'wb') as f:
    f.write(audio)
Sambert

将合成音频保存为文件将合成的音频通过扬声器播放
PythonJava

 
import dashscope
from dashscope.audio.tts import SpeechSynthesizer

# 若没有将API Key配置到环境变量中，需将下面这行代码注释放开，并将apiKey替换为自己的API Key
# dashscope.api_key = "apiKey"
result = SpeechSynthesizer.call(model='sambert-zhichu-v1',
                                # 当text内容的语种发生变化时，请确认model是否匹配。不同model支持不同的语种，详情请参见Sambert音色列表中的“语言”列。
                                text='今天天气怎么样',
                                sample_rate=48000,
                                format='wav')
print('requestId: ', result.get_response()['request_id'])
if result.get_audio_data() is not None:
    with open('output.wav', 'wb') as f:
        f.write(result.get_audio_data())
print(' get response: %s' % (result.get_response()))


API参考
语音合成-CosyVoice API参考

声音复刻-CosyVoice API参考

语音合成-Sambert API参考

模型应用上架及备案
参见应用合规备案。

模型功能特性对比






功能/特性

cosyvoice-v3-plus

cosyvoice-v3-flash

cosyvoice-v2

cosyvoice-v1

Sambert

支持语言

因音色而异：中文（普通话、广东话、东北话、甘肃话、贵州话、河南话、湖北话、江西话、闽南话、宁夏话、山西话、陕西话、山东话、上海话、四川话、天津话、云南话）、英文、法语、德语、日语、韩语、俄语

因音色而异：中文、英文（英式、美式）、韩语、日语

因音色而异：中文、英文

因音色而异：中文、英文、美式英文、意大利语、西班牙语、印尼语、法语、德语、泰语

音频格式

pcm、wav、mp3、opus

pcm、wav、mp3

音频采样率

8kHz、16kHz、22.05kHz、24kHz、44.1kHz、48kHz

16kHz、48kHz

声音复刻

支持 参见CosyVoice声音复刻API

不支持

SSML

支持 参见SSML标记语言介绍；此功能适用于复刻音色，以及音色列表中已标记为支持的系统音色

不支持

支持

LaTeX

支持 参见LaTeX 公式转语音

不支持

音量调节

支持

语速调节

支持

语调（音高）调节

支持

码率调节

支持 仅opus格式音频支持

不支持

时间戳

支持 默认关闭，可开启；此功能适用于复刻音色，以及音色列表中已标记为支持的系统音色

不支持

支持 默认关闭，可开启

指令控制（Instruct）

支持 此功能适用于复刻音色，以及音色列表中已标记为支持的系统音色

不支持

流式输入

支持

不支持

流式输出

支持

限流（RPS）

3

20

接入方式

Java/Python/Android/iOS SDK、WebSocket API

价格

2元/万字符

1元/万字符

2元/万字符

1元/万字符

常见问题
Q：语音合成的发音读错怎么办？多音字如何控制发音？
将多音字替换成同音的其他汉字，快速解决发音问题。

使用SSML标记语言控制发音：Sambert和Cosyvoice都支持SSML。