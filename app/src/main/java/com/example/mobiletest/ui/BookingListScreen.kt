package com.example.mobiletest.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.mobiletest.data.BookingDataManager
import com.example.mobiletest.data.BookingDataManager.BookingResult
import com.example.mobiletest.model.Booking
import com.example.mobiletest.model.Segment
import com.example.mobiletest.service.BookingServiceImpl
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingListScreen(lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current) {
    // 獲取 Context 和 SharedPreferences
    val context = LocalContext.current
    val bookingService = BookingServiceImpl(context)
    val sharedPreferences = context.getSharedPreferences("booking_prefs", Context.MODE_PRIVATE)
    
    // 創建 BookingDataManager 實例
    val bookingDataManager = BookingDataManager(bookingService, sharedPreferences)

    // 創建 MutableState 來保存數據狀態、刷新狀態和上次更新時間
    val bookingState = remember { mutableStateOf<BookingResult>(BookingResult.Loading) }
    val isRefreshing = remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val lastUpdateTime = remember { mutableStateOf("") }

    // 獲取數據並處理結果的函數
    val fetchData = {
        coroutineScope.launch {
            // 開始刷新
            isRefreshing.value = true
            
            // 調用 BookingDataManager 的 getBookingDataFlow 方法獲取數據
            bookingDataManager.getBookingDataFlow(true).collectLatest { result ->
                // 更新數據狀態
                bookingState.value = result
                
                // 停止刷新
                if (result !is BookingResult.Loading) {
                    isRefreshing.value = false
                    
                    // 更新上次刷新時間
                    val lastUpdate = bookingDataManager.getLastUpdateTime()
                    if (lastUpdate > 0) {
                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        lastUpdateTime.value = "上次更新: ${sdf.format(Date(lastUpdate))}"
                    }
                }
                
                // 在控制台打印數據
                if (result is BookingResult.Success) {
                    println("Booking Data: ${result.data}")
                }
            }
        }
    }
    
    // 監聽生命週期，在 onResume 時重新獲取數據
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                fetchData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // 初始化時獲取數據
    LaunchedEffect(Unit) {
        fetchData()
    }

    // 使用 Scaffold 創建 UI 結構
    Scaffold(
        topBar = {
            // 創建頂部應用欄
            TopAppBar(
                title = { Text("Booking Details") },
                actions = {
                    // 創建刷新按鈕
                    IconButton(onClick = { fetchData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { innerPadding ->
        // 使用 SwipeRefresh 創建下拉刷新功能
        SwipeRefresh(
            state = rememberSwipeRefreshState(isRefreshing.value),
            onRefresh = { fetchData() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 創建 Column 來垂直排列 UI 元素
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // 顯示上次更新時間
                if (lastUpdateTime.value.isNotEmpty()) {
                    Text(
                        text = lastUpdateTime.value,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                
                // 根據數據狀態顯示不同的 UI
                when (val state = bookingState.value) {
                    is BookingResult.Loading -> {
                        // 顯示加載指示器
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is BookingResult.Error -> {
                        // 顯示錯誤信息和重試按鈕
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "錯誤: ${state.message}",
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                                Button(onClick = { fetchData() }) {
                                    Text("重試")
                                }
                            }
                        }
                    }
                    is BookingResult.Success -> {
                        // 顯示 Booking 數據
                        BookingContent(state.data)
                    }
                }
            }
        }
    }
}

// 顯示 Booking 數據的 Composable 函數
@Composable
fun BookingContent(booking: Booking) {
    // 使用 LazyColumn 創建可滾動的列表
    LazyColumn {
        item {
            // 創建 Card 來顯示 Booking 數據
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                // 創建 Column 來垂直排列 Card 中的 UI 元素
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "船舶參考號: ${booking.shipReference}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(text = "船舶令牌: ${booking.shipToken}")
                    Text(
                        text = "可出票檢查: ${if (booking.canIssueTicketChecking) "是" else "否"}",
                        color = if (booking.canIssueTicketChecking) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.error
                    )
                    Text(text = "到期時間: ${formatTimestamp(booking.expiryTime)}")
                    Text(text = "持續時間: ${formatDuration(booking.duration)}")
                }
            }
        }
        
        item {
            // 顯示航段列表標題
            Text(
                text = "航段列表",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
        
        // 創建航段列表
        items(booking.segments) { segment ->
            SegmentItem(segment)
        }
    }
}

// 顯示航段數據的 Composable 函數
@Composable
fun SegmentItem(segment: Segment) {
    // 創建 Card 來顯示航段數據
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // 創建 Column 來垂直排列 Card 中的 UI 元素
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "航段 ID: ${segment.id}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // 創建 Row 來水平排列 UI 元素
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 顯示出發地信息
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "出發地", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = segment.originAndDestinationPair.origin.displayName,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = segment.originAndDestinationPair.origin.code,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "城市: ${segment.originAndDestinationPair.originCity}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                // 顯示目的地信息
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "目的地", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = segment.originAndDestinationPair.destination.displayName,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = segment.originAndDestinationPair.destination.code,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "城市: ${segment.originAndDestinationPair.destinationCity}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

// 格式化時間戳記的函數
fun formatTimestamp(timestamp: String): String {
    try {
        val time = timestamp.toLong() * 1000 // 轉換為毫秒
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(time))
    } catch (e: Exception) {
        return timestamp
    }
}

// 格式化持續時間的函數
fun formatDuration(durationMinutes: Int): String {
    val hours = durationMinutes / 60
    val minutes = durationMinutes % 60
    return "${hours}小時 ${minutes}分鐘"
}
