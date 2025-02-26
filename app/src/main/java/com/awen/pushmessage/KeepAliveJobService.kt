package com.awen.pushmessage

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * 保活JobService，用于定期唤醒应用
 */
class KeepAliveJobService : JobService() {
    
    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d(TAG, "KeepAliveJobService onStartJob")
        
        // 启动前台服务
        val serviceIntent = Intent(this, KeepAliveService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        
        // 重新调度任务
        scheduleJob(this)
        
        // 返回false表示任务已完成
        return false
    }
    
    override fun onStopJob(params: JobParameters?): Boolean {
        Log.d(TAG, "KeepAliveJobService onStopJob")
        
        // 返回true表示任务应该重新调度
        return true
    }
    
    companion object {
        private const val TAG = "KeepAliveJobService"
        private const val JOB_ID = 10
        
        /**
         * 调度保活任务
         */
        fun scheduleJob(context: Context) {
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            val componentName = ComponentName(context, KeepAliveJobService::class.java)
            
            val jobInfoBuilder = JobInfo.Builder(JOB_ID, componentName)
                .setPersisted(true) // 设备重启后仍然有效
                .setRequiresCharging(false)
                .setRequiresBatteryNotLow(false)
            
            // 根据Android版本设置不同的周期
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Android 7.0及以上，最小间隔为15分钟
                jobInfoBuilder.setPeriodic(15 * 60 * 1000)
            } else {
                // Android 7.0以下，可以设置更短的间隔
                jobInfoBuilder.setPeriodic(5 * 60 * 1000)
            }
            
            val jobInfo = jobInfoBuilder.build()
            
            val resultCode = jobScheduler.schedule(jobInfo)
            if (resultCode == JobScheduler.RESULT_SUCCESS) {
                Log.d(TAG, "Job scheduled successfully")
            } else {
                Log.d(TAG, "Job scheduling failed")
            }
        }
    }
} 