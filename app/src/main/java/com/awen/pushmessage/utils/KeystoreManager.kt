package com.awen.pushmessage.utils

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore 管理器
 * 用于安全地存储和检索加密密钥
 */
object KeystoreManager {
    
    private const val TAG = "KeystoreManager"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "PushMessageEncryptionKey"
    private const val PREFS_NAME = "keystore_prefs"
    private const val PREF_KEY_WRAPPED = "wrapped_encryption_key"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12
    
    /**
     * 获取或生成加密密钥
     * 使用 Android Keystore 保护密钥安全
     */
    fun getEncryptionKey(context: Context): String {
        return try {
            // 首先尝试从 SharedPreferences 获取已包装（加密存储）的密钥
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val wrappedKey = prefs.getString(PREF_KEY_WRAPPED, null)
            
            if (wrappedKey != null) {
                // 解包已存在的密钥
                unwrapKey(wrappedKey)
            } else {
                // 生成新密钥并包装存储
                val newKey = CryptoUtils.generateEncryptionKey()
                wrapAndStoreKey(context, newKey, prefs)
                newKey
            }
        } catch (e: Exception) {
            Log.e(TAG, "Keystore 操作失败，回退到普通存储", e)
            // 降级处理：使用旧的存储方式
            getLegacyKey(context)
        }
    }
    
    /**
     * 获取 Legacy 密钥（用于兼容旧版本）
     */
    private fun getLegacyKey(context: Context): String {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val savedKey = prefs.getString("encryption_key", null)
        
        return savedKey ?: run {
            val newKey = CryptoUtils.generateEncryptionKey()
            prefs.edit().putString("encryption_key", newKey).apply()
            newKey
        }
    }
    
    /**
     * 包装并存储密钥
     */
    private fun wrapAndStoreKey(context: Context, key: String, prefs: SharedPreferences) {
        try {
            val keystore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keystore.load(null)
            
            // 生成或获取 Keystore 密钥
            val secretKey = getOrCreateKeystoreKey()
            
            // 加密（包装）用户密钥
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val iv = cipher.iv
            val encryptedKey = cipher.doFinal(key.toByteArray(Charsets.UTF_8))
            
            // 合并 IV 和加密数据
            val combined = ByteArray(iv.size + encryptedKey.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedKey, 0, combined, iv.size, encryptedKey.size)
            
            // 保存包装后的密钥
            val wrappedKey = Base64.getEncoder().encodeToString(combined)
            prefs.edit().putString(PREF_KEY_WRAPPED, wrappedKey).apply()
            
            // 删除旧的不安全存储
            context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .edit()
                .remove("encryption_key")
                .apply()
            
            Log.d(TAG, "密钥已成功包装并存储到 Keystore")
        } catch (e: Exception) {
            Log.e(TAG, "包装密钥失败", e)
            throw e
        }
    }
    
    /**
     * 解包密钥
     */
    private fun unwrapKey(wrappedKey: String): String {
        try {
            val keystore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keystore.load(null)
            
            val secretKey = keystore.getKey(KEY_ALIAS, null) as? SecretKey
                ?: throw IllegalStateException("Keystore 密钥不存在")
            
            val combined = Base64.getDecoder().decode(wrappedKey)
            
            // 提取 IV
            val iv = ByteArray(GCM_IV_LENGTH)
            val encryptedKey = ByteArray(combined.size - GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
            System.arraycopy(combined, GCM_IV_LENGTH, encryptedKey, 0, encryptedKey.size)
            
            // 解密
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            
            val decryptedBytes = cipher.doFinal(encryptedKey)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "解包密钥失败", e)
            throw e
        }
    }
    
    /**
     * 获取或创建 Keystore 密钥
     */
    private fun getOrCreateKeystoreKey(): SecretKey {
        val keystore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keystore.load(null)
        
        // 检查密钥是否已存在
        val existingKey = keystore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) {
            return existingKey
        }
        
        // 创建新密钥
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
        
        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }
    
    /**
     * 清除存储的密钥（用于重置或登出）
     */
    fun clearStoredKey(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(PREF_KEY_WRAPPED).apply()
            
            val keystore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keystore.load(null)
            keystore.deleteEntry(KEY_ALIAS)
            
            Log.d(TAG, "密钥已清除")
        } catch (e: Exception) {
            Log.e(TAG, "清除密钥失败", e)
        }
    }
    
    /**
     * 检查是否使用 Keystore 存储
     */
    fun isUsingKeystore(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(PREF_KEY_WRAPPED)
    }
}
