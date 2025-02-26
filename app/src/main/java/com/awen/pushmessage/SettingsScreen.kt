package com.awen.pushmessage

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.awen.pushmessage.settings.settingItems

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    onNavigateBack: () -> Unit
) {
    var showHttpSettings by remember { mutableStateOf(false) }
    var showFilterSettings by remember { mutableStateOf(false) }
    
    when {
        showHttpSettings -> {
            HttpSettingsScreen(
                settings = settings,
                onSettingsChange = onSettingsChange,
                onNavigateBack = { showHttpSettings = false }
            )
        }
        showFilterSettings -> {
            FilterSettingsScreen(
                settings = settings,
                onSettingsChange = onSettingsChange,
                onNavigateBack = { showFilterSettings = false }
            )
        }
        else -> {
            SettingsMainScreen(
                onHttpSettingsClick = { showHttpSettings = true },
                onFilterSettingsClick = { showFilterSettings = true },
                onNavigateBack = onNavigateBack
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsMainScreen(
    onHttpSettingsClick: () -> Unit,
    onFilterSettingsClick: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var showAboutDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ListItem(
                headlineContent = { Text("HTTP推送设置") },
                supportingContent = { Text("配置推送服务器地址和加密密钥") },
                leadingContent = { Icon(Icons.Default.Send, contentDescription = null) },
                trailingContent = { Icon(Icons.Default.KeyboardArrowRight, "进入") },
                modifier = Modifier.clickable { onHttpSettingsClick() }
            )
            Divider()
            
            ListItem(
                headlineContent = { Text("验证码过滤规则") },
                supportingContent = { Text("自定义短信验证码的匹配规则") },
                leadingContent = { Icon(Icons.Default.List, contentDescription = null) },
                trailingContent = { Icon(Icons.Default.KeyboardArrowRight, "进入") },
                modifier = Modifier.clickable { onFilterSettingsClick() }
            )
            Divider()
            
            ListItem(
                headlineContent = { Text("关于") },
                supportingContent = { Text("版本信息和使用说明") },
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                trailingContent = { Icon(Icons.Default.KeyboardArrowRight, "进入") },
                modifier = Modifier.clickable { showAboutDialog = true }
            )
            Divider()
        }
    }
    
    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关于") },
        text = {
            Column {
                Text("短信验证码推送")
                Text("版本：1.0.0")
                Spacer(modifier = Modifier.height(8.dp))
                Text("本应用用于自动识别接收到的验证码短信并通过HTTP接口推送到指定服务器。")
                Spacer(modifier = Modifier.height(8.dp))
                Text("功能特点：")
                Text("• 自动识别验证码短信")
                Text("• 支持自定义验证码匹配规则")
                Text("• 支持加密传输")
                Text("• 实时推送通知")
                Text("                          ")
                Text("作者： 阿文 邮箱 hi@awen.me")

            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("确定")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSettingsScreen(
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    onNavigateBack: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("验证码过滤规则") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, "添加规则")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (settings.customFilters.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("点击右上角添加过滤规则")
                }
            } else {
                LazyColumn {
                    items(settings.customFilters) { filter ->
                        FilterItem(
                            filter = filter,
                            onToggle = { isEnabled ->
                                val updatedFilters = settings.customFilters.map {
                                    if (it.id == filter.id) it.copy(isEnabled = isEnabled)
                                    else it
                                }
                                onSettingsChange(settings.copy(customFilters = updatedFilters))
                            },
                            onDelete = {
                                val updatedFilters = settings.customFilters.filter { it.id != filter.id }
                                onSettingsChange(settings.copy(customFilters = updatedFilters))
                            }
                        )
                        Divider()
                    }
                }
            }
        }
    }
    
    if (showAddDialog) {
        AddFilterDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { type, pattern ->
                val newFilter = SmsFilter(type = type, pattern = pattern)
                val updatedFilters = settings.customFilters + newFilter
                onSettingsChange(settings.copy(customFilters = updatedFilters))
                showAddDialog = false
            }
        )
    }
}

@Composable
fun FilterItem(
    filter: SmsFilter,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    ListItem(
        headlineContent = { Text(filter.pattern) },
        supportingContent = { 
            Text(when(filter.type) {
                FilterType.KEYWORD -> "关键字匹配"
                FilterType.REGEX -> "正则表达式匹配"
            })
        },
        leadingContent = {
            Switch(
                checked = filter.isEnabled,
                onCheckedChange = onToggle
            )
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "删除")
            }
        }
    )
}

@Composable
fun AddFilterDialog(
    onDismiss: () -> Unit,
    onConfirm: (FilterType, String) -> Unit
) {
    var pattern by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf(FilterType.KEYWORD) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加过滤规则") },
        text = {
            Column {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("匹配内容") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = filterType == FilterType.KEYWORD,
                        onClick = { filterType = FilterType.KEYWORD }
                    )
                    Text("关键字")
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    RadioButton(
                        selected = filterType == FilterType.REGEX,
                        onClick = { filterType = FilterType.REGEX }
                    )
                    Text("正则表达式")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(filterType, pattern) },
                enabled = pattern.isNotBlank()
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HttpSettingsScreen(
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var showKey by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HTTP推送设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = settings.apiUrl,
                onValueChange = { onSettingsChange(settings.copy(apiUrl = it)) },
                label = { Text("HTTP接口地址") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = if (showKey) settings.encryptionKey else "••••••••",
                onValueChange = { },
                label = { Text("加密密钥") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                singleLine = true,
                trailingIcon = {
                    Row {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                imageVector = if (showKey) 
                                    Icons.Default.Close 
                                else 
                                    Icons.Default.Info,
                                contentDescription = if (showKey) "隐藏密钥" else "显示密钥"
                            )
                        }
                        if (settings.encryptionKey.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    copyToClipboard(context, settings.encryptionKey)
                                    Toast.makeText(context, "密钥已复制到剪贴板", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "复制密钥"
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("encryption key", text)
    clipboard.setPrimaryClip(clip)
} 