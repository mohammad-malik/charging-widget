package com.chargingwidget.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.chargingwidget.MainActivity
import com.chargingwidget.R
import com.chargingwidget.data.ChargingDataProvider
import com.chargingwidget.widget.ChargingWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.DecimalFormat

class WidgetUpdateService : Service() {

    private var updateJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var powerReceiver: BroadcastReceiver? = null

    companion object {
        private const val CHANNEL_ID = "charging_widget_channel"
        private const val NOTIFICATION_ID = 1001
        private const val UPDATE_INTERVAL_MS = 2000L

        fun start(context: Context) {
            try {
                val intent = Intent(context, WidgetUpdateService::class.java)
                context.startForegroundService(intent)
            } catch (e: Exception) {
                // Fallback: update widget directly
                ChargingWidgetProvider.updateAllWidgets(context)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, WidgetUpdateService::class.java))
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerPowerReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        // Immediate update
        ChargingWidgetProvider.updateAllWidgets(this)

        startUpdating()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        updateJob?.cancel()
        unregisterPowerReceiver()
    }

    private fun registerPowerReceiver() {
        powerReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_POWER_DISCONNECTED -> {
                        ChargingWidgetProvider.updateAllWidgets(context)
                        stopSelf()
                    }
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_POWER_DISCONNECTED)
        ContextCompat.registerReceiver(
            this,
            powerReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun unregisterPowerReceiver() {
        powerReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                // Ignore
            }
        }
        powerReceiver = null
    }

    private fun startUpdating() {
        updateJob?.cancel()
        updateJob = serviceScope.launch {
            while (isActive) {
                // Update widget
                ChargingWidgetProvider.updateAllWidgets(this@WidgetUpdateService)
                updateNotification()

                delay(UPDATE_INTERVAL_MS)

                // Check if still charging
                if (!isCharging()) {
                    ChargingWidgetProvider.updateAllWidgets(this@WidgetUpdateService)
                    stopSelf()
                    break
                }
            }
        }
    }

    private fun isCharging(): Boolean {
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        if (batteryManager.isCharging) return true

        val batteryIntent: Intent? = try {
            registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (e: Exception) { null }
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Charging Widget Updates", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows charging status"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val info = ChargingDataProvider(this).getChargingInfo()
        val wattageText = DecimalFormat("#.#").format(info.wattage)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Charging: $wattageText W")
            .setContentText("${info.batteryPercent}% - ${info.chargingType}")
            .setSmallIcon(R.drawable.ic_charging)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            ))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, createNotification())
    }
}
