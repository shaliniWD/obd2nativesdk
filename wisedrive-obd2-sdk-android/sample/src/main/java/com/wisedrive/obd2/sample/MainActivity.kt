package com.wisedrive.obd2.sample

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wisedrive.obd2.WiseDriveOBD2SDK
import com.wisedrive.obd2.models.*
import kotlinx.coroutines.launch

/**
 * WiseDrive OBD2 SDK Demo Application
 * Demonstrates full SDK functionality with polished Jetpack Compose UI
 */
@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private lateinit var sdk: WiseDriveOBD2SDK
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Some permissions denied", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize SDK with mock adapter for demo
        sdk = WiseDriveOBD2SDK.initialize(this, useMock = true)
        sdk.setLoggingEnabled(true)
        
        setContent {
            WiseDriveTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    OBDScannerApp(sdk, ::requestPermissions)
                }
            }
        }
    }

    private fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        permissionLauncher.launch(permissions)
    }
}

// Color scheme
val DarkBackground = Color(0xFF1A1A2E)
val DarkSurface = Color(0xFF16213E)
val AccentBlue = Color(0xFF0F3460)
val AccentCyan = Color(0xFF00D9FF)
val AccentGreen = Color(0xFF00F5A0)
val AccentRed = Color(0xFFFF4757)
val AccentOrange = Color(0xFFFF9F43)
val TextPrimary = Color(0xFFE8E8E8)
val TextSecondary = Color(0xFF9CA3AF)

@Composable
fun WiseDriveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AccentCyan,
            secondary = AccentGreen,
            background = DarkBackground,
            surface = DarkSurface,
            onPrimary = DarkBackground,
            onSecondary = DarkBackground,
            onBackground = TextPrimary,
            onSurface = TextPrimary
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OBDScannerApp(sdk: WiseDriveOBD2SDK, requestPermissions: () -> Unit) {
    val scope = rememberCoroutineScope()
    
    // State
    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    var devices by remember { mutableStateOf(listOf<BLEDevice>()) }
    var isScanning by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(false) }
    var connectedDevice by remember { mutableStateOf<BLEDevice?>(null) }
    var scanStages by remember { mutableStateOf(listOf<ScanStage>()) }
    var isScanRunning by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<EncryptedPayload?>(null) }
    var isInitialized by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Selected manufacturer
    var selectedManufacturer by remember { mutableStateOf("hyundai") }
    var orderId by remember { mutableStateOf("ORDER-${System.currentTimeMillis()}") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DarkBackground, DarkSurface)
                )
            )
    ) {
        // Header
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.DirectionsCar,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "WiseDrive OBD2",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            ),
            actions = {
                if (isConnected) {
                    StatusChip(
                        text = "Connected",
                        color = AccentGreen,
                        icon = Icons.Default.Bluetooth
                    )
                }
            }
        )

        // Main Content
        when (currentScreen) {
            Screen.HOME -> {
                HomeScreen(
                    isInitialized = isInitialized,
                    isConnected = isConnected,
                    connectedDevice = connectedDevice,
                    onRequestPermissions = requestPermissions,
                    onInitialize = {
                        scope.launch {
                            try {
                                isInitialized = sdk.initializeWithKey("demo-key")
                            } catch (e: Exception) {
                                errorMessage = e.message
                            }
                        }
                    },
                    onDiscoverDevices = {
                        currentScreen = Screen.DEVICES
                        scope.launch {
                            devices = emptyList()
                            isScanning = true
                            sdk.discoverDevices(
                                onDeviceFound = { device ->
                                    devices = devices + device
                                },
                                timeoutMs = 8000
                            )
                            isScanning = false
                        }
                    },
                    onStartScan = {
                        currentScreen = Screen.SCANNING
                        scanStages = emptyList()
                        scanResult = null
                        isScanRunning = true
                        
                        scope.launch {
                            try {
                                val result = sdk.runFullScan(ScanOptions(
                                    orderId = orderId,
                                    manufacturer = selectedManufacturer,
                                    year = 2022,
                                    onProgress = { stage ->
                                        scanStages = scanStages.filterNot { it.id == stage.id } + stage
                                    }
                                ))
                                scanResult = result
                                currentScreen = Screen.RESULTS
                            } catch (e: Exception) {
                                errorMessage = e.message
                            } finally {
                                isScanRunning = false
                            }
                        }
                    },
                    onDisconnect = {
                        scope.launch {
                            sdk.disconnect()
                            isConnected = false
                            connectedDevice = null
                        }
                    },
                    selectedManufacturer = selectedManufacturer,
                    onManufacturerChange = { selectedManufacturer = it },
                    orderId = orderId,
                    onOrderIdChange = { orderId = it }
                )
            }
            
            Screen.DEVICES -> {
                DevicesScreen(
                    devices = devices,
                    isScanning = isScanning,
                    onDeviceSelected = { device ->
                        scope.launch {
                            try {
                                sdk.connect(device.id)
                                isConnected = true
                                connectedDevice = device
                                currentScreen = Screen.HOME
                            } catch (e: Exception) {
                                errorMessage = e.message
                            }
                        }
                    },
                    onBack = { currentScreen = Screen.HOME }
                )
            }
            
            Screen.SCANNING -> {
                ScanningScreen(
                    stages = scanStages,
                    onCancel = {
                        sdk.stopScan()
                        currentScreen = Screen.HOME
                    }
                )
            }
            
            Screen.RESULTS -> {
                ResultsScreen(
                    encryptedPayload = scanResult,
                    onSubmit = {
                        scope.launch {
                            try {
                                scanResult?.let { result ->
                                    val success = sdk.submitReport(result)
                                    errorMessage = if (success) "Report submitted successfully!" else "Failed to submit report"
                                }
                            } catch (e: Exception) {
                                errorMessage = e.message
                            }
                        }
                    },
                    onNewScan = { currentScreen = Screen.HOME }
                )
            }
        }
        
        // Error Snackbar
        errorMessage?.let { message ->
            LaunchedEffect(message) {
                kotlinx.coroutines.delay(3000)
                errorMessage = null
            }
            
            Snackbar(
                modifier = Modifier.padding(16.dp),
                containerColor = if (message.contains("success", ignoreCase = true)) AccentGreen else AccentRed
            ) {
                Text(message)
            }
        }
    }
}

