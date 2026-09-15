package com.molinax

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.molinax.navigation.NavigationHost
import com.molinax.ui.theme.MolinaXTheme
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // VERIFIED: KeyboardUtils.setSoftInputModeAdjustResize (termux-shared v0.118.3) --
        // tanpa ini, window tidak di-resize saat soft keyboard muncul, dan pada beberapa
        // device turut berkontribusi pada keyboard yang gagal tampil/terpotong di TerminalView.
        @Suppress("DEPRECATION")
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val crashLogFile = File(filesDir, "crash-logs/last-crash.txt")
        val crashLogText = if (crashLogFile.exists()) crashLogFile.readText() else null

        setContent {
            MolinaXTheme {
                Surface(modifier = Modifier) {
                    if (crashLogText != null) {
                        CrashReportScreen(
                            logText = crashLogText,
                            onDismiss = {
                                crashLogFile.delete()
                                recreate()
                            },
                        )
                    } else {
                        NavigationHost()
                    }
                }
            }
        }
    }
}

@Composable
private fun CrashReportScreen(logText: String, onDismiss: () -> Unit) {
    val clipboardManager: ClipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Molina-X crash terakhir",
            style = MaterialTheme.typography.titleLarge,
        )
        Button(onClick = {
            clipboardManager.setText(AnnotatedString(logText))
            copied = true
        }) {
            Text(if (copied) "Tersalin ke clipboard" else "Salin ke Clipboard")
        }
        Button(onClick = onDismiss) {
            Text("Tutup & Hapus Log")
        }
        SelectionContainer(
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = logText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
