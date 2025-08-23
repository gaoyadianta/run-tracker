package com.sdevprem.runtrack.common.utils

object RunUtils {

    fun calculateCaloriesBurnt(distanceInMeters: Int, weightInKg: Float) =
        //from chat gpt
        (0.75f * weightInKg) * (distanceInMeters / 1000f)

    /**
     * 将速度（km/h）转换为配速（分钟/公里）
     * @param speedInKMH 速度（公里/小时）
     * @return 配速（分钟/公里），如果速度为0则返回0
     */
    fun convertSpeedToPace(speedInKMH: Float): Float {
        return if (speedInKMH > 0) {
            60f / speedInKMH  // 分钟/公里 = 60分钟 / 速度(公里/小时)
        } else {
            0f
        }
    }

    /**
     * 格式化配速显示
     * @param paceInMinutesPerKm 配速（分钟/公里）
     * @return 格式化的配速字符串，如 "5'30""
     */
    fun formatPace(paceInMinutesPerKm: Float): String {
        return if (paceInMinutesPerKm > 0) {
            val minutes = paceInMinutesPerKm.toInt()
            val seconds = ((paceInMinutesPerKm - minutes) * 60).toInt()
            "${minutes}'${seconds.toString().padStart(2, '0')}\""
        } else {
            "0'00\""
        }
    }

}