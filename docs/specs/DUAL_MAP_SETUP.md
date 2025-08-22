# 双地图支持配置指南

本应用现已支持 Google Maps 和高德地图的双地图系统，会根据用户所在地区自动选择合适的地图服务。

## 自动切换逻辑

应用会根据以下条件自动选择地图服务：

1. **系统语言和地区**: 如果系统设置为中文或地区设置为中国，将使用高德地图
2. **时区检测**: 如果设备时区设置为中国时区（Asia/Shanghai），将使用高德地图  
3. **Google Play Services**: 如果无法检测到Google Play Services，将回退到高德地图
4. **默认**: 其他情况使用Google Maps

## API Key配置

### 1. Google Maps API Key（原有配置）

在 `local.properties` 文件中添加：
```properties
MAPS_API_KEY=your_google_maps_api_key_here
```

获取方式：
1. 访问 [Google Cloud Console](https://console.cloud.google.com/)
2. 创建项目或选择现有项目
3. 启用 Maps SDK for Android API
4. 创建 API Key
5. 限制 API Key 使用范围（推荐）

### 2. 高德地图API Key（新增配置）

在 `local.properties` 文件中添加：
```properties
AMAP_API_KEY=your_amap_api_key_here
```

获取方式：
1. 访问 [高德开放平台](https://lbs.amap.com/)
2. 注册开发者账号
3. 进入控制台 → 应用管理 → 我的应用
4. 创建新应用，选择 Android 平台
5. 填写应用信息，包括 SHA1 签名和包名
6. 获取 API Key

#### 获取Android签名SHA1
```bash
# Debug版本签名
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android

# Release版本签名（如果有的话）
keytool -list -v -keystore /path/to/your/release.keystore -alias your_alias
```

## 完整的 local.properties 示例

```properties
# Google Maps API Key
MAPS_API_KEY=AIzaSyBxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

# 高德地图 API Key  
AMAP_API_KEY=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

# 其他配置...
sdk.dir=/Users/xxx/Library/Android/sdk
```

## 权限配置

应用已自动包含所需权限，无需额外配置：

### Google Maps权限（已有）
- 网络访问权限
- 位置权限
- Google Play Services

### 高德地图权限（已添加）
- 网络访问权限  
- 位置权限
- 写入外部存储权限（用于缓存）

## 测试验证

### 1. 测试Google Maps（海外环境）
```bash
# 设置系统语言为英文
# 设置地区为美国/其他海外地区
# 确保Google Play Services可用
# 启动应用，验证Google Maps正常工作
```

### 2. 测试高德地图（国内环境）
```bash
# 设置系统语言为中文
# 设置地区为中国
# 或者设置时区为Asia/Shanghai
# 启动应用，验证高德地图正常工作
```

### 3. 功能验证清单
- [ ] 地图显示正常
- [ ] 实时位置追踪工作
- [ ] 跑步路径绘制正确
- [ ] 起点/终点标记显示
- [ ] 地图截图功能正常
- [ ] 相机跟随当前位置
- [ ] 应用在前台/后台切换正常

## 故障排除

### 1. Google Maps相关问题
- 检查API Key是否正确配置
- 确认Google Play Services已安装且为最新版本
- 检查网络连接
- 验证API Key权限和配额

### 2. 高德地图相关问题
- 检查API Key是否正确配置
- 确认包名和签名与申请时一致
- 检查网络连接
- 验证API Key是否已审核通过

### 3. 自动切换问题
- 检查系统语言和地区设置
- 查看应用日志确认选择的地图服务
- 手动测试不同的系统设置

## 开发调试

如需强制使用特定地图服务进行调试，可临时修改 `MapProviderFactory.kt` 中的 `detectRegion()` 方法：

```kotlin
private fun detectRegion(): String {
    // 强制返回指定的地图服务用于调试
    return MapProvider.PROVIDER_AMAP  // 或 MapProvider.PROVIDER_GOOGLE
}
```

记得在正式版本中移除此类调试代码。