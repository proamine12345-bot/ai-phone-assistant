import { createFileRoute } from "@tanstack/react-router";
import { useServerFn } from "@tanstack/react-start";
import {
  AlertTriangle,
  Bot,
  CheckCircle2,
  ChevronsLeft,
  CircleStop,
  Eye,
  Mic,
  MicOff,
  Pause,
  Play,
  Send,
  ShieldCheck,
  Smartphone,
  Sparkles,
  Wrench,
} from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";

import type { AgentPlan, AgentStep } from "@/lib/agent-protocol";
import { planCommand } from "@/lib/agent.functions";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "AI Phone Agent — وكيل هاتف ذكي ينفّذ أوامرك" },
      {
        name: "description",
        content:
          "وكيل ذكاء اصطناعي عام لأندرويد: يفهم أوامرك بالعربية، يحولها إلى خطة خطوات، وينفذها داخل التطبيقات التي تسمح بها.",
      },
      { property: "og:title", content: "AI Phone Agent — وكيل هاتف ذكي ينفّذ أوامرك" },
      {
        property: "og:description",
        content: "خطط وأوامر طبيعية تُترجم إلى إجراءات حقيقية على هاتف أندرويد عبر AccessibilityService.",
      },
    ],
  }),
  component: Console,
});

type Status = "idle" | "planning" | "ready" | "running" | "paused" | "stopped" | "done" | "error";

interface ChatMessage {
  id: number;
  from: "user" | "agent";
  text: string;
}

interface LogEntry {
  id: number;
  label: string;
  detail?: string | undefined;
  ok: boolean;
}

const DEFAULT_APPS = [
  { label: "WhatsApp", pkg: "com.whatsapp" },
  { label: "YouTube", pkg: "com.google.android.youtube" },
  { label: "TikTok", pkg: "com.zhiliaoapp.musically" },
  { label: "الإعدادات", pkg: "com.android.settings" },
  { label: "الهاتف", pkg: "com.android.dialer" },
  { label: "Chrome", pkg: "com.android.chrome" },
];

const EXAMPLES = [
  "افتح WhatsApp وأرسل رسالة إلى أحمد تقول: سأصل بعد قليل.",
  "افتح YouTube وابحث عن فيديو عن الفضاء وشغّله.",
  "افتح TikTok وشاهد الفيديوهات لمدة 10 دقائق وانتقل للفيديو التالي.",
  "افتح الإعدادات وانتقل إلى قسم الشبكة والإنترنت.",
];

const STATUS_LABEL: Record<Status, string> = {
  idle: "جاهز",
  planning: "يفهم ويخطط…",
  ready: "الخطة جاهزة",
  running: "قيد التنفيذ",
  paused: "متوقف مؤقتاً",
  stopped: "متوقف",
  done: "اكتملت",
  error: "خطأ",
};

