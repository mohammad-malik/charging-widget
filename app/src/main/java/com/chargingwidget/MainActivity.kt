package com.chargingwidget

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.content.ContextCompat
import com.chargingwidget.data.ChargingDataProvider
import com.chargingwidget.service.ChargingJobService
import com.chargingwidget.service.WidgetUpdateService
import com.chargingwidget.widget.ChargingWidgetProvider
import com.chargingwidget.worker.ChargingCheckWorker
import kotlinx.coroutines.delay
import java.text.DecimalFormat

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            initializeAndStartService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    initializeAndStartService()
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            initializeAndStartService()
        }

        setContent {
            val darkTheme = isSystemInDarkTheme()
            val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (darkTheme) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
            } else {
                if (darkTheme) darkColorScheme() else lightColorScheme()
            }
            val view = LocalView.current
            val window = (view.context as ComponentActivity).window
            SideEffect {
                window.statusBarColor = colorScheme.surface.toArgb()
                window.navigationBarColor = colorScheme.surface.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            MaterialTheme(colorScheme = colorScheme) {
                MainScreen(
                    onUpdateWidget = {
                        updateWidget()
                        Toast.makeText(this, "Widget updated", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    private fun initializeAndStartService() {
        // Schedule JobScheduler for reliable charging detection
        ChargingJobService.schedule(this)
        // Schedule periodic worker as fallback
        ChargingCheckWorker.schedule(this)
        // Update widget state
        updateWidget()
        // Then start service if charging
        startServiceIfCharging()
    }

    private fun isCharging(): Boolean {
        // Method 1: BatteryManager.isCharging
        val batteryManager = getSystemService(BATTERY_SERVICE) as BatteryManager
        if (batteryManager.isCharging) {
            return true
        }

        // Method 2: Sticky broadcast (fallback)
        val batteryIntent: Intent? = try {
            registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (e: Exception) {
            null
        }
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun startServiceIfCharging(): Boolean {
        return if (isCharging()) {
            WidgetUpdateService.start(this)
            true
        } else {
            false
        }
    }

    private fun updateWidget() {
        ChargingWidgetProvider.updateAllWidgets(this)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onUpdateWidget: () -> Unit
) {
    val context = LocalContext.current
    val chargingDataProvider = remember { ChargingDataProvider(context) }
    var chargingInfo by remember { mutableStateOf(chargingDataProvider.getChargingInfo()) }

    // Auto-refresh every 2 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            chargingInfo = chargingDataProvider.getChargingInfo()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Charging Widget") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Main wattage card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (chargingInfo.isCharging) "Charging" else "Not Charging",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val wattageFormat = DecimalFormat("#.#")
                    Row(
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = if (chargingInfo.isCharging)
                                wattageFormat.format(chargingInfo.wattage)
                            else "--",
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = " W",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Battery progress
                    @Suppress("DEPRECATION")
                    LinearProgressIndicator(
                        progress = chargingInfo.batteryPercent / 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = if (chargingInfo.isCharging)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${chargingInfo.batteryPercent}%",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Details card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DetailRow("Type", chargingInfo.chargingType)
                    DetailRow("Current", "${chargingInfo.currentMa} mA")
                    DetailRow("Voltage", "${chargingInfo.voltageMv} mV")
                    DetailRow("Temperature", "${chargingInfo.temperature}°C")
                }
            }

            // Update widget button
            Button(
                onClick = onUpdateWidget,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Update Widget")
            }

            Spacer(modifier = Modifier.weight(1f))

            // Instructions
            Text(
                text = "Widget updates automatically when charging. Add it to your lock screen from widget settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}
