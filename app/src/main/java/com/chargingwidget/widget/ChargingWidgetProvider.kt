package com.chargingwidget.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.chargingwidget.R
import com.chargingwidget.data.BatteryStateStore
import com.chargingwidget.data.ChargingDataProvider
import com.chargingwidget.service.ChargingJobService
import com.chargingwidget.service.WidgetUpdateService
import com.chargingwidget.worker.ChargingCheckWorker
import android.app.PendingIntent
import android.content.IntentFilter
import android.os.BatteryManager
import com.chargingwidget.MainActivity
import java.text.DecimalFormat

class ChargingWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }

        // Start service if charging
        if (isCharging(context)) {
            WidgetUpdateService.start(context)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // Schedule JobScheduler for reliable charging detection
        ChargingJobService.schedule(context)
        // Schedule periodic worker as fallback
        ChargingCheckWorker.schedule(context)
        if (isCharging(context)) {
            WidgetUpdateService.start(context)
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetUpdateService.stop(context)
    }

    private fun isCharging(context: Context): Boolean {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        if (batteryManager.isCharging) return true

        val batteryIntent: Intent? = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (e: Exception) { null }
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
    }

    companion object {
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val info = ChargingDataProvider(context).getChargingInfo()
            val wattageFormat = DecimalFormat("#.#")

            val views = RemoteViews(context.packageName, R.layout.widget_charging)

            // Set charging type
            views.setTextViewText(R.id.tvChargingType, info.chargingType)

            // Set wattage
            val wattageText = if (info.isCharging) "${wattageFormat.format(info.wattage)} W" else "-- W"
            views.setTextViewText(R.id.tvWattage, wattageText)

            // Set battery percent
            views.setTextViewText(R.id.tvBatteryPercent, "${info.batteryPercent}%")

            // Set current if charging
            if (info.isCharging) {
                views.setTextViewText(R.id.tvCurrent, "${info.currentMa} mA")
                views.setViewVisibility(R.id.tvCurrent, android.view.View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.tvCurrent, android.view.View.GONE)
            }

            // Set progress bar
            views.setProgressBar(R.id.progressBattery, 100, info.batteryPercent, false)

            // Set icon
            views.setImageViewResource(
                R.id.ivChargingIcon,
                if (info.isCharging) R.drawable.ic_charging else R.drawable.ic_battery
            )

            // Set click to open app
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, ChargingWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)

            for (widgetId in widgetIds) {
                updateWidget(context, appWidgetManager, widgetId)
            }
        }
    }
}
