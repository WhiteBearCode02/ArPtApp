import test from "node:test"
import assert from "node:assert/strict"

test("Deno entrypoint remains unavailable without any external fetch", async () => {
  let handler: ((request: Request) => Promise<Response>) | undefined
  const originalFetch = globalThis.fetch
  let calls = 0
  Object.assign(globalThis, { Deno: {
    env: { get: (name: string) => name === "LLM_ENABLED" ? "true" : undefined },
    serve: (value: (request: Request) => Promise<Response>) => { handler = value },
  } })
  globalThis.fetch = async () => { calls++; throw new Error("No outbound calls allowed") }
  try {
    await import("./index.ts")
    assert.ok(handler)
    const result = await handler(new Request("https://example.test", { method: "POST", headers: { authorization: "Bearer test.jwt.token" } }))
    assert.equal(result.status, 503)
    assert.equal(calls, 0)
  } finally {
    globalThis.fetch = originalFetch
    Reflect.deleteProperty(globalThis, "Deno")
  }
})
