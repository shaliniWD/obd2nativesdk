@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wisedrive.obd2.WiseDriveOBD2SDK
import com.wisedrive.obd2.models.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * WiseDrive OBD2 SDK Demo Application
 * Demonstrates full SDK functionality with polished Jetpack Compose UI
 * Includes logging and mock mode for testing
 */
class MainActivity : ComponentActivity() {

    private var sdk: WiseDriveOBD2SDK? = null
    
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
        
        setContent {
            WiseDriveTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    OBDScannerApp(
                        context = this,
                        getSdk = { sdk },
                        setSdk = { sdk = it },
                        requestPermissions = ::requestPermissions
                    )
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

    override fun onDestroy() {
        super.onDestroy()
        sdk?.cleanup()
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
val AccentPurple = Color(0xFF9B59B6)
val TextPrimary = Color(0xFFE8E8E8)
val TextSecondary = Color(0xFF9CA3AF)
val CodeBackground = Color(0xFF0D1117)

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

data class LogEntry(
    val timestamp: String,
    val type: LogType,
    val message: String,
    val details: String? = null
)

enum class LogType { INFO, SUCCESS, ERROR, DATA }

@Composable
fun OBDScannerApp(
    context: ComponentActivity,
    getSdk: () -> WiseDriveOBD2SDK?,
    setSdk: (WiseDriveOBD2SDK) -> Unit,
    requestPermissions: () -> Unit
) {
    val scope = rememberCoroutineScope()
    
    // State
    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    var devices by remember { mutableStateOf(listOf<BLEDevice>()) }
    var isScanning by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(false) }
    var connectedDevice by remember { mutableStateOf<BLEDevice?>(null) }
    var scanStages by remember { mutableStateOf(listOf<ScanStage>()) }
    var isScanRunning by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<ScanReport?>(null) }
    var isInitialized by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Configuration
    var selectedManufacturer by remember { mutableStateOf("hyundai") }
    var registrationNumber by remember { mutableStateOf("ORD6894331") } // Default to test value
    var useMockMode by remember { mutableStateOf(true) }
    
    // Logs
    var logs by remember { mutableStateOf(listOf<LogEntry>()) }
    var analyticsPayloadJson by remember { mutableStateOf<String?>(null) }
    var analyticsResponse by remember { mutableStateOf<String?>(null) }
    var analyticsSubmitted by remember { mutableStateOf(false) }
    
    fun addLog(type: LogType, message: String, details: String? = null) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        logs = logs + LogEntry(timestamp, type, message, details)
    }

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
                    Column {
                        Text(
                            "WiseDrive OBD2",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            if (useMockMode) "Mock Mode" else "Live Mode",
                            fontSize = 10.sp,
                            color = if (useMockMode) AccentOrange else AccentGreen
                        )
                    }
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
                