function Console() {
  const plan = useServerFn(planCommand);

  const [command, setCommand] = useState("");
  const [status, setStatus] = useState<Status>("idle");
  const [currentPlan, setCurrentPlan] = useState<AgentPlan | null>(null);
  const [stepIndex, setStepIndex] = useState(-1);
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      id: 1,
      from: "agent",
      text: "أهلاً. اكتب أو انطق ما تريد تنفيذه على هاتفك، وسأحوّله إلى خطة خطوات قابلة للتنفيذ عبر تطبيق الأندرويد.",
    },
  ]);
  const [log, setLog] = useState<LogEntry[]>([]);
  const [allowed, setAllowed] = useState<string[]>(["com.google.android.youtube", "com.android.settings"]);
  const [listening, setListening] = useState(false);
  const [confirmed, setConfirmed] = useState(false);

  const counter = useRef(2);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const recognition = useRef<{ stop: () => void } | null>(null);

  const nextId = () => ++counter.current;

  const say = useCallback((text: string, from: "user" | "agent" = "agent") => {
    setMessages((prev) => [...prev, { id: nextId(), from, text }]);
  }, []);

  const addLog = useCallback((label: string, detail?: string, ok = true) => {
    setLog((prev) => [{ id: nextId(), label, detail, ok }, ...prev].slice(0, 60));
  }, []);

  const toggleApp = (pkg: string) =>
    setAllowed((prev) => (prev.includes(pkg) ? prev.filter((p) => p !== pkg) : [...prev, pkg]));

  const submit = async (text: string) => {
    const value = text.trim();
    if (!value || status === "planning") return;
    say(value, "user");
    setCommand("");
    setStatus("planning");
    setCurrentPlan(null);
    setStepIndex(-1);
    setConfirmed(false);
    addLog("إرسال الأمر إلى طبقة الذكاء", value);

    try {
      const result = await plan({ data: { command: value, allowedApps: allowed } });
      setCurrentPlan(result);
      setStatus("ready");
      say(result.summary || result.goal);
      addLog("تم إنشاء الخطة", `${result.steps.length} خطوة`);
      if (result.needsConfirmation) {
        addLog("الخطة تحتوي خطوات حساسة", result.riskNotes ?? undefined, false);
      }
    } catch (error) {
      setStatus("error");
      const message = error instanceof Error ? error.message : "تعذّر إنشاء الخطة";
      say(`تعذّر إنشاء الخطة: ${message}`);
      addLog("فشل التخطيط", message, false);
    }
  };

  // Plan walkthrough (the real execution happens on the phone via the Android agent)
  useEffect(() => {
    if (status !== "running" || !currentPlan) return;
    if (stepIndex >= currentPlan.steps.length - 1) {
      setStatus("done");
      addLog("انتهت معاينة الخطة", currentPlan.goal);
      return;
    }
    timer.current = setTimeout(() => {
      const next = stepIndex + 1;
      const step = currentPlan.steps[next];
      setStepIndex(next);
      if (step) addLog(step.description || step.action, step.target ?? undefined, !step.dangerous);
    }, 900);
    return () => {
      if (timer.current) clearTimeout(timer.current);
    };
  }, [status, stepIndex, currentPlan, addLog]);

  const start = () => {
    if (!currentPlan) return;
    if (currentPlan.needsConfirmation && !confirmed) return;
    setStatus("running");
  };

  const emergencyStop = () => {
    if (timer.current) clearTimeout(timer.current);
    setStatus("stopped");
    setStepIndex(-1);
    addLog("إيقاف طارئ — أُلغيت كل الإجراءات", undefined, false);
  };

  const toggleMic = () => {
    if (listening) {
      recognition.current?.stop();
      setListening(false);
      return;
    }
    const w = window as unknown as {
      SpeechRecognition?: new () => SpeechRecognitionLike;
      webkitSpeechRecognition?: new () => SpeechRecognitionLike;
    };
    const Ctor = w.SpeechRecognition ?? w.webkitSpeechRecognition;
    if (!Ctor) {
      say("التعرّف على الصوت غير مدعوم في هذا المتصفح. استخدم زر الميكروفون داخل تطبيق الأندرويد.");
      return;
    }
    const rec = new Ctor();
    rec.lang = "ar-SA";
    rec.interimResults = true;
    rec.onresult = (event) => {
      const transcript = Array.from(event.results)
        .map((r) => r[0]?.transcript ?? "")
        .join(" ");
      setCommand(transcript);
      if (event.results[event.results.length - 1]?.isFinal) {
        setListening(false);
        void submit(transcript);
      }
    };
    rec.onerror = () => setListening(false);
    rec.onend = () => setListening(false);
    rec.start();
    recognition.current = rec;
    setListening(true);
  };

  const currentStep: AgentStep | null =
    currentPlan && stepIndex >= 0 ? (currentPlan.steps[stepIndex] ?? null) : null;

  return (
    <main
      className="min-h-screen text-foreground"
      style={{ background: "var(--gradient-hero)" }}
    >
      <div className="mx-auto w-full max-w-6xl px-4 pb-16 pt-8 md:px-8">
        <header className="mb-8">
          <div className="flex flex-wrap items-center gap-3">
            <span
              className="flex h-11 w-11 items-center justify-center rounded-2xl text-primary-foreground"
              style={{ background: "var(--gradient-accent)" }}
            >
              <Bot className="h-6 w-6" />
            </span>
            <div>
              <h1 className="text-2xl font-black tracking-tight md:text-3xl">AI Phone Agent</h1>
              <p className="text-sm text-muted-foreground">
                وكيل هاتف عام: يفهم أوامرك بالعربية، يخطّط، وينفّذ على أندرويد داخل التطبيقات المسموح بها فقط.
              </p>
            </div>
          </div>

          <div className="mt-5 flex flex-wrap items-center gap-2 text-xs">
            <Badge tone="primary">
              <Sparkles className="h-3.5 w-3.5" /> {STATUS_LABEL[status]}
            </Badge>
            <Badge>
              <Smartphone className="h-3.5 w-3.5" />
              {currentStep ? currentStep.description : "لا خطوة جارية"}
            </Badge>
            <Badge>
              <ShieldCheck className="h-3.5 w-3.5" />
              {allowed.length} تطبيق مسموح
            </Badge>
          </div>
        </header>

        <div className="grid gap-5 lg:grid-cols-[1.15fr_0.85fr]">
          {/* المحادثة */}
          <section className="rounded-3xl border border-border bg-card/70 p-5 backdrop-blur" style={{ boxShadow: "var(--shadow-panel)" }}>
            <h2 className="mb-4 flex items-center gap-2 text-lg font-bold">
              <ChevronsLeft className="h-5 w-5 text-primary" /> المحادثة مع الوكيل
            </h2>

            <div className="mb-4 max-h-[320px] space-y-3 overflow-y-auto pl-1">
              {messages.map((m) => (
                <div key={m.id} className={m.from === "user" ? "flex justify-start" : ""}>
                  <p
                    className={
                      m.from === "user"
                        ? "max-w-[85%] rounded-2xl bg-primary px-4 py-2 text-sm leading-relaxed text-primary-foreground"
                        : "max-w-[95%] text-sm leading-relaxed text-foreground"
                    }
                  >
                    {m.text}
                  </p>
                </div>
              ))}
            </div>

            <div className="rounded-2xl border border-input bg-background/40 p-3">
              <textarea
                value={command}
                onChange={(e) => setCommand(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter" && !e.shiftKey) {
                    e.preventDefault();
                    void submit(command);
                  }
                }}
                rows={3}
                placeholder="مثال: افتح YouTube وابحث عن فيديو عن الفضاء وشغّله"
                className="w-full resize-none bg-transparent text-sm outline-none placeholder:text-muted-foreground"
              />
              <div className="mt-2 flex items-center justify-between gap-2">
                <button
                  type="button"
                  onClick={toggleMic}
                  aria-label="أمر صوتي"
                  className={`flex h-10 w-10 items-center justify-center rounded-xl border border-border transition-colors ${
                    listening ? "bg-destructive text-destructive-foreground" : "hover:bg-accent"
                  }`}
                >
                  {listening ? <MicOff className="h-4 w-4" /> : <Mic className="h-4 w-4" />}
                </button>
                <button
                  type="button"
                  onClick={() => void submit(command)}
                  disabled={status === "planning" || !command.trim()}
                  className="flex h-10 items-center gap-2 rounded-xl px-5 text-sm font-bold text-primary-foreground transition-opacity disabled:opacity-40"
                  style={{ background: "var(--gradient-accent)" }}
                >
                  <Send className="h-4 w-4" />
                  {status === "planning" ? "يخطط…" : "تنفيذ"}
                </button>
              </div>
            </div>

            <div className="mt-4 flex flex-wrap gap-2">
              {EXAMPLES.map((example) => (
                <button
                  key={example}
                  type="button"
                  onClick={() => void submit(example)}
                  className="rounded-full border border-border px-3 py-1.5 text-xs text-muted-foreground transition-colors hover:bg-accent hover:text-accent-foreground"
                >
                  {example}
                </button>
              ))}
            </div>
          </section>

          {/* الخطة + التحكم */}
          <section className="space-y-5">
            <div className="rounded-3xl border border-border bg-card/70 p-5 backdrop-blur" style={{ boxShadow: "var(--shadow-panel)" }}>
              <h2 className="mb-3 flex items-center gap-2 text-lg font-bold">
                <Wrench className="h-5 w-5 text-primary" /> الخطة الحالية
              </h2>

              {!currentPlan ? (
                <p className="text-sm text-muted-foreground">
                  لا توجد خطة بعد. أرسل أمراً وسيحوّله الوكيل إلى خطوات مرقّمة.
                </p>
              ) : (
                <>
                  <p className="text-sm font-bold">{currentPlan.goal}</p>
                  <p className="text-xs text-muted-foreground">
                    {currentPlan.app ?? "تطبيق غير محدد"}
                    {currentPlan.appPackage ? ` • ${currentPlan.appPackage}` : ""}
                  </p>
                  {currentPlan.needsConfirmation && (
                    <label className="mt-3 flex items-start gap-2 rounded-2xl border border-warning/40 bg-warning/10 p-3 text-xs text-warning">
                      <input
                        type="checkbox"
                        checked={confirmed}
                        onChange={(e) => setConfirmed(e.target.checked)}
                        className="mt-0.5"
                      />
                      <span>
                        <AlertTriangle className="mb-0.5 mr-1 inline h-3.5 w-3.5" />
                        {currentPlan.riskNotes ?? "تحتوي الخطة على خطوات حساسة. أوافق على تنفيذها."}
                      </span>
                    </label>
                  )}

                  <ol className="mt-4 space-y-2">
                    {currentPlan.steps.map((step, i) => (
                      <li
                        key={`${step.id}-${i}`}
                        className={`rounded-2xl border p-3 text-xs transition-colors ${
                          i === stepIndex
                            ? "border-primary/60 bg-primary/10"
                            : i < stepIndex
                              ? "border-border bg-background/30 opacity-70"
                              : "border-border bg-background/20"
                        }`}
                      >
                        <p className="text-sm font-semibold text-foreground">
                          {step.id}. {step.description}
                        </p>
                        <p className={step.dangerous ? "text-warning" : "text-muted-foreground"}>
                          {step.action}
                          {step.target ? ` • ${step.target}` : ""}
                          {step.text ? ` • «${step.text}»` : ""}
                          {step.dangerous ? " • خطوة حساسة" : ""}
                        </p>
                      </li>
                    ))}
                  </ol>
                </>
              )}

              <div className="mt-4 grid grid-cols-2 gap-2 md:grid-cols-4">
                <ControlButton onClick={status === "paused" ? () => setStatus("running") : start} disabled={!currentPlan}>
                  <Play className="h-4 w-4" /> {status === "paused" ? "استئناف" : "بدء"}
                </ControlButton>
                <ControlButton onClick={() => setStatus("paused")} disabled={status !== "running"}>
                  <Pause className="h-4 w-4" /> إيقاف مؤقت
                </ControlButton>
                <ControlButton onClick={() => setStatus("stopped")} disabled={status === "idle"}>
                  <CircleStop className="h-4 w-4" /> إيقاف
                </ControlButton>
                <button
                  type="button"
                  onClick={emergencyStop}
                  className="flex items-center justify-center gap-1.5 rounded-xl bg-destructive px-3 py-2 text-xs font-bold text-destructive-foreground"
                >
                  <AlertTriangle className="h-4 w-4" /> طارئ
                </button>
              </div>
              <p className="mt-3 text-[11px] leading-relaxed text-muted-foreground">
                هذه اللوحة تعرض الخطة وتتابعها خطوة بخطوة. التنفيذ الفعلي على الشاشة يقوم به تطبيق الأندرويد
                (مجلد <code>android/</code>) عبر خدمة الوصول، وهو من يستدعي نفس واجهات الذكاء هنا.
              </p>
            </div>

            {/* التطبيقات المسموح بها */}
            <div className="rounded-3xl border border-border bg-card/70 p-5 backdrop-blur">
              <h2 className="mb-3 flex items-center gap-2 text-lg font-bold">
                <ShieldCheck className="h-5 w-5 text-primary" /> التطبيقات المسموح بها
              </h2>
              <div className="flex flex-wrap gap-2">
                {DEFAULT_APPS.map((app) => {
                  const on = allowed.includes(app.pkg);
                  return (
                    <button
                      key={app.pkg}
                      type="button"
                      onClick={() => toggleApp(app.pkg)}
                      className={`rounded-full border px-3 py-1.5 text-xs transition-colors ${
                        on
                          ? "border-primary/60 bg-primary/15 text-primary"
                          : "border-border text-muted-foreground hover:bg-accent"
                      }`}
                    >
                      {app.label}
                    </button>
                  );
                })}
              </div>
              <p className="mt-3 text-[11px] text-muted-foreground">
                الوكيل لا يلمس أي تطبيق خارج هذه القائمة، وكل خطوة حساسة تحتاج تأكيدك.
              </p>
            </div>
          </section>
        </div>

        {/* سجل الإجراءات + الإعداد */}
        <div className="mt-5 grid gap-5 lg:grid-cols-2">
          <div className="rounded-3xl border border-border bg-card/70 p-5 backdrop-blur">
            <h2 className="mb-3 flex items-center gap-2 text-lg font-bold">
              <Eye className="h-5 w-5 text-primary" /> سجل الإجراءات
            </h2>
            {log.length === 0 ? (
              <p className="text-sm text-muted-foreground">لا توجد إجراءات بعد.</p>
            ) : (
              <ul className="max-h-72 space-y-2 overflow-y-auto text-sm">
                {log.map((entry) => (
                  <li key={entry.id} className="flex items-start gap-2">
                    {entry.ok ? (
                      <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-success" />
                    ) : (
                      <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-warning" />
                    )}
                    <span>
                      {entry.label}
                      {entry.detail ? (
                        <span className="block text-xs text-muted-foreground">{entry.detail}</span>
                      ) : null}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <div className="rounded-3xl border border-border bg-card/70 p-5 backdrop-blur">
            <h2 className="mb-3 flex items-center gap-2 text-lg font-bold">
              <Smartphone className="h-5 w-5 text-primary" /> تشغيل تطبيق الأندرويد
            </h2>
            <ol className="space-y-2 text-sm leading-relaxed text-muted-foreground">
              <li>١. انشر هذا المشروع لتحصل على رابط طبقة الذكاء.</li>
              <li>
                ٢. افتح مجلد <code className="text-foreground">android/</code> في Android Studio وضع الرابط في
                <code className="text-foreground"> AGENT_API_BASE</code>.
              </li>
              <li>٣. ابنِ APK وثبّته على الجهاز.</li>
              <li>٤. فعّل خدمة الوصول (Accessibility) بموافقتك الصريحة، ثم اختر التطبيقات المسموح بها.</li>
              <li>٥. شغّل تحليل الشاشة (OCR) عند الحاجة للألعاب والواجهات غير القابلة للقراءة.</li>
            </ol>
            <p className="mt-3 text-[11px] text-muted-foreground">
              نقاط التكامل: <code>/api/public/agent/plan</code> لبناء الخطة و<code>/api/public/agent/decide</code>
              لاختيار الخطوة التالية حسب حالة الشاشة.
            </p>
          </div>
        </div>
      </div>
    </main>
  );
}

function Badge({ children, tone }: { children: React.ReactNode; tone?: "primary" }) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1 ${
        tone === "primary"
          ? "border-primary/50 bg-primary/15 text-primary"
          : "border-border bg-background/30 text-muted-foreground"
      }`}
    >
      {children}
    </span>
  );
}

function ControlButton({
  children,
  onClick,
  disabled,
}: {
  children: React.ReactNode;
  onClick: () => void;
  disabled?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className="flex items-center justify-center gap-1.5 rounded-xl border border-border bg-background/30 px-3 py-2 text-xs font-bold transition-colors hover:bg-accent disabled:opacity-40"
    >
      {children}
    </button>
  );
}

interface SpeechRecognitionLike {
  lang: string;
  interimResults: boolean;
  onresult: (event: { results: ArrayLike<ArrayLike<{ transcript: string }> & { isFinal: boolean }> }) => void;
  onerror: () => void;
  onend: () => void;
  start: () => void;
  stop: () => void;
}
