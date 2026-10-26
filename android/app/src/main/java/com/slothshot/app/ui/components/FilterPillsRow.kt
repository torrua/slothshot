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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slothshot.app.model.ContentCategory
import com.slothshot.app.ui.theme.SlothPrimary

sealed class FilterType {
    object All : FilterType()
    object EphemeralOnly : FilterType()
    data class ByCategory(val category: ContentCategory) : FilterType()
    object PurgedOnly : FilterType()
}

@Composable
fun FilterPillsRow(
    selectedFilter: FilterType,
    onFilterSelected: (FilterType) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp)
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SleekChip(
            selected = selectedFilter is FilterType.All,
            onClick = { onFilterSelected(FilterType.All) },
            label = "Все",
            icon = Icons.Default.Apps
        )
        SleekChip(
            selected = selectedFilter is FilterType.EphemeralOnly,
            onClick = { onFilterSelected(FilterType.EphemeralOnly) },
            label = "Мусор / 2FA",
            icon = Icons.Default.Bolt
        )
        SleekChip(
            selected = selectedFilter is FilterType.ByCategory && selectedFilter.category == ContentCategory.ENTERTAINMENT_MEDIA,
            onClick = { onFilterSelected(FilterType.ByCategory(ContentCategory.ENTERTAINMENT_MEDIA)) },
            label = "Кино и книги",
            icon = Icons.Default.Movie
        )
        SleekChip(
            selected = selectedFilter is FilterType.ByCategory && selectedFilter.category == ContentCategory.TRANSACTIONAL_RECEIPT,
            onClick = { onFilterSelected(FilterType.ByCategory(ContentCategory.TRANSACTIONAL_RECEIPT)) },
            label = "Чеки и заказы",
            icon = Icons.AutoMirrored.Filled.ReceiptLong
        )
        SleekChip(
            selected = selectedFilter is FilterType.ByCategory && selectedFilter.category == ContentCategory.KNOWLEDGE_REFERENCE,
            onClick = { onFilterSelected(FilterType.ByCategory(ContentCategory.KNOWLEDGE_REFERENCE)) },
            label = "Заметки",
            icon = Icons.AutoMirrored.Filled.MenuBook
        )
        SleekChip(
            selected = selectedFilter is FilterType.PurgedOnly,
            onClick = { onFilterSelected(FilterType.PurgedOnly) },
            label = "Архив",
            icon = Icons.Default.Archive
        )
    }
}

@Composable
private fun SleekChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    icon: ImageVector? = null
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        leadingIcon = if (icon != null) {
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = if (selected) Color(0xFF090D16) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else null,
        label = {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        },
        shape = RoundedCornerShape(100.dp),
        border = if (selected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = SlothPrimary,
            selectedLabelColor = Color(0xFF090D16),
            selectedLeadingIconColor = Color(0xFF090D16),
            containerColor = Color.White.copy(alpha = 0.04f),
            labelColor = Color(0xFFCBD5E1),
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}
