package com.example.Bamboozled

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.drawable.Drawable
import android.os.*
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.app.NotificationCompat
import com.example.Bamboozled.ui.theme.BambuMonitorTheme
import kotlinx.coroutines.Dispatchers
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
import java.util.concurrent.Executors
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class PrinterState(
    val progress: Int = 0,
    val remainingTimeMinutes: Int = 0,
    val statusText: String = "Connecting...",
    val isIdle: Boolean = true,
    val nozzleTemp: Float = 0f,
    val bedTemp: Float = 0f,
    val appName: String = "Bamboozled",
    val lastUpdate: Long = 0L,
    val deviceName: String = "null"
)

object PrinterDataManager {
    private val _state = MutableStateFlow(PrinterState())
    val state = _state.asStateFlow()

    fun updateState(newState: PrinterState) {
        _state.value = newState
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("bambu_prefs", Context.MODE_PRIVATE)
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
            startService(intent)
        } catch (e: Exception) { Log.e("BambuMonitor", "Service start failed", e) }
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
    
    val accentColor = if (isSystemInDarkTheme()) Color(0xFF00E676) else Color(0xFF00C853)

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
                    context.startService(intent)
                }
                delay(1000)
                isRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()
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
            Text(text = "v1.0.8", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            Text(text = state.appName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        }
        val status = state.statusText
        val color = if (status.contains("Error") || status == "Offline") MaterialTheme.colorScheme.error else if (status.contains("Connecting")) MaterialTheme.colorScheme.secondary else accentColor
        
        Surface(shape = RoundedCornerShape(24.dp), color = color.copy(alpha = 0.15f), modifier = Modifier.height(48.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(text = if (status.startsWith("Connecte")) "Connected" else if (status.contains("Connecting")) "Connecting" else "Offline", 
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = color)
            }
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onSettingsClick, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) {
            Icon(Icons.Default.Settings, null)
        }
    }
}

