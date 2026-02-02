package com.chargingwidget.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import java.io.File
import kotlin.math.abs

/**
 * Provides accurate charging data by reading from system files with BatteryManager fallback.
 */
class ChargingDataProvider(context: Context) {

    // Always use application context to avoid issues with widget/receiver contexts
    private val appContext: Context = context.applicationContext

    data class ChargingInfo(
        val wattage: Double,
        val currentMa: Int,
        val voltageMv: Int,
        val batteryPercent: Int,
        val isCharging: Boolean,
        val chargingType: String,
        val temperature: Float
    )

    private val batteryManager: BatteryManager by lazy {
        appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    }

    /**
     * Get current charging information with accurate wattage reading.
     */
    fun getChargingInfo(): ChargingInfo {
        // Try to get battery intent, but handle failure gracefully
        val batteryIntent = try {
            appContext.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
        } catch (e: Exception) {
            null
        }

        // Use BatteryManager.isCharging as primary method (more reliable in widget context)
        val isCharging = getIsCharging(batteryIntent)
        val batteryPercent = getBatteryPercent(batteryIntent)
        val chargingType = getChargingType(batteryIntent, isCharging)
        val temperature = getTemperature(batteryIntent)

        // Get current and voltage (prefer system files for accuracy)
        val currentMicroAmps = getCurrentMicroAmps()
        val voltageMicroVolts = getVoltageMicroVolts()

        // Convert to mA and mV
        val currentMa = abs(currentMicroAmps / 1000)
        val voltageMv = voltageMicroVolts / 1000

        // Calculate wattage: P = V * I
        // currentMicroAmps is in µA, voltageMicroVolts is in µV
        // Wattage = (µV * µA) / 10^12 = Watts
        val wattage = if (isCharging && currentMicroAmps != 0) {
            abs(voltageMicroVolts.toDouble() * currentMicroAmps.toDouble()) / 1_000_000_000_000.0
        } else {
            0.0
        }

        return ChargingInfo(
            wattage = wattage,
            currentMa = currentMa,
            voltageMv = voltageMv,
            batteryPercent = batteryPercent,
            isCharging = isCharging,
            chargingType = chargingType,
            temperature = temperature
        )
    }

    /**
     * Read current from system files (most accurate) with BatteryManager fallback.
     */
    private fun getCurrentMicroAmps(): Int {
        // Try multiple paths used by different manufacturers
        val paths = listOf(
            "/sys/class/power_supply/battery/current_now",
            "/sys/class/power_supply/Battery/current_now",
            "/sys/class/power_supply/usb/current_now",
            "/sys/class/power_supply/ac/current_now",
            "/sys/class/power_supply/main/current_now"
        )

        for (path in paths) {
            val value = readSysFile(path)
            if (value != null && value != 0) {
                return value
            }
        }

        // Fallback to BatteryManager API
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
    }

    /**
     * Read voltage from system files (most accurate) with BatteryManager fallback.
     */
    private fun getVoltageMicroVolts(): Int {
        // Try system file first
        val paths = listOf(
            "/sys/class/power_supply/battery/voltage_now",
            "/sys/class/power_supply/Battery/voltage_now",
            "/sys/class/power_supply/usb/voltage_now"
        )

        for (path in paths) {
            val value = readSysFile(path)
            if (value != null && value != 0) {
                return value
            }
        }

        // Fallback to BatteryManager (returns mV, convert to µV)
        val intent = try {
            appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (e: Exception) {
            null
        }
        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        return voltageMv * 1000
    }

    /**
     * Read integer value from a system file.
     */
    private fun readSysFile(path: String): Int? {
        return try {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                file.readText().trim().toIntOrNull()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getIsCharging(intent: Intent?): Boolean {
        // Primary: Use BatteryManager.isCharging() - works reliably in all contexts
        if (batteryManager.isCharging) {
            return true
        }

        // Fallback: Check intent if available
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun getBatteryPercent(intent: Intent?): Int {
        // Primary: Use BatteryManager API
        val percent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (percent > 0) {
            return percent
        }

        // Fallback: Use intent
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return if (level >= 0 && scale > 0) {
            (level * 100) / scale
        } else {
            0
        }
    }

    private fun getChargingType(intent: Intent?, isCharging: Boolean): String {
        if (!isCharging) {
            return "Not Charging"
        }

        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        return when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "Charging" // Generic fallback when intent is unavailable
        }
    }

    private fun getTemperature(intent: Intent?): Float {
        val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        return temp / 10f // Temperature is in tenths of a degree
    }
}
