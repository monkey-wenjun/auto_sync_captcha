package com.awen.pushmessage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log

/**
 * 屏幕状态监听广播接收器
 */
class ScreenStateReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "接收到广播: ${intent.action}")
        
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                // 屏幕关闭，启动1像素Activity
                PixelActivity.togglePixelActivity(context, false)
            }
            Intent.ACTION_SCREEN_ON -> {
                // 屏幕点亮，关闭1像素Activity
                PixelActivity.togglePixelActivity(context, true)
            }
            Intent.ACTION_USER_PRESENT -> {
                // 用户解锁，确保关闭1像素Activity
                PixelActivity.togglePixelActivity(context, true)
            }
            "com.awen.pushmessage.CLOSE_PIXEL_ACTIVITY" -> {
                // 关闭所有1像素Activity的广播
                val closeIntent = Intent(context, PixelActivity::class.java)
                closeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                closeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                closeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                context.startActivity(closeIntent)
            }
        }
    }
    
    companion object {
        private const val TAG = "ScreenStateReceiver"
        
        /**
         * 注册屏幕状态广播接收器
         */
        fun register(context: Context): ScreenStateReceiver {
            val receiver = ScreenStateReceiver()
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction("com.awen.pushmessage.CLOSE_PIXEL_ACTIVITY")
            }
            
            // Android 13 (API 33) 及以上版本需要指定 RECEIVER_NOT_EXPORTED
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            
            return receiver
        }
    }
} 