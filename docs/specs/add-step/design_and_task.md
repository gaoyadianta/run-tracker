# 步频、步数功能设计与任务规划

## 1. 需求分析

### 1.1 功能需求
- **实时显示**: 在跑步界面实时显示步频(steps/min)和步数(total steps)
- **UI集成**: 与现有的距离、卡路里、配速统一显示格式
- **后台支持**: 支持后台运行时的数据更新和记录
- **历史数据**: 在历史跑步记录中显示对应的步频和步数
- **权限处理**: 处理必要的权限申请和无权限时的降级体验

### 1.2 技术方案
采用Android系统级计步API:
- `Sensor.TYPE_STEP_COUNTER`: 累积步数计数器（低功耗）
- `Sensor.TYPE_STEP_DETECTOR`: 单步检测器（高实时性）
- 优势：系统级优化、低功耗、硬件加速支持

## 2. 架构设计

### 2.1 系统架构集成点

```
TrackingManager
├── LocationTrackingManager (现有)
├── TimeTracker (现有)  
├── StepTrackingManager (新增) ← 核心新增组件
└── BackgroundTrackingManager (现有)
```

### 2.2 数据流设计

```
StepSensor → StepTrackingManager → TrackingManager → CurrentRunState → UI
                     ↓
            StepTrackingInfo → Database (历史数据)
```

### 2.3 核心组件设计

#### 2.3.1 数据模型扩展
```kotlin
// CurrentRunState 扩展
data class CurrentRunState(
    val distanceInMeters: Int = 0,
    val speedInKMH: Float = 0f,
    val isTracking: Boolean = false,
    val pathPoints: List<PathPoint> = emptyList(),
    // 新增步数相关字段
    val totalSteps: Int = 0,           // 总步数
    val stepsPerMinute: Float = 0f,    // 步频
    val initialStepCount: Int? = null   // 记录开始跑步时的初始步数
)

// Run 实体扩展  
@Entity(tableName = "running_table")
data class Run(
    var img: Bitmap,
    var timestamp: Date = Date(),
    var avgSpeedInKMH: Float = 0f,
    var distanceInMeters: Int = 0,
    var durationInMillis: Long = 0L,
    var caloriesBurned: Int = 0,
    // 新增字段
    var totalSteps: Int = 0,           // 总步数
    var avgStepsPerMinute: Float = 0f, // 平均步频
    
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0
)
```

#### 2.3.2 步数追踪管理器
```kotlin
interface StepTrackingManager {
    val stepTrackingInfo: StateFlow<StepTrackingInfo>
    
    fun startStepTracking(callback: StepCallback)
    fun stopStepTracking()
    fun resetStepTracking()
    
    interface StepCallback {
        fun onStepUpdate(stepInfo: StepTrackingInfo)
    }
}

data class StepTrackingInfo(
    val totalSteps: Int = 0,           // 从开始跑步到现在的总步数
    val stepsPerMinute: Float = 0f,    // 当前步频
    val isStepSensorAvailable: Boolean = true
)
```

### 2.4 权限需求分析

Android系统的计步传感器**不需要额外权限**:
- `Sensor.TYPE_STEP_COUNTER` 和 `Sensor.TYPE_STEP_DETECTOR` 不需要危险权限
- 属于系统内置传感器，应用可直接访问
- **结论**: 无需新增权限申请流程

## 3. 详细技术实现

### 3.1 步数追踪管理器实现

