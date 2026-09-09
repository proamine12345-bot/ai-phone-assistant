import { createFileRoute } from "@tanstack/react-router";
import { z } from "zod";

import { decideNextStep, GatewayError } from "@/lib/agent-planner.server";

const Body = z.object({
  goal: z.string().min(1).max(2000),
  plannedStep: z.record(z.string(), z.unknown()).nullish(),
  history: z.array(z.string().max(400)).max(60).optional(),
  screen: z.object({
    packageName: z.string().max(200).nullish(),
    activity: z.string().max(300).nullish(),
    ocrText: z.string().max(20000).nullish(),
    nodes: z
      .array(
        z.object({
          text: z.string().max(600).nullish(),
          desc: z.string().max(600).nullish(),
          viewId: z.string().max(300).nullish(),
          className: z.string().max(200).nullish(),
          clickable: z.boolean().optional(),
          editable: z.boolean().optional(),
          scrollable: z.boolean().optional(),
          bounds: z.string().max(120).nullish(),
        }),
      )
      .max(400)
      .optional(),
  }),
});

const json = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });

/**
 * POST /api/public/agent/decide
 * Screen-state -> next single action. Called after every executed step.
 */
export const Route = createFileRoute("/api/public/agent/decide")({
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
          const decision = await decideNextStep({
            goal: parsed.data.goal,
            plannedStep: parsed.data.plannedStep ?? undefined,
            screen: parsed.data.screen as never,
            history: parsed.data.history ?? [],
            signal: request.signal,
          });
          return json({ decision });
        } catch (error) {
          if (error instanceof GatewayError) {
            return json({ error: error.message, status: error.status }, error.status);
          }
          if (error instanceof Error && error.name === "AbortError") {
            return new Response(null, { status: 499 });
          }
          console.error(error);
          return json({ error: "decision failed" }, 500);
        }
      },
    },
  },
});
