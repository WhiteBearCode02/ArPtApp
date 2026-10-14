// Read-only, scoped to changed/untracked public text. Never print secret values or scan Git history.
import { existsSync, readFileSync } from "node:fs"
import { execFileSync } from "node:child_process"

const git = (...args) => execFileSync("git", args, { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] }).trim()
const tracked = git("ls-files").split(/\r?\n/)
const privateName = /(^|\/)(\.env(?:\..+)?|credentials(?:[^/]*)|service[-_]account[^/]*\.json|(?:llm|supabase)-secrets\.[^/]+|service-role-key[^/]*|api-key[^/]*|private-key[^/]*)$|\.(?:jks|keystore|p12|pfx|pem|key|pk8|p8|secret|secrets|credentials)$/i
const forbidden = tracked.filter(path => privateName.test(path) && !path.endsWith("/.env.example") && path !== ".env.example")
if (forbidden.length) throw new Error("Tracked private credential filenames detected; inspect locally before publishing.")

const changed = new Set([
  ...git("diff", "--name-only").split(/\r?\n/),
  ...git("diff", "--cached", "--name-only").split(/\r?\n/),
  ...git("ls-files", "--others", "--exclude-standard").split(/\r?\n/),
])
const credential = /\bsb_secret_[A-Za-z0-9_-]{12,}|\bsk-(?:proj-)?[A-Za-z0-9_-]{24,}|\bAIza[0-9A-Za-z_-]{30,}|\bAKIA[0-9A-Z]{16}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/
let checked = 0
for (const path of changed) {
  if (!path || !existsSync(path) || !/\.(?:kt|kts|ts|mjs|md|xml|sql|toml|example)$/.test(path)) continue
  const source = readFileSync(path, "utf8")
  if (credential.test(source)) throw new Error(`Potential secret detected in ${path}; value suppressed.`)
  checked++
}
// Inspect only the classification of the APK's existing Supabase key, not its value or claims.
if (existsSync("local.properties")) {
  const source = readFileSync("local.properties", "utf8")
  const value = source.match(/^supabase\.key\s*=\s*(.*)$/m)?.[1]?.trim() ?? ""
  if (value.startsWith("sb_secret_")) throw new Error("A server secret cannot be embedded in Android.")
  if (value.split(".").length === 3) {
    let role
    try { role = JSON.parse(Buffer.from(value.split(".")[1], "base64url").toString("utf8")).role }
    catch { throw new Error("Unable to classify Supabase key; inspect locally.") }
    if (role !== "anon") throw new Error("Non-anon Supabase key cannot be embedded in Android.")
    console.log("APK Supabase key classification: public anon (value suppressed).")
  } else if (value.startsWith("sb_publishable_")) console.log("APK Supabase key classification: publishable (value suppressed).")
  else if (!value) console.log("APK Supabase key: not configured.")
  else throw new Error("Unknown Supabase key format; verify locally before distribution.")
}
console.log(`Scoped secret-pattern check passed for ${checked} changed public text files. Git history was not scanned.`)