```kotlin
@Singleton
class DefaultStepTrackingManager @Inject constructor(
    @ApplicationContext private val context: Context
) : StepTrackingManager {
    
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    
    private val _stepTrackingInfo = MutableStateFlow(StepTrackingInfo())
    override val stepTrackingInfo = _stepTrackingInfo.asStateFlow()
    
    private var callback: StepCallback? = null
    private var initialStepCount: Int? = null
    private var stepCountBuffer = mutableListOf<Long>() // 用于计算步频
    
    private val stepSensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            event?.let { handleSensorEvent(it) }
        }
        
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // 处理精度变化
        }
    }
    
    override fun startStepTracking(callback: StepCallback) {
        this.callback = callback
        
        val isAvailable = stepCounterSensor != null || stepDetectorSensor != null
        _stepTrackingInfo.value = _stepTrackingInfo.value.copy(
            isStepSensorAvailable = isAvailable
        )
        
        if (!isAvailable) return
        
        // 优先使用 STEP_COUNTER，降级使用 STEP_DETECTOR
        val sensor = stepCounterSensor ?: stepDetectorSensor
        sensor?.let {
            sensorManager.registerListener(
                stepSensorListener, 
                it, 
                SensorManager.SENSOR_DELAY_UI
            )
        }
    }
    
    private fun handleSensorEvent(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> handleStepCounter(event.values[0].toInt())
            Sensor.TYPE_STEP_DETECTOR -> handleStepDetector()
        }
    }
    
    private fun handleStepCounter(totalSystemSteps: Int) {
        if (initialStepCount == null) {
            initialStepCount = totalSystemSteps
        }
        
        val sessionSteps = totalSystemSteps - (initialStepCount ?: 0)
        updateStepInfo(sessionSteps)
    }
    
    private fun updateStepInfo(steps: Int) {
        val currentTime = System.currentTimeMillis()
        stepCountBuffer.add(currentTime)
        
        // 保持最近60秒的数据用于计算步频
        stepCountBuffer.removeAll { currentTime - it > 60_000 }
        
        val stepsPerMinute = if (stepCountBuffer.size > 1) {
            val timeSpan = (stepCountBuffer.last() - stepCountBuffer.first()) / 1000f / 60f
            if (timeSpan > 0) stepCountBuffer.size / timeSpan else 0f
        } else 0f
        
        val stepInfo = StepTrackingInfo(
            totalSteps = steps,
            stepsPerMinute = stepsPerMinute,
            isStepSensorAvailable = true
        )
        
        _stepTrackingInfo.value = stepInfo
        callback?.onStepUpdate(stepInfo)
    }
}
```

### 3.2 TrackingManager 集成

```kotlin
class TrackingManager @Inject constructor(
    private val locationTrackingManager: LocationTrackingManager,
    private val timeTracker: TimeTracker,
    private val backgroundTrackingManager: BackgroundTrackingManager,
    private val stepTrackingManager: StepTrackingManager // 新增
) {
    
    private val stepCallback = object : StepTrackingManager.StepCallback {
        override fun onStepUpdate(stepInfo: StepTrackingInfo) {
            _currentRunState.update { state ->
                state.copy(
                    totalSteps = stepInfo.totalSteps,
                    stepsPerMinute = stepInfo.stepsPerMinute
                )
            }
        }
    }
    
    fun startResumeTracking() {
        if (isTracking) return
        if (isFirst) {
            postInitialValue()
            backgroundTrackingManager.startBackgroundTracking()
            stepTrackingManager.startStepTracking(stepCallback) // 新增
            isFirst = false
        }
        // ... 现有逻辑
    }
    
    fun stop() {
        // ... 现有逻辑
        stepTrackingManager.stopStepTracking() // 新增
        stepTrackingManager.resetStepTracking() // 新增
    }
}
```

### 3.3 UI层集成

#### 3.3.1 CurrentRunStatsCard 扩展
```kotlin
@Composable
private fun RunningStats(
    runState: CurrentRunStateWithCalories
) {
    Row(
        horizontalArrangement = Arrangement.SpaceAround,
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp)
            .height(IntrinsicSize.Min)
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        // 现有的三个统计项：距离、卡路里、配速
        RunningStatsItem(...)
        VerticalDivider(...)
        RunningStatsItem(...)
        VerticalDivider(...)
        RunningStatsItem(...)
        
        // 新增：步数统计
        VerticalDivider(...)
        RunningStatsItem(
            modifier = Modifier,
            painter = painterResource(id = R.drawable.ic_raising_hand), // 使用现有图标
            unit = "steps",
            value = runState.currentRunState.totalSteps.toString()
        )
        
        // 新增：步频统计  
        VerticalDivider(...)
        RunningStatsItem(
            modifier = Modifier,
            painter = painterResource(id = R.drawable.stopwatch), // 使用现有图标
            unit = "spm", // steps per minute
            value = String.format("%.1f", runState.currentRunState.stepsPerMinute)
        )
    }
}
```

