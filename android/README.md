# AI Phone Agent — Android (Kotlin)

وكيل ذكاء اصطناعي عام لأندرويد: يفهم أوامر المستخدم بالعربية/الإنجليزية، يحوّلها إلى خطة خطوات،
ثم ينفّذها على الجهاز عبر `AccessibilityService` داخل التطبيقات التي يسمح بها المستخدم فقط.

> هذا المجلد مشروع Android Studio كامل ومنفصل عن تطبيق الويب في `src/`.
> Lovable لا يبني APK؛ افتح مجلد `android/` في Android Studio ثم `Build > Build APK(s)`.

## البنية

| الطبقة | الملفات |
| --- | --- |
| AI Layer (على الخادم) | `../src/lib/agent-planner.server.ts`, `../src/routes/api/public/agent/*` |
| بروتوكول مشترك | `../src/lib/agent-protocol.ts` ⇄ `core/Models.kt` |
| عميل الذكاء | `net/PlannerClient.kt` |
| Vision Layer | `agent/ScreenReader.kt`, `vision/OcrEngine.kt`, `vision/ScreenCaptureService.kt` |
| Android Agent | `service/AgentAccessibilityService.kt`, `agent/GestureController.kt`, `agent/AppLauncher.kt`, `agent/ActionExecutor.kt` |
| Task Engine | `agent/TaskEngine.kt` (تنفيذ + تحقق + إعادة محاولة + تصحيح ذاتي) |
| التحكم والأمان | `core/AgentBus.kt` (Start/Pause/Stop/Emergency), `core/SafetyGuard.kt`, `data/AllowedAppsStore.kt` |
| UI | `MainActivity.kt`, `ui/AgentViewModel.kt`, `speech/SpeechInput.kt` |

## الإعداد

1. انشر مشروع Lovable هذا (زر Publish) لتحصل على رابط طبقة الذكاء.
2. في `app/build.gradle.kts` عدّل:
   ```kotlin
   buildConfigField("String", "AGENT_API_BASE", "\"https://<your-project>.lovable.app\"")
   buildConfigField("String", "AGENT_API_TOKEN", "\"<optional-token>\"")
   ```
   إن استخدمت رمزاً، أضف نفس القيمة كسر باسم `AGENT_API_TOKEN` في إعدادات المشروع؛ عند غيابه تبقى النقاط مفتوحة.
3. `./gradlew :app:assembleDebug` أو من Android Studio.
4. على الهاتف: فعّل خدمة الوصول من داخل التطبيق (تبويب الإعدادات)، واختر التطبيقات المسموح بها، وشغّل تحليل الشاشة (OCR) عند الحاجة.

## نقاط التكامل مع الخادم

- `POST /api/public/agent/plan` → `{ command, allowedApps, device, screen? }` ⇒ `{ plan }`
- `POST /api/public/agent/decide` → `{ goal, plannedStep, history, screen }` ⇒ `{ decision }`

## الأمان

- لا يعمل الوكيل إلا بعد موافقة صريحة على Accessibility، ولا يتحكم إلا بالتطبيقات المفعّلة في القائمة.
- كل خطوة حساسة (إرسال، دفع، حذف، إعدادات أمان) تتطلب تأكيداً من المستخدم.
- زر إيقاف طارئ يقطع التنفيذ فوراً، وفصل خدمة الوصول يوقف الوكيل تلقائياً.
- لا يوجد أي تجاوز لصلاحيات أندرويد، ولا root، ولا إخفاء للإجراءات عن المستخدم.
- الإجراءات تُختار من عناصر الشاشة (نص/وصف/معرّف) ولا تعتمد على إحداثيات ثابتة إلا كحل أخير.

## التوسعة

أضف إجراءً جديداً في ثلاث نقاط فقط: `ACTIONS` في `agent-protocol.ts`، ثم `ActionExecutor.execute`، ثم وصفه في تعليمات المخطط.
