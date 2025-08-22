package com.example.cellularsocks.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun AppTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val light = lightColorScheme()
    val darkScheme = darkColorScheme()
    MaterialTheme(colorScheme = if (dark) darkScheme else light, content = content)
} 