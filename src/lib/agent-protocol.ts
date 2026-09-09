/**
 * AI Phone Agent — shared protocol between the AI layer (this server),
 * the web console, and the native Android agent.
 *
 * Everything here is client-safe (no secrets, no server-only imports).
 */

export const ACTIONS = [
  "open_app", // launch an app (appPackage or target = app label)
  "tap_text", // tap a node whose text/description matches `target`
  "tap_id", // tap a node by viewId resource name
  "long_press_text",
  "type_text", // focus a text field (target) and type `text`
  "clear_text",
  "press_back",
  "press_home",
  "press_enter",
  "open_notifications",
  "scroll", // direction: up/down/left/right on scrollable container
  "swipe", // raw directional swipe gesture
  "tap_point", // normalized x/y fallback (0..1) — only when no node is available
  "wait",
  "assert_text", // verification step: text must be present on screen
  "read_screen", // capture accessibility tree + OCR, feed back to the AI layer
  "launch_url", // Intent VIEW
  "ask_user", // pause and ask for confirmation / missing info
  "repeat_until", // loop the previous block until durationMs elapses
  "finish",
] as const;

export type AgentAction = (typeof ACTIONS)[number];

export const DIRECTIONS = ["up", "down", "left", "right"] as const;

export interface AgentStep {
  id: number;
  action: AgentAction;
  description: string;
  target: string | null;
  text: string | null;
  appPackage: string | null;
  direction: (typeof DIRECTIONS)[number] | null;
  durationMs: number | null;
  x: number | null;
  y: number | null;
  verify: string | null;
  dangerous: boolean;
}

export interface AgentPlan {
  goal: string;
  app: string | null;
  appPackage: string | null;
  summary: string;
  needsConfirmation: boolean;
  riskNotes: string | null;
  steps: AgentStep[];
}

export interface ScreenNode {
  text?: string | null;
  desc?: string | null;
  viewId?: string | null;
  className?: string | null;
  clickable?: boolean;
  editable?: boolean;
  scrollable?: boolean;
  bounds?: string | null;
}

export interface ScreenSnapshot {
  packageName?: string | null;
  activity?: string | null;
  nodes?: ScreenNode[];
  ocrText?: string | null;
}

export interface DecideResult {
  step: AgentStep;
  reasoning: string;
  done: boolean;
}

const stepSchema = {
  type: "object",
  additionalProperties: false,
  properties: {
    id: { type: "integer" },
    action: { type: "string", enum: [...ACTIONS] },
    description: { type: "string" },
    target: { type: ["string", "null"] },
    text: { type: ["string", "null"] },
    appPackage: { type: ["string", "null"] },
    direction: { type: ["string", "null"], enum: [...DIRECTIONS, null] },
    durationMs: { type: ["integer", "null"] },
    x: { type: ["number", "null"] },
    y: { type: ["number", "null"] },
    verify: { type: ["string", "null"] },
    dangerous: { type: "boolean" },
  },
  required: [
    "id",
    "action",
    "description",
    "target",
    "text",
    "appPackage",
    "direction",
    "durationMs",
    "x",
    "y",
    "verify",
    "dangerous",
  ],
} as const;

export const PLAN_JSON_SCHEMA = {
  type: "object",
  additionalProperties: false,
  properties: {
    goal: { type: "string" },
    app: { type: ["string", "null"] },
    appPackage: { type: ["string", "null"] },
    summary: { type: "string" },
    needsConfirmation: { type: "boolean" },
    riskNotes: { type: ["string", "null"] },
    steps: { type: "array", items: stepSchema },
  },
  required: ["goal", "app", "appPackage", "summary", "needsConfirmation", "riskNotes", "steps"],
} as const;

export const DECIDE_JSON_SCHEMA = {
  type: "object",
  additionalProperties: false,
  properties: {
    step: stepSchema,
    reasoning: { type: "string" },
    done: { type: "boolean" },
  },
  required: ["step", "reasoning", "done"],
} as const;

export const PLANNER_SYSTEM_PROMPT = `أنت طبقة التخطيط في "AI Phone Agent"، وكيل ذكاء اصطناعي عام يعمل على أندرويد عبر AccessibilityService.
مهمتك: تحويل أمر المستخدم بالعربية أو الإنجليزية إلى خطة خطوات قابلة للتنفيذ آلياً على الهاتف.

قواعد صارمة:
- استخدم فقط الإجراءات المتاحة في المخطط (schema). لا تخترع إجراءات.
- اعتمد على النص أو الوصف الظاهر على الشاشة (tap_text / tap_id) ولا تستخدم tap_point إلا كحل أخير.
- أضف خطوة assert_text أو read_screen للتحقق بعد الخطوات الحساسة (الإرسال، الدفع، الحذف، تسجيل الدخول).
- ضع dangerous=true على أي خطوة قد ترسل رسالة، تنشر محتوى، تدفع مالاً، تحذف بيانات، أو تغيّر إعدادات أمان، واضبط needsConfirmation=true للخطة.
- إذا كانت معلومة ناقصة (اسم جهة الاتصال، نص الرسالة) استخدم إجراء ask_user بدل التخمين.
- لا تقترح أي شيء يتجاوز صلاحيات أندرويد أو يعطّل حماية الجهاز أو يخفي الإجراءات عن المستخدم؛ ارفض ذلك بخطة تحتوي خطوة ask_user توضح السبب.
- الوصف والملخص بالعربية، مختصر وواضح.
- appPackage: استخدم اسم الحزمة الحقيقي إن كنت متأكداً (مثال com.whatsapp، com.google.android.youtube، com.zhiliaoapp.musically، com.android.settings)، وإلا اتركه null واستخدم target باسم التطبيق.
- للمهام المستمرة (مشاهدة فيديوهات لمدة معينة، اللعب) استخدم خطوات متكررة ثم repeat_until مع durationMs.
- أنهِ الخطة دائماً بخطوة finish.`;

export const DECIDER_SYSTEM_PROMPT = `أنت طبقة القرار في "AI Phone Agent". تستلم الهدف، الخطوة المخطط لها، ولقطة من حالة الشاشة الحالية (عناصر Accessibility + نص OCR).
اختر الإجراء الواحد التالي الذي يقرّب الجهاز من الهدف، معتمداً على العناصر الموجودة فعلاً في اللقطة.
- لا تعتمد على إحداثيات ثابتة إذا وُجد عنصر بنص أو وصف مناسب.
- إذا كان الهدف قد تحقق، اضبط done=true مع إجراء finish.
- إذا كانت الشاشة غير متوقعة، اختر إجراء تصحيحي (press_back، scroll، read_screen) واشرح السبب في reasoning.
- الشرح بالعربية.`;
