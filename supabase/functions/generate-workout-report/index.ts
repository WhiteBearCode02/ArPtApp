import { createHandler } from "./handler.ts"
import { createProvider } from "./provider.ts"

// Built-in Supabase publishable/anon configuration ONLY. This function needs no service-role key.
const url = Deno.env.get("SUPABASE_URL") ?? ""
const key = Deno.env.get("SUPABASE_ANON_KEY") ?? ""
function endpoint(path: string): URL {
  const result = new URL(url.replace(/\/$/, "") + path)
  if (result.protocol !== "https:" || result.username || result.password) throw new Error("Invalid server config")
  return result
}
function userHeaders(token: string) {
  return { apikey: key, Authorization: `Bearer ${token}`, "Content-Type": "application/json" }
}

Deno.serve(createHandler({
  enabled: Deno.env.get("LLM_ENABLED") === "true",
  dailyLimit: Number(Deno.env.get("DAILY_LIMIT") ?? "5"),
  provider: createProvider(),
  authenticate: async (token, signal) => {
    // Auth server verifies the token and retrieves the user; never trust decoded JWT claims alone.
    const response = await fetch(endpoint("/auth/v1/user"), { headers: userHeaders(token), signal, redirect: "error" })
    if (!response.ok) return false
    const user = await response.json()
    return typeof user.id === "string" && /^[0-9a-f-]{36}$/i.test(user.id) && user.is_anonymous !== true
  },
  reserveQuota: async (token, limit, signal) => {
    // auth.uid() in Postgres derives the account, not a client-provided userId in JSON.
    const response = await fetch(endpoint("/rest/v1/rpc/reserve_workout_coaching_request"), {
      method: "POST", headers: userHeaders(token), body: JSON.stringify({ p_limit: limit }), signal, redirect: "error",
    })
    if (!response.ok) throw new Error("Quota unavailable")
    const allowed = await response.json()
    if (typeof allowed !== "boolean") throw new Error("Invalid quota result")
    return allowed
  },
}))
