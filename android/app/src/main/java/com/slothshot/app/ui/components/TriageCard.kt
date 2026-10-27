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
import androidx.compose.ui.text.style.TextAlign
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

            // Middle: Image thumbnail on the left, Title & ALL TAGS to the right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Thumbnail (clickable for full-screen view)
                Box(
                    modifier = Modifier
                        .size(76.dp)
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

                // Right of thumbnail: Title and ALL TAGS
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // All tags positioned right next to the thumbnail
                    if (item.tags.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            item.tags.forEach { tag ->
                                val formattedTag = if (tag.startsWith("#")) tag else "#$tag"
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = SlothPrimary.copy(alpha = 0.08f),
                                    border = BorderStroke(1.dp, SlothPrimary.copy(alpha = 0.22f))
                                ) {
                                    Text(
                                        text = formattedTag,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 11.sp,
                                        color = SlothPrimary,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Text section below (Текст - внизу, свободный от пузырей)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isExpanded = !isExpanded
                    },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Main summary text (чистый читаемый текст, а не пузыри)
                Text(
                    text = item.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    lineHeight = 20.sp,
                    fontSize = 13.5.sp,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )

                // Key Facts & Parameters (структурированная аккуратная таблица фактов вместо облака пузырей)
                if (item.keyValues.isNotEmpty()) {
                    val displayedKvs = if (isExpanded) item.keyValues.entries.toList() else item.keyValues.entries.take(3).toList()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.035f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        displayedKvs.forEach { (k, v) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = k,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(0.45f)
                                )
                                Text(
                                    text = v,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SlothPrimary,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(0.55f)
                                )
                            }
                        }

                        if (!isExpanded && item.keyValues.size > 3) {
                            Text(
                                text = "+ ещё ${item.keyValues.size - 3} параметра",
                                style = MaterialTheme.typography.labelSmall,
                                color = SlothPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }

                // Expand/collapse indicator button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
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
