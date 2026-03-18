package com.awen.pushmessage

import android.content.Context
import android.provider.Settings.Secure
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

data class Settings(
    val apiUrl: String = "",
    val encryptionKey: String = "",
    val customFilters: List<SmsFilter> = listOf()
) {
    companion object {
        /**
         * 生成新的加密密钥
         * 新密钥将通过 KeystoreManager 安全存储
         */
        fun generateEncryptionKey(context: Context): String {
            // 使用 CryptoUtils 生成随机密钥
            val newKey = CryptoUtils.generateEncryptionKey()
            // 通过 KeystoreManager 安全存储
            com.awen.pushmessage.utils.KeystoreManager.clearStoredKey(context)
            return com.awen.pushmessage.utils.KeystoreManager.getEncryptionKey(context)
        }
        
        /**
         * 获取当前存储的加密密钥
         */
        fun getEncryptionKey(context: Context): String {
            return com.awen.pushmessage.utils.KeystoreManager.getEncryptionKey(context)
        }
    }
}

data class SmsFilter(
    val id: String = UUID.randomUUID().toString(),
    val type: FilterType = FilterType.KEYWORD,
    val pattern: String = "",
    val isEnabled: Boolean = true
)

enum class FilterType {
    KEYWORD,    // 关键字匹配
    REGEX       // 正则表达式匹配
} 