package com.weno.szuguardian

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.weno.szuguardian.data.AppConfig
import com.weno.szuguardian.data.ConfigStore

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GuardianService.createChannel(this)

        val config = ConfigStore(this).load()
        if (config.startOnLaunch && config.isUsable) {
            GuardianService.start(this)
        }

        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                GuardianScreen(onBackPressed = { moveTaskToBack(true) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardianScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    var config by remember { mutableStateOf(ConfigStore(context).load()) }
    var showPassword by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val status by GuardianState.status.collectAsState()
    val running by GuardianState.running.collectAsState()
    val logs by GuardianState.logs.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BackHandler { onBackPressed() }

    fun persist(): Boolean = try {
        config.validate()
        ConfigStore(context).save(config)
        error = null
        true
    } catch (e: IllegalArgumentException) {
        error = e.message
        false
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("SZU 网络守护") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val color = when (status.phase) {
                GuardianState.Phase.ONLINE -> Color(0xFF16A34A)
                GuardianState.Phase.OFFLINE -> Color(0xFFDC2626)
                GuardianState.Phase.CHECKING -> Color(0xFF2563EB)
                GuardianState.Phase.IDLE -> Color(0xFF94A3B8)
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        color = color,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.size(12.dp),
                    ) { }
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(status.message, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (running) "每 ${config.intervalMinutes} 分钟自动检测" else "尚未启动守护",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B),
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("连接设置", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = config.username,
                        onValueChange = { config = config.copy(username = it) },
                        label = { Text("校园网账号") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = config.password,
                        onValueChange = { config = config.copy(password = it) },
                        label = { Text("统一身份认证密码") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                )
                            }
                        },
                    )
                    Text("网络区域", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ZoneChip("教学 / 办公区", AppConfig.ZONE_OFFICE, config) {
                            config = config.copy(zone = it)
                        }
                        ZoneChip("宿舍区", AppConfig.ZONE_DORMITORY, config) {
                            config = config.copy(zone = it)
                        }
                        ZoneChip("自动", AppConfig.ZONE_AUTO, config) {
                            config = config.copy(zone = it)
                        }
                    }
                    OutlinedTextField(
                        value = config.intervalMinutes.toString(),
                        onValueChange = { config = config.copy(intervalMinutes = it.filter(Char::isDigit).toIntOrNull() ?: 1) },
                        label = { Text("检测间隔（分钟）") },
                        singleLine = true,
                        modifier = Modifier.width(160.dp),
                    )
                    SettingSwitch("开机自动启动", config.autostart) {
                        config = config.copy(autostart = it)
                    }
                    SettingSwitch("打开应用后自动守护", config.startOnLaunch) {
                        config = config.copy(startOnLaunch = it)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { if (persist()) GuardianService.start(context) },
                    enabled = !running,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text("开始守护")
                }
                OutlinedButton(
                    onClick = { GuardianService.checkNow(context) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text("立即检测")
                }
                OutlinedButton(
                    onClick = { GuardianService.stop(context) },
                    enabled = running,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Text("停止")
                }
            }

            OutlinedButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("关闭电池优化（保证后台长期守护）")
            }

            if (error != null) {
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("运行记录", style = MaterialTheme.typography.titleSmall)
                    if (logs.isEmpty()) {
                        Text(
                            "暂无运行记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8),
                        )
                    } else {
                        logs.takeLast(30).asReversed().forEach { line ->
                            Text(line, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Text(
                "返回键 / 关闭界面只会退到后台，通知栏常驻并继续守护；" +
                    "要完全停止请点「停止」或通知栏的「停止守护」。",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64748B),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZoneChip(
    label: String,
    zone: String,
    config: AppConfig,
    onSelect: (String) -> Unit,
) {
    FilterChip(
        selected = config.zone == zone,
        onClick = { onSelect(zone) },
        label = { Text(label) },
    )
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
