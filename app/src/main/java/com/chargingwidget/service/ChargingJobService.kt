package com.chargingwidget.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.chargingwidget.widget.ChargingWidgetProvider

class ChargingJobService : JobService() {

    companion object {
        private const val JOB_ID_CHARGING = 1001
        private const val JOB_ID_NOT_CHARGING = 1002

        fun schedule(context: Context) {
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

            // Job that triggers when charging starts
            val chargingJob = JobInfo.Builder(
                JOB_ID_CHARGING,
                ComponentName(context, ChargingJobService::class.java)
            )
                .setRequiresCharging(true)
                .setPersisted(true)
                .build()

            // Job that triggers when not charging (to detect unplug)
            val notChargingJob = JobInfo.Builder(
                JOB_ID_NOT_CHARGING,
                ComponentName(context, ChargingJobService::class.java)
            )
                .setRequiresCharging(false)
                .setPersisted(true)
                .build()

            jobScheduler.schedule(chargingJob)
            jobScheduler.schedule(notChargingJob)
        }
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        val isCharging = isCurrentlyCharging()

        // Update widget immediately
        ChargingWidgetProvider.updateAllWidgets(this)

        if (isCharging) {
            // Start the update service
            WidgetUpdateService.start(this)
        } else {
            // Stop the update service
            WidgetUpdateService.stop(this)
        }

        // Reschedule the jobs for next state change
        schedule(this)

        return false // Job is complete
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return true // Reschedule if stopped
    }

    private fun isCurrentlyCharging(): Boolean {
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        if (batteryManager.isCharging) return true

        val batteryIntent: Intent? = try {
            registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (e: Exception) { null }
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
    }
}