                // Logs button
                IconButton(onClick = { currentScreen = Screen.LOGS }) {
                    Badge(
                        containerColor = if (logs.isNotEmpty()) AccentCyan else Color.Transparent
                    ) {
                        Icon(Icons.Default.Terminal, "Logs", tint = TextPrimary)
                    }
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
                    useMockMode = useMockMode,
                    onMockModeChange = { newValue ->
                        if (!isInitialized) {
                            useMockMode = newValue
                            addLog(LogType.INFO, "Mock mode ${if (newValue) "enabled" else "disabled"}")
                        }
                    },
                    onRequestPermissions = requestPermissions,
                    onInitialize = {
                        scope.launch {
                            try {
                                addLog(LogType.INFO, "Initializing SDK (mock=$useMockMode)...")
                                val newSdk = WiseDriveOBD2SDK.initialize(context, useMock = useMockMode)
                                
                                // Set up analytics callbacks
                                newSdk.setOnAnalyticsPayloadPrepared { json ->
                                    analyticsPayloadJson = json
                                    addLog(LogType.DATA, "Analytics payload prepared", json.take(200) + "...")
                                }
                                
                                newSdk.setOnAnalyticsSubmissionResult { success, response ->
                                    analyticsSubmitted = success
                                    analyticsResponse = response
                                    if (success) {
                                        addLog(LogType.SUCCESS, "Analytics submitted successfully", response)
                                    } else {
                                        addLog(LogType.ERROR, "Analytics submission failed", response)
                                    }
                                }
                                
                                val result = newSdk.initializeWithKey("demo-key")
                                if (result) {
                                    setSdk(newSdk)
                                    isInitialized = true
                                    addLog(LogType.SUCCESS, "SDK initialized successfully")
                                } else {
                                    addLog(LogType.ERROR, "SDK initialization failed")
                                }
                            } catch (e: Exception) {
                                errorMessage = e.message
                                addLog(LogType.ERROR, "Initialization error", e.message)
                            }
                        }
                    },
                    onDiscoverDevices = {
                        currentScreen = Screen.DEVICES
                        scope.launch {
                            devices = emptyList()
                            isScanning = true
                            addLog(LogType.INFO, "Starting device discovery...")
                            getSdk()?.discoverDevices(
                                onDeviceFound = { device ->
                                    devices = devices + device
                                    addLog(LogType.INFO, "Device found: ${device.name ?: device.id}")
                                },
                                timeoutMs = 8000
                            )
                            isScanning = false
                            addLog(LogType.INFO, "Discovery completed. Found ${devices.size} devices")
                        }
                    },
                    onStartScan = {
                        if (registrationNumber.isBlank()) {
                            errorMessage = "Registration number is required"
                            return@HomeScreen
                        }
                        
                        currentScreen = Screen.SCANNING
                        scanStages = emptyList()
                        scanResult = null
                        analyticsPayloadJson = null
                        analyticsResponse = null
                        analyticsSubmitted = false
                        isScanRunning = true
                        
                        addLog(LogType.INFO, "Starting scan for: $registrationNumber")
                        
                        scope.launch {
                            try {
                                val result = getSdk()?.runFullScan(ScanOptions(
                                    registrationNumber = registrationNumber,
                                    manufacturer = selectedManufacturer,
                                    year = 2022,
                                    onProgress = { stage ->
                                        scanStages = scanStages.filterNot { it.id == stage.id } + stage
                                        addLog(LogType.INFO, "Stage: ${stage.label} - ${stage.status}")
                                    }
                                ))
                                scanResult = result
                                addLog(LogType.SUCCESS, "Scan completed", "DTCs: ${result?.summary?.totalDTCs ?: 0}")
                                currentScreen = Screen.RESULTS
                            } catch (e: Exception) {
                                errorMessage = e.message
                                addLog(LogType.ERROR, "Scan failed", e.message)
                                currentScreen = Screen.HOME
                            } finally {
                                isScanRunning = false
                            }
                        }
                    },
                    onDisconnect = {
                        scope.launch {
                            getSdk()?.disconnect()
                            isConnected = false
                            connectedDevice = null
                            addLog(LogType.INFO, "Disconnected")
                        }
                    },
                    selectedManufacturer = selectedManufacturer,
                    onManufacturerChange = { selectedManufacturer = it },
                    registrationNumber = registrationNumber,
                    onRegistrationNumberChange = { registrationNumber = it }
                )
            }
            
            Screen.DEVICES -> {
                DevicesScreen(
                    devices = devices,
                    isScanning = isScanning,
                    onDeviceSelected = { device ->
                        scope.launch {
                            try {
                                addLog(LogType.INFO, "Connecting to ${device.name ?: device.id}...")
                                getSdk()?.connect(device.id)
                                isConnected = true
                                connectedDevice = device
                                addLog(LogType.SUCCESS, "Connected successfully")
                                currentScreen = Screen.HOME
                            } catch (e: Exception) {
                                errorMessage = e.message
                                addLog(LogType.ERROR, "Connection failed", e.message)
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
                        getSdk()?.stopScan()
                        addLog(LogType.INFO, "Scan cancelled by user")
                        currentScreen = Screen.HOME
                    }
                )
            }
            
            Screen.RESULTS -> {
                ResultsScreen(
                    scanReport = scanResult,
                    analyticsPayloadJson = analyticsPayloadJson,
                    analyticsResponse = analyticsResponse,
                    analyticsSubmitted = analyticsSubmitted,
                    isMockMode = useMockMode,
                    onSubmit = {
                        scope.launch {
                            try {
                                addLog(LogType.INFO, "Submitting report...")
                                scanResult?.let { result ->
                                    val success = getSdk()?.submitReport(result) ?: false
                                    if (success) {
                                        addLog(LogType.SUCCESS, "Report submitted")
                                        errorMessage = "Report submitted successfully!"
                                    } else {
                                        addLog(LogType.ERROR, "Report submission failed")
                                        errorMessage = "Failed to submit report"
                                    }
                                }
                            } catch (e: Exception) {
                                errorMessage = e.message
                                addLog(LogType.ERROR, "Submit error", e.message)
                            }
                        }
                    },
                    onViewLogs = { currentScreen = Screen.LOGS },
                    onNewScan = { currentScreen = Screen.HOME }
                )
            }
            
            Screen.LOGS -> {
                LogsScreen(
                    logs = logs,
                    analyticsPayloadJson = analyticsPayloadJson,
                    onClear = { 
                        logs = emptyList()
                        addLog(LogType.INFO, "Logs cleared")
                    },
                    onBack = { currentScreen = Screen.HOME }
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
    useMockMode: Boolean,
    onMockModeChange: (Boolean) -> Unit,
    onRequestPermissions: () -> Unit,
    onInitialize: () -> Unit,
    onDiscoverDevices: () -> Unit,
    onStartScan: () -> Unit,
    onDisconnect: () -> Unit,
    selectedManufacturer: String,
    onManufacturerChange: (String) -> Unit,
    registrationNumber: String,
    onRegistrationNumberChange: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Mock Mode Toggle Card
        item {
            GlassCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Test Mode",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            if (useMockMode) "Using mock data (no real endpoint call)" 
                            else "Live mode (real API calls)",
                            fontSize = 12.sp,
                            color = if (useMockMode) AccentOrange else AccentGreen
                        )
                    }
                    Switch(
                        checked = useMockMode,
                        onCheckedChange = onMockModeChange,
                        enabled = !isInitialized,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AccentOrange,
                            checkedTrackColor = AccentOrange.copy(alpha = 0.5f)
                        )
                    )
                }
                
                if (isInitialized) {
                    Text(
                        "Restart app to change mode",
                        fontSize = 10.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                    )
                }
            }
        }
        
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
                    StatusRow(
                        label = "Mode",
                        value = if (useMockMode) "Mock" else "Live",
                        icon = if (useMockMode) Icons.Default.Science else Icons.Default.Cloud,
                        color = if (useMockMode) AccentOrange else AccentGreen
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
                        // Registration Number (MANDATORY)
                        OutlinedTextField(
                            value = registrationNumber,
                            onValueChange = onRegistrationNumberChange,
                            label = { Text("Registration/Tracking ID *") },
                            placeholder = { Text("e.g., ORD6894331") },
                            modifier = Modifier.fillMaxWidth(),
                            isError = registrationNumber.isBlank(),
                            supportingText = {
                                Column {
                                    if (registrationNumber.isBlank()) {
                                        Text("Required", color = AccentRed)
                                    }
                                    Text(
                                        "Use ORD6894331 for testing",
                                        fontSize = 10.sp,
                                        color = AccentCyan
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                                errorBorderColor = AccentRed
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
                        
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = manufacturers.find { it.first == selectedManufacturer }?.second ?: selectedManufacturer,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Manufacturer") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                        contentDescription = "Toggle dropdown"
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expanded = true },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentCyan,
                                    unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f)
                                )
                            )
                            
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.fillMaxWidth(0.9f)
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
                    enabled = registrationNumber.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentCyan,
                        disabledContainerColor = AccentCyan.copy(alpha = 0.3f)
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
                Icon(Icons.Default.Bluetooth, contentDescription = null, tint = AccentCyan)
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name ?: "Unknown Device", fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(device.id, fontSize = 12.sp, color = TextSecondary)
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${device.rssi} dBm",
                    fontSize = 12.sp,
                    color = if (device.rssi > -60) AccentGreen else if (device.rssi > -80) AccentOrange else AccentRed
                )
                if (!device.isConnectable) {
                    Text("Not connectable", fontSize = 10.sp, color = AccentRed)
                }
            }
        }
    }
}

