package com.example.mobiletest.data

import android.content.SharedPreferences
import com.example.mobiletest.model.Booking
import com.example.mobiletest.service.BookingService
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class BookingDataManager(
    private val bookingService: BookingService,
    private val sharedPreferences: SharedPreferences
) {

    private val gson =
        Gson()
    private val bookingKey = "booking_data"
    private val expiryTimeKey = "booking_expiry_time"
    private val lastUpdateKey = "booking_last_update"

    // 時效性設定為 10 分鐘
    private val dataExpiryMillis: Long = 10 * 60 * 1000

    // 定義數據加載狀態
    sealed class BookingResult {
        data class Success(val data: Booking) : BookingResult()
        data class Error(val message: String) : BookingResult()
        data object Loading : BookingResult()
    }

    // 使用 Flow 來提供數據和狀態
    fun getBookingDataFlow(forceRefresh: Boolean = false): Flow<BookingResult> = flow {
        try {
            emit(BookingResult.Loading)
            
            val cachedBooking = getCachedBooking()
            val expiryTime = sharedPreferences.getLong(expiryTimeKey, 0)
            val currentTime = System.currentTimeMillis()

            // 如果強制刷新或數據過期或沒有緩存數據
            if (forceRefresh || cachedBooking == null || currentTime > expiryTime) {
                val newBooking = bookingService.getBookingData()
                if (newBooking != null) {
                    cacheBooking(newBooking)
                    setExpiryTime()
                    updateLastFetchTime()
                    emit(BookingResult.Success(newBooking))
                } else {
                    // 從服務獲取數據失敗，但有緩存數據
                    if (cachedBooking != null) {
                        emit(BookingResult.Success(cachedBooking))
                    } else {
                        emit(BookingResult.Error("無法獲取數據，請檢查網絡連接"))
                    }
                }
            } else {
                // 緩存數據有效
                emit(BookingResult.Success(cachedBooking))
            }
        } catch (e: Exception) {
            val errorMsg = when (e) {
                is JsonSyntaxException -> "數據格式錯誤"
                else -> "發生未知錯誤：${e.message}"
            }
            emit(BookingResult.Error(errorMsg))
        }
    }

    // 同步版本，用於向後兼容
    fun getBookingData(forceRefresh: Boolean = false): Booking? {
        val cachedBooking = getCachedBooking()
        val expiryTime = sharedPreferences.getLong(expiryTimeKey, 0)
        val currentTime = System.currentTimeMillis()

        if (forceRefresh || cachedBooking == null || currentTime > expiryTime) {
            val newBooking = bookingService.getBookingData()
            newBooking?.let {
                cacheBooking(it)
                setExpiryTime()
                return it
            } ?: run {
                // 處理 service 層獲取數據失敗的情況
                return cachedBooking // 返回緩存的數據，即使可能過期
            }
        }
        return cachedBooking
    }

    // 獲取上次數據更新時間
    fun getLastUpdateTime(): Long {
        return sharedPreferences.getLong(lastUpdateKey, 0)
    }

    private fun updateLastFetchTime() {
        sharedPreferences.edit().putLong(lastUpdateKey, System.currentTimeMillis()).apply()
    }

    private fun getCachedBooking(): Booking? {
        val json = sharedPreferences.getString(bookingKey, null)
        return json?.let {
            gson.fromJson(it, Booking::class.java)
        }
    }

    private fun cacheBooking(booking: Booking) {
        val json = gson.toJson(booking)
        sharedPreferences.edit().putString(bookingKey, json).apply()
    }

    private fun setExpiryTime() {
        val expiryTime = System.currentTimeMillis() + dataExpiryMillis
        sharedPreferences.edit().putLong(expiryTimeKey, expiryTime).apply()
    }

    fun clearCache() {
        sharedPreferences.edit()
            .remove(bookingKey)
            .remove(expiryTimeKey)
            .remove(lastUpdateKey)
            .apply()
    }
}
