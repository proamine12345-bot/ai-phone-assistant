package com.aiphoneagent.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Single source of truth shared between the UI, the Task Engine and the
 * AccessibilityService. Also owns the Start / Pause / Stop / Emergency flags.
 */
object AgentBus {

    private val _status = MutableStateFlow(TaskStatus.IDLE)
    val status: StateFlow<TaskStatus> = _status.asStateFlow()

    private val _plan = MutableStateFlow<AgentPlan?>(null)
    val plan: StateFlow<AgentPlan?> = _plan.asStateFlow()

    private val _currentStep = MutableStateFlow<AgentStep?>(null)
    val currentStep: StateFlow<AgentStep?> = _currentStep.asStateFlow()

    private val _log = MutableStateFlow<List<LogEntry>>(emptyList())
    val log: StateFlow<List<LogEntry>> = _log.asStateFlow()

    private val _chat = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chat: StateFlow<List<ChatMessage>> = _chat.asStateFlow()

    private val _pending = MutableStateFlow<String?>(null)
    /** Question / confirmation the agent is waiting on. */
    val pending: StateFlow<String?> = _pending.asStateFlow()

    @Volatile var paused: Boolean = false; private set
    @Volatile var stopRequested: Boolean = false; private set
    @Volatile var emergency: Boolean = false; private set

    fun setStatus(status: TaskStatus) { _status.value = status }
    fun setPlan(plan: AgentPlan?) { _plan.value = plan }
    fun setCurrentStep(step: AgentStep?) { _currentStep.value = step }
    fun ask(question: String?) { _pending.value = question }

    fun log(label: String, detail: String = "", ok: Boolean = true) {
        _log.update { (it + LogEntry(label = label, detail = detail, ok = ok)).takeLast(300) }
    }

    fun say(text: String, fromUser: Boolean = false) {
        _chat.update { it + ChatMessage(fromUser = fromUser, text = text) }
    }

    fun clearLog() { _log.value = emptyList() }

    fun start() {
        paused = false
        stopRequested = false
        emergency = false
    }

    fun pause() {
        paused = true
        _status.value = TaskStatus.PAUSED
        log("إيقاف مؤقت")
    }

    fun resume() {
        paused = false
        _status.value = TaskStatus.RUNNING
        log("استئناف")
    }

    fun stop() {
        stopRequested = true
        paused = false
        _status.value = TaskStatus.STOPPED
        log("إيقاف المهمة", ok = false)
    }

    /** Emergency stop: aborts immediately and refuses to run again until Start. */
    fun emergencyStop() {
        emergency = true
        stopRequested = true
        paused = false
        _currentStep.value = null
        _status.value = TaskStatus.STOPPED
        log("إيقاف طارئ — تم إلغاء كل الإجراءات", ok = false)
    }
}
