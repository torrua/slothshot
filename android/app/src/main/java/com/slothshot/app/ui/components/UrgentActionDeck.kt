package com.slothshot.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.slothshot.app.model.TriageItem
import com.slothshot.app.ui.theme.SlothError
import com.slothshot.app.ui.theme.SlothPrimary

private val UrgentAmber = Color(0xFFF59E0B)

@Composable
fun UrgentActionDeck(
    urgentItems: List<TriageItem>,
    onMarkDone: (TriageItem) -> Unit,
    onPurgeClicked: (TriageItem) -> Unit,
    onImageClicked: (TriageItem) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = urgentItems.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        tint = UrgentAmber
                    )
                    Text(
                        text = "ТРЕБУЕТ ДЕЙСТВИЯ",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 0.5.sp
                    )
                    Surface(
                        color = UrgentAmber.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(100.dp),
                        border = BorderStroke(1.dp, UrgentAmber.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "${urgentItems.size}",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = UrgentAmber
                        )
                    }
                }

                Text(
                    text = "1 тап для решения",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            // Cards Carousel or Single Card
            if (urgentItems.size == 1) {
                UrgentCard(
                    item = urgentItems.first(),
                    onMarkDone = onMarkDone,
                    onPurgeClicked = onPurgeClicked,
                    onImageClicked = onImageClicked,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    items(urgentItems, key = { it.id }) { item ->
                        UrgentCard(
                            item = item,
                            onMarkDone = onMarkDone,
                            onPurgeClicked = onPurgeClicked,
                            onImageClicked = onImageClicked,
                            modifier = Modifier.width(300.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UrgentCard(
    item: TriageItem,
    onMarkDone: (TriageItem) -> Unit,
    onPurgeClicked: (TriageItem) -> Unit,
    onImageClicked: (TriageItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.2.dp,
            Brush.horizontalGradient(
                listOf(
                    UrgentAmber.copy(alpha = 0.55f),
                    UrgentAmber.copy(alpha = 0.2f)
                )
            )
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Category Chip + Action Buttons (Готово / Удалить)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Chip
                Surface(
                    color = UrgentAmber.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(100.dp),
                    border = BorderStroke(1.dp, UrgentAmber.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = item.category.icon,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = UrgentAmber
                        )
                        Text(
                            text = item.category.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            color = UrgentAmber
                        )
                    }
                }

                // Quick Header Actions
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Mark Done Button
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onMarkDone(item)
                        },
                        color = SlothPrimary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(100.dp),
                        border = BorderStroke(1.dp, SlothPrimary.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Выполнено",
                                modifier = Modifier.size(13.dp),
                                tint = SlothPrimary
                            )
                            Text(
                                text = "Готово",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = SlothPrimary
                            )
                        }
                    }

                    // Delete / Purge Button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPurgeClicked(item)
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Удалить в архив",
                            modifier = Modifier.size(17.dp),
                            tint = SlothError.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Middle: Thumbnail + Title + Highlighted Parameter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Screenshot Thumbnail
                AsyncImage(
                    model = item.uriString,
                    contentDescription = "Превью скриншота",
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onImageClicked(item) },
                    contentScale = ContentScale.Crop
                )

                // Info Column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // First Key-Value highlight or brief summary
                    val firstKv = item.keyValues.entries.firstOrNull()
                    if (firstKv != null) {
                        Surface(
                            color = Color.White.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.08f))
                        ) {
                            Text(
                                text = "${firstKv.key}: ${firstKv.value}",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            text = item.summary,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Bottom CTA: 1-Tap Execution Button
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    handleItemAction(context, item.primaryActionType, item.primaryActionPayload)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = UrgentAmber,
                    contentColor = Color(0xFF1A1A1A)
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                val actionIcon = when (item.primaryActionType) {
                    ActionType.COPY -> Icons.Default.ContentCopy
                    ActionType.OPEN_URL -> Icons.Default.OpenInBrowser
                    ActionType.SEARCH_WEB -> Icons.Default.Search
                    ActionType.SHARE -> Icons.Default.Share
                    ActionType.PURGE -> Icons.Default.DeleteOutline
                }
                Icon(
                    imageVector = actionIcon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFF1A1A1A)
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = item.primaryActionLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
