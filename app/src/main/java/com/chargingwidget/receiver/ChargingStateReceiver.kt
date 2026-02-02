package com.chargingwidget.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chargingwidget.service.WidgetUpdateService
import com.chargingwidget.widget.ChargingWidgetProvider

class ChargingStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> {
                // Update widget immediately
                ChargingWidgetProvider.updateAllWidgets(context)
                // Start service for continuous updates
                WidgetUpdateService.start(context)
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                // Stop service
                WidgetUpdateService.stop(context)
                // Update widget to show not charging
                ChargingWidgetProvider.updateAllWidgets(context)
            }
        }
    }
}
