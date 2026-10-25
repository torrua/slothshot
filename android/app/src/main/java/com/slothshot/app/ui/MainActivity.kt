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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

                Scaffold(
                    topBar = {
                        SlothTopAppBar(
                            isMonitoring = viewModel.isMonitoringActive(),
                            onSettingsClicked = { viewModel.showSettings.value = true }
                        )
                    },
                    containerColor = SlothDarkBackground
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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🦥 SlothShot",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isMonitoring) {
                    Box(
                        modifier = Modifier
                            .background(SlothPrimary.copy(alpha = 0.2f), shape = MaterialTheme.shapes.small)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "LIVE",
                            color = SlothPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        actions = {
            IconButton(onClick = onSettingsClicked) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurface
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
                text = if (isScanning) "Sloth is scanning your screenshots..." else "No screenshots found yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isScanning) "Running Google ML Kit on-device OCR..." else "Take a screenshot on your phone, or tap 'Scan Gallery' above to triage existing photos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