#### 3.3.2 历史数据UI集成
```kotlin
@Composable
private fun RunInfo(
    modifier: Modifier = Modifier,
    run: Run
) {
    Column(modifier) {
        Text(text = run.timestamp.getDisplayDate(), ...)
        Spacer(modifier = Modifier.size(12.dp))
        Text(text = "${(run.distanceInMeters / 1000f)} km", ...)
        Spacer(modifier = Modifier.size(12.dp))
        Row {
            Text(text = "${run.caloriesBurned} kcal", ...)
            Spacer(modifier = Modifier.size(8.dp))
            Text(text = "${run.avgSpeedInKMH} km/hr", ...)
            Spacer(modifier = Modifier.size(8.dp))
            // 新增：步数显示
            Text(text = "${run.totalSteps} steps", ...)
            Spacer(modifier = Modifier.size(8.dp))
            // 新增：步频显示  
            Text(text = "${String.format("%.1f", run.avgStepsPerMinute)} spm", ...)
        }
    }
}
```

### 3.4 数据库迁移

```kotlin
// 在 RunTrackDB 中添加迁移
@Database(
    entities = [Run::class],
    version = 2, // 版本升级
    exportSchema = false
)
abstract class RunTrackDB : RoomDatabase() {
    
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE running_table ADD COLUMN totalSteps INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE running_table ADD COLUMN avgStepsPerMinute REAL NOT NULL DEFAULT 0.0"
                )
            }
        }
    }
}

// 在 AppModule 中更新数据库构建
@Provides
@Singleton
fun provideRunningDB(@ApplicationContext context: Context): RunTrackDB = 
    Room.databaseBuilder(context, RunTrackDB::class.java, RUN_TRACK_DB_NAME)
        .addMigrations(RunTrackDB.MIGRATION_1_2) // 添加迁移
        .build()
```

### 3.5 无传感器降级处理

```kotlin
@Composable
fun StepUnavailableWarning() {
    if (!stepTrackingInfo.isStepSensorAvailable) {
        Card(
            modifier = Modifier.padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "设备不支持步数检测功能",
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
```

## 4. 详细任务拆分

### 4.1 Phase 1: 核心数据层实现 (优先级: 🔴 最高)
**预估时间**: 4-5小时

#### 任务1.1: 数据模型扩展
- [ ] 扩展 `CurrentRunState` 添加步数相关字段
- [ ] 扩展 `Run` 实体添加步数和步频字段
- [ ] 创建 `StepTrackingInfo` 数据类

#### 任务1.2: 数据库迁移
- [ ] 创建数据库迁移脚本（v1 -> v2）
- [ ] 更新 `RunTrackDB` 版本和迁移配置
- [ ] 测试数据库迁移的正确性

#### 任务1.3: 步数追踪管理器实现
- [ ] 创建 `StepTrackingManager` 接口
- [ ] 实现 `DefaultStepTrackingManager`
- [ ] 集成传感器监听和数据处理逻辑
- [ ] 实现步频计算算法

### 4.2 Phase 2: TrackingManager 集成 (优先级: 🔴 最高)  
**预估时间**: 2-3小时

#### 任务2.1: TrackingManager 更新
- [ ] 在 `TrackingManager` 中集成 `StepTrackingManager`
- [ ] 实现步数回调处理逻辑
- [ ] 更新开始/暂停/停止跟踪的生命周期管理

#### 任务2.2: DI配置更新
- [ ] 在 `AppModule` 中添加 `StepTrackingManager` 的DI配置
- [ ] 确保依赖注入正确配置

### 4.3 Phase 3: UI层集成 (优先级: 🟡 高)
**预估时间**: 3-4小时

#### 任务3.1: 实时UI更新
- [ ] 更新 `CurrentRunStatsCard` 添加步数和步频显示
- [ ] 调整UI布局适配新增的统计项（可能需要分行显示）
- [ ] 确保UI响应式更新

