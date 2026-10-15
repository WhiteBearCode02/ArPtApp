import { createRankingHandler } from "./handler.ts"
import { loadRanking } from "./ranking-service.ts"

const url = Deno.env.get("SUPABASE_URL") ?? ""
const key = Deno.env.get("SUPABASE_ANON_KEY") ?? ""
const endpoint = (path: string) => {
  const parsed = new URL(url)
  if (parsed.protocol !== "https:" || parsed.username || parsed.password || parsed.pathname !== "/" || parsed.search || parsed.hash || !key) throw new Error("Invalid server configuration")
  return `${parsed.origin}${path}`
}
const userHeaders = (token: string) => ({ apikey: key, Authorization: `Bearer ${token}`, "Content-Type": "application/json" })

Deno.serve(createRankingHandler({
  authenticate: async (token, signal) => {
    // Auth validates signature/expiry/current user. Never authorize from decoded JWT claims alone.
    const response = await fetch(endpoint("/auth/v1/user"), { headers: userHeaders(token), signal, redirect: "error" })
    if (!response.ok) return false
    const user = await response.json()
    return typeof user.id === "string" && /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(user.id) && user.is_anonymous === false
  },
  reserveRequest: async (token, signal) => {
    const response = await fetch(endpoint("/rest/v1/rpc/reserve_youtube_ranking_request"), {
      method: "POST", headers: userHeaders(token), body: "{}", signal, redirect: "error",
    })
    if (!response.ok) throw new Error("Request limit unavailable")
    const result = await response.json()
    if (typeof result !== "boolean") throw new Error("Invalid quota result")
    return result
  },
  loadRanking,
}))
