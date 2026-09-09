package com.aiphoneagent.agent

import com.aiphoneagent.core.AgentBus
import com.aiphoneagent.core.AgentPlan
import com.aiphoneagent.core.AgentStep
import com.aiphoneagent.core.SafetyGuard
import com.aiphoneagent.core.TaskStatus
import com.aiphoneagent.net.PlannerClient
import com.aiphoneagent.net.PlannerException
import com.aiphoneagent.service.AgentAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Task Engine: executes a plan step by step, verifies the screen after every
 * step, retries, and asks the AI layer for a corrective action when the screen
 * does not match expectations. Honors Pause / Stop / Emergency at every step.
 */
class TaskEngine(private val scope: CoroutineScope) {

    private var job: Job? = null

    fun isBusy() = job?.isActive == true

    fun cancel() {
        job?.cancel()
        job = null
    }

    fun run(
        plan: AgentPlan,
        allowedPackages: Set<String>,
        confirmed: Boolean,
        onNeedsConfirmation: (String) -> Unit,
    ) {
        if (isBusy()) {
            AgentBus.log("مهمة أخرى تعمل بالفعل", ok = false)
            return
        }
        SafetyGuard.rejectionReason(plan)?.let {
            AgentBus.setStatus(TaskStatus.ERROR)
            AgentBus.say(it)
            AgentBus.log("خطة مرفوضة", it, ok = false)
            return
        }
        if (plan.needsConfirmation && !confirmed) {
            AgentBus.setStatus(TaskStatus.AWAITING_CONFIRMATION)
            onNeedsConfirmation(plan.riskNotes ?: "تحتوي الخطة على خطوات حساسة. هل أتابع؟")
            return
        }

        job = scope.launch { execute(plan, allowedPackages, onNeedsConfirmation) }
    }

    private suspend fun execute(
        plan: AgentPlan,
        allowedPackages: Set<String>,
        onNeedsConfirmation: (String) -> Unit,
    ) {
        val service = AgentAccessibilityService.instance
        if (service == null) {
            AgentBus.setStatus(TaskStatus.ERROR)
            AgentBus.say("خدمة الوصول غير مُفعّلة. من فضلك فعّلها من الإعدادات أولاً.")
            return
        }

        AgentBus.start()
        AgentBus.setStatus(TaskStatus.RUNNING)
        AgentBus.setPlan(plan)
        val history = mutableListOf<String>()

        var index = 0
        var repeatUntil = 0L
        var guard = 0

        while (index < plan.steps.size && guard < MAX_ACTIONS) {
            guard++
            if (!waitWhilePaused()) return

            var step = plan.steps[index]
            AgentBus.setCurrentStep(step)

            if (step.action == "repeat_until") {
                val deadline = System.currentTimeMillis() + (step.durationMs ?: 60_000L)
                if (repeatUntil == 0L) repeatUntil = deadline
                if (System.currentTimeMillis() < repeatUntil) {
                    index = 0 // replay the block
                    AgentBus.log("تكرار الخطوات حتى انتهاء المدة")
                    continue
                }
                repeatUntil = 0L
                index++
                continue
            }

            if (SafetyGuard.needsConfirmation(step) && !plan.needsConfirmation) {
                AgentBus.setStatus(TaskStatus.AWAITING_CONFIRMATION)
                onNeedsConfirmation("خطوة حساسة: ${step.description}")
                return
            }

            var result = ActionExecutor.execute(service, step, allowedPackages)
            AgentBus.log(step.description.ifBlank { step.action }, result.detail, result.ok)

            // Retry / self-correct via the AI decision layer using the live screen.
            var attempt = 0
            while (!result.ok && result.askUser == null && attempt < MAX_RETRIES) {
                attempt++
                delay(700)
                val snapshot = ScreenReader.snapshot(service)
                val corrective = runCatching {
                    PlannerClient.decide(plan.goal, step, history, snapshot)
                }.getOrElse { error ->
                    val message = if (error is PlannerException) error.message else "تعذّر الاتصال بطبقة الذكاء"
                    AgentBus.log("فشل طلب التصحيح", message ?: "", ok = false)
                    null
                } ?: break

                AgentBus.log("تصحيح تلقائي", corrective.reasoning, ok = true)
                if (corrective.done) {
                    finish(plan)
                    return
                }
                step = corrective.step
                AgentBus.setCurrentStep(step)
                result = ActionExecutor.execute(service, step, allowedPackages)
                AgentBus.log(step.description.ifBlank { step.action }, result.detail, result.ok)
            }

            if (result.askUser != null) {
                AgentBus.setStatus(TaskStatus.AWAITING_CONFIRMATION)
                AgentBus.ask(result.askUser)
                onNeedsConfirmation(result.askUser)
                return
            }

            if (!result.ok) {
                AgentBus.setStatus(TaskStatus.ERROR)
                AgentBus.say("توقفت عند: ${step.description} — ${result.detail}")
                AgentBus.setCurrentStep(null)
                return
            }

            history += "${step.action}: ${step.description}"
            delay(600) // let the UI settle before observing

            // Post-step verification
            step.verify?.takeIf { it.isNotBlank() }?.let { expected ->
                if (!ScreenReader.containsText(service, expected)) {
                    AgentBus.log("تحقق غير ناجح", "لم يظهر «$expected»", ok = false)
                }
            }

            if (step.action == "finish") {
                finish(plan)
                return
            }
            index++
        }

        finish(plan)
    }

    private fun finish(plan: AgentPlan) {
        AgentBus.setCurrentStep(null)
        AgentBus.setStatus(TaskStatus.DONE)
        AgentBus.log("اكتملت المهمة", plan.goal)
        AgentBus.say("تم تنفيذ المهمة: ${plan.summary.ifBlank { plan.goal }}")
    }

    /** @return false when the task must abort (stop / emergency). */
    private suspend fun waitWhilePaused(): Boolean {
        while (AgentBus.paused && !AgentBus.stopRequested && !AgentBus.emergency) delay(250)
        if (AgentBus.stopRequested || AgentBus.emergency) {
            AgentBus.setCurrentStep(null)
            return false
        }
        return true
    }

    private companion object {
        const val MAX_RETRIES = 2
        const val MAX_ACTIONS = 400
    }
}
