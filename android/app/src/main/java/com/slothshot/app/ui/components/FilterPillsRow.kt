package com.slothshot.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
            label = "Все"
        )
        SleekChip(
            selected = selectedFilter is FilterType.EphemeralOnly,
            onClick = { onFilterSelected(FilterType.EphemeralOnly) },
            label = "⚡ Мусор / 2FA"
        )
        SleekChip(
            selected = selectedFilter is FilterType.ByCategory && selectedFilter.category == ContentCategory.ENTERTAINMENT_MEDIA,
            onClick = { onFilterSelected(FilterType.ByCategory(ContentCategory.ENTERTAINMENT_MEDIA)) },
            label = "🎬 Кино и книги"
        )
        SleekChip(
            selected = selectedFilter is FilterType.ByCategory && selectedFilter.category == ContentCategory.TRANSACTIONAL_RECEIPT,
            onClick = { onFilterSelected(FilterType.ByCategory(ContentCategory.TRANSACTIONAL_RECEIPT)) },
            label = "🧾 Чеки и заказы"
        )
        SleekChip(
            selected = selectedFilter is FilterType.ByCategory && selectedFilter.category == ContentCategory.KNOWLEDGE_REFERENCE,
            onClick = { onFilterSelected(FilterType.ByCategory(ContentCategory.KNOWLEDGE_REFERENCE)) },
            label = "📚 Заметки"
        )
        SleekChip(
            selected = selectedFilter is FilterType.PurgedOnly,
            onClick = { onFilterSelected(FilterType.PurgedOnly) },
            label = "🗑️ Очищенные"
        )
    }
}

@Composable
private fun SleekChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
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
            containerColor = Color.White.copy(alpha = 0.04f),
            labelColor = Color(0xFFCBD5E1)
        )
    )
}
