实时语音识别服务可将音频流实时转换为带标点的文本，实现“边说边出文字”的效果。无论是麦克风语音、会议录音还是本地音频文件，都能轻松转录。服务广泛应用于会议实时记录、直播字幕、语音聊天、智能客服等场景。

核心功能
支持多语种实时语音识别，覆盖中英文及多种方言

支持热词定制，可提升特定词汇的识别准确率

支持时间戳输出，生成结构化识别结果

灵活采样率与多种音频格式，适配不同录音环境

可选VAD（Voice Activity Detection），自动过滤静音片段，提升长音频处理效率

SDK + WebSocket 接入，低延迟稳定服务

适用范围
支持的模型：

中国内地国际
在中国内地部署模式下，接入点与数据存储均位于北京地域，模型推理计算资源仅限于中国内地。

调用以下模型时，请选择北京地域的API Key：

Fun-ASR：fun-asr-realtime（稳定版，当前等同fun-asr-realtime-2025-11-07）、fun-asr-realtime-2025-11-07（快照版）、fun-asr-realtime-2025-09-15（快照版）

Gummy：gummy-realtime-v1、gummy-chat-v1

Paraformer：paraformer-realtime-v2、paraformer-realtime-v1、paraformer-realtime-8k-v2、paraformer-realtime-8k-v1

更多信息请参见模型列表

模型选型



场景

推荐模型

理由

中文普通话识别（会议/直播）

fun-asr-realtime、fun-asr-realtime-2025-11-07、paraformer-realtime-v2

多格式兼容，高采样率支持，稳定延迟

多语种识别（国际会议）

gummy-realtime-v1、paraformer-realtime-v2

覆盖多语种

中文方言识别（客服/政务）

fun-asr-realtime-2025-11-07、paraformer-realtime-v2

覆盖多地方言

中英日混合识别（课堂/演讲）

fun-asr-realtime、fun-asr-realtime-2025-11-07

中英日识别优化

短音频快速交互（智能客服）

gummy-chat-v1

1分钟内音频，低成本，支持多语种

低带宽电话录音转写

paraformer-realtime-8k-v2

支持8kHz，默认情感识别

热词定制场景（品牌名/专有术语）

Gummy、Paraformer、Fun-ASR最新版本模型

热词可开关，易于迭代配置

更多说明请参见模型功能特性对比。

快速开始
下面是调用API的示例代码。更多常用场景的代码示例，请参见GitHub。

您需要已获取API Key并配置API Key到环境变量。如果通过SDK调用，还需要安装DashScope SDK。

Fun-ASRGummyParaformer
识别传入麦克风的语音识别本地音频文件
实时语音识别可以识别麦克风中传入的语音并输出识别结果，达到“边说边出文字”的效果。

JavaPython
 
import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionResult;
import com.alibaba.dashscope.common.ResultCallback;
import com.alibaba.dashscope.utils.Constants;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.TargetDataLine;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        // 以下为北京地域url，若使用新加坡地域的模型，需将url替换为：wss://dashscope-intl.aliyuncs.com/api-ws/v1/inference
        Constants.baseWebsocketApiUrl = "wss://dashscope.aliyuncs.com/api-ws/v1/inference";
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(new RealtimeRecognitionTask());
        executorService.shutdown();
        executorService.awaitTermination(1, TimeUnit.MINUTES);
        System.exit(0);
    }
}

class RealtimeRecognitionTask implements Runnable {
    @Override
    public void run() {
        RecognitionParam param = RecognitionParam.builder()
                .model("fun-asr-realtime")
                // 新加坡和北京地域的API Key不同。获取API Key：https://help.aliyun.com/zh/model-studio/get-api-key
                // 若没有配置环境变量，请用百炼API Key将下行替换为：.apiKey("sk-xxx")
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .format("pcm")
                .sampleRate(16000)
                .build();
        Recognition recognizer = new Recognition();

        ResultCallback<RecognitionResult> callback = new ResultCallback<RecognitionResult>() {
            @Override
            public void onEvent(RecognitionResult result) {
                if (result.isSentenceEnd()) {
                    System.out.println("Final Result: " + result.getSentence().getText());
                } else {
                    System.out.println("Intermediate Result: " + result.getSentence().getText());
                }
            }

            @Override
            public void onComplete() {
                System.out.println("Recognition complete");
            }

            @Override
            public void onError(Exception e) {
                System.out.println("RecognitionCallback error: " + e.getMessage());
            }
        };
        try {
            recognizer.call(param, callback);
            // 创建音频格式
            AudioFormat audioFormat = new AudioFormat(16000, 16, 1, true, false);
            // 根据格式匹配默认录音设备
            TargetDataLine targetDataLine =
                    AudioSystem.getTargetDataLine(audioFormat);
            targetDataLine.open(audioFormat);
            // 开始录音
            targetDataLine.start();
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            long start = System.currentTimeMillis();
            // 录音50s并进行实时转写
            while (System.currentTimeMillis() - start < 50000) {
                int read = targetDataLine.read(buffer.array(), 0, buffer.capacity());
                if (read > 0) {
                    buffer.limit(read);
                    // 将录音音频数据发送给流式识别服务
                    recognizer.sendAudioFrame(buffer);
                    buffer = ByteBuffer.allocate(1024);
                    // 录音速率有限，防止cpu占用过高，休眠一小会儿
                    Thread.sleep(20);
                }
            }
            recognizer.stop();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 任务结束后关闭 Websocket 连接
            recognizer.getDuplexApi().close(1000, "bye");
        }

        System.out.println(
                "[Metric] requestId: "
                        + recognizer.getLastRequestId()
                        + ", first package delay ms: "
                        + recognizer.getFirstPackageDelay()
                        + ", last package delay ms: "
                        + recognizer.getLastPackageDelay());
    }
}
应用于生产环境
提升识别效果
选择正确采样率的模型：8kHz 的电话音频应直接使用 8kHz 模型，而不是升采样到 16kHz 再识别，这样可以避免信息失真，获得更佳效果。

