import { MAX_INPUT_BYTES, MAX_OUTPUT_BYTES, validateInput, validateReport } from "./contracts.ts"
import { buildPrompt } from "./prompt.ts"
import type { LlmProvider } from "./provider.ts"

export interface HandlerDependencies {
  enabled: boolean
  dailyLimit: number
  provider: LlmProvider
  authenticate(token: string, signal: AbortSignal): Promise<boolean>
  reserveQuota(token: string, limit: number, signal: AbortSignal): Promise<boolean>
  timeoutMs?: number
}
const headers = { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff" }
const reply = (status: number, code: string) => new Response(JSON.stringify({ code }), { status, headers })
async function boundedJson(request: Request, signal: AbortSignal): Promise<unknown> {
  const declared = request.headers.get("content-length")
  if (declared && (!/^\d+$/.test(declared) || Number(declared) > MAX_INPUT_BYTES)) throw new RangeError()
  const reader = request.body?.getReader()
  if (!reader) throw new Error("Missing body")
  let bytes = 0
  const chunks: Uint8Array[] = []
  const abort = () => { void reader.cancel().catch(() => {}) }
  signal.addEventListener("abort", abort, { once: true })
  try {
    while (true) {
      const next = await reader.read()
      if (next.done) break
      bytes += next.value.length
      if (bytes > MAX_INPUT_BYTES) throw new RangeError()
      chunks.push(next.value)
    }
  } finally {
    signal.removeEventListener("abort", abort)
    await reader.cancel().catch(() => {}); reader.releaseLock()
  }
  const body = new Uint8Array(bytes)
  let offset = 0
  for (const chunk of chunks) { body.set(chunk, offset); offset += chunk.length }
  return JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(body))
}

/** Auth -> strict payload -> durable quota -> one provider attempt -> strict output. No logs/retries. */
export function createHandler(deps: HandlerDependencies): (request: Request) => Promise<Response> {
  return async request => {
    if (request.method !== "POST") return reply(405, "METHOD_NOT_ALLOWED")
    const authorization = request.headers.get("authorization")
    if (!authorization || authorization.length > 8192 || !/^Bearer [A-Za-z0-9_.-]+$/.test(authorization)) return reply(401, "UNAUTHORIZED")
    // Disabled configuration must not contact Auth/DB/provider (no new cloud usage at all).
    if (!deps.enabled || !deps.provider.available) return reply(503, "UNAVAILABLE")
    if (!Number.isInteger(deps.dailyLimit) || deps.dailyLimit < 1) return reply(503, "UNAVAILABLE")
    if (request.headers.get("content-type")?.split(";")[0].trim() !== "application/json") return reply(415, "JSON_REQUIRED")
    const token = authorization.slice(7)
    const controller = new AbortController()
    const timeout = Math.min(deps.timeoutMs ?? 15_000, 15_000)
    let timer: ReturnType<typeof setTimeout> | undefined
    const deadline = new Promise<Response>(resolve => {
      timer = setTimeout(() => { controller.abort(); resolve(reply(504, "TIMEOUT")) }, timeout)
    })
    const work = async (): Promise<Response> => {
      try {
        if (!await deps.authenticate(token, controller.signal)) return reply(401, "UNAUTHORIZED")
        if (controller.signal.aborted) return reply(504, "TIMEOUT")
        let input
        try { input = validateInput(await boundedJson(request, controller.signal)) }
        catch (e) { return reply(e instanceof RangeError ? 413 : 400, "INVALID_INPUT") }
        if (controller.signal.aborted) return reply(504, "TIMEOUT")
        const limit = Math.min(deps.dailyLimit, 5)
        // No in-memory quota: RPC failure is caught and fails closed, never invokes provider.
        if (!await deps.reserveQuota(token, limit, controller.signal)) return reply(429, "DAILY_LIMIT")
        if (controller.signal.aborted) return reply(504, "TIMEOUT")
        const raw = await deps.provider.generateWorkoutReport(input, buildPrompt(input), controller.signal)
        const encoded = typeof raw === "string" ? raw : JSON.stringify(raw)
        if (new TextEncoder().encode(encoded).length > MAX_OUTPUT_BYTES) return reply(502, "INVALID_REPORT")
        let report
        try { report = validateReport(typeof raw === "string" ? JSON.parse(raw) : raw, input) }
        catch { return reply(502, "INVALID_REPORT") }
        return new Response(JSON.stringify(report), { status: 200, headers })
      } catch { return reply(503, "UNAVAILABLE") }
    }
    try { return await Promise.race([work(), deadline]) }
    finally { clearTimeout(timer) }
  }
}
