package jp.ikigai.netspeedmon.ui.screens

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Context.BIND_AUTO_CREATE
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import jp.ikigai.netspeedmon.R
import jp.ikigai.netspeedmon.services.NetSpeedMonService
import jp.ikigai.netspeedmon.utils.batteryOptimizationStatus
import jp.ikigai.netspeedmon.utils.requestBatteryUnrestricted
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current

    var netSpeedMonService: NetSpeedMonService? by remember {
        mutableStateOf(null)
    }

    var isRunning by remember {
        mutableStateOf(false)
    }

    val connection by remember {
        mutableStateOf(
            object : ServiceConnection {
                override fun onServiceConnected(className: ComponentName, service: IBinder) {
                    val binder = service as NetSpeedMonService.LocalBinder
                    netSpeedMonService = binder.getService()
                }

                override fun onServiceDisconnected(arg0: ComponentName) {
                    isRunning = false
                    netSpeedMonService = null
                }
            }
        )
    }

    LaunchedEffect(key1 = netSpeedMonService) {
        if (netSpeedMonService != null) {
            netSpeedMonService?.serviceState?.collectLatest { state ->
                isRunning = state
            }
        }
    }

    var batteryOptimizationStatus by remember {
        mutableStateOf(context.batteryOptimizationStatus)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        batteryOptimizationStatus = context.batteryOptimizationStatus
    }

    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        Intent(context, NetSpeedMonService::class.java).also { intent ->
            context.bindService(intent, connection, BIND_AUTO_CREATE)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        context.unbindService(connection)
    }

    var postNotificationPermissionStatus by remember {
        mutableStateOf(false)
    }

    val postNotificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        postNotificationPermissionStatus = it
    }

    LaunchedEffect(Unit) {
        when (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            PackageManager.PERMISSION_GRANTED -> {
                postNotificationPermissionStatus = true
            }

            PackageManager.PERMISSION_DENIED -> {
                postNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.app_name)
                    )
                }
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!batteryOptimizationStatus || !postNotificationPermissionStatus) {
                if (!batteryOptimizationStatus) {
                    Button(
                        onClick = {
                            context.requestBatteryUnrestricted()
                        }
                    ) {
                        Text(
                            text = stringResource(id = R.string.disabled_battery_optimization)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                if (!postNotificationPermissionStatus) {
                    Text(
                        text = stringResource(id = R.string.post_notification_permission_required)
                    )
                }
            } else {
                if (isRunning) {
                    Button(
                        onClick = {
                            stopForegroundService(context)
                        }
                    ) {
                        Text(
                            text = stringResource(id = R.string.stop_service)
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            startForegroundService(context)
                        }
                    ) {
                        Text(
                            text = stringResource(id = R.string.start_service)
                        )
                    }
                }
            }
        }
    }
}

private fun startForegroundService(context: Context) {
    val intent = Intent(context, NetSpeedMonService::class.java)
    context.startForegroundService(intent)
}

private fun stopForegroundService(context: Context) {
    val intent = Intent(context, NetSpeedMonService::class.java).apply {
        action = "ACTION_STOP"
    }
    context.startForegroundService(intent)
}
