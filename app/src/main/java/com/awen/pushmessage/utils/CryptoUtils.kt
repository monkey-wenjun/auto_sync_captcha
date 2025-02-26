package com.awen.pushmessage.utils

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    
    /**
     * 生成一个随机的加密密钥
     */
    fun generateEncryptionKey(): String {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256, SecureRandom())
        val secretKey = keyGenerator.generateKey()
        return Base64.getEncoder().encodeToString(secretKey.encoded)
    }
    
    /**
     * 生成随机初始化向量(IV)
     */
    private fun generateIv(): ByteArray {
        val iv = ByteArray(16) // AES块大小为16字节
        SecureRandom().nextBytes(iv)
        return iv
    }
    
    /**
     * 使用AES加密文本
     */
    fun encrypt(text: String, keyString: String): String {
        val key = SecretKeySpec(Base64.getDecoder().decode(keyString), "AES")
        val iv = generateIv()
        val ivSpec = IvParameterSpec(iv)
        
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec)
        val encryptedBytes = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        
        // 将IV和加密后的数据合并，以便解密时使用
        val combined = ByteArray(iv.size + encryptedBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
        
        return Base64.getEncoder().encodeToString(combined)
    }
    
    /**
     * 使用AES解密文本
     */
    fun decrypt(encryptedText: String, keyString: String): String {
        val key = SecretKeySpec(Base64.getDecoder().decode(keyString), "AES")
        val combined = Base64.getDecoder().decode(encryptedText)
        
        // 从合并数据中提取IV和加密数据
        val iv = ByteArray(16)
        val encryptedBytes = ByteArray(combined.size - 16)
        System.arraycopy(combined, 0, iv, 0, iv.size)
        System.arraycopy(combined, iv.size, encryptedBytes, 0, encryptedBytes.size)
        
        val ivSpec = IvParameterSpec(iv)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, key, ivSpec)
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        
        return String(decryptedBytes, Charsets.UTF_8)
    }
} 