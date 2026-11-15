package com.example.ddlreminder.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.ddlreminder.util.CountdownLevel

@Composable
fun countdownColor(level: CountdownLevel, completed: Boolean): Color {
    if (completed) return MaterialTheme.colorScheme.outline

    val dark = isSystemInDarkTheme()
    return when (level) {
        CountdownLevel.NONE -> MaterialTheme.colorScheme.outline
        CountdownLevel.GREEN -> if (dark) Color(0xFF81C784) else Color(0xFF2E7D32)
        CountdownLevel.ORANGE -> if (dark) Color(0xFFFFB74D) else Color(0xFFEF6C00)
        CountdownLevel.RED -> if (dark) Color(0xFFEF9A9A) else Color(0xFFC62828)
    }
}
