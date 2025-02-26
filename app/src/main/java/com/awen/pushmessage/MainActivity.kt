@file:OptIn(ExperimentalMaterialApi::class)

package com.awen.pushmessage

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.rememberDismissState
import androidx.compose.material.Card as MaterialCard
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.animation.animateColorAsState
import com.awen.pushmessage.data.SmsMessage
import com.awen.pushmessage.utils.CryptoUtils
import com.awen.pushmessage.utils.EventBus
import kotlinx.coroutines.Job
import androidx.lifecycle.lifecycleScope
import com.awen.pushmessage.ui.theme.PushMessageTheme
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import java.util.Base64
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState

@OptIn(ExperimentalMaterialApi::class)
class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.READ_SMS] == true &&
            permissions[Manifest.permission.RECEIVE_SMS] == true) {
            readSms()
        }
    }

    private var _smsMessages = mutableStateOf<List<SmsMessage>>(emptyList())
    private var _showSettings = mutableStateOf(false)
    private var _settings = mutableStateOf(Settings())
    private var refreshJob: Job? = null
    private var eventCollectorJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        loadSettings()
        checkSmsPermissions()
        
        // 清理过期的同步记录
        cleanOldSyncRecords()

        // 监听SMS更新事件
        eventCollectorJob = lifecycleScope.launch {
            EventBus.smsUpdateEvent.collect {
                readSms()
            }
        }

        // 启动定期刷新
        startPeriodicRefresh()

        setContent {
            PushMessageTheme {
                var showSettings by remember { _showSettings }
                var settings by remember { _settings }
                
                if (showSettings) {
                    SettingsScreen(
                        settings = settings,
                        onSettingsChange = { 
                            settings = it
                            saveSettings(it)
                        },
                        onNavigateBack = { showSettings = false }
                    )
                } else {
                    MainScreen(
                        smsList = _smsMessages.value,
                        onRefresh = { readSms() },
                        onSettingsClick = { showSettings = true },
                        onDeleteSms = { sms -> moveToTrash(sms.id) },
                        onRestoreSms = { sms -> markAsRead(sms.id) },
                        onMarkAsRead = { sms -> markAsRead(sms.id) }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        refreshJob?.cancel()
        eventCollectorJob?.cancel()
        super.onDestroy()
    }

    private fun checkSmsPermissions() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED -> {
                readSms()
            }
            else -> {
                requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_SMS,
                        Manifest.permission.RECEIVE_SMS
                    )
                )
            }
        }
    }

    private fun readSms() {
        try {
            val thirtyMinutesAgo = System.currentTimeMillis() - (30 * 60 * 1000)
            
            // 读取已删除和永久删除的消息ID
            val deletedPrefs = getSharedPreferences("deleted_messages", MODE_PRIVATE)
            val deletedMessages = deletedPrefs.all.keys.toSet()
            
            val permanentlyDeletedPrefs = getSharedPreferences("permanently_deleted_messages", MODE_PRIVATE)
            val permanentlyDeletedMessages = permanentlyDeletedPrefs.all.keys.toSet()
            
            // 合并所有需要排除的消息ID
            val excludedMessages = deletedMessages + permanentlyDeletedMessages
            
            val cursor = contentResolver.query(
                android.provider.Telephony.Sms.CONTENT_URI,
                arrayOf(
                    android.provider.Telephony.Sms._ID,
                    android.provider.Telephony.Sms.ADDRESS,
                    android.provider.Telephony.Sms.BODY,
                    android.provider.Telephony.Sms.DATE,
                    android.provider.Telephony.Sms.TYPE,
                    android.provider.Telephony.Sms.READ
                ),
                "${android.provider.Telephony.Sms.TYPE} = ? AND ${android.provider.Telephony.Sms.DATE} >= ?",
                arrayOf(
                    android.provider.Telephony.Sms.MESSAGE_TYPE_INBOX.toString(),
                    thirtyMinutesAgo.toString()
                ),
                "${android.provider.Telephony.Sms.DATE} DESC"
            )

            val messages = mutableListOf<SmsMessage>()
            
            cursor?.use {
                if (it.moveToFirst()) {
                    do {
                        val msgId = it.getLong(it.getColumnIndexOrThrow(android.provider.Telephony.Sms._ID))
                        
                        // 果消息在排除列表中，跳过
                        if (excludedMessages.contains(msgId.toString())) {
                            continue
                        }
                        
                        val address = it.getString(it.getColumnIndexOrThrow(android.provider.Telephony.Sms.ADDRESS))
                        val body = it.getString(it.getColumnIndexOrThrow(android.provider.Telephony.Sms.BODY))
                        val date = it.getLong(it.getColumnIndexOrThrow(android.provider.Telephony.Sms.DATE))
                        val isRead = it.getInt(it.getColumnIndexOrThrow(android.provider.Telephony.Sms.READ)) == 1
                        
                        // 首先检查短信内容是否包含"验证码"
                        if (body.contains("验证码")) {
                            val verificationCode = Regex("""(?<!\d)(\d{4,6})(?!\d)""").find(body)?.groupValues?.get(1)
                            if (verificationCode != null) {
                                val smsMessage = SmsMessage(
                                    id = msgId,
                                    address = address,
                                    body = body,
                                    date = date,
                                    verificationCode = verificationCode,
                                    isRead = isRead,
                                    isDeleted = false
                                )
                                messages.add(smsMessage)
                                
                                // 检查并同步未同步的消息
                                if (!isSmsAlreadySynced(smsMessage)) {
                                    sendSmsToServer(smsMessage)
                                }
                            }
                        }
                    } while (it.moveToNext())
                }
            }
            
            // 更新内存中的消息列表
            if (messages.isNotEmpty()) {
                // 保留有的已删除状态
                val existingDeletedMessages = _smsMessages.value.filter { it.isDeleted }
                _smsMessages.value = messages + existingDeletedMessages
                Log.d("SMS", "Updated SMS list with ${messages.size} messages")
            }
            
        } catch (e: Exception) {
            Log.e("SMS", "Error reading SMS", e)
        }
    }

    private fun markAsRead(smsId: Long) {
        try {
            // 更新系统短信的已读状态
            contentResolver.update(
                android.provider.Telephony.Sms.CONTENT_URI,
                android.content.ContentValues().apply {
                    put(android.provider.Telephony.Sms.READ, 1)
                },
                "${android.provider.Telephony.Sms._ID} = ?",
                arrayOf(smsId.toString())
            )

            // 更新内存中的状态
            _smsMessages.value = _smsMessages.value.map { sms ->
                if (sms.id == smsId) sms.copy(isRead = true) else sms
            }
        } catch (e: Exception) {
            Log.e("SMS", "Error marking message as read", e)
        }
    }

    private fun moveToTrash(smsId: Long) {
        // 检查消息是否已经在回收站
        val isInTrash = _smsMessages.value.find { it.id == smsId }?.isDeleted == true
        
        if (isInTrash) {
            permanentDelete(smsId)
        } else {
            // 保存删除状态和时间戳
            getSharedPreferences("deleted_messages", MODE_PRIVATE).edit().apply {
                putBoolean(smsId.toString(), true)
                apply()
            }
            
            // 保存操作时间戳
            getSharedPreferences("message_timestamps", MODE_PRIVATE).edit().apply {
                putLong(smsId.toString(), System.currentTimeMillis())
                apply()
            }

            _smsMessages.value = _smsMessages.value.map { sms ->
                if (sms.id == smsId) sms.copy(isDeleted = true) else sms
            }
        }
    }

    private fun permanentDelete(smsId: Long) {
        // 从表全移除消息
        _smsMessages.value = _smsMessages.value.filter { it.id != smsId }
        
        // 从删除记录中移除
        getSharedPreferences("deleted_messages", MODE_PRIVATE).edit().apply {
            remove(smsId.toString())
            apply()
        }
        
        // 添加到永久删除记录
        getSharedPreferences("permanently_deleted_messages", MODE_PRIVATE).edit().apply {
            putBoolean(smsId.toString(), true)
            apply()
        }
        
        // 从同步记录中移除
        getSharedPreferences("synced_messages", MODE_PRIVATE).edit().apply {
            val messageHash = _smsMessages.value.find { sms -> sms.id == smsId }?.let { foundSms -> generateMessageHash(foundSms) }
            if (messageHash != null) {
                remove(messageHash)
            }
            apply()
        }
    }

    private fun saveSettings(settings: Settings) {
        getSharedPreferences("settings", MODE_PRIVATE).edit().apply {
            putString("api_url", settings.apiUrl)
            putString("encryption_key", settings.encryptionKey)
            apply()
        }
    }

    private fun sendSmsToServer(sms: SmsMessage) {
        val settings = _settings.value
        if (settings.apiUrl.isNotBlank()) {
            if (isSmsAlreadySynced(sms)) {
                Log.d("SMS", "Message already synced locally, skipping: ${sms.verificationCode}")
                return
            }

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    Log.d("SMS", "========== 开始发送消息 ==========")
                    Log.d("SMS", "发送地址: ${settings.apiUrl}")
                    Log.d("SMS", "原始验证码: ${sms.verificationCode}")
                    
                    // 只密验证码
                    val encryptedCode = encrypt(sms.verificationCode, settings.encryptionKey)
                    Log.d("SMS", "加密后的验证码: $encryptedCode")
                    
                    val client = OkHttpClient()
                    
                    // 简化 JSON 结构，只发送 message 字段
                    val json = """
                        {
                            "message": "$encryptedCode"
                        }
                    """.trimIndent()

                    Log.d("SMS", "Sending JSON: $json")

                    val request = Request.Builder()
                        .url(settings.apiUrl)
                        .post(json.toRequestBody("application/json".toMediaType()))
                        .build()

                    client.newCall(request).execute().use { response ->
                        val responseBody = response.body?.string()
                        Log.d("SMS", "Server response: $responseBody")
                        
                        when (response.code) {
                            200 -> {
                                markSmsAsSynced(sms)
                                Log.d("SMS", "Message synced successfully: ${sms.verificationCode}")
                            }
                            409 -> {
                                markSmsAsSynced(sms)
                                Log.d("SMS", "Message already exists on server: ${sms.verificationCode}")
                            }
                            else -> {
                                Log.e("SMS", "Failed to send message: ${response.code}")
                            }
                        }
                    }

                    Log.d("SMS", "========== 发送完成 ==========")
                } catch (e: Exception) {
                    Log.e("SMS", "========== 发送失败 ==========")
                    Log.e("SMS", "错误信息: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }

    private fun isSmsAlreadySynced(sms: SmsMessage): Boolean {
        val prefs = getSharedPreferences("synced_messages", MODE_PRIVATE)
        val messageHash = generateMessageHash(sms)
        return prefs.getBoolean(messageHash, false)
    }

    private fun markSmsAsSynced(sms: SmsMessage) {
        val prefs = getSharedPreferences("synced_messages", MODE_PRIVATE)
        val messageHash = generateMessageHash(sms)
        prefs.edit().putBoolean(messageHash, true).apply()
    }

    private fun generateMessageHash(sms: SmsMessage): String {
        // 只使用验证码和发送者来生成哈希
        val key = "${sms.address}_${sms.verificationCode}"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(key.toByteArray())
        return Base64.getEncoder().encodeToString(hash)
    }

    private fun cleanOldSyncRecords() {
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        
        // 清理已删除消息记录
        val deletedPrefs = getSharedPreferences("deleted_messages", MODE_PRIVATE)
        val permanentlyDeletedPrefs = getSharedPreferences("permanently_deleted_messages", MODE_PRIVATE)
        
        // 添加时间记录
        val timestampPrefs = getSharedPreferences("message_timestamps", MODE_PRIVATE)
        
        // 清理过期的记录
        deletedPrefs.all.keys.forEach { messageId ->
            val timestamp = timestampPrefs.getLong(messageId, 0)
            if (timestamp < thirtyDaysAgo) {
                deletedPrefs.edit().remove(messageId).apply()
                permanentlyDeletedPrefs.edit().remove(messageId).apply()
                timestampPrefs.edit().remove(messageId).apply()
            }
        }
    }

    private fun encrypt(message: String, key: String): String {
        try {
            val decodedKey = Base64.getDecoder().decode(key)
            val secretKey = SecretKeySpec(decodedKey, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val encryptedBytes = cipher.doFinal(message.toByteArray())
            val iv = cipher.iv
            
            // 组合 IV 和加密数据
            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
            
            return Base64.getEncoder().encodeToString(combined)
        } catch (e: Exception) {
            Log.e("Encryption", "Error encrypting message", e)
            return message
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val savedKey = prefs.getString("encryption_key", null)
        _settings.value = Settings(
            apiUrl = prefs.getString("api_url", "") ?: "",
            encryptionKey = savedKey ?: run {
                // 果没有保存的密钥，使用信息生成新密钥
                val newKey = CryptoUtils.generateEncryptionKey()
                // 保存生成的密钥
                prefs.edit().putString("encryption_key", newKey).apply()
                newKey
            }
        )
    }

    private fun startPeriodicRefresh() {
        refreshJob = lifecycleScope.launch {
            while (true) {
                delay(1000) // 每1秒刷新一次
                readSms()
                
                // 每天清理一次过期记录
                if (System.currentTimeMillis() % (24 * 60 * 60 * 1000) < 1000) {
                    cleanOldSyncRecords()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MainScreen(
    smsList: List<SmsMessage>,
    onRefresh: () -> Unit,
    onSettingsClick: () -> Unit,
    onDeleteSms: (SmsMessage) -> Unit,
    onRestoreSms: (SmsMessage) -> Unit,
    onMarkAsRead: (SmsMessage) -> Unit
) {
    var currentTab by remember { mutableStateOf(TabItem.Inbox) }
    var isRefreshing by remember { mutableStateOf(false) }
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = isRefreshing)
    
    @OptIn(ExperimentalMaterial3Api::class)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("验证码消息") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置"
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                TabItem.entries.forEach { tab ->
                    NavigationBarItem(
                        icon = { 
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title
                            )
                        },
                        label = { Text(tab.title) },
                        selected = currentTab == tab,
                        onClick = { currentTab = tab }
                    )
                }
            }
        }
    ) { padding ->
        SwipeRefresh(
            state = swipeRefreshState,
            onRefresh = {
                isRefreshing = true
                onRefresh()
                isRefreshing = false
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 原有的列表内容
            val filteredList = when (currentTab) {
                TabItem.Inbox -> smsList.filter { !it.isDeleted && !it.isRead }
                TabItem.Read -> smsList.filter { !it.isDeleted && it.isRead }
                TabItem.Trash -> smsList.filter { it.isDeleted }
            }

            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (currentTab) {
                            TabItem.Inbox -> "下拉刷新获取最新验证码"
                            TabItem.Read -> "没有已读的验证码短信"
                            TabItem.Trash -> "回收站为空"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = filteredList,
                        key = { sms: SmsMessage -> sms.id }
                    ) { sms ->
                        SwipeableSmsCard(
                            sms = sms,
                            onDelete = { onDeleteSms(sms) },
                            onMarkAsRead = { onMarkAsRead(sms) },
                            enableMarkAsRead = currentTab == TabItem.Inbox
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SwipeableSmsCard(
    sms: SmsMessage,
    onDelete: () -> Unit,
    onMarkAsRead: () -> Unit,
    enableMarkAsRead: Boolean
) {
    val dismissState = rememberDismissState(
        confirmStateChange = { dismissValue ->
            when (dismissValue) {
                DismissValue.DismissedToEnd -> {
                    if (enableMarkAsRead && !sms.isRead) {
                        onMarkAsRead()
                    }
                    false
                }
                DismissValue.DismissedToStart -> {
                    onDelete()
                    false
                }
                DismissValue.Default -> false
            }
        }
    )

    SwipeToDismiss(
        state = dismissState,
        background = {
            val direction = dismissState.dismissDirection
            val color by animateColorAsState(
                when (direction) {
                    DismissDirection.StartToEnd -> MaterialTheme.colorScheme.primary
                    DismissDirection.EndToStart -> MaterialTheme.colorScheme.error
                    null -> Color.Transparent
                }
            )
            val alignment = when (direction) {
                DismissDirection.StartToEnd -> Alignment.CenterStart
                DismissDirection.EndToStart -> Alignment.CenterEnd
                null -> Alignment.Center
            }
            val icon = when (direction) {
                DismissDirection.StartToEnd -> Icons.Default.CheckCircle
                DismissDirection.EndToStart -> Icons.Default.Delete
                null -> null
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        },
        dismissContent = {
            MaterialCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                elevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = sms.address,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "验证码: ${sms.verificationCode}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            .format(Date(sms.date)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        directions = setOf(
            DismissDirection.StartToEnd,
            DismissDirection.EndToStart
        )
    )
}

enum class TabItem(
    val title: String,
    val icon: ImageVector
) {
    Inbox("收件箱", Icons.Default.Email),
    Read("已读", Icons.Default.Done),
    Trash("回收站", Icons.Default.Delete)
}
