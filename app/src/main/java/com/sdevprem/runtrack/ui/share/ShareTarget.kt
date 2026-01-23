package com.sdevprem.runtrack.ui.share

enum class ShareTarget(
    val label: String,
    val width: Int = 1080,
    val height: Int
) {
    WECHAT(label = "WeChat", height = 1440),
    XHS(label = "XHS", height = 1920)
}
