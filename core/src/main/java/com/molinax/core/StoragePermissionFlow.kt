package com.molinax.core

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState

/**
 * State holder for Android device storage access permissions.
 *
 * Integrates Accompanist Permissions for runtime permissions (API < 30 and Media Permissions)
 * with Scoped Storage / All Files Access management (API 30+ MANAGE_EXTERNAL_STORAGE).
 */
data class StoragePermissionState(
    val isGranted: Boolean,
    val shouldShowRationale: Boolean,
    val isAllFilesAccessRequired: Boolean,
    val launchPermissionRequest: () -> Unit,
    val openSettings: () -> Unit
)

/**
 * Remembers and observes the storage permission state using Accompanist Permissions.
 *
 * Listens to lifecycle events to automatically refresh the granted state when the user
 * returns from Android system settings.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberStoragePermissionState(): StoragePermissionState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val requiredPermissions = remember {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            else -> listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }

    val accompanistPermissionState: MultiplePermissionsState =
        rememberMultiplePermissionsState(permissions = requiredPermissions)

    var isAllFilesGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
            } else {
                accompanistPermissionState.allPermissionsGranted
            }
        )
    }

    // Refresh state when returning to foreground
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAllFilesGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
                } else {
                    PermissionHelper.hasStoragePermission(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isAllFilesGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
        } else {
            PermissionHelper.hasStoragePermission(context)
        }
    }

    val openSettingsAction: () -> Unit = {
        val intent = PermissionHelper.getManageStorageIntent(context)
        try {
            settingsLauncher.launch(intent)
        } catch (e: Exception) {
            val fallbackIntent = Intent(Settings.ACTION_SETTINGS)
            settingsLauncher.launch(fallbackIntent)
        }
    }

    val launchRequestAction: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            openSettingsAction()
        } else {
            accompanistPermissionState.launchMultiplePermissionRequest()
        }
    }

    val effectiveGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        isAllFilesGranted
    } else {
        accompanistPermissionState.allPermissionsGranted || isAllFilesGranted
    }

    return StoragePermissionState(
        isGranted = effectiveGranted,
        shouldShowRationale = accompanistPermissionState.shouldShowRationale,
        isAllFilesAccessRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
        launchPermissionRequest = launchRequestAction,
        openSettings = openSettingsAction
    )
}

/**
 * Gate composable that ensures device storage permissions are granted before
 * rendering the child [content].
 *
 * If permissions are missing or revoked, displays a high-contrast Material 3
 * rationale layout offering one-click permission requests or direct settings navigation.
 */
@Composable
fun StoragePermissionGate(
    modifier: Modifier = Modifier,
    onPermissionGranted: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val permissionState = rememberStoragePermissionState()

    LaunchedEffect(permissionState.isGranted) {
        if (permissionState.isGranted) {
            onPermissionGranted?.invoke()
        }
    }

    if (permissionState.isGranted) {
        content()
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF090D16))
                .padding(24.dp)
                .testTag("storage_permission_gate"),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF111827)
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .testTag("storage_permission_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color(0xFF1E293B), RoundedCornerShape(32.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderSpecial,
                            contentDescription = "Storage Permission Required",
                            tint = Color(0xFF00D2FF),
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Storage Access Required",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Molina-X requires permission to access device storage to list directories, manage Linux environment files, edit scripts, and play media files.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { permissionState.launchPermissionRequest() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00D2FF),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("grant_storage_permission_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (permissionState.isAllFilesAccessRequired) "Allow All Files Access" else "Grant Permission",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { permissionState.openSettings() },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF94A3B8)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("open_storage_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open App Settings")
                    }
                }
            }
        }
    }
}
