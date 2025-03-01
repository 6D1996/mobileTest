package com.example.mobiletest.service

import android.content.Context
import com.example.mobiletest.model.Booking
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.io.FileNotFoundException
import java.io.IOException

interface BookingService {
    fun getBookingData(): Booking?
    
    // 定義可能的錯誤類型
    sealed class BookingError(val message: String) {
        class NetworkError(message: String) : BookingError(message)
        class DataError(message: String) : BookingError(message)
        class UnknownError(message: String) : BookingError(message)
    }
}

class BookingServiceImpl(private val context: Context) : BookingService {
    override fun getBookingData(): Booking? {
        return try {
            val jsonString = context.assets.open("booking.json").bufferedReader().use {
                it.readText()
            }
            Gson().fromJson(jsonString, Booking::class.java)
        } catch (e: Exception) {
            when (e) {
                is FileNotFoundException -> {
                    println("錯誤: 找不到 booking.json 檔案")
                }
                is JsonSyntaxException -> {
                    println("錯誤: booking.json 格式不正確")
                }
                is IOException -> {
                    println("IO 錯誤: ${e.message}")
                }
                else -> {
                    println("未知錯誤: ${e.message}")
                }
            }
            e.printStackTrace()
            null
        }
    }
}
