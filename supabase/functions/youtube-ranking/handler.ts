import type { RankingCategory } from "./config.ts"

export interface RankingDependencies {
  authenticate(token: string, signal: AbortSignal): Promise<boolean>
  reserveRequest(token: string, signal: AbortSignal): Promise<boolean>
  loadRanking(category: RankingCategory, signal: AbortSignal): Promise<Response>
  timeoutMs?: number
}
const headers = { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff" }
const reply = (status: number, code: string, message: string) => new Response(JSON.stringify({ code, message }), { status, headers })

async function readCategory(request: Request, signal: AbortSignal): Promise<RankingCategory> {
  const declared = request.headers.get("content-length")
  if (declared && (!/^\d+$/.test(declared) || Number(declared) > 512)) throw new RangeError()
  const reader = request.body?.getReader()
  if (!reader) throw new Error("No body")
  const chunks: Uint8Array[] = []
  let size = 0
  const abort = () => { void reader.cancel().catch(() => {}) }
  signal.addEventListener("abort", abort, { once: true })
  try {
    while (true) {
      const part = await reader.read()
      if (part.done) break
      size += part.value.length
      if (size > 512) throw new RangeError()
      chunks.push(part.value)
    }
  } finally {
    signal.removeEventListener("abort", abort)
    await reader.cancel().catch(() => {}); reader.releaseLock()
  }
  const bytes = new Uint8Array(size)
  let offset = 0
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length }
  const body = JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes))
  if (!body || Array.isArray(body) || Object.keys(body).length !== 1 ||
      (body.category !== "SQUAT" && body.category !== "SHOULDER_PRESS")) throw new Error("Invalid category")
  return body.category
}

/** Verified Auth identity and durable quota are required even for cache hits. No in-memory fallback. */
export function createRankingHandler(deps: RankingDependencies) {
  return async (request: Request): Promise<Response> => {
    if (request.method !== "POST") return reply(405, "METHOD_NOT_ALLOWED", "지원하지 않는 요청입니다.")
    const authorization = request.headers.get("authorization") ?? ""
    if (authorization.length > 8192 || !/^Bearer [A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/.test(authorization)) {
      return reply(401, "UNAUTHORIZED", "로그인이 필요합니다.")
    }
    if (request.headers.get("content-type")?.split(";")[0].trim() !== "application/json") return reply(415, "JSON_REQUIRED", "JSON 요청이 필요합니다.")
    const controller = new AbortController()
    let timer: ReturnType<typeof setTimeout> | undefined
    const deadline = new Promise<Response>(resolve => {
      timer = setTimeout(() => { controller.abort(); resolve(reply(504, "TIMEOUT", "요청 시간이 초과되었습니다.")) }, Math.min(deps.timeoutMs ?? 12_000, 12_000))
    })
    const work = async (): Promise<Response> => {
      try {
        const token = authorization.slice(7)
        if (!await deps.authenticate(token, controller.signal)) return reply(401, "UNAUTHORIZED", "로그인이 만료되었습니다.")
        if (controller.signal.aborted) return reply(504, "TIMEOUT", "요청 시간이 초과되었습니다.")
        let category: RankingCategory
        try { category = await readCategory(request, controller.signal) }
        catch (error) { return reply(error instanceof RangeError ? 413 : 400, "INVALID_INPUT", "올바른 운동 카테고리가 필요합니다.") }
        if (controller.signal.aborted) return reply(504, "TIMEOUT", "요청 시간이 초과되었습니다.")
        if (!await deps.reserveRequest(token, controller.signal)) return reply(429, "REQUEST_LIMIT", "조회 요청 한도에 도달했습니다. 잠시 후 다시 이용해 주세요.")
        if (controller.signal.aborted) return reply(504, "TIMEOUT", "요청 시간이 초과되었습니다.")
        return await deps.loadRanking(category, controller.signal)
      } catch { return reply(503, "UNAVAILABLE", "순위 서비스를 사용할 수 없습니다.") }
    }
    try { return await Promise.race([work(), deadline]) }
    finally { clearTimeout(timer) }
  }
}
