package com.slothshot.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slothshot.app.ui.theme.SlothPrimary
import com.slothshot.app.ui.theme.SlothSurfaceVariant

@Composable
fun SettingsModal(
    currentApiKey: String,
    autoCopyOtp: Boolean,
    monitoringActive: Boolean,
    onSaveSettings: (apiKey: String, autoCopyOtp: Boolean, monitoringActive: Boolean) -> Unit,
    onClearAndRescan: () -> Unit,
    onDismiss: () -> Unit
) {
    var apiKey by remember { mutableStateOf(currentApiKey) }
    var isKeyVisible by remember { mutableStateOf(false) }
    var autoCopy by remember { mutableStateOf(autoCopyOtp) }
    var monitoring by remember { mutableStateOf(monitoringActive) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "⚙️ SlothShot Settings",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Gemini API Section
                Text(
                    text = "Cloud Intelligence (Optional)",
                    style = MaterialTheme.typography.titleSmall,
                    color = SlothPrimary,
                    fontWeight = FontWeight.SemiBold
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("Gemini API Key") },
                    placeholder = { Text("AIzaSy...") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                            Icon(
                                imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isKeyVisible) "Hide key" else "Show key"
                            )
                        }
                    },
                    visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "When empty, SlothShot runs 100% on-device (ML Kit OCR + heuristics) with zero internet. Provide a key for Gemini 2.5 Flash cloud enhancement.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                Divider(color = SlothSurfaceVariant)

                // Switches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-copy OTPs", fontWeight = FontWeight.Medium)
                        Text(
                            "Copy verification codes immediately on screenshot",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoCopy,
                        onCheckedChange = { autoCopy = it }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Background Monitor", fontWeight = FontWeight.Medium)
                        Text(
                            "Watch MediaStore for incoming screenshots",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = monitoring,
                        onCheckedChange = { monitoring = it }
                    )
                }

                Divider(color = SlothSurfaceVariant)

                OutlinedButton(
                    onClick = {
                        onClearAndRescan()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SlothPrimary)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Re-scan Gallery (Clear Cache)")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveSettings(apiKey, autoCopy, monitoring)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = SlothPrimary)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp)
    )
}
