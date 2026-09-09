/**
 * Credit-saving cache for the AI layer (server-only).
 *
 * Identical requests (same command / same screen state + same planned step)
 * reuse the previous AI answer instead of paying for another gateway call.
 * Entries are short-lived so behaviour stays identical for real state changes.
 */

interface Entry<T> {
  value: T;
  expires: number;
}

class TtlCache<T> {
  private map = new Map<string, Entry<T>>();

  constructor(
    private ttlMs: number,
    private maxEntries = 200,
  ) {}

  get(key: string): T | undefined {
    const hit = this.map.get(key);
    if (!hit) return undefined;
    if (hit.expires < Date.now()) {
      this.map.delete(key);
      return undefined;
    }
    // refresh LRU order
    this.map.delete(key);
    this.map.set(key, hit);
    return hit.value;
  }

  set(key: string, value: T): void {
    if (this.map.size >= this.maxEntries) {
      const oldest = this.map.keys().next().value;
      if (oldest !== undefined) this.map.delete(oldest);
    }
    this.map.set(key, { value, expires: Date.now() + this.ttlMs });
  }
}

/** Plans for the same command are stable for a while. */
export const planCache = new TtlCache<unknown>(15 * 60_000, 150);
/** Decisions are only reused while the screen state is literally unchanged. */
export const decideCache = new TtlCache<unknown>(45_000, 200);

/** Small, stable, non-cryptographic key (no crypto import needed at the edge). */
export function fingerprint(parts: unknown[]): string {
  const raw = parts.map((p) => (typeof p === "string" ? p : JSON.stringify(p) ?? "")).join("\u0000");
  let h1 = 0x811c9dc5;
  let h2 = 0x01000193;
  for (let i = 0; i < raw.length; i++) {
    const c = raw.charCodeAt(i);
    h1 = (h1 ^ c) * 16777619 >>> 0;
    h2 = (h2 + c * (i + 1)) >>> 0;
  }
  return `${raw.length.toString(36)}-${h1.toString(36)}-${h2.toString(36)}`;
}

/** In-flight de-duplication: concurrent identical calls share one AI request. */
const inFlight = new Map<string, Promise<unknown>>();

export function dedupe<T>(key: string, run: () => Promise<T>): Promise<T> {
  const existing = inFlight.get(key) as Promise<T> | undefined;
  if (existing) return existing;
  const promise = run().finally(() => inFlight.delete(key));
  inFlight.set(key, promise);
  return promise;
}
