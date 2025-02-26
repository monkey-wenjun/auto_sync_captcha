package com.awen.pushmessage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log
import com.awen.pushmessage.data.SmsMessage
import com.awen.pushmessage.utils.EventBus
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
            // 启动保活服务
            startKeepAliveService(context)
            
            if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                messages.forEach { smsMessage ->
                    val body = smsMessage.messageBody
                    val sender = smsMessage.originatingAddress
                    val timestamp = System.currentTimeMillis()
                    
                    Log.d("SmsReceiver", "Received new SMS from: $sender, message: $body")
                    
                    // 如果包含验证码，立即处理
                    if (body.contains("验证码")) {
                        val matchResult = Regex("""(?<!\d)(\d{4,6})(?!\d)""").find(body)
                        Log.d("SmsReceiver", "正则匹配结果: $matchResult")
                        val verificationCode = matchResult?.groupValues?.get(1)
                        Log.d("SmsReceiver", "提取的验证码: $verificationCode")
                        
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
                    
                    // 使用EventBus发送SMS更新事件
                    try {
                        CoroutineScope(Dispatchers.Main).launch {
                            EventBus.postSmsUpdateEvent()
                        }
                    } catch (e: Exception) {
                        Log.e("SmsReceiver", "Error posting event", e)
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
                // 只加密验证码
                val encryptedCode = encrypt(sms.verificationCode, encryptionKey)
                val client = OkHttpClient()
                
                // 只发送加密后的验证码
                val json = """
                    {
                        "message": "$encryptedCode"
                    }
                """.trimIndent()

                Log.d("SmsReceiver", "Sending verification code: ${sms.verificationCode}")
                Log.d("SmsReceiver", "Sending JSON: $json")

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
        // 只使用验证码和发送者来生成哈希
        val key = "${sms.address}_${sms.verificationCode}"
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

    private fun matchVerificationCode(body: String, settings: Settings): String? {
        // 首先检查自定义过滤规则
        for (filter in settings.customFilters) {
            if (!filter.isEnabled) continue
            
            val code = when (filter.type) {
                FilterType.KEYWORD -> {
                    if (body.contains(filter.pattern)) {
                        // 如果包含关键字，使用默认的验证码提取规则
                        Regex("""(?<!\d)(\d{4,6})(?!\d)""").find(body)?.groupValues?.get(1)
                    } else null
                }
                FilterType.REGEX -> {
                    try {
                        Regex(filter.pattern).find(body)?.groupValues?.get(1)
                    } catch (e: Exception) {
                        Log.e("SmsReceiver", "正则表达式错误: ${filter.pattern}", e)
                        null
                    }
                }
            }
            
            if (code != null) return code
        }
        
        // 如果没有自定义规则或都未匹配，使用默认规则
        if (body.contains("验证码")) {
            return Regex("""(?<!\d)(\d{4,6})(?!\d)""").find(body)?.groupValues?.get(1)
        }
        
        return null
    }

    /**
     * 启动保活服务
     */
    private fun startKeepAliveService(context: Context) {
        // 启动前台服务
        val serviceIntent = Intent(context, KeepAliveService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
        
        // 启动JobService
        KeepAliveJobService.scheduleJob(context)
        
        // 启动主活动
        val activityIntent = Intent(context, MainActivity::class.java)
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(activityIntent)
    }
} 