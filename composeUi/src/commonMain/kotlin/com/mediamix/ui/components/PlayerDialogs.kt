package com.mediamix.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediamix.shared.player.SubtitleTrack

/**
 * "More" menu dialog — provides access to Aspect Ratio, Power Mode, and Parser selectors.
 */
@Composable
fun PlayerMoreMenuDialog(
    onDismiss: () -> Unit,
    onAspectClick: () -> Unit,
    onPowerClick: () -> Unit,
    onParserClick: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF2A2A2A),
        title = { Text("More", color = Color.White) },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAspectClick() }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AspectRatio, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Aspect Ratio", color = Color.White)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPowerClick() }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Speed, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Power Mode", color = Color.White)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onParserClick() }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.SettingsInputComponent, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Parser", color = Color.White)
                }
            }
        },
        confirmButton = {}
    )
}

/**
 * Subtitle sync offset adjustment dialog.
 */
@Composable
fun SubtitleOffsetDialog(
    subtitleOffsetMs: Long,
    showSubtitles: Boolean,
    onAdjust: (Long) -> Unit,
    onReset: () -> Unit,
    onToggleSubtitles: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF2A2A2A),
        title = { Text("\u5B57\u5E55\u540C\u6B65", color = Color.White) },
        text = {
            Column {
                Text(
                    text = "\u5F53\u524D\u504F\u79FB: ${if (subtitleOffsetMs > 0) "+" else ""}${subtitleOffsetMs}ms",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(onClick = { onAdjust(-200L) }) {
                        Text("-200ms", color = Color.White)
                    }
                    OutlinedButton(onClick = { onAdjust(-50L) }) {
                        Text("-50ms", color = Color.White)
                    }
                    OutlinedButton(onClick = { onReset() }) {
                        Text("\u91CD\u7F6E", color = Color.White)
                    }
                    OutlinedButton(onClick = { onAdjust(50L) }) {
                        Text("+50ms", color = Color.White)
                    }
                    OutlinedButton(onClick = { onAdjust(200L) }) {
                        Text("+200ms", color = Color.White)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = { onToggleSubtitles() }) {
                        Text(
                            text = if (showSubtitles) "\u5173\u95ED\u5B57\u5E55" else "\u5F00\u542F\u5B57\u5E55",
                            color = if (showSubtitles) Color(0xFFFF8A80) else Color(0xFF80CBC4)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("\u786E\u5B9A", color = Color.White)
            }
        }
    )
}
