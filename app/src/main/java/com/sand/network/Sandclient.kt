package com.sand.network

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object SandClient {

    private const val PREFS_NAME = "sand_prefs"
    const val KEY_API_IP = "api_ip"
    private const val PORT = 8000
    private const val TIMEOUT_SECONDS = 5L  // Fail fast — no IP = blocked

    fun getStoredIp(context: Context): String? {
        return context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_API_IP, null)
            ?.trim()
    }

    fun saveIp(context: Context, ip: String) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_API_IP, ip.trim())
            .apply()
    }

    fun clearIp(context: Context) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_API_IP)
            .apply()
    }

    /**
     * Build a SandApi instance for the given IP.
     * Returns null if IP is null or blank — caller should treat this as blocked.
     */
    fun build(ip: String?): SandApi? {
        if (ip.isNullOrBlank()) return null

        // Strip subnet mask if user pastes the full CIDR (e.g. 10.125.192.232/24)
        val cleanIp = ip.trim().substringBefore("/")

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl("http://$cleanIp:$PORT/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SandApi::class.java)
    }

    /**
     * Convenience — build from stored IP directly.
     * Returns null if no IP stored.
     */
    fun buildFromPrefs(context: Context): SandApi? {
        return build(getStoredIp(context))
    }
}
