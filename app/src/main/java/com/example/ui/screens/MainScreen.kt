package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LogManager
import com.example.data.ProfileRepository
import com.example.model.ConfigParser
import com.example.model.ProxyProfile
import com.example.service.SangarProxyService
import com.example.ui.components.BatteryOptimizationCard
import com.example.ui.components.LogViewer
import com.example.ui.components.ProfileEditDialog
import com.example.ui.components.QrDisplayDialog
import com.example.ui.components.QrScanSheet
import com.example.ui.components.TipCard
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberEmerald
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    repository: ProfileRepository,
    currentTheme: String,
    onThemeChange: (String) -> Unit
) {
    val context = LocalContext.current
    val profiles by repository.profiles.collectAsState()
    val activeProfileId by repository.activeProfileId.collectAsState()

    val isRunning by SangarProxyService.isRunningFlow.collectAsState()
    val activeConnections by SangarProxyService.activeConnectionsFlow.collectAsState()
    val uploadBytes by SangarProxyService.uploadBytesFlow.collectAsState()
    val downloadBytes by SangarProxyService.downloadBytesFlow.collectAsState()

    val activeProfile = remember(profiles, activeProfileId) {
        profiles.find { it.id == activeProfileId } ?: profiles.firstOrNull()
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Profiles, 1: Live Logs, 2: Info & Battery

    // Dialog & Sheet States
    var showEditDialog by remember { mutableStateOf(false) }
    var profileToEdit by remember { mutableStateOf<ProxyProfile?>(null) }
    var profileToDelete by remember { mutableStateOf<ProxyProfile?>(null) }
    var showQrDisplayDialog by remember { mutableStateOf(false) }
    var showQrScanSheet by remember { mutableStateOf(false) }

    // Dropdown Menus
    var importMenuExpanded by remember { mutableStateOf(false) }
    var exportMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Sangar Logo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Sangar",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "SOCKS5 Worker Tunnel",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp
                            )
                        }
                    }
                },
                actions = {
                    // Theme switcher button
                    IconButton(
                        onClick = {
                            val nextTheme = when (currentTheme) {
                                "dark" -> "light"
                                "light" -> "system"
                                else -> "dark"
                            }
                            onThemeChange(nextTheme)
                        },
                        modifier = Modifier.testTag("theme_toggle_button")
                    ) {
                        val icon = when (currentTheme) {
                            "dark" -> Icons.Default.DarkMode
                            "light" -> Icons.Default.LightMode
                            else -> Icons.Default.SettingsBrightness
                        }
                        Icon(icon, contentDescription = "Toggle Theme")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            // Master Control & Status Card
            MasterControlCard(
                isRunning = isRunning,
                activeProfile = activeProfile,
                activeConnections = activeConnections,
                uploadBytes = uploadBytes,
                downloadBytes = downloadBytes,
                onToggleRunning = {
                    if (isRunning) {
                        SangarProxyService.stop(context)
                    } else {
                        if (activeProfile != null) {
                            SangarProxyService.start(context)
                        } else {
                            Toast.makeText(context, "Please configure a profile first.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // User Requested Reminder Banner with Lightbulb Icon
            TipCard()

            Spacer(modifier = Modifier.height(12.dp))

            // User Requested Grouped Import & Export Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Import Group Button with Menu
                Box(modifier = Modifier.weight(1f)) {
                    FilledTonalButton(
                        onClick = { importMenuExpanded = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("import_group_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import", fontWeight = FontWeight.Bold)
                    }

                    DropdownMenu(
                        expanded = importMenuExpanded,
                        onDismissRequest = { importMenuExpanded = false }
                    ) {
                        // 1. Import config from clipboard
                        DropdownMenuItem(
                            text = { Text("Import config from clipboard") },
                            leadingIcon = { Icon(Icons.Default.ContentPaste, contentDescription = null) },
                            onClick = {
                                importMenuExpanded = false
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                                if (!clipText.isNullOrBlank()) {
                                    val parsed = ConfigParser.parse(clipText)
                                    parsed.onSuccess { newProfile ->
                                        repository.addOrUpdateProfile(newProfile)
                                        Toast.makeText(context, "Imported: ${newProfile.name}", Toast.LENGTH_SHORT).show()
                                        LogManager.success("Config imported from clipboard: ${newProfile.name}")
                                    }.onFailure { err ->
                                        Toast.makeText(context, "Invalid format: ${err.message}", Toast.LENGTH_LONG).show()
                                        LogManager.error("Failed to parse config: ${err.message}")
                                    }
                                } else {
                                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.testTag("menu_import_clipboard")
                        )

                        // 2. Import QR barcode
                        DropdownMenuItem(
                            text = { Text("Import QR barcode") },
                            leadingIcon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                            onClick = {
                                importMenuExpanded = false
                                showQrScanSheet = true
                            },
                            modifier = Modifier.testTag("menu_import_qr")
                        )

                        // 3. Manual Add
                        DropdownMenuItem(
                            text = { Text("Add profile manually") },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                            onClick = {
                                importMenuExpanded = false
                                profileToEdit = null
                                showEditDialog = true
                            },
                            modifier = Modifier.testTag("menu_add_profile_manual")
                        )
                    }
                }

                // Export Group Button with Menu
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { exportMenuExpanded = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("export_group_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export", fontWeight = FontWeight.Bold)
                    }

                    DropdownMenu(
                        expanded = exportMenuExpanded,
                        onDismissRequest = { exportMenuExpanded = false }
                    ) {
                        // 1. Export config to clipboard
                        DropdownMenuItem(
                            text = { Text("Export config to clipboard") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = {
                                exportMenuExpanded = false
                                if (activeProfile != null) {
                                    val uri = ConfigParser.toConfigUri(activeProfile)
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Sangar Config", uri))
                                    Toast.makeText(context, "Active config copied to clipboard!", Toast.LENGTH_SHORT).show()
                                    LogManager.info("Exported active config to clipboard")
                                } else {
                                    Toast.makeText(context, "No active profile to export", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.testTag("menu_export_clipboard")
                        )

                        // 2. Export QR barcode
                        DropdownMenuItem(
                            text = { Text("Export QR barcode") },
                            leadingIcon = { Icon(Icons.Default.QrCode, contentDescription = null) },
                            onClick = {
                                exportMenuExpanded = false
                                if (activeProfile != null) {
                                    showQrDisplayDialog = true
                                } else {
                                    Toast.makeText(context, "No active profile to export", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.testTag("menu_export_qr")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Navigation Tabs (Profiles, Logs, Settings)
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .testTag("main_tab_row")
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Profiles (${profiles.size})", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("tab_profiles")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Live Logs", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("tab_logs")
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Power & Info", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("tab_settings")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Content Area based on Tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedTab) {
                    0 -> {
                        // Profiles List
                        ProfilesList(
                            profiles = profiles,
                            activeProfileId = activeProfileId,
                            onSelect = { repository.setActiveProfile(it.id) },
                            onEdit = {
                                profileToEdit = it
                                showEditDialog = true
                            },
                            onDelete = { profileToDelete = it },
                            onExportQr = {
                                repository.setActiveProfile(it.id)
                                showQrDisplayDialog = true
                            }
                        )
                    }
                    1 -> {
                        // Live Terminal Logs
                        LogViewer(modifier = Modifier.fillMaxSize())
                    }
                    2 -> {
                        // Settings & Battery info
                        SettingsPanel()
                    }
                }
            }
        }
    }

    // Dialogs
    if (showEditDialog) {
        ProfileEditDialog(
            profile = profileToEdit,
            onDismiss = { showEditDialog = false },
            onSave = { updated ->
                repository.addOrUpdateProfile(updated)
                showEditDialog = false
                Toast.makeText(context, "Saved ${updated.name}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showQrDisplayDialog && activeProfile != null) {
        QrDisplayDialog(
            profile = activeProfile,
            onDismiss = { showQrDisplayDialog = false }
        )
    }

    if (showQrScanSheet) {
        QrScanSheet(
            onDismiss = { showQrScanSheet = false },
            onScanned = { rawCode ->
                val parsed = ConfigParser.parse(rawCode)
                parsed.onSuccess { newProfile ->
                    repository.addOrUpdateProfile(newProfile)
                    Toast.makeText(context, "Imported QR: ${newProfile.name}", Toast.LENGTH_SHORT).show()
                    LogManager.success("Imported QR config: ${newProfile.name}")
                }.onFailure { err ->
                    Toast.makeText(context, "Invalid QR code: ${err.message}", Toast.LENGTH_LONG).show()
                    LogManager.error("Failed to parse QR code: ${err.message}")
                }
            }
        )
    }

    if (profileToDelete != null) {
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            title = { Text("Delete Profile") },
            text = { Text("Are you sure you want to delete '${profileToDelete?.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        profileToDelete?.let { repository.deleteProfile(it.id) }
                        profileToDelete = null
                        Toast.makeText(context, "Profile deleted", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { profileToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun MasterControlCard(
    isRunning: Boolean,
    activeProfile: ProxyProfile?,
    activeConnections: Int,
    uploadBytes: Long,
    downloadBytes: Long,
    onToggleRunning: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val buttonBgColor by animateColorAsState(
        targetValue = if (isRunning) CyberEmerald else MaterialTheme.colorScheme.surfaceVariant,
        label = "btnBg"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("master_control_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isRunning) CyberEmerald else Color.Gray)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRunning) "RUNNING" else "DISCONNECTED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isRunning) CyberEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = activeProfile?.name ?: "No Profile Selected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "127.0.0.1:${activeProfile?.localPort ?: 40446}  →  ${activeProfile?.getCleanWorkerHost() ?: "-"}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Big power toggle button
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .scale(if (isRunning) pulseScale else 1f)
                        .clip(CircleShape)
                        .background(buttonBgColor)
                        .clickable { onToggleRunning() }
                        .testTag("power_toggle_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "Power Toggle",
                        tint = if (isRunning) Color(0xFF00381B) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Telemetry stats row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    .padding(vertical = 10.dp, horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tunnels
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Tunnels", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "$activeConnections",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (activeConnections > 0) CyberCyan else MaterialTheme.colorScheme.onSurface
                    )
                }

                // Up
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(text = "Upload", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = formatBytes(uploadBytes), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Down
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = CyberEmerald, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(text = "Download", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = formatBytes(downloadBytes), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun ProfilesList(
    profiles: List<ProxyProfile>,
    activeProfileId: String?,
    onSelect: (ProxyProfile) -> Unit,
    onEdit: (ProxyProfile) -> Unit,
    onDelete: (ProxyProfile) -> Unit,
    onExportQr: (ProxyProfile) -> Unit
) {
    if (profiles.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No profiles found. Use Import to add one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(profiles, key = { it.id }) { profile ->
                val isSelected = profile.id == activeProfileId

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelect(profile) }
                        .then(
                            if (isSelected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                            else Modifier
                        )
                        .testTag("profile_card_${profile.id}"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = if (isSelected) "Active Profile" else "Inactive Profile",
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${profile.getCleanWorkerHost()}:${profile.localPort}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (profile.cleanIp.isNotBlank()) {
                                Text(
                                    text = "Clean IP: ${profile.cleanIp}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyberEmerald,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        // Actions: Export QR, Edit, Delete
                        IconButton(
                            onClick = { onExportQr(profile) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.QrCode, contentDescription = "Export QR", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }

                        IconButton(
                            onClick = { onEdit(profile) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Profile", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }

                        IconButton(
                            onClick = { onDelete(profile) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Profile", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsPanel() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Battery Optimization Exemption Card
        BatteryOptimizationCard()

        // Connection Technical Specs
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Connection Protocol Details",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• Local SOCKS5 Proxy: RFC 1928, listens on 127.0.0.1\n" +
                            "• Tunnel Engine: Cloudflare Worker Secure WebSocket (chat.v2 subprotocol)\n" +
                            "• Cryptography: 16-byte nonce key derivation with HMAC-SHA256 & dynamic fast XOR streaming cipher\n" +
                            "• Clean IP Routing: Direct Cloudflare edge routing with SNI and certificate preserved\n" +
                            "• Compatibility: Designed for seamless chaining with v2rayNG, Nekobox, Clash, Telegram, browsers, and terminal tools",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val format = DecimalFormat("#,##0.#")
    return "${format.format(bytes / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
}
