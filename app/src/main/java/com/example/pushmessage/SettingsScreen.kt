package com.example.pushmessage

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.pushmessage.settings.settingItems

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    onNavigateBack: () -> Unit
) {
    var showHttpSettings by remember { mutableStateOf(false) }
    
    if (showHttpSettings) {
        HttpSettingsScreen(
            settings = settings,
            onSettingsChange = onSettingsChange,
            onNavigateBack = { showHttpSettings = false }
        )
    } else {
        SettingsMainScreen(
            onHttpSettingsClick = { showHttpSettings = true },
            onNavigateBack = onNavigateBack
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsMainScreen(
    onHttpSettingsClick: () -> Unit,
    onNavigateBack: () -> Unit
) {
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
            settingItems.forEach { item ->
                ListItem(
                    headlineContent = { Text(item.title) },
                    supportingContent = item.description.let { if (it.isNotEmpty()) { { Text(it) } } else null },
                    leadingContent = { Icon(item.icon, contentDescription = null) },
                    trailingContent = { Icon(Icons.Default.KeyboardArrowRight, "进入") },
                    modifier = Modifier.clickable { onHttpSettingsClick() }
                )
                Divider()
            }
        }
    }
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