package com.slothshot.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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

@Composable
fun TriageCard(
    item: TriageItem,
    onPurgeClicked: (TriageItem) -> Unit,
    onViewRawContent: (TriageItem) -> Unit,
    onImageClicked: (TriageItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Badges row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Chip
                Text(
                    text = "${item.category.emoji} ${item.category.displayName}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Retention Badge
                val retentionColor = when (item.retention) {
                    RetentionClass.EPHEMERAL_PURGE -> SlothError
                    RetentionClass.KEEP_ARCHIVE -> SlothPrimary
                    RetentionClass.ACTION_REQUIRED -> Color(0xFFF59E0B)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (item.isPurged) Color.Gray.copy(alpha = 0.2f) else retentionColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (item.isPurged) "PURGED 🗑️" else item.retention.label,
                        color = if (item.isPurged) Color.Gray else retentionColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Middle: Image thumbnail + Title & Summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Thumbnail (clickable for full-screen view)
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SlothSurfaceVariant)
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

                // Title & Summary
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Key-Values chips
            if (item.keyValues.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item.keyValues.entries.take(3).forEach { (k, v) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(SlothSurfaceVariant.copy(alpha = 0.6f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "$k: $v",
                                style = MaterialTheme.typography.labelSmall,
                                color = SlothPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Divider(color = SlothSurfaceVariant.copy(alpha = 0.4f), thickness = 0.8.dp)

            // Actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Primary Action Button
                FilledTonalButton(
                    onClick = {
                        handleAction(context, item.primaryActionType, item.primaryActionPayload)
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = SlothPrimary.copy(alpha = 0.2f),
                        contentColor = SlothPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
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
                        maxLines = 1
                    )
                }

                // OCR Raw Content Button
                IconButton(
                    onClick = { onViewRawContent(item) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "View Raw OCR",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Purge Button
                if (!item.isPurged) {
                    IconButton(
                        onClick = { onPurgeClicked(item) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Purge Screenshot",
                            tint = SlothError
                        )
                    }
                }
            }
        }
    }
}

private fun handleAction(context: Context, type: ActionType, payload: String) {
    when (type) {
        ActionType.COPY -> {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("SlothShot", payload)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Copied: $payload", Toast.LENGTH_SHORT).show()
        }
        ActionType.OPEN_URL -> {
            runCatching {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(payload))
                context.startActivity(intent)
            }.onFailure {
                Toast.makeText(context, "Cannot open URL", Toast.LENGTH_SHORT).show()
            }
        }
        ActionType.SEARCH_WEB -> {
            runCatching {
                val searchUri = Uri.parse("https://www.google.com/search?q=${Uri.encode(payload)}")
                val intent = Intent(Intent.ACTION_VIEW, searchUri)
                context.startActivity(intent)
            }.onFailure {
                Toast.makeText(context, "Cannot open web search", Toast.LENGTH_SHORT).show()
            }
        }
        else -> {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("SlothShot", payload)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Copied: $payload", Toast.LENGTH_SHORT).show()
        }
    }
}
