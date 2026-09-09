package com.aiphoneagent

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiphoneagent.agent.AppLauncher
import com.aiphoneagent.core.TaskStatus
import com.aiphoneagent.ui.AgentViewModel
import com.aiphoneagent.vision.ScreenCaptureService

private val Bg = Color(0xFF0B1220)
private val Card1 = Color(0xFF121B2E)
private val Accent = Color(0xFF2DD4BF)
private val Warn = Color(0xFFF59E0B)
private val Danger = Color(0xFFEF4444)
private val TextMain = Color(0xFFE8EEF9)
private val TextDim = Color(0xFF93A3BC)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent,
                    background = Bg,
                    surface = Card1,
                    onPrimary = Color(0xFF04211D),
                    onBackground = TextMain,
                    onSurface = TextMain,
                ),
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AgentApp()
                }
            }
        }
    }
}

@Composable
private fun AgentApp(vm: AgentViewModel = viewModel()) {
    val context = LocalContext.current
    val status by vm.status.collectAsStateWithLifecycle()
    val plan by vm.plan.collectAsStateWithLifecycle()
    val currentStep by vm.currentStep.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()
    val chat by vm.chat.collectAsStateWithLifecycle()
    val input by vm.input.collectAsStateWithLifecycle()
    val listening by vm.listening.collectAsStateWithLifecycle()
    val confirmation by vm.confirmation.collectAsStateWithLifecycle()
    val installed by vm.installed.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.toggleMic()
    }
    val captureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode != 0 && data != null) ScreenCaptureService.start(context, result.resultCode, data)
    }

    LaunchedEffect(Unit) { vm.refreshApps() }

    Scaffold(
        containerColor = Bg,
        topBar = {
            Column(
                Modifier
                    .background(Brush.horizontalGradient(listOf(Color(0xFF0E1A31), Color(0xFF0B1220))))
                    .padding(16.dp),
            ) {
                Text("وكيل الهاتف الذكي", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "أخبره بما تريد، وسيخطط وينفذ على جهازك داخل التطبيقات المسموح بها فقط.",
                    color = TextDim,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(10.dp))
                StatusRow(status, currentStep?.description)
            }
        },
        bottomBar = {
            Column(Modifier.background(Card1).padding(12.dp)) {
                ControlBar(status, vm)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = vm::setInput,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("مثال: افتح يوتيوب وابحث عن فيديو عن الفضاء وشغّله", color = TextDim) },
                        maxLines = 3,
                        keyboardActions = KeyboardActions(onDone = { vm.planCommand() }),
                    )
                    IconButton(onClick = {
                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }) {
                        Icon(
                            if (listening) Icons.Filled.MicOff else Icons.Filled.Mic,
                            contentDescription = "أمر صوتي",
                            tint = if (listening) Danger else Accent,
                        )
                    }
                    Button(onClick = { vm.planCommand() }, shape = RoundedCornerShape(14.dp)) {
                        Text("تنفيذ")
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            TabRow(selectedTabIndex = tab, containerColor = Bg, contentColor = Accent) {
                listOf("المحادثة", "الخطة", "السجل", "الإعدادات").forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                }
            }
            when (tab) {
                0 -> LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
                    items(chat) { message ->
                        Surface(
                            color = if (message.fromUser) Accent.copy(alpha = 0.14f) else Card1,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Text(message.text, color = TextMain, modifier = Modifier.padding(12.dp))
                        }
                    }
                }

                1 -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
                    val p = plan
                    if (p == null) {
                        Text("لا توجد خطة بعد.", color = TextDim)
                    } else {
                        Text(p.goal, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(p.summary, color = TextDim, fontSize = 13.sp)
                        p.riskNotes?.let { Text(it, color = Warn, fontSize = 13.sp) }
                        Spacer(Modifier.height(10.dp))
                        p.steps.forEach { step ->
                            Surface(
                                color = if (currentStep?.id == step.id) Accent.copy(alpha = 0.18f) else Card1,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("${step.id}. ${step.description}", color = TextMain)
                                    Text(
                                        buildString {
                                            append(step.action)
                                            step.target?.let { append(" • $it") }
                                            if (step.dangerous) append(" • حساسة")
                                        },
                                        color = if (step.dangerous) Warn else TextDim,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    }
                }

                2 -> LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
                    items(log.reversed()) { entry ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Icon(
                                if (entry.ok) Icons.Filled.CheckCircle else Icons.Filled.Error,
                                contentDescription = null,
                                tint = if (entry.ok) Accent else Danger,
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(entry.label, color = TextMain, fontSize = 14.sp)
                                if (entry.detail.isNotBlank()) {
                                    Text(entry.detail, color = TextDim, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
                    SettingsCard(
                        title = "خدمة الوصول (Accessibility)",
                        body = if (vm.accessibilityEnabled) "مُفعّلة — الوكيل قادر على القراءة والتنفيذ." else "غير مُفعّلة — لن يستطيع الوكيل التنفيذ.",
                        action = "فتح إعدادات الوصول",
                        onAction = { AppLauncher.openAccessibilitySettings(context) },
                    )
                    SettingsCard(
                        title = "تحليل الشاشة (OCR)",
                        body = "اختياري، يُستخدم في الألعاب والواجهات التي لا تعرض عناصر قابلة للقراءة.",
                        action = "تشغيل التحليل",
                        onAction = {
                            val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                            captureLauncher.launch(manager.createScreenCaptureIntent())
                        },
                        secondary = "إيقاف" to { ScreenCaptureService.stop(context) },
                    )
                    Text("التطبيقات المسموح بها", color = TextMain, fontWeight = FontWeight.Bold)
                    Text("الوكيل لا يتحكم إلا بما تختاره هنا.", color = TextDim, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    installed.forEach { app ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(app.label, color = TextMain, fontSize = 14.sp)
                                Text(app.packageName, color = TextDim, fontSize = 11.sp)
                            }
                            Switch(checked = app.allowed, onCheckedChange = { vm.toggleApp(app.packageName) })
                        }
                    }
                }
            }
        }
    }

    confirmation?.let { question ->
        AlertDialog(
            onDismissRequest = vm::dismissConfirmation,
            title = { Text("تأكيد مطلوب") },
            text = { Text(question) },
            confirmButton = { TextButton(onClick = vm::confirm) { Text("أتابع") } },
            dismissButton = { TextButton(onClick = vm::dismissConfirmation) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun StatusRow(status: TaskStatus, step: String?) {
    val label = when (status) {
        TaskStatus.IDLE -> "جاهز"
        TaskStatus.PLANNING -> "يخطط…"
        TaskStatus.AWAITING_CONFIRMATION -> "بانتظار تأكيدك"
        TaskStatus.RUNNING -> "ينفّذ"
        TaskStatus.PAUSED -> "متوقف مؤقتاً"
        TaskStatus.STOPPED -> "متوقف"
        TaskStatus.DONE -> "اكتملت"
        TaskStatus.ERROR -> "خطأ"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = Accent.copy(alpha = 0.18f), shape = RoundedCornerShape(20.dp)) {
            Text(label, color = Accent, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(step ?: "لا خطوة جارية", color = TextDim, fontSize = 12.sp)
    }
}

@Composable
private fun ControlBar(status: TaskStatus, vm: AgentViewModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { if (status == TaskStatus.PAUSED) vm.resume() else vm.start() },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
        ) { Text(if (status == TaskStatus.PAUSED) "استئناف" else "بدء") }

        OutlinedButton(onClick = vm::pause, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
            Text("إيقاف مؤقت")
        }
        OutlinedButton(onClick = vm::stop, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
            Text("إيقاف")
        }
        Button(
            onClick = vm::emergencyStop,
            colors = ButtonDefaults.buttonColors(containerColor = Danger),
            shape = RoundedCornerShape(12.dp),
        ) { Text("طارئ") }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    secondary: Pair<String, () -> Unit>? = null,
) {
    Surface(color = Card1, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = TextMain, fontWeight = FontWeight.Bold)
            Text(body, color = TextDim, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAction, shape = RoundedCornerShape(12.dp)) { Text(action) }
                secondary?.let { (label, onClick) ->
                    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(12.dp)) { Text(label) }
                }
            }
        }
    }
}
