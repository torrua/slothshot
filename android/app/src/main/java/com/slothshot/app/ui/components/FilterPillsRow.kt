package com.slothshot.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slothshot.app.model.ContentCategory
import com.slothshot.app.ui.theme.SlothError
import com.slothshot.app.ui.theme.SlothPrimary

sealed class FilterType {
    object All : FilterType()
    object Receipts : FilterType()
    object Shopping : FilterType()
    object Knowledge : FilterType()
    object EphemeralOnly : FilterType()
    data class ByCategory(val category: ContentCategory) : FilterType()
    object PurgedOnly : FilterType()
}

data class HubStats(
    val totalCount: Int = 0,
    val receiptsCount: Int = 0,
    val receiptsTotalAmount: Double = 0.0,
    val shoppingCount: Int = 0,
    val knowledgeCount: Int = 0,
    val ephemeralCount: Int = 0,
    val ephemeralBytes: Long = 0L,
    val archivedCount: Int = 0
)

@Composable
fun FilterPillsRow(
    selectedFilter: FilterType,
    onFilterSelected: (FilterType) -> Unit,
    hubStats: HubStats = HubStats(),
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp)
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Все
        SmartHubChip(
            selected = selectedFilter is FilterType.All,
            onClick = { onFilterSelected(FilterType.All) },
            label = "Все",
            badgeText = if (hubStats.totalCount > 0) "${hubStats.totalCount}" else null,
            icon = Icons.Default.Apps
        )

        // 2. Чеки и заказы (с подсчетом суммы или количества)
        val receiptsBadge = when {
            hubStats.receiptsTotalAmount > 0 -> formatAmountBadge(hubStats.receiptsTotalAmount)
            hubStats.receiptsCount > 0 -> "${hubStats.receiptsCount}"
            else -> null
        }
        SmartHubChip(
            selected = selectedFilter is FilterType.Receipts,
            onClick = { onFilterSelected(FilterType.Receipts) },
            label = "Чеки",
            badgeText = receiptsBadge,
            badgeColor = SlothPrimary,
            icon = Icons.AutoMirrored.Filled.ReceiptLong
        )

        // 3. Покупки и вишлист
        SmartHubChip(
            selected = selectedFilter is FilterType.Shopping,
            onClick = { onFilterSelected(FilterType.Shopping) },
            label = "Покупки",
            badgeText = if (hubStats.shoppingCount > 0) "${hubStats.shoppingCount}" else null,
            badgeColor = Color(0xFFA855F7), // Purple / Violet
            icon = Icons.Default.ShoppingCart
        )

        // 4. Заметки и база знаний
        SmartHubChip(
            selected = selectedFilter is FilterType.Knowledge,
            onClick = { onFilterSelected(FilterType.Knowledge) },
            label = "Заметки",
            badgeText = if (hubStats.knowledgeCount > 0) "${hubStats.knowledgeCount}" else null,
            badgeColor = Color(0xFF38BDF8), // Sky Blue
            icon = Icons.AutoMirrored.Filled.MenuBook
        )

        // 5. Временный мусор / 2FA
        val junkBadge = when {
            hubStats.ephemeralBytes > 0 -> formatBytesBadge(hubStats.ephemeralBytes)
            hubStats.ephemeralCount > 0 -> "${hubStats.ephemeralCount}"
            else -> null
        }
        SmartHubChip(
            selected = selectedFilter is FilterType.EphemeralOnly,
            onClick = { onFilterSelected(FilterType.EphemeralOnly) },
            label = "Мусор",
            badgeText = junkBadge,
            badgeColor = SlothError,
            icon = Icons.Default.DeleteOutline
        )

        // 6. Архив
        SmartHubChip(
            selected = selectedFilter is FilterType.PurgedOnly,
            onClick = { onFilterSelected(FilterType.PurgedOnly) },
            label = "Архив",
            badgeText = if (hubStats.archivedCount > 0) "${hubStats.archivedCount}" else null,
            badgeColor = Color.White.copy(alpha = 0.5f),
            icon = Icons.Default.Archive
        )
    }
}

@Composable
private fun SmartHubChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    badgeText: String? = null,
    badgeColor: Color = SlothPrimary,
    icon: ImageVector? = null
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(100.dp),
        color = if (selected) SlothPrimary else Color.White.copy(alpha = 0.04f),
        border = if (selected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.height(34.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = if (selected) Color(0xFF090D16) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color(0xFF090D16) else Color(0xFFCBD5E1)
            )
            if (badgeText != null) {
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = if (selected) Color(0xFF090D16).copy(alpha = 0.15f) else badgeColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = badgeText,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color(0xFF090D16) else badgeColor
                    )
                }
            }
        }
    }
}

private fun formatAmountBadge(amount: Double): String {
    return if (amount >= 1000) {
        String.format("%,.0f ₽", amount).replace(",", " ")
    } else {
        String.format("%.0f ₽", amount)
    }
}

private fun formatBytesBadge(bytes: Long): String {
    return if (bytes >= 1_000_000) {
        String.format("%.1f МБ", bytes / 1_000_000.0).replace(",", ".")
    } else {
        String.format("%d КБ", bytes / 1000)
    }
}
