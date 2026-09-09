/**
 * AI layer — calls the Lovable AI Gateway Responses API to turn natural
 * language into a structured, executable phone-automation plan.
 *
 * Server-only. Never import from browser code.
 */
import {
  ACTIONS,
  DECIDER_SYSTEM_PROMPT,
  DECIDE_JSON_SCHEMA,
  PLANNER_SYSTEM_PROMPT,
  PLAN_JSON_SCHEMA,
  type AgentPlan,
  type DecideResult,
  type ScreenSnapshot,
} from "./agent-protocol";

const GATEWAY_URL = "https://ai.gateway.lovable.dev/v1/responses";
const MODEL = "openai/gpt-6-astra";

export class GatewayError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

interface CallOptions {
  system: string;
  input: string;
  schemaName: string;
  schema: unknown;
  runId?: string | null | undefined;
  signal?: AbortSignal | undefined;
}

async function callGateway({
  system,
  input,
  schemaName,
  schema,
  runId,
  signal,
}: CallOptions): Promise<unknown> {
  const apiKey = process.env["LOVABLE_API_KEY"];
  if (!apiKey) throw new GatewayError(401, "Missing LOVABLE_API_KEY");

  const res = await fetch(GATEWAY_URL, {
    method: "POST",
    signal: signal ?? null,
    headers: {
      "Content-Type": "application/json",
      "Lovable-API-Key": apiKey,
      "X-Lovable-AIG-SDK": "fetch",
      ...(runId ? { "X-Lovable-AIG-Run-ID": runId } : {}),
    },
    body: JSON.stringify({
      model: MODEL,
      instructions: system,
      input,
      stream: true,
      reasoning: { effort: "low", summary: "auto" },
      text: {
        format: {
          type: "json_schema",
          name: schemaName,
          strict: true,
          schema,
        },
      },
    }),
  });

  if (!res.ok || !res.body) {
    const detail = await res.text().catch(() => "");
    throw new GatewayError(res.status || 500, detail || "AI gateway request failed");
  }

  // Streamed SSE — accumulate output text deltas (buffered calls time out).
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let out = "";

  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split("\n");
    buffer = lines.pop() ?? "";
    for (const line of lines) {
      if (!line.startsWith("data:")) continue;
      const payload = line.slice(5).trim();
      if (!payload || payload === "[DONE]") continue;
      try {
        const evt = JSON.parse(payload) as {
          type?: string;
          delta?: string;
          response?: { output_text?: string };
        };
        if (evt.type === "response.output_text.delta" && typeof evt.delta === "string") {
          out += evt.delta;
        } else if (evt.type === "response.completed" && !out && evt.response?.output_text) {
          out = evt.response.output_text;
        }
      } catch {
        // ignore keep-alive / non-JSON frames
      }
    }
  }

  const trimmed = out.trim();
  if (!trimmed) throw new GatewayError(502, "AI returned an empty response");
  try {
    return JSON.parse(trimmed);
  } catch {
    const start = trimmed.indexOf("{");
    const end = trimmed.lastIndexOf("}");
    if (start >= 0 && end > start) return JSON.parse(trimmed.slice(start, end + 1));
    throw new GatewayError(502, "AI returned malformed JSON");
  }
}

export async function createPlan(args: {
  command: string;
  allowedApps?: string[];
  device?: string | null;
  screen?: ScreenSnapshot | null;
  runId?: string | null | undefined;
  signal?: AbortSignal | undefined;
}): Promise<AgentPlan> {
  const parts = [`أمر المستخدم: ${args.command}`];
  if (args.allowedApps?.length) {
    parts.push(`التطبيقات المسموح بالتحكم بها: ${args.allowedApps.join(", ")}`);
  }
  if (args.device) parts.push(`الجهاز: ${args.device}`);
  if (args.screen) {
    parts.push(`حالة الشاشة الحالية (json): ${JSON.stringify(args.screen).slice(0, 12000)}`);
  }
  parts.push("أعد خطة json مطابقة للمخطط المطلوب.");

  const raw = (await callGateway({
    system: PLANNER_SYSTEM_PROMPT,
    input: parts.join("\n"),
    schemaName: "agent_plan",
    schema: PLAN_JSON_SCHEMA,
    runId: args.runId,
    signal: args.signal,
  })) as AgentPlan;

  return validatePlan(raw);
}

/** Guards TaskEngine against anything that is not a schema-valid plan. */
function validatePlan(raw: unknown): AgentPlan {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) {
    throw new GatewayError(502, "AI returned a non-plan payload");
  }
  const plan = raw as AgentPlan;
  const steps = (Array.isArray(plan.steps) ? plan.steps : []).filter(
    (s) => s && typeof s.action === "string" && (ACTIONS as readonly string[]).includes(s.action),
  );
  if (steps.length === 0) throw new GatewayError(502, "AI returned a plan with no valid steps");
  plan.steps = steps.map((s, i) => ({ ...s, id: typeof s.id === "number" && s.id > 0 ? s.id : i + 1 }));
  if (typeof plan.goal !== "string") plan.goal = "";
  if (typeof plan.summary !== "string") plan.summary = "";
  plan.needsConfirmation = Boolean(plan.needsConfirmation) || plan.steps.some((s) => s.dangerous);
  return plan;
}


export async function decideNextStep(args: {
  goal: string;
  plannedStep?: unknown;
  screen: ScreenSnapshot;
  history?: string[];
  runId?: string | null | undefined;
  signal?: AbortSignal | undefined;
}): Promise<DecideResult> {
  const input = [
    `الهدف: ${args.goal}`,
    args.plannedStep ? `الخطوة المخططة: ${JSON.stringify(args.plannedStep)}` : "",
    args.history?.length ? `الإجراءات المنفذة سابقاً: ${args.history.slice(-15).join(" | ")}` : "",
    `لقطة الشاشة (json): ${JSON.stringify(args.screen).slice(0, 14000)}`,
    "أعد قرار json مطابقاً للمخطط.",
  ]
    .filter(Boolean)
    .join("\n");

  return (await callGateway({
    system: DECIDER_SYSTEM_PROMPT,
    input,
    schemaName: "agent_decision",
    schema: DECIDE_JSON_SCHEMA,
    runId: args.runId,
    signal: args.signal,
  })) as DecideResult;
}
