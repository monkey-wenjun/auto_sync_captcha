package com.awen.pushmessage

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.view.Gravity
import android.view.Window
import android.view.WindowManager

/**
 * 1像素Activity，用于提高进程优先级
 * 在锁屏时启动，解锁时关闭
 */
class PixelActivity : Activity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "PixelActivity onCreate")
        
        // 设置1像素的窗口
        val window = window
        window.setGravity(Gravity.START or Gravity.TOP)
        val params = window.attributes
        params.x = 0
        params.y = 0
        params.height = 1
        params.width = 1
        window.attributes = params
    }
    
    override fun onDestroy() {
        Log.d(TAG, "PixelActivity onDestroy")
        super.onDestroy()
    }
    
    companion object {
        private const val TAG = "PixelActivity"
        
        /**
         * 根据屏幕状态打开或关闭1像素Activity
         */
        fun togglePixelActivity(context: Context, screenOn: Boolean) {
            if (screenOn) {
                // 屏幕点亮，关闭1像素Activity
                context.sendBroadcast(Intent("com.awen.pushmessage.CLOSE_PIXEL_ACTIVITY"))
            } else {
                // 屏幕关闭，打开1像素Activity
                val intent = Intent(context, PixelActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
        
        /**
         * 检查屏幕状态
         */
        fun isScreenOn(context: Context): Boolean {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isInteractive
        }
    }
} 