@Composable
fun PrinterStatusView(state: PrinterState, accentColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(modifier = Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                if (state.statusText.contains("Connecting")) {
                    CircularProgressIndicator(
                        modifier = Modifier.fillMaxSize(), 
                        color = accentColor, 
                        strokeWidth = 12.dp, 
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                } else if (state.isIdle) {
                    CircularProgressIndicator(
                        progress = { 0f },
                        modifier = Modifier.fillMaxSize(),
                        color = accentColor,
                        strokeWidth = 12.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(text = "Idle", fontSize = 44.sp, fontWeight = FontWeight.Black)
                } else {
                    CircularProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxSize(), color = accentColor, strokeWidth = 12.dp, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "${state.progress}%", fontSize = 44.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            Column(modifier = Modifier.weight(1f).height(180.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(label = "Remaining", value = if (state.isIdle) "--" else formatRemainingTime(state.remainingTimeMinutes), modifier = Modifier.fillMaxWidth().weight(1f), containerColor = MaterialTheme.colorScheme.secondaryContainer)
                StatCard(label = "Finish", value = if (state.isIdle) "--" else calculateFinishTime(state.remainingTimeMinutes), modifier = Modifier.fillMaxWidth().weight(1f), containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard(label = "Nozzle", value = "${state.nozzleTemp.toInt()}\u00B0C", modifier = Modifier.weight(1f))
            StatCard(label = "Bed", value = "${state.bedTemp.toInt()}\u00B0C", modifier = Modifier.weight(1f))
        }
    }
}

private fun formatRemainingTime(mins: Int): String {
    val hours = mins / 60
    val minutes = mins % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun calculateFinishTime(mins: Int): String {
    val c = java.util.Calendar.getInstance(); c.add(java.util.Calendar.MINUTE, mins)
    return String.format("%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier, containerColor: Color = MaterialTheme.colorScheme.surfaceVariant) {
    val contentColor = when (containerColor) {
        MaterialTheme.colorScheme.secondaryContainer -> MaterialTheme.colorScheme.onSecondaryContainer
        MaterialTheme.colorScheme.tertiaryContainer -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> contentColorFor(containerColor)
    }
    Card(modifier = modifier, shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = containerColor)) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = contentColor.copy(alpha = 0.8f))
            Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = contentColor)
        }
    }
}

@Composable
fun CardContent(ip: String, onIpChange: (String) -> Unit, code: String, onCodeChange: (String) -> Unit, serial: String, onSerialChange: (String) -> Unit, onSave: () -> Unit) {
    Column(modifier = Modifier.padding(24.dp)) {
        OutlinedTextField(value = ip, onValueChange = onIpChange, label = { Text("IP") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = code, onValueChange = onCodeChange, label = { Text("Code") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = serial, onValueChange = onSerialChange, label = { Text("Serial") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("Save") }
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
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel("print_status_v4", "Status", NotificationManager.IMPORTANCE_LOW))
        }

        handler.postDelayed(object : Runnable {
            override fun run() {
                requestPush()
                handler.postDelayed(this, 15000)
            }
        }, 15000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
            
            handler.post { PrinterDataManager.updateState(PrinterDataManager.state.value.copy(statusText = "Connecting...")) }
            
            try { client?.disconnectForcibly(200) } catch (e: Exception) {}
            client = MqttClient("ssl://$ip:8883", "BM_${serial.takeLast(4)}_${System.currentTimeMillis()%1000}", MemoryPersistence())
            
            val options = MqttConnectOptions().apply {
                userName = "bblp"; password = code.toCharArray(); connectionTimeout = 10; isAutomaticReconnect = true; isCleanSession = true
                val tm = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(p0: Array<out X509Certificate>?, p1: String?) {}
                    override fun checkServerTrusted(p0: Array<out X509Certificate>?, p1: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })
                val ssl = SSLContext.getInstance("TLS"); ssl.init(null, tm, SecureRandom()); socketFactory = ssl.socketFactory
            }
            client?.setCallback(object : MqttCallback {
                override fun messageArrived(t: String?, m: MqttMessage?) {
                    val p = JSONObject(m?.toString() ?: return).optJSONObject("print") ?: return
                    val s = PrinterDataManager.state.value
                    val g = p.optString("gcode_state", "")
                    val mc = p.optInt("mc_percent", s.progress)
                    val idle = when { g == "RUNNING" || g == "PREPARE" -> false; g == "IDLE" || g == "FINISH" -> true; else -> s.isIdle }
                    val newState = s.copy(progress = mc, remainingTimeMinutes = p.optInt("mc_remaining_time", s.remainingTimeMinutes),
                        statusText = if (g.isNotEmpty()) g.lowercase().replaceFirstChar { it.uppercase() } else "Connected", isIdle = idle,
                        nozzleTemp = p.optDouble("nozzle_temper", s.nozzleTemp.toDouble()).toFloat(), bedTemp = p.optDouble("bed_temper", s.bedTemp.toDouble()).toFloat(),
                        lastUpdate = System.currentTimeMillis())
                    PrinterDataManager.updateState(newState); updateNotification(newState)
                }
                override fun connectionLost(c: Throwable?) { PrinterDataManager.updateState(PrinterDataManager.state.value.copy(statusText = "Disconnected")) }
                override fun deliveryComplete(p0: IMqttDeliveryToken?) {}
            })
            client?.connect(options)
            client?.subscribe("device/$serial/report", 0)
            requestPush()
            
            handler.post { 
                val current = PrinterDataManager.state.value
                if (current.statusText.contains("Connecting")) {
                    PrinterDataManager.updateState(current.copy(statusText = "Connected"))
                }
                updateNotification(PrinterDataManager.state.value)
            }
        } catch (e: Exception) { 
            handler.post { PrinterDataManager.updateState(PrinterDataManager.state.value.copy(statusText = "Error")) }
        }
    }

    private fun requestPush() {
        executor.execute {
            try {
                if (client?.isConnected == true) {
                    val payload = JSONObject().put("pushing", JSONObject().put("sequence_id", "0").put("command", "push_all"))
                    client?.publish("device/${currentInfo.third}/request", MqttMessage(payload.toString().toByteArray()))
                }
            } catch (e: Exception) { Log.e("BambuMonitor", "Push failed", e) }
        }
    }

    private fun formatRemainingTime(mins: Int): String {
        val hours = mins / 60
        val minutes = mins % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    private fun createNotification(s: PrinterState): Notification {
        return NotificationCompat.Builder(this, "print_status_v4")
            .setContentTitle(if (s.isIdle) "Printer idle" else "Printing ${s.progress}%")
            .setContentText(if (s.isIdle) "You're not printing anything" else "Remaining: ${formatRemainingTime(s.remainingTimeMinutes)}")
            .setSmallIcon(R.drawable.new_icon)
            .setProgress(100, if (s.isIdle) 0 else s.progress, false)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .build()
    }

    private fun updateNotification(s: PrinterState) {
        if (s.statusText == "Disconnected" || s.statusText == "Error") {
            if (isForeground) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                isForeground = false
            }
            return
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = createNotification(s)
        if (!isForeground) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(1, notification)
            }
            isForeground = true
        } else {
            manager.notify(1, notification)
        }
    }

    override fun onDestroy() {
        try { client?.disconnectForcibly(500) } catch (e: Exception) {}
        executor.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
