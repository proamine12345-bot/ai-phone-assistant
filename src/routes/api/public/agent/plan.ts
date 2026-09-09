import { createFileRoute } from "@tanstack/react-router";
import { z } from "zod";

import { createPlan, GatewayError } from "@/lib/agent-planner.server";

const Body = z.object({
  command: z.string().min(2).max(4000),
  allowedApps: z.array(z.string().max(120)).max(60).optional(),
  device: z.string().max(200).nullish(),
  screen: z.record(z.string(), z.unknown()).nullish(),
});

const json = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });

/**
 * POST /api/public/agent/plan
 * Called by the Android agent (PlannerClient.kt) with an optional agent token.
 */
export const Route = createFileRoute("/api/public/agent/plan")({
  server: {
    handlers: {
      POST: async ({ request }) => {
        const expected = process.env["AGENT_API_TOKEN"];
        if (expected && request.headers.get("x-agent-token") !== expected) {
          return json({ error: "unauthorized" }, 401);
        }

        let body: unknown;
        try {
          body = await request.json();
        } catch {
          return json({ error: "invalid json body" }, 400);
        }

        const parsed = Body.safeParse(body);
        if (!parsed.success) {
          return json({ error: "invalid request", issues: parsed.error.issues }, 400);
        }

        try {
          const plan = await createPlan({
            command: parsed.data.command,
            allowedApps: parsed.data.allowedApps,
            device: parsed.data.device ?? null,
            screen: parsed.data.screen ?? null,
            signal: request.signal,
          });
          return json({ plan });
        } catch (error) {
          if (error instanceof GatewayError) {
            return json({ error: error.message, status: error.status }, error.status);
          }
          if (error instanceof Error && error.name === "AbortError") {
            return new Response(null, { status: 499 });
          }
          console.error(error);
          return json({ error: "planning failed" }, 500);
        }
      },
    },
  },
});
