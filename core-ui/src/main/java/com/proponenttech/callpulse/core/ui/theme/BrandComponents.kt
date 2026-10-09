package com.proponenttech.callpulse.core.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhoneInTalk
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Shared backdrop keeps the brand consistent, including in dark mode. */
@Composable
fun BrandBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier.background(Brush.linearGradient(listOf(
            colors.primaryContainer, colors.background, colors.surfaceVariant,
        ))),
        content = content,
    )
}

@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Box(
        modifier.size(60.dp).background(
            Brush.linearGradient(listOf(Color(0xFF4A5DF9), Color(0xFF7786FF))),
            RoundedCornerShape(20.dp),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.PhoneInTalk, contentDescription = null,
            tint = Color.White, modifier = Modifier.size(28.dp))
    }
}

@Composable
fun StatusPill(text: String, modifier: Modifier = Modifier, warning: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = if (warning) colors.errorContainer else colors.secondaryContainer,
        contentColor = if (warning) colors.onErrorContainer else colors.onSecondaryContainer,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}
