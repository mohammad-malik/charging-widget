package com.chargingwidget.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Simple file-based storage for battery state.
 * Avoids SharedPreferences caching issues.
 */
object BatteryStateStore {
    private const val FILE_NAME = "battery_state.json"

    private fun getFile(context: Context): File {
        return File(context.applicationContext.filesDir, FILE_NAME)
    }

    fun save(context: Context, info: ChargingDataProvider.ChargingInfo) {
        try {
            val json = JSONObject().apply {
                put("isCharging", info.isCharging)
                put("wattage", info.wattage)
                put("batteryPercent", info.batteryPercent)
                put("currentMa", info.currentMa)
                put("chargingType", info.chargingType)
            }
            getFile(context).writeText(json.toString())
        } catch (e: Exception) {
            // Ignore write errors
        }
    }

    fun load(context: Context): ChargingDataProvider.ChargingInfo {
        return try {
            val file = getFile(context)
            if (file.exists()) {
                val json = JSONObject(file.readText())
                ChargingDataProvider.ChargingInfo(
                    wattage = json.optDouble("wattage", 0.0),
                    currentMa = json.optInt("currentMa", 0),
                    voltageMv = 0,
                    batteryPercent = json.optInt("batteryPercent", 0),
                    isCharging = json.optBoolean("isCharging", false),
                    chargingType = json.optString("chargingType", "Not Charging"),
                    temperature = 0f
                )
            } else {
                defaultInfo()
            }
        } catch (e: Exception) {
            defaultInfo()
        }
    }

    private fun defaultInfo() = ChargingDataProvider.ChargingInfo(
        wattage = 0.0,
        currentMa = 0,
        voltageMv = 0,
        batteryPercent = 0,
        isCharging = false,
        chargingType = "Not Charging",
        temperature = 0f
    )
}
