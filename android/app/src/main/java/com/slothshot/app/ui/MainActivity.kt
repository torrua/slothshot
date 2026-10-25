package com.slothshot.app.ui

import android.Manifest
import android.app.Activity
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FolderDelete
import androidx.compose.material.icons.filled.Psychology
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
import com.slothshot.app.ai.QwenModelState
import com.slothshot.app.model.RetentionClass
import com.slothshot.app.model.TriageItem
import com.slothshot.app.service.ScreenshotMonitorService
import com.slothshot.app.ui.components.*
import com.slothshot.app.ui.theme.SlothError
import com.slothshot.app.ui.theme.SlothPrimary
import com.slothshot.app.ui.theme.SlothSecondary
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
                            isQwenReady = qwenModelState is QwenModelState.Ready,
                            isScanning = isScanning,
                            onScanClicked = { viewModel.scanGallery() },
                            onSettingsClicked = { viewModel.showSettings.value = true },
                            onQwenClicked = { viewModel.showSettings.value = true }
                        )
                    },
                    floatingActionButton = {
                        AnimatedVisibility(
                            visible = metrics.ephemeralJunkCount > 0,
                            enter = fadeIn() + slideInVertically { it / 2 },
                            exit = fadeOut() + slideOutVertically { it / 2 }
                        ) {
                            ExtendedFloatingActionButton(
                                onClick = {
                                    requestPurge(viewModel.getAllEphemeralItems())
                                },
                                containerColor = SlothError,
                                contentColor = Color.White,
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.FolderDelete,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                text = {
                                    Text(
                                        text = "Очистить мусор (${metrics.ephemeralJunkCount})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                },
                                shape = RoundedCornerShape(16.dp),
                                elevation = FloatingActionButtonDefaults.elevation(6.dp)
                            )
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp)
                    ) {
                        // 1. Dashboard Metrics Card (Ultra-compact hero strip, expandable on demand)
                        item(key = "dashboard_metrics") {
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                DashboardMetricsCard(
                                    metrics = metrics,
                                    hasApiKey = viewModel.getApiKey().isNotBlank(),
                                    isScanning = isScanning,
                                    onConfigureKeyClicked = { viewModel.showSettings.value = true },
                                    onScanGalleryClicked = { viewModel.scanGallery() },
                                    onPurgeAllClicked = {
                                        requestPurge(viewModel.getAllEphemeralItems())
                                    }
                                )
                            }
                        }

                        // 2. Active Download or Error Banner for Local VLM (only shown when Downloading or Error!)
                        if (qwenModelState is QwenModelState.Downloading || qwenModelState is QwenModelState.Error) {
                            item(key = "qwen_model_status") {
                                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    QwenModelStatusCard(
                                        modelState = qwenModelState,
                                        onStartDownload = { viewModel.startQwenDownload() },
                                        onCancelDownload = { viewModel.cancelQwenDownload() },
                                        onDeleteModel = { viewModel.deleteQwenModel() }
                                    )
                                }
                            }
                        }

                        // 3. Sticky Category Filter Pills Row (pinned under TopAppBar with subtle border & elevation)
                        @OptIn(ExperimentalFoundationApi::class)
                        stickyHeader(key = "sticky_filter_pills") {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.background.copy(alpha = 0.98f),
                                shadowElevation = 2.dp
                            ) {
                                Column {
                                    FilterPillsRow(
                                        selectedFilter = filter,
                                        onFilterSelected = { viewModel.filter.value = it },
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                    HorizontalDivider(
                                        color = Color.White.copy(alpha = 0.06f),
                                        thickness = 0.8.dp
                                    )
                                }
                            }
                        }

                        // 4. Screenshots List or Empty State (dominates the viewport!)
                        if (items.isEmpty()) {
                            item(key = "empty_state") {
                                EmptyState(
                                    isScanning = isScanning,
                                    onScanClicked = { viewModel.scanGallery() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 32.dp)
                                )
                            }
                        } else {
                            items(items, key = { it.id }) { item ->
                                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
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

                    // Settings Dialog with Qwen on-device AI management
                    if (showSettings) {
                        SettingsModal(
                            currentApiKey = viewModel.getApiKey(),
                            autoCopyOtp = viewModel.isAutoCopyOtpEnabled(),
                            monitoringActive = viewModel.isMonitoringActive(),
                            qwenModelState = qwenModelState,
                            onSaveSettings = { key, autoCopy, monitoring ->
                                viewModel.updateSettings(key, autoCopy, monitoring)
                            },
                            onStartQwenDownload = { viewModel.startQwenDownload() },
                            onCancelQwenDownload = { viewModel.cancelQwenDownload() },
                            onDeleteQwenModel = { viewModel.deleteQwenModel() },
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
    isQwenReady: Boolean,
    isScanning: Boolean,
    onScanClicked: () -> Unit,
    onSettingsClicked: () -> Unit,
    onQwenClicked: () -> Unit = onSettingsClicked
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(SlothPrimary)
                            )
                            Text(
                                text = "LIVE",
                                color = SlothPrimary,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.4.sp
                            )
                        }
                    }
                }
                if (isQwenReady) {
                    Surface(
                        onClick = onQwenClicked,
                        color = SlothSecondary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(100.dp),
                        border = BorderStroke(1.dp, SlothSecondary.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = SlothSecondary,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "QWEN AI",
                                color = SlothSecondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.4.sp
                            )
                        }
                    }
                }
            }
        },
        actions = {
            IconButton(
                onClick = onScanClicked,
                enabled = !isScanning,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f))
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        strokeWidth = 2.dp,
                        color = SlothPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.DocumentScanner,
                        contentDescription = "Сканировать",
                        modifier = Modifier.size(18.dp),
                        tint = SlothPrimary
                    )
                }
            }

            IconButton(
                onClick = onSettingsClicked,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f))
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.size(18.dp),
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
private fun EmptyState(
    isScanning: Boolean,
    onScanClicked: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
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
                text = if (isScanning) "Работает локальное распознавание текста (Google ML Kit)..." else "Сделайте новый скриншот или нажмите «Сканировать» для быстрого разбора галереи.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!isScanning) {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onScanClicked,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SlothPrimary,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Сканировать галерею", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}
