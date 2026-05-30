package com.sand.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

// ── Request bodies ────────────────────────────────────────────────────────────

data class TimeSyncRequest(val app_id: String, val minutes: Int)

data class VerifyRequest(
    val app_id: String,
    val set_id: String,
    val token: String,
    val answer: String
)

// ── Response bodies ───────────────────────────────────────────────────────────

data class SyncResponse(
    val current_level: Int,
    val instagram_minutes: Int,
    val gated: Boolean
)

data class Problem(
    val token: String,
    val problem: String
)

data class GenerateResponse(
    val set_id: String,
    val level: Int,
    val problems: List<Problem>,
    val message: String? = null  // returned when level == 0 (free zone)
)

data class VerifyResponse(
    val correct: Boolean,
    val message: String,
    val remaining: Int = 0,
    val next_token: String? = null,
    val next_problem: String? = null,
    val grant_minutes: Int = 0
)

data class StateResponse(
    val instagram_minutes: Int,
    val current_level: Int,
    val last_reset: String,
    val active_set: Any? = null
)

// ── API interface ─────────────────────────────────────────────────────────────

interface SandApi {

    @POST("sync")
    suspend fun sync(@Body body: TimeSyncRequest): Response<SyncResponse>

    @POST("generate")
    suspend fun generate(@Query("app_id") app_id: String): Response<GenerateResponse>

    @POST("verify")
    suspend fun verify(@Body body: VerifyRequest): Response<VerifyResponse>

    @GET("state")
    suspend fun state(@Query("app_id") app_id: String): Response<StateResponse>
}
