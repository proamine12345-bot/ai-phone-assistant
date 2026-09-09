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

    /** POST with retry + strict JSON validation (never lets HTML through). */
    private suspend fun post(path: String, body: String): String = withContext(Dispatchers.IO) {
        var lastError: PlannerException? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(RETRY_DELAY_MS * attempt)
            try {
                return@withContext postOnce(path, body)
            } catch (e: PlannerException) {
                lastError = e
                // Retry only transient failures: rate limits, server errors, non-JSON pages.
                val retryable = e.status == 429 || e.status >= 500 || e.status == NOT_JSON
                if (!retryable) throw e
            } catch (e: java.io.IOException) {
                lastError = PlannerException(0, "تعذّر الاتصال بالخادم: ${e.message}")
            }
        }
        throw lastError ?: PlannerException(0, "تعذّر الاتصال بطبقة الذكاء")
    }

    private fun postOnce(path: String, body: String): String {
        val base = baseUrl()
        if (base.isBlank() || !base.startsWith("http")) {
            throw PlannerException(0, "رابط خدمة الذكاء غير مضبوط (AGENT_API_BASE)")
        }
        val builder = Request.Builder()
            .url("$base$path")
            .addHeader("Accept", "application/json")
            .post(body.toRequestBody(jsonMedia))
        if (BuildConfig.AGENT_API_TOKEN.isNotBlank()) {
            builder.addHeader("x-agent-token", BuildConfig.AGENT_API_TOKEN)
        }
        client.newCall(builder.build()).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            val contentType = response.body?.contentType()?.let { "${it.type}/${it.subtype}" }.orEmpty()
            if (!response.isSuccessful) {
                val detail = if (looksLikeHtml(payload, contentType)) {
                    "الخادم أعاد صفحة ويب وليس JSON (HTTP ${response.code}). تأكد أن التطبيق منشور وأن AGENT_API_BASE صحيح."
                } else {
                    payload.take(400).ifBlank { "HTTP ${response.code}" }
                }
                throw PlannerException(response.code, detail)
            }
            if (looksLikeHtml(payload, contentType)) {
                throw PlannerException(
                    NOT_JSON,
                    "استجابة غير صالحة: الخادم أعاد HTML بدل JSON. تأكد من نشر الخدمة وصحة AGENT_API_BASE.",
                )
            }
            val trimmed = payload.trim()
            if (!trimmed.startsWith("{")) {
                throw PlannerException(NOT_JSON, "استجابة غير صالحة من طبقة الذكاء: ${trimmed.take(200)}")
            }
            return trimmed
        }
    }

    private fun looksLikeHtml(payload: String, contentType: String): Boolean {
        val head = payload.trimStart().take(200).lowercase()
        return contentType.contains("html") ||
            head.startsWith("<!doctype") ||
            head.startsWith("<html") ||
            head.startsWith("<") ||
            head.contains("build incomplete")
    }

    private fun <T> parse(
        raw: String,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): T = try {
        json.decodeFromString(serializer, raw)
    } catch (e: Exception) {
        throw PlannerException(NOT_JSON, "تعذّر قراءة استجابة الذكاء (JSON غير مطابق للمخطط)")
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
        val parsed = parse(post("/api/public/agent/plan", payload), PlanResponse.serializer())
        val plan = parsed.plan ?: throw PlannerException(502, parsed.error ?: "لا توجد خطة")
        val steps = plan.steps.filter { it.action.isNotBlank() && it.action in ALLOWED_ACTIONS }
        if (steps.isEmpty()) throw PlannerException(502, "الخطة المستلمة لا تحتوي خطوات صالحة")
        return plan.copy(steps = steps.mapIndexed { i, s -> s.copy(id = if (s.id > 0) s.id else i + 1) })
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
        val parsed = parse(post("/api/public/agent/decide", payload), DecideResponse.serializer())
        val decision = parsed.decision ?: throw PlannerException(502, parsed.error ?: "لا يوجد قرار")
        if (decision.step.action !in ALLOWED_ACTIONS) {
            throw PlannerException(502, "إجراء غير معروف من الذكاء: ${decision.step.action}")
        }
        return decision
    }

    /** Mirrors ACTIONS in src/lib/agent-protocol.ts. */
    private val ALLOWED_ACTIONS = setOf(
        "open_app", "tap_text", "tap_id", "long_press_text", "type_text", "clear_text",
        "press_back", "press_home", "press_enter", "open_notifications", "scroll", "swipe",
        "tap_point", "wait", "assert_text", "read_screen", "launch_url", "ask_user",
        "repeat_until", "finish",
    )

    private const val MAX_ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 1200L
    /** Internal status for "server replied with something that is not JSON". */
    const val NOT_JSON = 598
}


class PlannerException(val status: Int, message: String) : Exception(message)
