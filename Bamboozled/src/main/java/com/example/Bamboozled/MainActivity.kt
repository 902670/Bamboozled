package com.example.Bamboozled

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.materialIcon
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.app.NotificationCompat
import androidx.glance.appwidget.updateAll
import com.example.Bamboozled.ui.theme.BambuMonitorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Locale
import java.util.concurrent.Executors
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.math.PI
import kotlin.math.sin
import androidx.core.graphics.toColorInt

data class PrinterState(
    val progress: Int = 0,
    val remainingTimeMinutes: Int = 0,
    val statusText: String = "Connecting...",
    val isIdle: Boolean = true,
    val nozzleTemp: Float = 0f,
    val bedTemp: Float = 0f,
    val appName: String = "Bamboozled",
    val lastUpdate: Long = 0L,
    val deviceName: String = "Unknown",
    val filamentColor: String = "#00E676",
    val filamentType: String = "Plastic I guess"
)

object PrinterDataManager {
    private val _state = MutableStateFlow(PrinterState())
    val state = _state.asStateFlow()
    private val scope = MainScope()

    fun updateState(context: Context, newState: PrinterState) {
        _state.value = newState
        scope.launch {
            try {
                BambuWidget().updateAll(context)
            } catch (e: Exception) {
                Log.e("Bamboozled", "Widget update failed", e)
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("bambu_prefs", MODE_PRIVATE)
        val ip = prefs.getString("ip", "")
        val code = prefs.getString("code", "")
        val serial = prefs.getString("serial", "")

        if (!ip.isNullOrEmpty() && !code.isNullOrEmpty() && !serial.isNullOrEmpty()) {
            startMonitorService(ip, code, serial)
        }

        setContent {
            BambuMonitorTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    BambuDashboard()
                }
            }
        }
    }

