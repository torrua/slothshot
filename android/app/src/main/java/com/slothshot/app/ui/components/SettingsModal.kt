package com.slothshot.app.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slothshot.app.ui.theme.SlothPrimary
import com.slothshot.app.ui.theme.SlothSecondary
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
    val context = LocalContext.current
    var apiKey by remember { mutableStateOf(currentApiKey) }
    var isKeyVisible by remember { mutableStateOf(false) }
    var autoCopy by remember { mutableStateOf(autoCopyOtp) }
    var monitoring by remember { mutableStateOf(monitoringActive) }

    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = SlothPrimary,
        uncheckedThumbColor = Color(0xFF94A3B8),
        uncheckedTrackColor = Color.White.copy(alpha = 0.08f),
        uncheckedBorderColor = Color.Transparent
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Настройки",
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    letterSpacing = (-0.3).sp
                )
                Text(
                    text = "Интеллект и параметры фонового мониторинга",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Section: Gemini AI
                Text(
                    text = "Мультимодальный Gemini AI",
                    style = MaterialTheme.typography.titleSmall,
                    color = SlothPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("Ключ Gemini API", fontSize = 12.sp) },
                    placeholder = { Text("AIzaSy...", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Key,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = SlothPrimary
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                            Icon(
                                imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isKeyVisible) "Скрыть" else "Показать",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SlothPrimary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.10f),
                        focusedContainerColor = Color.White.copy(alpha = 0.03f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.02f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Buttons: Paste from Clipboard & Get Key (Unified elegant styling)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = cb.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                            if (!clip.isNullOrBlank()) {
                                apiKey = clip
                                Toast.makeText(context, "Ключ вставлен из буфера!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Буфер обмена пуст", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color.White.copy(alpha = 0.06f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Вставить", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }

                    FilledTonalButton(
                        onClick = {
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = SlothSecondary.copy(alpha = 0.12f),
                            contentColor = SlothSecondary
                        )
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Получить ключ", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }

                // Info Callout Card
                Surface(
                    color = SlothSecondary.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SlothSecondary.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("💡", fontSize = 14.sp)
                        Text(
                            text = "Бесплатный ключ Gemini от Google AI Studio (15 запр/мин) обеспечивает визуальный перевод и OCR кириллицы со 100% точностью.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.07f))

                // Switches with refined thumb and track colors
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Автокопирование кодов", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        Text(
                            "Копировать OTP/2FA сразу в буфер при скриншоте",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = autoCopy,
                        onCheckedChange = { autoCopy = it },
                        colors = switchColors
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Фоновый мониторинг", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        Text(
                            "Слушать появление новых скриншотов в MediaStore",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = monitoring,
                        onCheckedChange = { monitoring = it },
                        colors = switchColors
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.07f))

                OutlinedButton(
                    onClick = {
                        onClearAndRescan()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SlothPrimary),
                    border = BorderStroke(1.dp, SlothPrimary.copy(alpha = 0.35f))
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Сбросить кэш и пересканировать", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveSettings(apiKey, autoCopy, monitoring)
                    onDismiss()
                },
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SlothPrimary,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text("Сохранить", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp)
    )
}
