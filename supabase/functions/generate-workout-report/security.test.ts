import test from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import { spawnSync } from "node:child_process"

test("real secrets ignored, empty example remains publishable", () => {
  for (const file of [".env", ".env.production", "supabase/functions/generate-workout-report/.env",
    "credentials.json", "credentials", "credentials-dev.properties", "service-account-dev.json", "api-key.private", "llm-secrets.json", "supabase-secrets.json", "service-role-key.txt", "signing.jks", "signing.pk8", "local.properties"]) {
    assert.equal(spawnSync("git", ["check-ignore", "--no-index", "-q", file]).status, 0, file)
  }
  const example = "supabase/functions/generate-workout-report/.env.example"
  assert.equal(spawnSync("git", ["check-ignore", "--no-index", "-q", example]).status, 1)
  const values = new Map(readFileSync(example, "utf8").split(/\r?\n/).filter(v => !v.startsWith("#") && v.includes("="))
    .map(v => v.split("=", 2) as [string, string]))
  assert.equal(values.get("LLM_ENABLED"), "false")
  assert.equal(values.get("DAILY_LIMIT"), "5")
  for (const key of ["LLM_API_KEY", "LLM_API_URL", "LLM_MODEL", "LLM_PROVIDER", "SUPABASE_URL", "SUPABASE_ANON_KEY"]) assert.equal(values.get(key), "")
})
test("Android has no LLM secret configuration; provider has no outbound call", () => {
  const gradle = readFileSync("app/build.gradle.kts", "utf8")
  assert.ok(!gradle.includes("LLM_API_KEY"))
  const provider = readFileSync("supabase/functions/generate-workout-report/provider.ts", "utf8")
  assert.ok(!/\bfetch\s*\(/.test(provider))
  const android = readFileSync("app/src/main/java/com/example/arptapp/data/remote/AiCoachingRepository.kt", "utf8")
  assert.ok(android.includes("const val ENABLED = false"))
  assert.ok(!android.includes("LLM_API_KEY"))
  assert.ok(readFileSync("supabase/config.toml", "utf8").includes("verify_jwt = true"))
})
test("quota SQL is auth-scoped, atomic and private (static check, not DB execution)", () => {
  const sql = readFileSync("supabase/workout-coaching-rate-limit.sql", "utf8")
  assert.ok(sql.includes("owner_id uuid := auth.uid()"))
  assert.ok(sql.includes("on conflict (user_id, request_day) do update"))
  assert.ok(sql.includes("where quota.requests < p_limit"))
  assert.ok(sql.includes("enable row level security"))
  assert.ok(sql.includes("security definer set search_path = ''"))
  assert.ok(sql.includes("security invoker set search_path = ''"))
  assert.ok(!/grant\s+all/i.test(sql))
  assert.ok(!/p_user_id/.test(sql))
})
test("rep diagnostics are DEBUG-only and transient ratios have no wire serializer", () => {
  const dashboard = readFileSync("app/src/main/java/com/example/arptapp/DashboardActivity.kt", "utf8")
  assert.ok(dashboard.includes('if (BuildConfig.DEBUG) Log.d("RepAnalysis"'))
  assert.ok(dashboard.includes("frame.imageWidth.toDouble() / frame.imageHeight, frame.timestampMs"))
  const formatter = readFileSync("app/src/main/java/com/example/arptapp/domain/report/RepDiagnosticFormatter.kt", "utf8")
  for (const name of ["userId", "email", "accessToken", "apiKey", "landmarks"]) assert.ok(!formatter.includes(name))
  const serialized = readFileSync("app/src/main/java/com/example/arptapp/domain/report/StructuredAnalysis.kt", "utf8").split("data class RepFrameFeatures")[0]
  assert.ok(!serialized.includes("KneeValgusRatio")); assert.ok(!serialized.includes("visibleSide"))
})