@Composable
fun ScanningScreen(stages: List<ScanStage>, onCancel: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Scanning Vehicle", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = TextPrimary)
        Spacer(Modifier.height(24.dp))
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(stages) { stage -> ScanStageRow(stage) }
        }
        
        Spacer(Modifier.weight(1f))
        
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed)
        ) { Text("Cancel Scan") }
    }
}

@Composable
fun ScanStageRow(stage: ScanStage) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
            stage.detail?.let { Text(it, fontSize = 12.sp, color = TextSecondary) }
        }
    }
}

@Composable
fun ResultsScreen(
    scanReport: ScanReport?,
    analyticsPayloadJson: String?,
    analyticsResponse: String?,
    analyticsSubmitted: Boolean,
    isMockMode: Boolean,
    onSubmit: () -> Unit,
    onViewLogs: () -> Unit,
    onNewScan: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Scan Complete", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = TextPrimary)
        Spacer(Modifier.height(16.dp))
        
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Scan Summary Card
            item {
                GlassCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Assessment, null, tint = AccentCyan, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Scan Report", fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                        Spacer(Modifier.height(12.dp))
                        Divider(color = TextSecondary.copy(alpha = 0.2f))
                        Spacer(Modifier.height(12.dp))
                        
                        scanReport?.let { report ->
                            InfoRow("Scan ID", report.scanId.take(8) + "...")
                            InfoRow("Registration", report.inspectionId ?: "N/A")
                            InfoRow("Vehicle", report.vehicle.manufacturer ?: "Unknown")
                            InfoRow("VIN", report.vehicle.vin ?: "Unknown")
                            InfoRow("Total DTCs", "${report.summary.totalDTCs}")
                            InfoRow("Duration", "${report.scanDuration}ms")
                        }
                    }
                }
            }
            
            // Analytics Status Card
            item {
                GlassCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (analyticsSubmitted) Icons.Default.CloudDone else Icons.Default.CloudUpload,
                                null,
                                tint = if (analyticsSubmitted) AccentGreen else AccentOrange,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "WiseDrive Analytics",
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    when {
                                        isMockMode -> "Mock Mode - Simulated"
                                        analyticsSubmitted -> "Submitted Successfully"
                                        else -> "Pending..."
                                    },
                                    fontSize = 12.sp,
                                    color = if (analyticsSubmitted) AccentGreen else AccentOrange
                                )
                            }
                        }
                        
                        analyticsResponse?.let { response ->
                            Spacer(Modifier.height(12.dp))
                            Text("Response:", fontSize = 12.sp, color = TextSecondary)
                            Text(
                                response,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = AccentGreen
                            )
                        }
                    }
                }
            }
            
            // Analytics Payload Preview
            analyticsPayloadJson?.let { json ->
                item {
                    GlassCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Analytics Payload", fontWeight = FontWeight.Bold, color = TextPrimary)
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(json))
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCopy, "Copy", tint = AccentCyan)
                                }
                            }
                            
                            Spacer(Modifier.height(8.dp))
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CodeBackground)
                                    .padding(12.dp)
                            ) {
                                Text(
                                    json.take(1500) + if (json.length > 1500) "\n..." else "",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = AccentGreen,
                                    modifier = Modifier.verticalScroll(rememberScrollState())
                                )
                            }
                        }
                    }
                }
            }
            
            // DTCs if any
            scanReport?.let { report ->
                if (report.diagnosticTroubleCodes.isNotEmpty()) {
                    item {
                        GlassCard {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("DTCs Found", fontWeight = FontWeight.Bold, color = TextPrimary)
                                Spacer(Modifier.height(12.dp))
                                
                                report.diagnosticTroubleCodes.take(5).forEach { dtc ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Text(dtc.code, fontWeight = FontWeight.Bold, color = AccentOrange, modifier = Modifier.width(80.dp))
                                        Text(dtc.description.take(35) + "...", fontSize = 12.sp, color = TextSecondary)
                                    }
                                }
                                
                                if (report.diagnosticTroubleCodes.size > 5) {
                                    Text("+${report.diagnosticTroubleCodes.size - 5} more...", fontSize = 12.sp, color = AccentCyan)
                                }
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // View Logs Button
        OutlinedButton(
            onClick = onViewLogs,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Terminal, null)
            Spacer(Modifier.width(8.dp))
            Text("View Full Logs")
        }
        
        Spacer(Modifier.height(8.dp))
        
        Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.CloudUpload, null)
            Spacer(Modifier.width(8.dp))
            Text("Confirm Submission", fontWeight = FontWeight.Bold)
        }
        
        Spacer(Modifier.height(8.dp))
        
        OutlinedButton(onClick = onNewScan, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Text("New Scan")
        }
    }
}

