package com.slothshot.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Movie
import androidx.compose.ui.graphics.vector.ImageVector
import com.slothshot.app.model.ContentCategory
import com.slothshot.app.model.RetentionClass

val ContentCategory.icon: ImageVector
    get() = when (this) {
        ContentCategory.TEMPORARY_UTILITY -> Icons.Default.Bolt
        ContentCategory.ENTERTAINMENT_MEDIA -> Icons.Default.Movie
        ContentCategory.TRANSACTIONAL_RECEIPT -> Icons.AutoMirrored.Filled.ReceiptLong
        ContentCategory.KNOWLEDGE_REFERENCE -> Icons.AutoMirrored.Filled.MenuBook
        ContentCategory.PERSONAL_CHAT -> Icons.Default.Forum
        ContentCategory.UNKNOWN -> Icons.Default.Folder
    }

val RetentionClass.icon: ImageVector
    get() = when (this) {
        RetentionClass.EPHEMERAL_PURGE -> Icons.Default.DeleteSweep
        RetentionClass.ACTION_REQUIRED -> Icons.Default.HourglassEmpty
        RetentionClass.KEEP_ARCHIVE -> Icons.Default.Bookmark
    }
