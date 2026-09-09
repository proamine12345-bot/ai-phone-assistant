package com.aiphoneagent.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aiphoneagent.agent.TaskEngine
import com.aiphoneagent.core.AgentBus
import com.aiphoneagent.core.AgentPlan
import com.aiphoneagent.core.TaskStatus
import com.aiphoneagent.data.AllowedAppsStore
import com.aiphoneagent.data.InstalledApp
import com.aiphoneagent.net.PlannerClient
import com.aiphoneagent.net.PlannerException
import com.aiphoneagent.service.AgentAccessibilityService
import com.aiphoneagent.speech.SpeechInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AgentViewModel(app: Application) : AndroidViewModel(app) {

    val appsStore = AllowedAppsStore(app)
    val speech = SpeechInput(app)
    private val engine = TaskEngine(viewModelScope)

    private val _installed = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installed: StateFlow<List<InstalledApp>> = _installed

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<@JvmSuppressWildcards Boolean> = _listening

    private val _confirmation = MutableStateFlow<String?>(null)
    val confirmation: StateFlow<String?> = _confirmation

    private var lastPlan: AgentPlan? = null

    val status = AgentBus.status
    val plan = AgentBus.plan
    val currentStep = AgentBus.currentStep
    val log = AgentBus.log
    val chat = AgentBus.chat
    val allowed = appsStore.allowed

    fun refreshApps() {
        _installed.value = appsStore.installedApps()
    }

    fun setInput(value: String) { _input.value = value }

    fun toggleApp(pkg: String) {
        appsStore.toggle(pkg)
        refreshApps()
    }

    val accessibilityEnabled: Boolean get() = AgentAccessibilityService.isRunning

    /** Understand the command and build a plan (AI layer). */
    fun planCommand(command: String = _input.value) {
        val text = command.trim()
        if (text.isEmpty()) return
        AgentBus.say(text, fromUser = true)
        _input.value = ""
        AgentBus.setStatus(TaskStatus.PLANNING)

        viewModelScope.launch {
            try {
                val plan = PlannerClient.plan(
                    command = text,
                    allowedApps = appsStore.allowed.value.toList(),
                    device = "${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE}",
                )
                lastPlan = plan
                AgentBus.setPlan(plan)
                AgentBus.setStatus(TaskStatus.IDLE)
                AgentBus.say(plan.summary.ifBlank { plan.goal })
                AgentBus.log("تم إنشاء خطة", "${plan.steps.size} خطوة")
            } catch (e: PlannerException) {
                AgentBus.setStatus(TaskStatus.ERROR)
                val message = when (e.status) {
                    402 -> "انتهى رصيد الذكاء الاصطناعي. أضف رصيداً للمتابعة."
                    403 -> "الوصول إلى الذكاء الاصطناعي محجوب لهذه المساحة."
                    429 -> "الطلبات كثيرة، أعد المحاولة بعد قليل."
                    else -> "تعذّر إنشاء الخطة: ${e.message}"
                }
                AgentBus.say(message)
                AgentBus.log("فشل التخطيط", message, ok = false)
            } catch (e: Exception) {
                AgentBus.setStatus(TaskStatus.ERROR)
                AgentBus.say("تعذّر الاتصال بطبقة الذكاء: ${e.message}")
            }
        }
    }

    /** Start executing the current plan (Task Engine). */
    fun start(confirmed: Boolean = false) {
        val plan = lastPlan ?: AgentBus.plan.value ?: run {
            AgentBus.say("لا توجد خطة بعد. أخبرني بما تريد تنفيذه.")
            return
        }
        if (!accessibilityEnabled) {
            AgentBus.say("فعّل خدمة الوصول أولاً من الإعدادات لكي أتمكن من التنفيذ.")
            return
        }
        _confirmation.value = null
        engine.run(plan, appsStore.allowed.value, confirmed) { question ->
            _confirmation.value = question
        }
    }

    fun pause() = AgentBus.pause()
    fun resume() {
        AgentBus.resume()
    }

    fun stop() {
        AgentBus.stop()
        engine.cancel()
    }

    fun emergencyStop() {
        AgentBus.emergencyStop()
        engine.cancel()
        speech.stop()
        _listening.value = false
    }

    fun confirm() {
        _confirmation.value = null
        AgentBus.ask(null)
        start(confirmed = true)
    }

    fun dismissConfirmation() {
        _confirmation.value = null
        AgentBus.ask(null)
        AgentBus.setStatus(TaskStatus.IDLE)
    }

    fun toggleMic() {
        if (_listening.value) {
            speech.stop()
            _listening.value = false
            return
        }
        if (!speech.isAvailable) {
            AgentBus.say("التعرف على الصوت غير متاح على هذا الجهاز.")
            return
        }
        _listening.value = true
        speech.start(
            onPartial = { _input.value = it },
            onResult = {
                _input.value = it
                _listening.value = false
                planCommand(it)
            },
            onError = {
                _listening.value = false
                AgentBus.say(it)
            },
        )
    }

    fun clearLog() = AgentBus.clearLog()
}
