package com.example.cellularsocks

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.InetAddresses
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.cellularsocks.service.ProxyForegroundService
import com.example.cellularsocks.ui.AppTheme
import com.example.cellularsocks.util.LogBus
import com.example.cellularsocks.util.NetUtils
import com.example.cellularsocks.util.Settings
import com.example.cellularsocks.util.SettingsRepo
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var boundService: ProxyForegroundService? = null

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            boundService = (service as ProxyForegroundService.LocalBinder).getService()
        }
        override fun onServiceDisconnected(name: ComponentName?) { boundService = null }
    }

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val intent = Intent(this, ProxyForegroundService::class.java)
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, conn, Context.BIND_AUTO_CREATE)

        setContent { AppTheme { AppScreen() } }
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(conn)
    }

    @Composable
    private fun AppScreen() {
        val ctx = this
        val scope = rememberCoroutineScope()

        // 从 DataStore 恢复设置
        val settings by SettingsRepo.flow(ctx).collectAsState(initial = Settings())

        var portText by remember(settings.port) { mutableStateOf(settings.port.toString()) }
        var username by remember(settings.username) { mutableStateOf(settings.username) }
        var password by remember(settings.password) { mutableStateOf(settings.password) }
        var authEnabled by remember(settings.authEnabled) { mutableStateOf(settings.authEnabled) }
        var running by remember { mutableStateOf(false) }
        var ip by remember { mutableStateOf(NetUtils.wifiIpv4(ctx) ?: "0.0.0.0") }
        val logs = remember { mutableStateListOf<String>() }

        // 订阅日志
        LaunchedEffect(Unit) {
            LogBus.flow.collectLatest { msg ->
                logs.add(0, msg)
                if (logs.size > 200) logs.removeLast()
            }
        }

        // 订阅统计（当服务已绑定并启动时）
        val statsFlow = boundService?.statsFlow
        val stats = statsFlow?.collectAsState(initial = null)?.value

        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("局域网监听地址（Wi‑Fi）", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ip, modifier = Modifier.weight(1f))
                    Button(onClick = { ip = NetUtils.wifiIpv4(ctx) ?: "0.0.0.0" }) { Text("刷新") }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = portText, onValueChange = {
                    val v = it.filter { c -> c.isDigit() }.take(5)
                    portText = v
                    v.toIntOrNull()?.let { p ->
                        scope.launch { SettingsRepo.update(ctx) { s -> s.copy(port = p) } }
                    }
                }, label = { Text("端口") })
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = authEnabled, onCheckedChange = {
                        authEnabled = it
                        scope.launch { SettingsRepo.update(ctx) { s -> s.copy(authEnabled = it) } }
                    })
                    Spacer(Modifier.width(8.dp))
                    Text("启用账号密码鉴权")
                }
                if (authEnabled) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = username, onValueChange = {
                        username = it; scope.launch { SettingsRepo.update(ctx) { s -> s.copy(username = it) } }
                    }, label = { Text("用户名") })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = password, onValueChange = {
                        password = it; scope.launch { SettingsRepo.update(ctx) { s -> s.copy(password = it) } }
                    }, label = { Text("密码") }, visualTransformation = PasswordVisualTransformation())
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(enabled = !running, onClick = {
                        val p = portText.toIntOrNull()
                        if (p == null || p !in 1..65535) {
                            Toast.makeText(ctx, "端口无效", Toast.LENGTH_SHORT).show(); return@Button
                        }
                        val ok = boundService?.startProxy(ip, p,
                            if (authEnabled) ProxyForegroundService.Auth(username, password) else null
                        ) ?: false
                        running = ok
                        if (!ok) Toast.makeText(ctx, "启动失败，检查蜂窝网络是否激活", Toast.LENGTH_LONG).show()
                    }) { Text("启动代理") }

                    OutlinedButton(enabled = running, onClick = {
                        boundService?.stopProxy(); running = false
                    }) { Text("停止") }
                }
                Spacer(Modifier.height(12.dp))
                if (running && stats != null) {
                    Text("运行中：会话 ${stats.activeSessions}，累计 ${stats.totalSessions}，传输 ${formatBytes(stats.totalBytes)}")
                    Spacer(Modifier.height(8.dp))
                }
                Divider()
                Spacer(Modifier.height(8.dp))
                Text("日志", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(logs) { line -> Text(line) }
                }
            }
        }
    }
}

private fun formatBytes(b: Long): String {
    if (b < 1024) return "$b B"
    val kb = b / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.1f GB", gb)
} 