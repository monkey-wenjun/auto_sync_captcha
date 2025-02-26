package com.awen.pushmessage.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.ui.graphics.vector.ImageVector

data class SettingItem(
    val title: String,
    val icon: ImageVector,
    val description: String = ""
)

val settingItems = listOf(
    SettingItem(
        title = "HTTP推送设置",
        icon = Icons.Default.Send,
        description = "配置接口地址和加密密钥"
    )
) 