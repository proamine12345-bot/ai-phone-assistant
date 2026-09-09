import { createServerFn } from "@tanstack/react-start";
import { z } from "zod";

import type { AgentPlan, DecideResult } from "./agent-protocol";

const PlanInput = z.object({
  command: z.string().min(2),
  allowedApps: z.array(z.string()).optional(),
});

export const planCommand = createServerFn({ method: "POST" })
  .inputValidator((input: unknown) => PlanInput.parse(input))
  .handler(async ({ data }): Promise<AgentPlan> => {
    const { createPlan } = await import("./agent-planner.server");
    return createPlan({ command: data.command, allowedApps: data.allowedApps });
  });

const DecideInput = z.object({
  goal: z.string().min(1),
  screen: z.record(z.string(), z.unknown()),
  history: z.array(z.string()).optional(),
});

export const decideStep = createServerFn({ method: "POST" })
  .inputValidator((input: unknown) => DecideInput.parse(input))
  .handler(async ({ data }): Promise<DecideResult> => {
    const { decideNextStep } = await import("./agent-planner.server");
    return decideNextStep({ goal: data.goal, screen: data.screen, history: data.history });
  });
