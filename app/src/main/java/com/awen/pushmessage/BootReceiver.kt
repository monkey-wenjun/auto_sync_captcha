package com.awen.pushmessage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * 开机自启动广播接收器 - 仅启动后台服务，不启动界面
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "接收到广播: ${intent.action}")
        
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_REBOOT,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_USER_PRESENT -> {
                // 只启动前台服务，不启动Activity
                startKeepAliveService(context)
                Log.d(TAG, "服务已启动")
            }
        }
    }
    
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
        private const val TAG = "BootReceiver"
    }
}
