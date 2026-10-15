import test from "node:test"
import assert from "node:assert/strict"
import { createRankingHandler } from "./handler.ts"

const request = (body = '{"category":"SQUAT"}', auth = "Bearer test.jwt.signature") => new Request("https://example.test", {
  method: "POST", headers: { authorization: auth, "content-type": "application/json" }, body,
})
function fixture() {
  const calls: string[] = []
  const deps = {
    authenticate: async () => { calls.push("auth"); return true },
    reserveRequest: async () => { calls.push("quota"); return true },
    loadRanking: async () => { calls.push("ranking"); return Response.json({ cacheStatus: "HIT", videos: [] }) },
  }
  return { calls, deps, handle: createRankingHandler(deps) }
}
test("API key alone, malformed/oversized bearer and unsupported method cause no outbound call", async () => {
  for (const auth of ["", "Bearer sb_publishable_example", "Bearer not-a-jwt", "Bearer " + "x".repeat(8193)]) {
    const f = fixture(); assert.equal((await f.handle(request(undefined, auth))).status, 401); assert.deepEqual(f.calls, [])
  }
  const f = fixture(); assert.equal((await f.handle(new Request("https://example.test"))).status, 405); assert.deepEqual(f.calls, [])
})
test("invalid/anonymous Auth user cannot reach cache, DB quota or YouTube", async () => {
  const f = fixture(); f.deps.authenticate = async () => false
  assert.equal((await f.handle(request())).status, 401); assert.deepEqual(f.calls, [])
})
test("even cache hits require verified auth then durable quota", async () => {
  const f = fixture(); const response = await f.handle(request())
  assert.equal(response.status, 200); assert.deepEqual(f.calls, ["auth", "quota", "ranking"])
})
test("limit exceeded and unavailable quota fail closed before ranking", async () => {
  for (const broken of [false, true]) {
    const f = fixture(); f.deps.reserveRequest = async () => { if (broken) throw new Error("offline"); return false }
    assert.equal((await f.handle(request())).status, broken ? 503 : 429)
    assert.deepEqual(f.calls, ["auth"])
  }
})
test("invalid category, extra PII, malformed JSON, oversized declared/streamed body rejected", async () => {
  for (const body of ['{"category":"OTHER"}', '{"category":"SQUAT","userId":"injected"}', 'null', '[]', '{}', '{', 'x'.repeat(513)]) {
    const f = fixture(); const response = await f.handle(request(body))
    assert.equal(response.status, body.length > 512 ? 413 : 400); assert.deepEqual(f.calls, ["auth"])
  }
  const f = fixture(); const req = request(); req.headers.set("content-length", "9999")
  assert.equal((await f.handle(req)).status, 413); assert.deepEqual(f.calls, ["auth"])
})
test("deadline aborts and does not continue to quota/ranking", async () => {
  const f = fixture(); let release!: (value: boolean) => void
  f.deps.authenticate = () => new Promise<boolean>(resolve => { release = resolve })
  const response = await createRankingHandler({ ...f.deps, timeoutMs: 5 })(request())
  assert.equal(response.status, 504); release(true)
  await new Promise(resolve => setTimeout(resolve, 1)); assert.deepEqual(f.calls, [])
})
test("wrong content type rejected before Auth", async () => {
  const f = fixture(); const req = request(); req.headers.set("content-type", "text/plain")
  assert.equal((await f.handle(req)).status, 415); assert.deepEqual(f.calls, [])
})
