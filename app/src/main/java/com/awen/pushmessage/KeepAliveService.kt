package com.awen.pushmessage

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * 保活前台服务 - 后台静默运行
 */
class KeepAliveService : Service() {
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "服务创建")
        startForeground()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "服务启动")
        // 被杀死后自动重启
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        Log.d(TAG, "服务销毁")
        super.onDestroy()
        // 尝试重启服务
        try {
            val restartIntent = Intent(applicationContext, KeepAliveService::class.java)
            restartIntent.setPackage(packageName)
            startService(restartIntent)
        } catch (e: Exception) {
            Log.e(TAG, "重启服务失败", e)
        }
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "应用从最近任务移除")
        super.onTaskRemoved(rootIntent)
        // 服务保持运行，不重启Activity
        try {
            val serviceIntent = Intent(applicationContext, KeepAliveService::class.java)
            serviceIntent.setPackage(packageName)
            startService(serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "任务移除后重启服务失败", e)
        }
        // 重新调度JobService
        KeepAliveJobService.scheduleJob(applicationContext)
    }
    
    private fun startForeground() {
        val channelId = "keep_alive_service"
        val channelName = "验证码监控服务"
        
        // 创建通知渠道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_MIN  // 最低重要性，静默通知
            ).apply {
                description = "保持验证码监控服务后台运行"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        
        // 点击通知打开主界面
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        // 创建前台服务通知
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("验证码监控运行中")
            .setContentText("正在后台监控新的验证码消息")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
    }
    
    companion object {
        private const val TAG = "KeepAliveService"
        private const val NOTIFICATION_ID = 1001
    }
}