    fun startMonitorService(ip: String?, code: String?, serial: String?, force: Boolean = false) {
        if (ip.isNullOrEmpty() || code.isNullOrEmpty() || serial.isNullOrEmpty()) return
        val intent = Intent(this, PrintProgressService::class.java).apply {
            putExtra("ip", ip); putExtra("code", code); putExtra("serial", serial)
            if (force) putExtra("force_reconnect", true)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) { Log.e("Bamboozled", "Service start failed", e) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BambuDashboard() {
    val printerState by PrinterDataManager.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val prefs = remember { context.getSharedPreferences("bambu_prefs", Context.MODE_PRIVATE) }

    var ip by remember { mutableStateOf(prefs.getString("ip", "") ?: "") }
    var code by remember { mutableStateOf(prefs.getString("code", "") ?: "") }
    var serial by remember { mutableStateOf(prefs.getString("serial", "") ?: "") }
    var showSettings by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    
    val accentColor = MaterialTheme.colorScheme.primary

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            scope.launch {
                withContext(Dispatchers.IO) {
                    val intent = Intent(context, PrintProgressService::class.java).apply {
                        putExtra("ip", ip); putExtra("code", code); putExtra("serial", serial)
                        putExtra("force_reconnect", true)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                }
                delay(1000)
                isRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize().statusBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState).padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HeaderSection(printerState, accentColor, onSettingsClick = { showSettings = !showSettings })
            
            if (showSettings) {
                Popup(alignment = Alignment.TopEnd, onDismissRequest = { showSettings = false }, properties = PopupProperties(focusable = true)) {
                    Card(modifier = Modifier.width(320.dp).padding(top = 4.dp), shape = RoundedCornerShape(28.dp), elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)) {
                        CardContent(ip, { ip = it }, code, { code = it }, serial, { serial = it }) {
                            prefs.edit().putString("ip", ip).putString("code", code).putString("serial", serial).apply()
                            (context as? MainActivity)?.startMonitorService(ip, code, serial, force = true)
                            showSettings = false
                        }
                    }
                }
            }

            PrinterStatusView(printerState, accentColor)
        }
    }
}

@Composable
fun HeaderSection(state: PrinterState, accentColor: Color, onSettingsClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "v1.1.1", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            Text(text = state.appName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        }
        val status = state.statusText
        val color = if (status.contains("Error") || status == "Offline") MaterialTheme.colorScheme.error else if (status.contains("Connecting")) MaterialTheme.colorScheme.secondary else accentColor
        
        Surface(shape = RoundedCornerShape(24.dp), color = color.copy(alpha = 0.15f), modifier = Modifier.height(48.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(text = if (status.startsWith("Connect")) "Connected" else if (status.contains("Connecting")) "Connecting" else "Offline",
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = color)
            }
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onSettingsClick, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) {
            Icon(Icons.Default.Settings, null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterStatusView(state: PrinterState, accentColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(modifier = Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                if (state.statusText.contains("Connecting")) {
                    CircularProgressIndicator(
                        modifier = Modifier.fillMaxSize(),
                        color = accentColor,
                        strokeWidth = 12.dp
                    )
                } else if (state.isIdle) {
                    CircularProgressIndicator(
                        progress = 0f,
                        modifier = Modifier.fillMaxSize(),
                        color = accentColor,
                        strokeWidth = 12.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(text = "Idle", fontSize = 44.sp, fontWeight = FontWeight.Black)
                } else {
                    val filamentColor = remember(state.filamentColor) {
                        try {
                            val hex = if (state.filamentColor.startsWith("#")) state.filamentColor else "#${state.filamentColor}"
                            Color(hex.toColorInt())
                        } catch (_: Exception) {
                            accentColor
                        }
                    }
                    CircularProgressIndicator(
                        progress = state.progress / 100f,
                        modifier = Modifier.fillMaxSize(),
                        color = filamentColor,
                        strokeWidth = 12.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${state.progress}%",
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f).height(180.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    label = "Remaining",
                    value = if (state.isIdle) "--" else formatRemainingTime(state.remainingTimeMinutes),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
                StatCard(
                    label = "Finish",
                    value = if (state.isIdle) "--" else calculateFinishTime(state.remainingTimeMinutes),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatCard(
                label = "Nozzle",
                value = "${state.nozzleTemp.toInt()}\u00B0C",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Bed",
                value = "${state.bedTemp.toInt()}\u00B0C",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        WavySlider(
            value = 1f,
            onValueChange = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            colors = SliderDefaults.colors(
                disabledActiveTrackColor =  MaterialTheme.colorScheme.inverseOnSurface
            )
        )
        Spacer(modifier = Modifier.height(32.dp))
        StatCard(
            label = "Last Update",
            value = formatLastUpdate(state.lastUpdate),
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,

        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {

        }
    }
}

@Composable
fun WavySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SliderColors = SliderDefaults.colors()
) {
    val trackColor = if (enabled) MaterialTheme.colorScheme.inverseOnSurface  else MaterialTheme.colorScheme.inverseOnSurface
    val backgroundTrackColor = trackColor.copy(alpha = 0.15f)

    Canvas(modifier = modifier.height(24.dp)) {
        val width = size.width * value
        val height = size.height
        val waveLength = 40.dp.toPx()
        val waveAmplitude = 6.dp.toPx()
        val centerY = height / 2

        val path = Path().apply {
            val steps = width.toInt().coerceAtLeast(1)
            for (i in 0..steps) {
                val x = i.toFloat()
                val y = centerY + sin(x * (2.0 * PI / waveLength)).toFloat() * waveAmplitude
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = trackColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

private fun formatRemainingTime(mins: Int): String {
    val hours = mins / 60
    val minutes = mins % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun calculateFinishTime(mins: Int): String {
    val c = java.util.Calendar.getInstance(); c.add(java.util.Calendar.MINUTE, mins)
    return String.format(
        Locale.getDefault(),
        "%02d:%02d",
        c.get(java.util.Calendar.HOUR_OF_DAY),
        c.get(java.util.Calendar.MINUTE)
    )
}

private fun formatLastUpdate(timestamp: Long): String {
    if (timestamp == 0L) return "Never"
    val diff = System.currentTimeMillis() - timestamp
    val seconds = diff / 1000
    if (seconds < 60) return "Just now"
    val minutes = seconds / 60
    return "${minutes}m ago"
}

@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    cornerRadius: Dp = 28.dp,
    shape: Shape = RoundedCornerShape(cornerRadius),
    aspectRatio: Float? = null,
    onClick: () -> Unit = {}
) {
    val contentColor = when (containerColor) {
        MaterialTheme.colorScheme.secondaryContainer -> MaterialTheme.colorScheme.onSecondaryContainer
        MaterialTheme.colorScheme.tertiaryContainer -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> contentColorFor(containerColor)
    }
    Card(
        modifier = modifier
            .then(if (aspectRatio != null) Modifier.aspectRatio(aspectRatio) else Modifier)
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = contentColor.copy(alpha = 0.8f)
            )
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = contentColor
            )
        }
    }
}

@Composable
fun CardContent(
    ip: String,
    onIpChange: (String) -> Unit,
    code: String,
    onCodeChange: (String) -> Unit,
    serial: String,
    onSerialChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Column(modifier = Modifier.padding(24.dp)) {
        OutlinedTextField(
            value = ip,
            onValueChange = onIpChange,
            label = { Text("IP address") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            label = { Text("Access code") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = serial,
            onValueChange = onSerialChange,
            label = { Text("Serial number") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) { Text("Save") }
    }
}

class PrintProgressService : Service() {
    private var client: MqttClient? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var currentInfo = Triple("", "", "")
    private var isForeground = false

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    "print_status_v4",
                    "Status",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        handler.postDelayed(object : Runnable {
            override fun run() {
                requestPush()
                handler.postDelayed(this, 15000)
            }
        }, 15000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val initialState = PrinterDataManager.state.value
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, createNotification(initialState), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, createNotification(initialState))
        }
        isForeground = true

        val ip = intent?.getStringExtra("ip") ?: ""
        val code = intent?.getStringExtra("code") ?: ""
        val serial = intent?.getStringExtra("serial") ?: ""
        val force = intent?.getBooleanExtra("force_reconnect", false) == true
        if (ip.isNotEmpty()) {
            val info = Triple(ip, code, serial)
            if (info != currentInfo || force) {
                currentInfo = info
                executor.execute { connect(ip, code, serial, force) }
            }
        }
        return START_STICKY
    }

    private fun connect(ip: String, code: String, serial: String, force: Boolean) {
        try {
            if (!force && client?.isConnected == true) return

            handler.post {
                PrinterDataManager.updateState(
                    this,
                    PrinterDataManager.state.value.copy(statusText = "Connecting...")
                )
            }

            try {
                client?.disconnectForcibly(200)
                client?.close()
            } catch (e: Exception) {
            }

            val clientId = "BM_${serial.takeLast(4)}_${(1000..9999).random()}"
            client = MqttClient("ssl://$ip:8883", clientId, MemoryPersistence())

            val options = MqttConnectOptions().apply {
                userName = "bblp"; password = code.toCharArray(); connectionTimeout =
                10; isAutomaticReconnect = true; isCleanSession = true; keepAliveInterval = 30
                val tm = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(
                        p0: Array<out X509Certificate>?,
                        p1: String?
                    ) {
                    }

                    override fun checkServerTrusted(
                        p0: Array<out X509Certificate>?,
                        p1: String?
                    ) {
                    }

                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })
                val ssl = SSLContext.getInstance("TLS"); ssl.init(
                null,
                tm,
                SecureRandom()
            ); socketFactory = ssl.socketFactory
            }
            client?.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    client?.subscribe("device/$serial/report", 0)
                    requestPush()
                }

                override fun messageArrived(t: String?, m: MqttMessage?) {
                    try {
                        val payload = m?.toString() ?: return
                        Log.i("Bamboozled", "MQTT Message: $payload")
                        val root = JSONObject(payload)
                        val s = PrinterDataManager.state.value
                        var newState = s

                        // Deep search for device name in all top-level objects
                        var foundName: String? =
                            root.optString("dev_name").takeIf { it.isNotEmpty() }
                        if (foundName == null) {
                            val keys = root.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                val obj = root.optJSONObject(key)
                                if (obj != null) {
                                    foundName =
                                        obj.optString("dev_name").takeIf { it.isNotEmpty() }
                                            ?: obj.optString("name").takeIf { it.isNotEmpty() }
                                    if (foundName != null) break
                                }
                            }
                        }

                        if (foundName != null) {
                            newState = newState.copy(deviceName = foundName)
                        }

                        val p = root.optJSONObject("print")
                        if (p != null) {
                            val g = p.optString("gcode_state", "")
                            val mc = p.optInt("mc_percent", newState.progress)
                            val idle = when {
                                g == "RUNNING" || g == "PREPARE" -> false; g == "IDLE" || g == "FINISH" -> true; else -> newState.isIdle
                            }
                            newState = newState.copy(
                                progress = mc,
                                remainingTimeMinutes = p.optInt(
                                    "mc_remaining_time",
                                    newState.remainingTimeMinutes
                                ),
                                statusText = if (g.isNotEmpty()) g.lowercase()
                                    .replaceFirstChar { it.uppercase() } else newState.statusText,
                                isIdle = idle,
                                nozzleTemp = p.optDouble(
                                    "nozzle_temper",
                                    newState.nozzleTemp.toDouble()
                                ).toFloat(),
                                bedTemp = p.optDouble("bed_temper", newState.bedTemp.toDouble())
                                    .toFloat(),
                                lastUpdate = System.currentTimeMillis(),
                                filamentColor = p.optString("filament_color", newState.filamentColor)
                            )
                        }

                        if (newState != s) {
                            PrinterDataManager.updateState(this@PrintProgressService, newState)
                            updateNotification(newState)
                        }
                    } catch (e: Exception) {
                        Log.e("Bamboozled", "Message processing failed", e)
                    }
                }

                override fun connectionLost(c: Throwable?) {
                    PrinterDataManager.updateState(
                        this@PrintProgressService,
                        PrinterDataManager.state.value.copy(statusText = "Disconnected")
                    )
                }

                override fun deliveryComplete(p0: IMqttDeliveryToken?) {}
            })
            client?.connect(options)

            handler.post {
                val current = PrinterDataManager.state.value
                val updateStatus = current.statusText.contains("Connecting")
                val updateName = current.deviceName == "Unknown"

                if (updateStatus || updateName) {
                    PrinterDataManager.updateState(
                        this, current.copy(
                            statusText = if (updateStatus) "Connected" else current.statusText,
                            deviceName = if (updateName) serial else current.deviceName
                        )
                    )
                }
                updateNotification(PrinterDataManager.state.value)
            }
        } catch (e: Exception) {
            handler.post {
                PrinterDataManager.updateState(
                    this,
                    PrinterDataManager.state.value.copy(statusText = "Error")
                )
            }
        }
    }

    private fun requestPush() {
        executor.execute {
            try {
                if (client?.isConnected == true) {
                    // Send multiple commands to ensure we get metadata
                    val pushAll = JSONObject().put(
                        "pushing",
                        JSONObject().put("sequence_id", "0").put("command", "push_all")
                    )
                    client?.publish(
                        "device/${currentInfo.third}/request",
                        MqttMessage(pushAll.toString().toByteArray())
                    )

                    val getVersion = JSONObject().put(
                        "info",
                        JSONObject().put("sequence_id", "1").put("command", "get_version")
                    )
                    client?.publish(
                        "device/${currentInfo.third}/request",
                        MqttMessage(getVersion.toString().toByteArray())
                    )
                }
            } catch (e: Exception) {
                Log.e("Bamboozled", "Push failed", e)
            }
        }
    }

    private fun formatRemainingTime(mins: Int): String {
        val hours = mins / 60
        val minutes = mins % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    private fun createNotification(s: PrinterState): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent =
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, "print_status_v4")
            .setContentTitle(if (s.isIdle) "Printer idle" else "Printing ${s.progress}%")
            .setContentText(
                if (s.isIdle) "Um... Nothings printing?" else "Remaining: ${
                    formatRemainingTime(
                        s.remainingTimeMinutes
                    )
                }"
            )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setProgress(
                100,
                if (s.isIdle) 0 else s.progress,
                s.statusText.contains("Connecting")
            )
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(s: PrinterState) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1, createNotification(s))
    }

    override fun onDestroy() {
        try {
            client?.disconnectForcibly(500)
            client?.close()
        } catch (e: Exception) {
        }
        executor.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
