package com.example.pushmessage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.pushmessage.data.SmsMessage
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class SmsReceiver : BroadcastReceiver() {
    private data class SmsMessage(
        val id: Long,
        val address: String,
        val body: String,
        val date: Long,
        val verificationCode: String,
        val isRead: Boolean = false,
        val isDeleted: Boolean = false
    )

    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                messages.forEach { smsMessage ->
                    val body = smsMessage.messageBody
                    val sender = smsMessage.originatingAddress
                    val timestamp = System.currentTimeMillis()
                    
                    Log.d("SmsReceiver", "Received new SMS from: $sender, message: $body")
                    
                    // 如果包含验证码，立即处理
                    if (body.contains("验证码")) {
                        val verificationCode = Regex("""(?<!\d)(\d{4,6})(?!\d)""").find(body)?.groupValues?.get(1)
                        if (verificationCode != null) {
                            // 创建 SmsMessage 对象
                            val sms = SmsMessage(
                                id = timestamp,  // 使用时间戳作为临时ID
                                address = sender ?: "",
                                body = body,
                                date = timestamp,
                                verificationCode = verificationCode
                            )
                            
                            // 立即发送到服务器
                            sendSmsToServer(context, sms)
                        }
                    }
                    
                    // 发送广播通知 MainActivity 更新UI
                    try {
                        val updateIntent = Intent("SMS_UPDATED")
                        context.sendBroadcast(updateIntent)
                    } catch (e: Exception) {
                        Log.e("SmsReceiver", "Error sending broadcast", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SmsReceiver", "Error in onReceive", e)
        }
    }

    private fun sendSmsToServer(context: Context, sms: SmsMessage) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val apiUrl = prefs.getString("api_url", "") ?: ""
        val encryptionKey = prefs.getString("encryption_key", "") ?: ""
        
        if (apiUrl.isBlank() || encryptionKey.isBlank()) {
            Log.e("SmsReceiver", "API URL or encryption key not configured")
            return
        }

        // 检查是否已同步
        val syncedPrefs = context.getSharedPreferences("synced_messages", Context.MODE_PRIVATE)
        val messageHash = generateMessageHash(sms)
        if (syncedPrefs.getBoolean(messageHash, false)) {
            Log.d("SmsReceiver", "Message already synced, skipping: ${sms.verificationCode}")
            return
        }

        Thread {
            try {
                val encryptedMessage = encrypt(sms.body, encryptionKey)
                val client = OkHttpClient()
                
                val json = """
                    {
                        "message": "$encryptedMessage",
                        "code": "${sms.verificationCode}",
                        "sender": "${sms.address}",
                        "timestamp": ${sms.date},
                        "messageHash": "$messageHash"
                    }
                """.trimIndent()

                val request = Request.Builder()
                    .url(apiUrl)
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    when (response.code) {
                        200, 409 -> {
                            // 标记为已同步
                            syncedPrefs.edit().putBoolean(messageHash, true).apply()
                            Log.d("SmsReceiver", "Message synced successfully: ${sms.verificationCode}")
                        }
                        else -> {
                            Log.e("SmsReceiver", "Failed to send message: ${response.code}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Error sending message", e)
            }
        }.start()
    }

    private fun generateMessageHash(sms: SmsMessage): String {
        val key = "${sms.address}_${sms.body}_${sms.verificationCode}"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(key.toByteArray())
        return Base64.getEncoder().encodeToString(hash)
    }

    private fun encrypt(message: String, key: String): String {
        try {
            val decodedKey = Base64.getDecoder().decode(key)
            val secretKey = SecretKeySpec(decodedKey, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val encryptedBytes = cipher.doFinal(message.toByteArray())
            val iv = cipher.iv
            
            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
            
            return Base64.getEncoder().encodeToString(combined)
        } catch (e: Exception) {
            Log.e("Encryption", "Error encrypting message", e)
            return message
        }
    }
} 