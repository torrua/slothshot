package com.slothshot.app.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slothshot.app.model.ContentCategory

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
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedFilter is FilterType.All,
            onClick = { onFilterSelected(FilterType.All) },
            label = { Text("All") }
        )
        FilterChip(
            selected = selectedFilter is FilterType.EphemeralOnly,
            onClick = { onFilterSelected(FilterType.EphemeralOnly) },
            label = { Text("⚡ Junk / OTP") }
        )
        FilterChip(
            selected = selectedFilter is FilterType.ByCategory && selectedFilter.category == ContentCategory.ENTERTAINMENT_MEDIA,
            onClick = { onFilterSelected(FilterType.ByCategory(ContentCategory.ENTERTAINMENT_MEDIA)) },
            label = { Text("🎬 Media & Books") }
        )
        FilterChip(
            selected = selectedFilter is FilterType.ByCategory && selectedFilter.category == ContentCategory.TRANSACTIONAL_RECEIPT,
            onClick = { onFilterSelected(FilterType.ByCategory(ContentCategory.TRANSACTIONAL_RECEIPT)) },
            label = { Text("🧾 Receipts & Orders") }
        )
        FilterChip(
            selected = selectedFilter is FilterType.ByCategory && selectedFilter.category == ContentCategory.KNOWLEDGE_REFERENCE,
            onClick = { onFilterSelected(FilterType.ByCategory(ContentCategory.KNOWLEDGE_REFERENCE)) },
            label = { Text("📚 Notes") }
        )
        FilterChip(
            selected = selectedFilter is FilterType.PurgedOnly,
            onClick = { onFilterSelected(FilterType.PurgedOnly) },
            label = { Text("🗑️ Purged") }
        )
    }
}
