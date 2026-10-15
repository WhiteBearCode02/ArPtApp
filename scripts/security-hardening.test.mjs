import test from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
const read = path => readFileSync(path, "utf8")
const root = "app/src/main/"

test("both backup formats exclude every app data domain, plus manifest disables backup", () => {
  assert.match(read(root + "AndroidManifest.xml"), /android:allowBackup="false"/)
  const domains = ["root", "file", "database", "sharedpref", "external", "device_root", "device_file", "device_database", "device_sharedpref"]
  for (const name of ["backup_rules", "data_extraction_rules"]) {
    const source = read(root + `res/xml/${name}.xml`)
    const sections = name === "backup_rules" ? [source] : [source.match(/<cloud-backup[\s\S]*?<\/cloud-backup>/)[0], source.match(/<device-transfer>[\s\S]*?<\/device-transfer>/)[0]]
    for (const section of sections) for (const domain of domains) assert.ok(section.includes(`<exclude domain="${domain}" path="." />`))
    assert.doesNotMatch(source, /<include /)
  }
})
test("all build variants exclude loose model assets without touching MediaPipe .task", () => {
  const gradle = read("app/build.gradle.kts")
  const pattern = gradle.match(/ignoreAssetsPattern = "([^"]+)"/)[1]
  for (const extension of ["tflite", "pt", "pth", "onnx"]) assert.ok(pattern.split(":").includes(`*.${extension}`))
  assert.ok(!pattern.includes("*.task"))
  assert.match(read("scripts/verify-public-apk.ps1"), /pose_landmarker_lite\.task/)
})
test("recovery uses a memory-only client, never the SDK's asynchronous deep-link shortcut", () => {
  const source = read(root + "java/com/example/arptapp/data/remote/SupabaseAuthRepository.kt")
  const recovery = source.slice(source.indexOf("suspend fun handlePasswordRecovery"), source.indexOf("suspend fun handleAuthCallback"))
  assert.doesNotMatch(recovery, /handleDeeplinks|client\.auth\.currentUserOrNull\(\)/)
  assert.match(recovery, /auth\.retrieveUser\(credentials.accessToken\)/)
  assert.match(recovery, /claims.sessionId == credentials.claims.sessionId/)
  assert.match(recovery, /recoveryGrant.requireAuthorized/)
  assert.match(read(root + "java/com/example/arptapp/data/remote/SupabaseClientProvider.kt"), /createRecoveryClient[\s\S]*minimalSettings\(\)/)
})
test("drafts are no-backup, TTL bounded; YouTube client attaches bearer and gateway stays authenticated", () => {
  const draft = read(root + "java/com/example/arptapp/data/preferences/PendingSignUpProfileRepository.kt")
  assert.match(draft, /noBackupFilesDir/); assert.match(draft, /86_400_000L/)
  const ranking = read(root + "java/com/example/arptapp/data/remote/YouTubeRankingRepository.kt")
  assert.match(ranking, /setRequestProperty\("Authorization", "Bearer \$\{session.accessToken\}"\)/)
  assert.match(ranking, /instanceFollowRedirects = false/)
  assert.match(read("supabase/config.toml"), /\[functions.youtube-ranking\]\s+verify_jwt = true/)
})
