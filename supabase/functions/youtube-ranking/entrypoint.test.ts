import test from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"

test("real entrypoint authenticates, rejects anonymous/expired users and uses user-scoped RPC even for cache hits", async () => {
  let handler!: (request: Request) => Promise<Response>
  const originalFetch = globalThis.fetch
  const calls: string[] = []
  let mode = "valid"
  const request = () => new Request("https://example.test", { method: "POST", headers: {
    authorization: "Bearer test.jwt.signature", "content-type": "application/json",
  }, body: '{"category":"SQUAT"}' })
  Object.assign(globalThis, { Deno: {
    env: { get: (name: string) => ({ SUPABASE_URL: "https://example.supabase.co", SUPABASE_ANON_KEY: "public-test-key", SUPABASE_SERVICE_ROLE_KEY: "server-test-placeholder" }[name]) },
    serve: (value: typeof handler) => { handler = value },
  } })
  globalThis.fetch = async (input, init) => {
    const url = String(input)
    const headers = new Headers(init?.headers)
    assert.equal(init?.redirect, "error")
    assert.ok(init?.signal)
    if (url.endsWith("/auth/v1/user")) {
      calls.push("auth")
      assert.equal(headers.get("authorization"), "Bearer test.jwt.signature")
      if (mode === "expired") return Response.json({}, { status: 401 })
      return Response.json({ id: "00000000-0000-4000-8000-000000000001", is_anonymous: mode === "anonymous" })
    }
    if (url.endsWith("/rpc/reserve_youtube_ranking_request")) {
      calls.push("quota")
      assert.equal(headers.get("authorization"), "Bearer test.jwt.signature")
      assert.equal(headers.get("apikey"), "public-test-key")
      assert.equal(init?.body, "{}") // no supplied UUID or limit bypass
      if (mode === "unavailable") return Response.json({}, { status: 404 })
      if (mode === "invalid-quota") return Response.json("true")
      return Response.json(mode !== "exhausted")
    }
    if (url.includes("/rest/v1/youtube_ranking_cache?")) {
      calls.push("cache")
      return Response.json([{ query: "squat workout", payload: { category: "SQUAT", videos: [] },
        fetched_at: new Date().toISOString(), expires_at: new Date(Date.now() + 3_600_000).toISOString() }])
    }
    throw new Error("Unexpected external request forbidden in tests")
  }
  try {
    await import("./index.ts")
    assert.ok(handler)
    for (const [scenario, status, expected] of [
      ["valid", 200, ["auth", "quota", "cache"]],
      ["expired", 401, ["auth"]], ["anonymous", 401, ["auth"]],
      ["exhausted", 429, ["auth", "quota"]],
      ["unavailable", 503, ["auth", "quota"]], ["invalid-quota", 503, ["auth", "quota"]],
    ] as const) {
      mode = scenario; calls.length = 0
      assert.equal((await handler(request())).status, status)
      assert.deepEqual(calls, expected)
    }
  } finally { globalThis.fetch = originalFetch; Reflect.deleteProperty(globalThis, "Deno") }
})

test("quota SQL uses auth UID and a private atomic fixed minute/day cap (static, not DB execution)", () => {
  const sql = readFileSync("supabase/youtube-ranking.sql", "utf8")
  assert.match(sql, /youtube_private\.request_limits[\s\S]*enable row level security/i)
  assert.match(sql, /revoke all on youtube_private\.request_limits from public, anon, authenticated/i)
  assert.match(sql, /owner_id uuid := auth\.uid\(\)/)
  assert.match(sql, /is_anonymous/)
  assert.match(sql, /on conflict \(user_id\) do update[\s\S]*day_count < 120[\s\S]*minute_count < 6/)
  assert.match(sql, /public\.reserve_youtube_ranking_request\(\)[\s\S]*security invoker/)
  assert.doesNotMatch(sql, /grant (?:select|insert|update|all).*youtube_private\.request_limits/i)
})
