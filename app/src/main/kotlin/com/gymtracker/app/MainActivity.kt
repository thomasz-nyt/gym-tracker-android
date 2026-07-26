package com.gymtracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.gymtracker.core.designsystem.theme.GymTrackerTheme
import dagger.hilt.android.AndroidEntryPoint

/** The single activity. Navigation and real screens arrive with M1. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GymTrackerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BlankScreen()
                }
            }
        }
    }
}

/** Placeholder content so the skeleton is installable and launchable. */
@Composable
fun BlankScreen(modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding))
    }
}

@Preview
@Composable
private fun BlankScreenPreview() {
    GymTrackerTheme {
        BlankScreen()
    }
}
