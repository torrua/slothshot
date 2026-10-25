package com.slothshot.app.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.slothshot.app.model.RetentionClass
import com.slothshot.app.model.TriageItem
import com.slothshot.app.service.ScreenshotMonitorService
import com.slothshot.app.ui.components.*
import com.slothshot.app.ui.theme.SlothDarkBackground
import com.slothshot.app.ui.theme.SlothPrimary
import com.slothshot.app.ui.theme.SlothShotTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val pendingDeleteIds = mutableSetOf<String>()

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.markEphemeralPurged(pendingDeleteIds)
            pendingDeleteIds.clear()
            Toast.makeText(this, "Screenshots purged successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_IMAGES] == true
        } else {
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }

        if (storageGranted) {
            if (viewModel.isMonitoringActive()) {
                ScreenshotMonitorService.start(this)
            }
            viewModel.scanGallery()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()

        setContent {
            SlothShotTheme {
                val metrics by viewModel.metrics.collectAsState()
                val items by viewModel.filteredItems.collectAsState()
                val filter by viewModel.filter.collectAsState()
                val isScanning by viewModel.isScanning.collectAsState()
                val rawContentItem by viewModel.rawContentItem.collectAsState()
                val fullScreenImageItem by viewModel.fullScreenImageItem.collectAsState()
                val showSettings by viewModel.showSettings.collectAsState()
                val qwenModelState by viewModel.qwenModelState.collectAsState()

                Scaffold(
                    topBar = {
                        SlothTopAppBar(
                            isMonitoring = viewModel.isMonitoringActive(),
                            onSettingsClicked = { viewModel.showSettings.value = true }
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Dashboard Metrics Card
                        DashboardMetricsCard(
                            metrics = metrics,
                            hasApiKey = viewModel.getApiKey().isNotBlank(),
                            isScanning = isScanning,
                            onConfigureKeyClicked = { viewModel.showSettings.value = true },
                            onScanGalleryClicked = { viewModel.scanGallery() },
                            onPurgeAllClicked = {
                                val ephemeralItems = items.filter { it.retention == RetentionClass.EPHEMERAL_PURGE && !it.isPurged }
                                requestPurge(ephemeralItems)
                            }
                        )

                        // On-Device VLM Card (Alibaba Qwen2.5-VL 3B)
                        QwenModelStatusCard(
                            modelState = qwenModelState,
                            onStartDownload = { viewModel.startQwenDownload() },
                            onCancelDownload = { viewModel.cancelQwenDownload() },
                            onDeleteModel = { viewModel.deleteQwenModel() }
                        )

                        // Filter Pills
                        FilterPillsRow(
                            selectedFilter = filter,
                            onFilterSelected = { viewModel.filter.value = it }
                        )

                        // Items List
                        if (items.isEmpty()) {
                            EmptyState(isScanning = isScanning)
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(bottom = 24.dp)
                            ) {
                                items(items, key = { it.id }) { item ->
                                    TriageCard(
                                        item = item,
                                        onPurgeClicked = { requestPurge(listOf(it)) },
                                        onViewRawContent = { viewModel.rawContentItem.value = it },
                                        onImageClicked = { viewModel.fullScreenImageItem.value = it }
                                    )
                                }
                            }
                        }
                    }

                    // Raw Content Bottom Sheet
                    RawContentBottomSheet(
                        item = rawContentItem,
                        onDismiss = { viewModel.rawContentItem.value = null }
                    )

                    // Full Screen Image Viewer Dialog
                    FullScreenImageViewerDialog(
                        item = fullScreenImageItem,
                        onDismiss = { viewModel.fullScreenImageItem.value = null }
                    )

                    // Settings Dialog
                    if (showSettings) {
                        SettingsModal(
                            currentApiKey = viewModel.getApiKey(),
                            autoCopyOtp = viewModel.isAutoCopyOtpEnabled(),
                            monitoringActive = viewModel.isMonitoringActive(),
                            onSaveSettings = { key, autoCopy, monitoring ->
                                viewModel.updateSettings(key, autoCopy, monitoring)
                            },
                            onClearAndRescan = { viewModel.clearAndRescan() },
                            onDismiss = { viewModel.showSettings.value = false }
                        )
                    }
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            if (viewModel.isMonitoringActive()) {
                ScreenshotMonitorService.start(this)
            }
        }
    }

    private fun requestPurge(itemsToPurge: List<TriageItem>) {
        if (itemsToPurge.isEmpty()) return
        pendingDeleteIds.clear()
        pendingDeleteIds.addAll(itemsToPurge.map { it.id })

        val uris = itemsToPurge.mapNotNull {
            runCatching { Uri.parse(it.uriString) }.getOrNull()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pi = MediaStore.createDeleteRequest(contentResolver, uris)
                deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
            } catch (e: Exception) {
                // Fallback direct delete
                directDeleteFallback(itemsToPurge)
            }
        } else {
            directDeleteFallback(itemsToPurge)
        }
    }

    private fun directDeleteFallback(itemsToPurge: List<TriageItem>) {
        for (item in itemsToPurge) {
            runCatching {
                contentResolver.delete(Uri.parse(item.uriString), null, null)
            }
            viewModel.markItemPurged(item.id)
        }
        pendingDeleteIds.clear()
        Toast.makeText(this, "Purged ${itemsToPurge.size} items", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlothTopAppBar(
    isMonitoring: Boolean,
    onSettingsClicked: () -> Unit
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF059669), Color(0xFF10B981))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🦥",
                        fontSize = 18.sp
                    )
                }
                Text(
                    text = "SlothShot",
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = (-0.4).sp
                )
                if (isMonitoring) {
                    Surface(
                        color = SlothPrimary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(100.dp),
                        border = BorderStroke(1.dp, SlothPrimary.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(SlothPrimary)
                            )
                            Text(
                                text = "LIVE",
                                color = SlothPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        },
        actions = {
            IconButton(
                onClick = onSettingsClicked,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f))
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.size(19.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        )
    )
}

@Composable
private fun EmptyState(isScanning: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "🦥",
                fontSize = 48.sp
            )
            Text(
                text = if (isScanning) "Ленивец анализирует скриншоты..." else "Лента скриншотов пуста",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isScanning) "Работает локальное распознавание текста (Google ML Kit)..." else "Сделайте новый скриншот или нажмите «Сканировать» выше для разбора галереи.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
