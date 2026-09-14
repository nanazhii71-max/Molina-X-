package com.molinax

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.molinax.ui.theme.MolinaXTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.molinax.navigation.NavigationHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MolinaXTheme {
                Surface(modifier = Modifier) {
                    NavigationHost()
                }
            }
        }
    }
}
