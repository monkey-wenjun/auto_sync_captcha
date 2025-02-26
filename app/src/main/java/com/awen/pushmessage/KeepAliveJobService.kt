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
        
        try {
            // 启动前台服务
            val serviceIntent = Intent(this, KeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting service", e)
        }
        
        // 返回false表示任务已完成
        return false
    }
    
    override fun onStopJob(params: JobParameters?): Boolean {
        Log.d(TAG, "KeepAliveJobService onStopJob")
        
        // 在任务停止时重新调度，而不是在每次执行完成后
        scheduleJob(this)
        
        // 返回false表示不需要重新执行
        return false
    }
    
    companion object {
        private const val TAG = "KeepAliveJobService"
        private const val JOB_ID = 10
        
        /**
         * 调度保活任务
         */
        fun scheduleJob(context: Context) {
            try {
                val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
                
                // 检查是否已经有相同的任务在运行
                val existingJob = jobScheduler.allPendingJobs.find { it.id == JOB_ID }
                if (existingJob != null) {
                    Log.d(TAG, "Job already scheduled")
                    return
                }
                
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
                    // Android 7.0以下，设置为10分钟
                    jobInfoBuilder.setPeriodic(10 * 60 * 1000)
                }
                
                val jobInfo = jobInfoBuilder.build()
                
                val resultCode = jobScheduler.schedule(jobInfo)
                if (resultCode == JobScheduler.RESULT_SUCCESS) {
                    Log.d(TAG, "Job scheduled successfully")
                } else {
                    Log.e(TAG, "Job scheduling failed with code: $resultCode")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling job", e)
            }
        }
    }
} 