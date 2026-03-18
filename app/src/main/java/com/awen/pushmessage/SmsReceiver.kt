package com.awen.pushmessage

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import com.awen.pushmessage.data.SmsMessage
import com.awen.pushmessage.utils.CryptoUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * 短信接收器 - 后台静默处理验证码
 */
class SmsReceiver : BroadcastReceiver() {
    private data class SmsData(
        val id: Long,
        val address: String,
        val body: String,
        val date: Long,
        val verificationCode: String
    )

    override fun onReceive(context: Context, intent: Intent) {
        try {
            // 启动保活服务（保持应用存活）
            startKeepAliveService(context)
            
            if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                messages.forEach { smsMessage ->
                    val body = smsMessage.messageBody
                    val sender = smsMessage.originatingAddress
                    val timestamp = System.currentTimeMillis()
                    
                    Log.d(TAG, "收到短信 from: $sender")
                    
                    // 检查是否包含验证码
                    if (body.contains("验证码")) {
                        val matchResult = Regex("""(?<!\d)(\d{4,6})(?!\d)""").find(body)
                        val verificationCode = matchResult?.groupValues?.get(1)
                        
                        if (verificationCode != null) {
                            Log.d(TAG, "提取验证码: $verificationCode")
                            
                            val sms = SmsData(
                                id = timestamp,
                                address = sender ?: "",
                                body = body,
                                date = timestamp,
                                verificationCode = verificationCode
                            )
                            
                            // 静默发送到服务器
                            sendSmsToServer(context, sms)
                            
                            // 显示静默通知（只显示在通知栏，不弹窗）
                            showSilentNotification(context, sender ?: "", verificationCode)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理短信出错", e)
        }
    }

    /**
     * 静默发送验证码到服务器
     */
    private fun sendSmsToServer(context: Context, sms: SmsData) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val apiUrl = prefs.getString("api_url", "") ?: ""
        val encryptionKey = prefs.getString("encryption_key", "") ?: ""
        
        if (apiUrl.isBlank()) {
            Log.d(TAG, "API地址未配置，跳过同步")
            return
        }

        // 检查是否已同步
        val syncedPrefs = context.getSharedPreferences("synced_messages", Context.MODE_PRIVATE)
        val messageHash = generateMessageHash(sms)
        if (syncedPrefs.getBoolean(messageHash, false)) {
            Log.d(TAG, "验证码已同步过，跳过: ${sms.verificationCode}")
            return
        }

        // 后台线程发送
        Thread {
            try {
                val encryptedCode = if (encryptionKey.isNotBlank()) {
                    encrypt(sms.verificationCode, encryptionKey)
                } else {
                    sms.verificationCode
                }
                
                val json = """{"message": "$encryptedCode"}"""
                
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(apiUrl)
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    when (response.code) {
                        200, 409 -> {
                            syncedPrefs.edit().putBoolean(messageHash, true).apply()
                            Log.d(TAG, "验证码同步成功: ${sms.verificationCode}")
                        }
                        else -> {
                            Log.e(TAG, "同步失败: ${response.code}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "发送验证码出错", e)
            }
        }.start()
    }

    /**
     * 显示静默通知（不弹窗，只显示在通知栏）
     */
    private fun showSilentNotification(context: Context, sender: String, code: String) {
        val channelId = "verification_code_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 创建通知渠道（Android 8.0+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "验证码通知",
                NotificationManager.IMPORTANCE_MIN  // 最低重要性，不弹窗
            ).apply {
                description = "收到验证码时显示"
                setShowBadge(true)
                enableLights(false)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("收到验证码")
            .setContentText("来自 $sender: $code")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MIN)  // 静默通知
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun generateMessageHash(sms: SmsData): String {
        val key = "${sms.address}_${sms.verificationCode}_${sms.date / 60000}"  // 按分钟去重
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(key.toByteArray())
        return Base64.getEncoder().encodeToString(hash)
    }

    private fun encrypt(message: String, key: String): String {
        return try {
            val decodedKey = Base64.getDecoder().decode(key)
            val secretKey = SecretKeySpec(decodedKey, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val encryptedBytes = cipher.doFinal(message.toByteArray())
            val iv = cipher.iv
            
            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
            
            Base64.getEncoder().encodeToString(combined)
        } catch (e: Exception) {
            Log.e(TAG, "加密失败", e)
            message
        }
    }

    /**
     * 启动保活服务
     */
    private fun startKeepAliveService(context: Context) {
        val serviceIntent = Intent(context, KeepAliveService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
        KeepAliveJobService.scheduleJob(context)
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}
