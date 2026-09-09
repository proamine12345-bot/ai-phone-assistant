package com.aiphoneagent.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirror of src/lib/agent-protocol.ts — keep both sides in sync. */
@Serializable
data class AgentStep(
    val id: Int = 0,
    val action: String,
    val description: String = "",
    val target: String? = null,
    val text: String? = null,
    val appPackage: String? = null,
    val direction: String? = null,
    val durationMs: Long? = null,
    val x: Float? = null,
    val y: Float? = null,
    val verify: String? = null,
    val dangerous: Boolean = false,
)

@Serializable
data class AgentPlan(
    val goal: String = "",
    val app: String? = null,
    val appPackage: String? = null,
    val summary: String = "",
    val needsConfirmation: Boolean = false,
    val riskNotes: String? = null,
    val steps: List<AgentStep> = emptyList(),
)

@Serializable
data class ScreenNode(
    val text: String? = null,
    val desc: String? = null,
    val viewId: String? = null,
    val className: String? = null,
    val clickable: Boolean = false,
    val editable: Boolean = false,
    val scrollable: Boolean = false,
    val bounds: String? = null,
)

@Serializable
data class ScreenSnapshot(
    val packageName: String? = null,
    val activity: String? = null,
    val nodes: List<ScreenNode> = emptyList(),
    val ocrText: String? = null,
)

@Serializable
data class AgentDecision(
    val step: AgentStep,
    val reasoning: String = "",
    val done: Boolean = false,
)

@Serializable
data class PlanRequest(
    val command: String,
    val allowedApps: List<String> = emptyList(),
    val device: String? = null,
    val screen: ScreenSnapshot? = null,
)

@Serializable
data class PlanResponse(val plan: AgentPlan? = null, val error: String? = null)

@Serializable
data class DecideRequest(
    val goal: String,
    val plannedStep: AgentStep? = null,
    val history: List<String> = emptyList(),
    val screen: ScreenSnapshot,
)

@Serializable
data class DecideResponse(val decision: AgentDecision? = null, val error: String? = null)

enum class TaskStatus { IDLE, PLANNING, AWAITING_CONFIRMATION, RUNNING, PAUSED, STOPPED, DONE, ERROR }

data class LogEntry(
    val time: Long = System.currentTimeMillis(),
    val label: String,
    val detail: String = "",
    val ok: Boolean = true,
)

data class ChatMessage(
    @SerialName("role") val fromUser: Boolean,
    val text: String,
    val time: Long = System.currentTimeMillis(),
)
