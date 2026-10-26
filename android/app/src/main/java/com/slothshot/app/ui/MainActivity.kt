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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.slothshot.app.R
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
            val count = pendingDeleteIds.size
            viewModel.markEphemeralPurged(pendingDeleteIds)
            pendingDeleteIds.clear()
            Toast.makeText(this, "Отправлено в архив ($count)", Toast.LENGTH_SHORT).show()
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
                val urgentItems by viewModel.urgentItems.collectAsState()
                val filter by viewModel.filter.collectAsState()
                val isScanning by viewModel.isScanning.collectAsState()
                val rawContentItem by viewModel.rawContentItem.collectAsState()
                val fullScreenImageItem by viewModel.fullScreenImageItem.collectAsState()
                val showSettings by viewModel.showSettings.collectAsState()

                Scaffold(
                    topBar = {
                        SlothTopAppBar(
                            isMonitoring = viewModel.isMonitoringActive(),
                            hasGeminiKey = viewModel.getApiKey().isNotBlank(),
                            isScanning = isScanning,
                            onScanClicked = { viewModel.scanGallery() },
                            onSettingsClicked = { viewModel.showSettings.value = true }
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

                        // 2. Urgent Action Deck (⚡ ТРЕБУЕТ ДЕЙСТВИЯ — 1-тап решения задач из скриншотов)
                        if (filter !is FilterType.PurgedOnly && urgentItems.isNotEmpty()) {
                            item(key = "urgent_action_deck") {
                                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                                    UrgentActionDeck(
                                        urgentItems = urgentItems,
                                        onMarkDone = { item ->
                                            viewModel.markActionDone(item.id)
                                            Toast.makeText(this@MainActivity, "Выполнено — сохранено в архив", Toast.LENGTH_SHORT).show()
                                        },
                                        onPurgeClicked = { item ->
                                            requestPurge(listOf(item))
                                        },
                                        onImageClicked = { item ->
                                            viewModel.fullScreenImageItem.value = item
                                        }
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
                                    isArchive = filter is FilterType.PurgedOnly,
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
                                        onImageClicked = { viewModel.fullScreenImageItem.value = it },
                                        onRestoreClicked = {
                                            viewModel.restoreItem(it.id)
                                            Toast.makeText(this@MainActivity, "Восстановлено из архива", Toast.LENGTH_SHORT).show()
                                        }
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
        Toast.makeText(this, "Отправлено в архив (${itemsToPurge.size})", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlothTopAppBar(
    isMonitoring: Boolean,
    hasGeminiKey: Boolean,
    isScanning: Boolean,
    onScanClicked: () -> Unit,
    onSettingsClicked: () -> Unit
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_sloth_logo),
                    contentDescription = "SlothShot Logo",
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
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
                if (hasGeminiKey) {
                    Surface(
                        onClick = onSettingsClicked,
                        color = SlothPrimary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(100.dp),
                        border = BorderStroke(1.dp, SlothPrimary.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = SlothPrimary
                            )
                            Text(
                                text = "GEMINI",
                                color = SlothPrimary,
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
                    contentDescription = "Настройки",
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
    isArchive: Boolean = false,
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
            if (isArchive) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(SlothPrimary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Archive,
                        contentDescription = "Архив",
                        tint = SlothPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            } else {
                Image(
                    painter = painterResource(id = R.drawable.ic_sloth_logo),
                    contentDescription = "Sloth Logo",
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(18.dp))
                )
            }
            Text(
                text = if (isScanning) "Анализ скриншотов..."
                       else if (isArchive) "В архиве пусто"
                       else "Скриншотов нет",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isScanning) "Извлекаем данные и категории..."
                       else if (isArchive) "Удаленные скриншоты хранятся здесь"
                       else "Сделайте скриншот или нажмите «Сканировать»",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!isScanning && !isArchive) {
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
                    Text("Сканировать", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}
