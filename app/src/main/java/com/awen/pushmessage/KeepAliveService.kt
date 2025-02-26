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
 * 保活前台服务
 */
class KeepAliveService : Service() {
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "KeepAliveService onCreate")
        startForeground()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "KeepAliveService onStartCommand")
        
        // 如果服务被系统杀死，则自动重启服务
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        Log.d(TAG, "KeepAliveService onDestroy")
        super.onDestroy()
        
        // 服务被销毁时，尝试重启服务
        val restartServiceIntent = Intent(applicationContext, KeepAliveService::class.java)
        restartServiceIntent.setPackage(packageName)
        startService(restartServiceIntent)
    }
    
    /**
     * 当应用被从最近任务列表中移除时调用
     * 这个方法是Service类中的方法，用于处理应用被杀死的情况
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "KeepAliveService onTaskRemoved - 应用被从最近任务列表中移除")
        super.onTaskRemoved(rootIntent)
        
        // 尝试重启服务
        val restartServiceIntent = Intent(applicationContext, KeepAliveService::class.java)
        restartServiceIntent.setPackage(packageName)
        startService(restartServiceIntent)
        
        // 尝试重启应用
        val restartAppIntent = Intent(applicationContext, MainActivity::class.java)
        restartAppIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(restartAppIntent)
        
        // 重新调度JobService
        KeepAliveJobService.scheduleJob(applicationContext)
    }
    
    private fun startForeground() {
        val channelId = "keep_alive_service"
        val channelName = "验证码监控服务"
        
        // 创建通知渠道（Android 8.0及以上需要）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持验证码监控服务运行"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        
        // 创建点击通知时打开应用的Intent
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        // 创建通知
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("验证码监控")
            .setContentText("正在监控新的验证码消息")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
        
        // 启动前台服务
        startForeground(NOTIFICATION_ID, notification)
    }
    
    companion object {
        private const val TAG = "KeepAliveService"
        private const val NOTIFICATION_ID = 1001
    }
} 