package com.slothshot.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.slothshot.app.model.ActionType
import com.slothshot.app.model.RetentionClass
import com.slothshot.app.model.TriageItem
import com.slothshot.app.ui.theme.SlothError
import com.slothshot.app.ui.theme.SlothPrimary
import com.slothshot.app.ui.theme.SlothSurfaceVariant

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TriageCard(
    item: TriageItem,
    onPurgeClicked: (TriageItem) -> Unit,
    onViewRawContent: (TriageItem) -> Unit,
    onImageClicked: (TriageItem) -> Unit,
    onRestoreClicked: ((TriageItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Badges row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Chip
                Surface(
                    color = Color.White.copy(alpha = 0.04f),
                    shape = RoundedCornerShape(100.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = item.category.icon,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = item.category.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Retention Badge
                val retentionColor = when (item.retention) {
                    RetentionClass.EPHEMERAL_PURGE -> SlothError
                    RetentionClass.KEEP_ARCHIVE -> SlothPrimary
                    RetentionClass.ACTION_REQUIRED -> Color(0xFFF59E0B)
                }

                Surface(
                    color = if (item.isPurged) Color.White.copy(alpha = 0.05f) else retentionColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(100.dp),
                    border = BorderStroke(1.dp, if (item.isPurged) Color.White.copy(alpha = 0.1f) else retentionColor.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val badgeIcon = if (item.isPurged) Icons.Default.Archive else item.retention.icon
                        Icon(
                            imageVector = badgeIcon,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = if (item.isPurged) Color.Gray else retentionColor
                        )
                        Text(
                            text = if (item.isPurged) "В АРХИВЕ" else item.retention.label,
                            color = if (item.isPurged) Color.Gray else retentionColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Middle: Image thumbnail + Title & Summary (tap text to toggle expansion)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Thumbnail (clickable for full-screen view)
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .clickable { onImageClicked(item) },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = item.uriString,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                // Title & Summary (tap to expand/collapse full description)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            isExpanded = !isExpanded
                        },
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )

                    // Expand/collapse indicator button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = if (isExpanded) "Свернуть" else "Подробнее",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlothPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Свернуть" else "Развернуть",
                            modifier = Modifier.size(14.dp),
                            tint = SlothPrimary
                        )
                    }
                }
            }

            // Key-Values & Tags chips with wrapping FlowRow (never truncated with ugly ellipses!)
            val hasContent = item.keyValues.isNotEmpty() || item.tags.isNotEmpty()
            if (hasContent) {
                val displayedKeyValues = if (isExpanded) item.keyValues.entries.toList() else item.keyValues.entries.take(3).toList()
                val displayedTags = if (isExpanded) item.tags else emptyList()

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    displayedKeyValues.forEach { (k, v) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.04f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                            modifier = Modifier.clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isExpanded = !isExpanded
                            }
                        ) {
                            Text(
                                text = "$k: $v",
                                style = MaterialTheme.typography.labelSmall,
                                color = SlothPrimary,
                                maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    displayedTags.forEach { tag ->
                        val formattedTag = if (tag.startsWith("#")) tag else "#$tag"
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SlothPrimary.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, SlothPrimary.copy(alpha = 0.22f)),
                            modifier = Modifier.clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isExpanded = !isExpanded
                            }
                        ) {
                            Text(
                                text = formattedTag,
                                style = MaterialTheme.typography.labelSmall,
                                color = SlothPrimary,
                                fontWeight = FontWeight.Medium,
                                maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (!isExpanded && (item.keyValues.size > 3 || item.tags.isNotEmpty())) {
                        val hiddenCount = (item.keyValues.size - 3).coerceAtLeast(0) + item.tags.size
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.03f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
                            modifier = Modifier.clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isExpanded = true
                            }
                        ) {
                            Text(
                                text = "+$hiddenCount ещё",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.8.dp)

            // Actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Primary Action Button (accessible height)
                FilledTonalButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        handleItemAction(context, item.primaryActionType, item.primaryActionPayload)
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = SlothPrimary.copy(alpha = 0.2f),
                        contentColor = SlothPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                ) {
                    val icon = when (item.primaryActionType) {
                        ActionType.COPY -> Icons.Default.ContentCopy
                        ActionType.OPEN_URL -> Icons.Default.OpenInBrowser
                        ActionType.SEARCH_WEB -> Icons.Default.Search
                        else -> Icons.Default.ContentCopy
                    }
                    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.primaryActionLabel,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // OCR Raw Content Button (WCAG 44-48dp touch target)
                IconButton(
                    onClick = { onViewRawContent(item) },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "Посмотреть распознанный текст",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Purge / Restore Button (WCAG 44-48dp touch target with haptic feedback)
                if (item.isPurged) {
                    if (onRestoreClicked != null) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onRestoreClicked(item)
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = "Восстановить из архива",
                                modifier = Modifier.size(20.dp),
                                tint = SlothPrimary
                            )
                        }
                    }
                } else {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPurgeClicked(item)
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Удалить скриншот в архив",
                            modifier = Modifier.size(20.dp),
                            tint = SlothError
                        )
                    }
                }
            }
        }
    }
}

fun handleItemAction(context: Context, type: ActionType, payload: String) {
    when (type) {
        ActionType.COPY -> {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("SlothShot", payload)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
        }
        ActionType.OPEN_URL -> {
            runCatching {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(payload))
                context.startActivity(intent)
            }.onFailure {
                Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
            }
        }
        ActionType.SEARCH_WEB -> {
            runCatching {
                val searchUri = Uri.parse("https://www.google.com/search?q=${Uri.encode(payload)}")
                val intent = Intent(Intent.ACTION_VIEW, searchUri)
                context.startActivity(intent)
            }.onFailure {
                Toast.makeText(context, "Не удалось открыть поиск", Toast.LENGTH_SHORT).show()
            }
        }
        else -> {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("SlothShot", payload)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
        }
    }
}
