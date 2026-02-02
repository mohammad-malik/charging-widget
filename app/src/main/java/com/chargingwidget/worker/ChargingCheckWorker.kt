package com.chargingwidget.worker

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.chargingwidget.service.WidgetUpdateService
import com.chargingwidget.widget.ChargingWidgetProvider
import java.util.concurrent.TimeUnit

class ChargingCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Update widget
        ChargingWidgetProvider.updateAllWidgets(applicationContext)

        // Start or stop service based on charging state
        if (isCharging()) {
            WidgetUpdateService.start(applicationContext)
        }

        return Result.success()
    }

    private fun isCharging(): Boolean {
        val batteryManager = applicationContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        if (batteryManager.isCharging) return true

        val batteryIntent: Intent? = try {
            applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (e: Exception) { null }
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
    }

    companion object {
        private const val WORK_NAME = "charging_check_worker"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ChargingCheckWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
