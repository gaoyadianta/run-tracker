1. AI相关
 - 连接失败的时候，要显示具体原因；
 - 字幕框的长度，做一下限制，显示3行即可，再多内容就滚动显示；
 - 现有实现基于火山RTC，需抽象为可替换方案（优先WebSocket），并明确VAD/STT/TTS的端侧/云侧职责；

2. UI相关
 - 

3. Recent activity相关
 - 点击查看其中的一条跑步信息，无法正确显示跑步的轨迹（可能是zoom不正确）

4. 高德地图
 - 日志如下
 ```shell
 2025-08-23 21:25:13.734 15412-15639 authErrLog              com.sdevprem.runtrack                I  ================================================================================
2025-08-23 21:25:13.734 15412-15639 authErrLog              com.sdevprem.runtrack                I                                     鉴权错误信息                                  
2025-08-23 21:25:13.734 15412-15639 authErrLog              com.sdevprem.runtrack                I  ================================================================================
2025-08-23 21:25:13.734 15412-15639 authErrLog              com.sdevprem.runtrack                I  |SHA1Package:24:A2:9D:86:1B:1D:CB:12:70:21:6D:4E:51:45:5A:96:4E:B5:55:38:com.sd|
2025-08-23 21:25:13.734 15412-15639 authErrLog              com.sdevprem.runtrack                I  |evprem.runtrack                                                               |
2025-08-23 21:25:13.734 15412-15639 authErrLog              com.sdevprem.runtrack                I  |key:6752dfdca6a51d248a7817cb48820150                                          |
2025-08-23 21:25:13.734 15412-15639 authErrLog              com.sdevprem.runtrack                I  |csid:2edf2af141b04b04b122ed087a652802                                         |
2025-08-23 21:25:13.734 15412-15639 authErrLog              com.sdevprem.runtrack                I  |gsid:033103009250175595551346600030480667870                                  |
2025-08-23 21:25:13.734 15412-15639 authErrLog              com.sdevprem.runtrack                I  |json:{"info":"INVALID_USER_SCODE","infocode":"10008","status":"0","sec_code_de|
2025-08-23 21:25:13.735 15412-15639 authErrLog              com.sdevprem.runtrack                I  |bug":"d41d8cd98f00b204e9800998ecf8427e","key":"6752dfdca6a51d248a7817cb4882015|
2025-08-23 21:25:13.735 15412-15639 authErrLog              com.sdevprem.runtrack                I  |0","sec_code":"43675d5db261b13e5c0a7530d6328e78"}                             |
2025-08-23 21:25:13.735 15412-15639 authErrLog              com.sdevprem.runtrack                I                                                                                 
2025-08-23 21:25:13.735 15412-15639 authErrLog              com.sdevprem.runtrack                I  请在高德开放平台官网中搜索"INVALID_USER_SCODE"相关内容进行解决
2025-08-23 21:25:13.735 15412-15639 authErrLog              com.sdevprem.runtrack                I  ================================================================================
```