@Composable
fun HomeScreen(
    isInitialized: Boolean,
    isConnected: Boolean,
    connectedDevice: BLEDevice?,
    onRequestPermissions: () -> Unit,
    onInitialize: () -> Unit,
    onDiscoverDevices: () -> Unit,
    onStartScan: () -> Unit,
    onDisconnect: () -> Unit,
    selectedManufacturer: String,
    onManufacturerChange: (String) -> Unit,
    orderId: String,
    onOrderIdChange: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Card
        item {
            GlassCard {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "SDK Status",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(16.dp))
                    
                    StatusRow(
                        label = "Initialized",
                        value = if (isInitialized) "Yes" else "No",
                        icon = if (isInitialized) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        color = if (isInitialized) AccentGreen else TextSecondary
                    )
                    StatusRow(
                        label = "Connected",
                        value = if (isConnected) connectedDevice?.name ?: "Yes" else "No",
                        icon = if (isConnected) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                        color = if (isConnected) AccentCyan else TextSecondary
                    )
                }
            }
        }
        
        // Setup Actions
        item {
            Text(
                "Setup",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionButton(
                    text = "Permissions",
                    icon = Icons.Outlined.Security,
                    onClick = onRequestPermissions,
                    modifier = Modifier.weight(1f)
                )
                ActionButton(
                    text = "Initialize",
                    icon = Icons.Outlined.PowerSettingsNew,
                    onClick = onInitialize,
                    enabled = !isInitialized,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        // Connection Actions
        item {
            Text(
                "Connection",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionButton(
                    text = "Find Devices",
                    icon = Icons.Outlined.Search,
                    onClick = onDiscoverDevices,
                    enabled = isInitialized,
                    modifier = Modifier.weight(1f)
                )
                if (isConnected) {
                    ActionButton(
                        text = "Disconnect",
                        icon = Icons.Outlined.BluetoothDisabled,
                        onClick = onDisconnect,
                        color = AccentRed,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        
        // Scan Configuration
        if (isConnected) {
            item {
                Text(
                    "Scan Configuration",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                GlassCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = orderId,
                            onValueChange = onOrderIdChange,
                            label = { Text("Order ID") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f)
                            )
                        )
                        
                        Spacer(Modifier.height(12.dp))
                        
                        // Manufacturer dropdown
                        var expanded by remember { mutableStateOf(false) }
                        val manufacturers = listOf(
                            "hyundai" to "Hyundai",
                            "kia" to "Kia",
                            "tata" to "Tata Motors",
                            "mahindra" to "Mahindra",
                            "maruti" to "Maruti Suzuki",
                            "toyota" to "Toyota",
                            "honda" to "Honda",
                            "ford" to "Ford",
                            "vw" to "Volkswagen",
                            "bmw" to "BMW"
                        )
                        
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = it }
                        ) {
                            OutlinedTextField(
                                value = manufacturers.find { it.first == selectedManufacturer }?.second ?: selectedManufacturer,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Manufacturer") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentCyan,
                                    unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f)
                                )
                            )
                            
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                manufacturers.forEach { (id, name) ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            onManufacturerChange(id)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Start Scan Button
            item {
                Button(
                    onClick = onStartScan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentCyan
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Start Full Scan",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun DevicesScreen(
    devices: List<BLEDevice>,
    isScanning: Boolean,
    onDeviceSelected: (BLEDevice) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Back button
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary)
        }
        
        if (isScanning) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AccentCyan)
                    Spacer(Modifier.height(16.dp))
                    Text("Scanning for OBD devices...", color = TextSecondary)
                }
            }
        }
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(devices) { device ->
                DeviceCard(device = device, onClick = { onDeviceSelected(device) })
            }
            
            if (devices.isEmpty() && !isScanning) {
                item {
                    Text(
                        "No devices found. Make sure your OBD adapter is powered on.",
                        color = TextSecondary,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceCard(device: BLEDevice, onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier.clickable(enabled = device.isConnectable, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(AccentBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = AccentCyan
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    device.name ?: "Unknown Device",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    device.id,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${device.rssi} dBm",
                    fontSize = 12.sp,
                    color = if (device.rssi > -60) AccentGreen else if (device.rssi > -80) AccentOrange else AccentRed
                )
                if (!device.isConnectable) {
                    Text(
                        "Not connectable",
                        fontSize = 10.sp,
                        color = AccentRed
                    )
                }
            }
        }
    }
}

@Composable
fun ScanningScreen(stages: List<ScanStage>, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Scanning Vehicle",
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = TextPrimary
        )
        
        Spacer(Modifier.height(24.dp))
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(stages) { stage ->
                ScanStageRow(stage)
            }
        }
        
        Spacer(Modifier.weight(1f))
        
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = AccentRed
            )
        ) {
            Text("Cancel Scan")
        }
    }
}

