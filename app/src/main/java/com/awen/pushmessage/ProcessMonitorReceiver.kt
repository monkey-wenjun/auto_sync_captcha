package com.awen.pushmessage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * 进程监控广播接收器，用于监听应用包状态变化
 */
class ProcessMonitorReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "接收到广播: ${intent.action}")
        
        when (intent.action) {
            Intent.ACTION_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // 应用被更新或重新安装时，启动服务
                val serviceIntent = Intent(context, KeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                
                // 启动主活动
                val activityIntent = Intent(context, MainActivity::class.java)
                activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(activityIntent)
                
                // 启动JobService
                KeepAliveJobService.scheduleJob(context)
            }
        }
    }
    
    companion object {
        private const val TAG = "ProcessMonitorReceiver"
    }
} 