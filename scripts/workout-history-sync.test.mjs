import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
const read = p => readFileSync(p, 'utf8')
const base = 'app/src/main/java/com/example/arptapp/'
test('one Room-first save path and no independent Dashboard remote upload', () => {
  assert.doesNotMatch(read(base + 'DashboardActivity.kt'), /SupabaseRepository|uploadSession\(/)
  assert.match(read(base + 'presentation/report/WorkoutResultViewModel.kt'), /repository\.save\(/)
  const repo = read(base + 'data/history/WorkoutHistoryRepository.kt')
  assert.ok(repo.indexOf('local.save(record)') < repo.indexOf('upload(account, current)'))
  assert.ok(repo.indexOf('onLocalSaved(saved)') < repo.indexOf('upload(account, current)'))
  assert.doesNotMatch(repo, /while\s*\(true\)|WorkManager\.|delay\(/)
})
test('summary projection excludes heavy report and detail always checks parent owner', () => {
  const remote = read(base + 'data/remote/SupabaseRepository.kt')
  const list = remote.slice(remote.indexOf('override suspend fun summaries'), remote.indexOf('override suspend fun details'))
  assert.doesNotMatch(list, /report_json|exercise_rep_records|Columns.ALL/)
  assert.match(list, /eq\("user_id", account.userId\)/)
  assert.match(remote, /header.userId == account.userId/)
  assert.match(remote, /currentUserOrNull\(\)\?\.id == account.userId/)
  assert.doesNotMatch(remote, /Log\.|println|yoloDiagnostics|service_role/)
})
test('public DTOs never serialize Room, camera, developer diagnostics or credentials', () => {
  const dto = read(base + 'data/remote/RemoteExerciseSession.kt')
  assert.doesNotMatch(dto, /val (yolo|password|token|landmarks|image)/i)
  assert.match(dto, /WorkoutPayloadCodec\.analyses\(row.scoresJson\)/)
  assert.doesNotMatch(read(base + 'data/remote/SupabaseRepository.kt'), /encodeToJsonElement\(ExerciseRecord|encodeToString\(record/)
})
test('additive migration keeps old rows local-only and unique session index', () => {
  const db = read(base + 'data/AppDatabase.kt')
  const migration = db.slice(db.indexOf('internal val MIGRATION_6_7'))
  assert.doesNotMatch(migration, /DROP TABLE|DELETE FROM|fallbackToDestructiveMigration/)
  assert.match(migration, /DEFAULT 'LOCAL_ONLY'/)
  assert.match(migration, /UNIQUE INDEX.*sessionId/)
  const dao = read(base + 'data/ExerciseDao.kt')
  assert.doesNotMatch(dao, /OnConflictStrategy.REPLACE/)
  assert.match(dao, /yoloDiagnosticsJson = existing.yoloDiagnosticsJson/)
})
test('SQL has owner RLS, explicit authentication, atomic idempotency and no definer/update grants', () => {
  const sql = read('supabase/workout-history-sync.sql').replace(/^--.*$/gm, '')
  assert.match(sql, /security invoker set search_path = ''/i)
  assert.doesNotMatch(sql, /security definer|grant\s+(?:all|update|delete)\s+on/i)
  assert.match(sql, /v.user_id is distinct from auth.uid\(\)/)
  assert.match(sql, /s.user_id = \(select auth.uid\(\)\)/)
  assert.match(sql, /on conflict \(id\) do nothing/)
  assert.match(sql, /on conflict\(session_id,rep_number\) do nothing/)
  assert.match(sql, /revoke all on function.*from public, anon, authenticated/)
  assert.match(sql, /Unexpected workout RLS policy/)
})
test('history and statistics use shared ViewModel rather than direct database access', () => {
  for (const file of ['HistoryActivity.kt', 'WorkoutStatisticsActivity.kt']) {
    const activity = read(base + file)
    assert.doesNotMatch(activity, /AppDatabase|exerciseDao\(/)
    assert.match(activity, /WorkoutHistoryViewModel/)
    assert.match(activity, /AuthSessionStore.workoutAccount.value/)
  }
})