@Composable
fun LogsScreen(
    logs: List<LogEntry>,
    analyticsPayloadJson: String?,
    onClear: () -> Unit,
    onBack: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary)
            }
            Text("Logs", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPrimary)
            Row {
                analyticsPayloadJson?.let { json ->
                    IconButton(onClick = { clipboardManager.setText(AnnotatedString(json)) }) {
                        Icon(Icons.Default.ContentCopy, "Copy Payload", tint = AccentCyan)
                    }
                }
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Delete, "Clear", tint = AccentRed)
                }
            }
        }
        
        Divider(color = TextSecondary.copy(alpha = 0.2f))
        
        // Logs List
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(CodeBackground)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(logs.reversed()) { log ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        log.timestamp,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary,
                        modifier = Modifier.width(90.dp)
                    )
                    
                    val typeColor = when (log.type) {
                        LogType.INFO -> AccentCyan
                        LogType.SUCCESS -> AccentGreen
                        LogType.ERROR -> AccentRed
                        LogType.DATA -> AccentPurple
                    }
                    
                    Text(
                        "[${log.type.name}]",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = typeColor,
                        modifier = Modifier.width(70.dp)
                    )
                    
                    Column {
                        Text(
                            log.message,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary
                        )
                        log.details?.let { details ->
                            Text(
                                details,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextSecondary.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
            
            if (logs.isEmpty()) {
                item {
                    Text(
                        "No logs yet. Run a scan to see activity.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

// UI Components
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.8f))
    ) { content() }
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
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Medium, color = color)
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
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
    HOME, DEVICES, SCANNING, RESULTS, LOGS
}