#### 任务3.2: 历史数据UI更新  
- [ ] 更新 `RunInfo` 组件显示步数和步频
- [ ] 更新历史跑步列表的数据显示
- [ ] 更新跑步统计页面（如果存在）

#### 任务3.3: 降级体验实现
- [ ] 实现传感器不可用时的UI处理
- [ ] 添加友好的提示信息
- [ ] 确保无传感器时不影响其他功能

### 4.4 Phase 4: 后台服务集成 (优先级: 🟡 高)
**预估时间**: 2-3小时

#### 任务4.1: 后台步数追踪
- [ ] 确保 `TrackingService` 中的步数追踪正常工作
- [ ] 测试后台运行时的步数记录准确性
- [ ] 处理后台限制对步数追踪的影响

#### 任务4.2: 数据持久化
- [ ] 确保跑步结束时正确保存步数数据
- [ ] 实现步频的平均值计算和保存
- [ ] 测试数据完整性

### 4.5 Phase 5: 测试与优化 (优先级: 🟢 中)
**预估时间**: 3-4小时

#### 任务5.1: 功能测试
- [ ] 测试步数计算的准确性
- [ ] 测试步频计算的实时性  
- [ ] 测试不同设备上的传感器兼容性
- [ ] 测试后台运行时的数据记录

#### 任务5.2: 边界情况测试
- [ ] 测试无传感器设备的降级体验
- [ ] 测试传感器精度变化的处理
- [ ] 测试快速开始/结束跑步的数据一致性
- [ ] 测试数据库迁移在不同场景下的稳定性

#### 任务5.3: 性能优化
- [ ] 优化传感器数据处理的性能
- [ ] 确保后台步数追踪的电量消耗合理
- [ ] 优化UI渲染性能

## 5. 技术风险与缓解策略

### 5.1 传感器兼容性风险
**风险**: 不同设备的计步传感器行为可能不一致
**缓解**: 
- 实现传感器可用性检测
- 提供优雅的降级体验
- 支持多种传感器类型（STEP_COUNTER + STEP_DETECTOR）

### 5.2 数据准确性风险  
**风险**: 计步传感器可能存在误差或延迟
**缓解**:
- 使用系统级传感器保证相对准确性
- 实现合理的数据平滑算法
- 提供用户校准选项（后续版本）

### 5.3 后台限制风险
**风险**: Android后台限制可能影响步数追踪
**缓解**:
- 利用现有的前台服务架构
- 测试不同Android版本的后台行为
- 确保关键数据不丢失

### 5.4 UI布局风险
**风险**: 新增统计项可能导致UI布局拥挤
**缓解**:
- 设计响应式布局方案
- 考虑分行显示或滚动显示
- 保持现有UI风格的一致性

## 6. 实施建议

### 6.1 开发顺序
1. **Phase 1 & 2**: 优先实现核心数据层和业务逻辑
2. **Phase 3**: 实现基础UI集成
3. **Phase 4**: 完善后台服务集成
4. **Phase 5**: 全面测试和优化

### 6.2 测试策略
- **单元测试**: 重点测试步频计算算法和数据处理逻辑
- **集成测试**: 测试与现有追踪系统的集成
- **设备测试**: 在多种设备上测试传感器兼容性
- **场景测试**: 测试各种跑步场景下的数据准确性

### 6.3 发布策略
- **Alpha版本**: 核心功能实现，内部测试
- **Beta版本**: UI优化完成，小范围用户测试  
- **正式版本**: 全面测试通过，性能优化完成

## 7. 后续扩展可能性

### 7.1 高级功能
- 步长计算（结合距离和步数）
- 跑步效率分析（步频与配速的关系）
- 个人步数目标设置和提醒

### 7.2 数据分析
- 步频趋势分析
- 步数与其他指标的关联分析
- 个性化跑步建议

### 7.3 社交功能
- 步数排行榜
- 步频挑战活动
- 跑步数据分享

---

**总结**: 本方案采用最小改动原则，充分利用Android系统级计步API，确保功能完整性的同时保持现有架构的稳定性。预计总开发时间14-19小时，分5个阶段实施，风险可控。