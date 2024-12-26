package com.example.pushmessage

import android.content.Context
import android.provider.Settings.Secure
import java.security.MessageDigest
import java.util.Base64

data class Settings(
    val apiUrl: String = "",
    val encryptionKey: String = ""  // 不再在这里生成密钥
) {
    companion object {
        fun generateEncryptionKey(context: Context): String {
            // 获取 ANDROID_ID
            val androidId = Secure.getString(context.contentResolver, Secure.ANDROID_ID)
            
            // 添加一个固定的盐值来增加安全性
            val saltedId = "PushMessage_$androidId"
            
            // 使用 SHA-256 生成哈希
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(saltedId.toByteArray())
            
            // 转换为 Base64 字符串
            return Base64.getEncoder().encodeToString(hash)
        }
    }
} 