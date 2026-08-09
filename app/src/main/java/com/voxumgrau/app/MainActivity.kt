package com.voxumgrau.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voxumgrau.app.ui.theme.JarvisTheme
import com.voxumgrau.app.ui.theme.JarvisBlack

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JarvisTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = JarvisBlack) {
                    JarvisChatScreen(viewModel())
                }
            }
        }
    }
}