@Composable
fun ScanStageRow(stage: ScanStage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    when (stage.status) {
                        StageStatus.COMPLETED -> AccentGreen
                        StageStatus.RUNNING -> AccentCyan
                        StageStatus.ERROR -> AccentRed
                        StageStatus.SKIPPED -> TextSecondary
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            when (stage.status) {
                StageStatus.COMPLETED -> Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                StageStatus.RUNNING -> CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                StageStatus.ERROR -> Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(20.dp))
                StageStatus.SKIPPED -> Icon(Icons.Default.Remove, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        
        Spacer(Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(stage.label, fontWeight = FontWeight.Medium, color = TextPrimary)
            stage.detail?.let {
                Text(it, fontSize = 12.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
fun ResultsScreen(
    encryptedPayload: EncryptedPayload?,
    onSubmit: () -> Unit,
    onNewScan: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Scan Complete",
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = TextPrimary
        )
        
        Spacer(Modifier.height(24.dp))
        
        GlassCard {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Report Encrypted",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            "AES-256-GCM",
                            fontSize = 12.sp,
                            color = AccentGreen
                        )
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                Divider(color = TextSecondary.copy(alpha = 0.2f))
                Spacer(Modifier.height(16.dp))
                
                encryptedPayload?.let { payload ->
                    InfoRow("Key ID", payload.keyId.take(8) + "...")
                    InfoRow("Algorithm", payload.algorithm)
                    InfoRow("Payload Size", "${payload.payload.length} chars")
                    InfoRow("Signed", if (payload.signature != null) "Yes" else "No")
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        Text(
            "The scan report is encrypted and can only be decrypted by the WiseDrive backend.",
            fontSize = 14.sp,
            color = TextSecondary
        )
        
        Spacer(Modifier.weight(1f))
        
        Button(
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.CloudUpload, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Submit Report", fontWeight = FontWeight.Bold)
        }
        
        Spacer(Modifier.height(12.dp))
        
        OutlinedButton(
            onClick = onNewScan,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("New Scan")
        }
    }
}

// UI Components
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkSurface.copy(alpha = 0.8f)
        )
    ) {
        content()
    }
}

@Composable
fun StatusChip(text: String, color: Color, icon: ImageVector) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun StatusRow(label: String, value: String, icon: ImageVector, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Medium, color = color)
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(label, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Medium, color = TextPrimary)
    }
}

@Composable
fun ActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = AccentCyan
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = color,
            disabledContentColor = TextSecondary.copy(alpha = 0.5f)
        )
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 14.sp)
    }
}

enum class Screen {
    HOME, DEVICES, SCANNING, RESULTS
}
