package com.aiphoneagent.net

import com.aiphoneagent.BuildConfig
import com.aiphoneagent.core.AgentDecision
import com.aiphoneagent.core.AgentPlan
import com.aiphoneagent.core.AgentStep
import com.aiphoneagent.core.DecideRequest
import com.aiphoneagent.core.DecideResponse
import com.aiphoneagent.core.PlanRequest
import com.aiphoneagent.core.PlanResponse
import com.aiphoneagent.core.ScreenSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** AI layer client: talks to the Lovable-hosted planner/decider endpoints. */
object PlannerClient {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    // Reasoning calls can run for a while — generous read timeout, no aggressive aborts.
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun baseUrl() = BuildConfig.AGENT_API_BASE.trimEnd('/')

    private suspend fun post(path: String, body: String): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url("${baseUrl()}$path")
            .post(body.toRequestBody(jsonMedia))
        if (BuildConfig.AGENT_API_TOKEN.isNotBlank()) {
            builder.addHeader("x-agent-token", BuildConfig.AGENT_API_TOKEN)
        }
        client.newCall(builder.build()).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw PlannerException(response.code, payload.ifBlank { "HTTP ${response.code}" })
            }
            payload
        }
    }

    suspend fun plan(
        command: String,
        allowedApps: List<String>,
        device: String?,
        screen: ScreenSnapshot? = null,
    ): AgentPlan {
        val payload = json.encodeToString(
            PlanRequest.serializer(),
            PlanRequest(command, allowedApps, device, screen),
        )
        val parsed = json.decodeFromString(PlanResponse.serializer(), post("/api/public/agent/plan", payload))
        return parsed.plan ?: throw PlannerException(502, parsed.error ?: "لا توجد خطة")
    }

    suspend fun decide(
        goal: String,
        plannedStep: AgentStep?,
        history: List<String>,
        screen: ScreenSnapshot,
    ): AgentDecision {
        val payload = json.encodeToString(
            DecideRequest.serializer(),
            DecideRequest(goal, plannedStep, history, screen),
        )
        val parsed = json.decodeFromString(DecideResponse.serializer(), post("/api/public/agent/decide", payload))
        return parsed.decision ?: throw PlannerException(502, parsed.error ?: "لا يوجد قرار")
    }
}

class PlannerException(val status: Int, message: String) : Exception(message)
