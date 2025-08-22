package com.sdevprem.runtrack.ai.model

/**
 * AI语音播报类型
 */
enum class AIBroadcastType(val description: String, val prompt: String) {
    ENCOURAGEMENT(
        "鼓励激励",
        "作为一个专业的跑步教练和陪跑伙伴，请根据用户当前的跑步状态给出温暖的鼓励和激励。语言要亲切友好，充满正能量，让用户感受到陪伴和支持。回复控制在30字以内。"
    ),
    
    PROFESSIONAL_ADVICE(
        "专业建议",
        "作为一个专业的跑步教练，请根据用户当前的跑步数据（配速、距离、时间等）给出专业的跑步建议，包括配速调整、呼吸节奏、跑姿等。语言要专业但易懂。回复控制在50字以内。"
    ),
    
    MILESTONE_CELEBRATION(
        "里程碑庆祝",
        "用户刚刚达成了一个跑步里程碑！请给出热烈的祝贺和鼓励，让用户感受到成就感。语言要充满激情和正能量。回复控制在30字以内。"
    ),
    
    PACE_REMINDER(
        "配速提醒",
        "根据用户当前的配速情况，给出适当的提醒。如果配速过快，提醒放慢节奏；如果配速过慢，鼓励适当加速。语言要温和友善。回复控制在30字以内。"
    ),
    
    WEATHER_REMINDER(
        "天气提醒",
        "根据当前的天气情况，给用户一些跑步相关的温馨提醒，比如补水、防晒、保暖等。语言要贴心关怀。回复控制在30字以内。"
    ),
    
    TRAINING_FEEDBACK(
        "训练反馈",
        "根据用户的跑步表现，给出训练效果的反馈和下一步的训练建议。语言要专业且鼓励性。回复控制在40字以内。"
    ),
    
    INTERACTIVE_RESPONSE(
        "互动回应",
        "用户刚刚说了话，请作为一个贴心的跑步伙伴，根据用户的话语和当前跑步状态给出合适的回应。语言要自然亲切，像朋友一样。回复控制在40字以内。"
    ),
    
    RUN_SUMMARY(
        "跑步总结",
        "用户刚刚完成了这次跑步！请作为专业的跑步教练，根据用户的跑步数据（距离、时间、配速、表现等）给出完整的跑步总结和鼓励。包括对表现的点评、改进建议，以及下次训练的建议。语言要专业、鼓励且温暖。回复控制在100字以内。"
    );
    
    companion object {
        fun getRandomEncouragement(): AIBroadcastType {
            return listOf(ENCOURAGEMENT, MILESTONE_CELEBRATION).random()
        }
        
        fun getByContext(context: RunningContext): AIBroadcastType {
            return when {
                context.currentPaceKmh > 12f -> PACE_REMINDER // 配速过快
                context.currentPaceKmh < 6f -> PACE_REMINDER  // 配速过慢
                context.distanceKm > 0 && context.distanceKm % 1f < 0.1f -> MILESTONE_CELEBRATION // 每公里庆祝
                context.durationMinutes > 0 && context.durationMinutes % 10 == 0 -> ENCOURAGEMENT // 每10分钟鼓励
                else -> PROFESSIONAL_ADVICE
            }
        }
    }
}