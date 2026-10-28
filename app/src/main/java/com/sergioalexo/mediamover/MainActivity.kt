package com.sergioalexo.mediamover

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sergioalexo.mediamover.data.Storage
import com.sergioalexo.mediamover.ui.AppViewModel
import com.sergioalexo.mediamover.ui.EditorScreen
import com.sergioalexo.mediamover.ui.HomeScreen
import com.sergioalexo.mediamover.ui.LogScreen
import com.sergioalexo.mediamover.ui.MediaMoverTheme
import com.sergioalexo.mediamover.ui.PreviewDialog
import com.sergioalexo.mediamover.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MediaMoverTheme { App() } }
    }
}

private sealed interface Screen {
    data object Home : Screen
    data class Edit(val ruleId: String?) : Screen
    data object Log : Screen
    data object Settings : Screen
}

@Composable
private fun App(vm: AppViewModel = viewModel()) {
    val context = LocalContext.current
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val snackbar = remember { SnackbarHostState() }

    // Permissions are granted in system Settings, so re-check whenever we come back.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }
    val hasAccess = remember(resumes) { Storage.hasAccess() }
    val batteryUnrestricted = remember(resumes) {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    when (val s = screen) {
        Screen.Home -> HomeScreen(
            vm = vm,
            hasAccess = hasAccess,
            snackbar = snackbar,
            onRequestAccess = { requestAllFilesAccess(context) },
            onEdit = { screen = Screen.Edit(it) },
            onLog = { screen = Screen.Log },
            onSettings = { screen = Screen.Settings },
        )
        is Screen.Edit -> {
            val rules by vm.rules.collectAsStateWithLifecycle()
            EditorScreen(
                initial = rules.find { it.id == s.ruleId },
                onSave = { vm.save(it); screen = Screen.Home },
                onDelete = { vm.delete(it); screen = Screen.Home },
                onBack = { screen = Screen.Home },
            )
        }
        Screen.Log -> LogScreen(vm = vm, snackbar = snackbar, onBack = { screen = Screen.Home })
        Screen.Settings -> SettingsScreen(
            vm = vm,
            hasAccess = hasAccess,
            batteryUnrestricted = batteryUnrestricted,
            onRequestAccess = { requestAllFilesAccess(context) },
            onRequestBattery = { requestBatteryExemption(context) },
            onBack = { screen = Screen.Home },
        )
    }

    val preview by vm.preview.collectAsStateWithLifecycle()
    preview?.let { PreviewDialog(entries = it, onDismiss = vm::closePreview) }
}

private fun requestAllFilesAccess(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
    )
}

@SuppressLint("BatteryLife") // sideloaded, not a Play app
private fun requestBatteryExemption(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
    )
}