使用热词功能：针对业务中的专有名词、人名、品牌名等，配置热词可以显著提升识别准确率，详情请参见定制热词-Paraformer/Fun-ASR、定制热词-Gummy。

优化输入音频质量：尽量使用高质量的麦克风，并确保录音环境信噪比高、无回声。在应用层面，可以集成降噪（如RNNoise）、回声消除（AEC）等算法对音频进行预处理，以获得更纯净的音频。

明确指定识别语种：对于Paraformer-v2等支持多语种的模型，如果在调用时能预先确定音频的语种（如使用Language_hints参数指定语种为［'zh','en'］），可以帮助模型收敛，避免在相似发音的语种间混淆，提升准确性。

语气词过滤：对于Paraformer模型，可以通过设置参数disfluency_removal_enabled开启语气词过滤功能，获得更书面、更易读的文本结果。

设置容错策略
客户端重连：客户端应实现断线自动重连机制，以应对网络抖动。以Python SDK为例，您可以参考如下建议：

捕获异常：在Callback类中实现on_error方法。当dashscope SDK遇到网络错误或其他问题时，会调用该方法。

状态通知：当on_error被触发时，设置重连信号。在Python中可以使用threading.Event，它是一种线程安全的信号标志。

重连循环：将主逻辑包裹在一个for循环中（例如重试3次）。当检测到重连信号后，当前轮次的识别会中断，清理资源，然后等待几秒钟，再次进入循环，创建一个全新的连接。

设置心跳防止连接断开：当需要与服务端保持长连接时，可将参数heartbeat设置为true，即使音频中长时间没有声音，与服务端的连接也不会中断。

模型限流：在调用模型接口时请注意模型的限流规则。

API参考
Fun-ASR实时语音识别API参考

Gummy实时长语音识别API参考

Gummy实时短语音（一句话）识别API参考

Paraformer实时语音识别API参考

模型功能特性对比









功能/特性

fun-asr-realtime、fun-asr-realtime-2025-11-07

fun-asr-realtime-2025-09-15

gummy-realtime-v1

gummy-chat-v1

paraformer-realtime-v2

paraformer-realtime-v1

paraformer-realtime-8k-v2

paraformer-realtime-8k-v1

核心场景

视频直播、会议、三语教学等

视频直播、会议、双语教学等

长语音流式识别（会议、直播）

短语音交互（对话、指令）

长语音流式识别（会议、直播）

电话客服等

支持语言

中文（普通话、粤语、吴语、闽南语、客家话、赣语、湘语、晋语；并支持中原、西南、冀鲁、江淮、兰银、胶辽、东北、北京、港台等，包括河南、陕西、湖北、四川、重庆、云南、贵州、广东、广西、河北、天津、山东、安徽、南京、江苏、杭州、甘肃、宁夏等地区官话口音）、英文、日语

中文（普通话）、英文

中文、英文、日语、韩语、法语、德语、西班牙语、意大利语、俄语、粤语、葡萄牙语、印尼语、阿拉伯语、泰语、印地语、丹麦语、乌尔都语、土耳其语、荷兰语、马来语、越南语

中文（普通话、粤语、吴语、闽南语、东北话、甘肃话、贵州话、河南话、湖北话、湖南话、宁夏话、山西话、陕西话、山东话、四川话、天津话、江西话、云南话、上海话）、英文、日语、韩语、德语、法语、俄语

中文（普通话）

支持的音频格式

pcm、wav、mp3、opus、speex、aac、amr

采样率

16kHz

≥ 16kHz

16kHz

任意采样率

16kHz

8kHz

声道

单声道

输入形式

二进制音频流

音频大小/时长

不限

1分钟以内

不限

情感识别

不支持

支持 默认开启，可关闭

不支持

敏感词过滤

不支持

说话人分离

不支持

语气词过滤

支持 默认关闭，可开启

不支持

支持 默认关闭，可开启

时间戳

支持 固定开启

标点符号预测

支持 固定开启

支持 默认开启，可关闭

支持 固定开启

支持 默认开启，可关闭

支持 固定开启

热词

支持 可配置

ITN

支持 固定开启

VAD

支持 固定开启

限流（RPS）

20

10

20

接入方式

Java/Python/Android/iOS SDK、WebSocket API

价格

中国内地：0.00033元/秒

国际：0.00066元/秒

中国内地：0.00033元/秒

中国内地：0.00015元/秒

中国内地：0.00024元/秒

