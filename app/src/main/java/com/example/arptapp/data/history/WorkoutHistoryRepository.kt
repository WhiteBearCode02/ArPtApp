package com.example.arptapp.data.history

import com.example.arptapp.data.ExerciseRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * Local-first account cache coordinator. No timers, WorkManager, realtime subscriptions or infinite
 * retries. One process-wide instance serializes saves/sync; epoch checks fence every remote await.
 * Remote immutable snapshots are authoritative once acknowledged; local pending data is retained.
 */
class WorkoutHistoryRepository(
    private val local: WorkoutLocalDataSource,
    private val remote: WorkoutRemoteDataSource,
    private val accounts: WorkoutAccounts,
    private val enabled: () -> Boolean = { true },
    private val now: () -> Long = System::currentTimeMillis
) {
    private val mutex = Mutex()
    private val attempts = mutableMapOf<WorkoutAccount, Long>()
    private val offsets = mutableMapOf<WorkoutAccount, Int>()

    fun currentAccount(): WorkoutAccount? = accounts.current()
    fun isCurrent(account: WorkoutAccount): Boolean = accounts.current() == account

    suspend fun cached(account: WorkoutAccount): List<ExerciseRecord> {
        accounts.requireCurrent(account)
        val rows = local.records(account.userId)
        accounts.requireCurrent(account)
        return rows.filter { it.userId == account.userId }
    }

    /** A cloud failure must never turn a successful local commit into a lost workout. */
    suspend fun save(account: WorkoutAccount, record: ExerciseRecord,
        onLocalSaved: (ExerciseRecord) -> Unit = {}): ExerciseRecord {
        accounts.requireCurrent(account)
        require(record.userId == account.userId && !record.sessionId.isNullOrBlank())
        val saved = local.save(record)
        accounts.requireCurrent(account)
        onLocalSaved(saved) // never wait for a queued/slow network sync before acknowledging Room
        if (!saved.cloudEligible || saved.syncState == WorkoutSyncState.SYNCED.name || !enabled()) return saved
        return mutex.withLock {
            accounts.requireCurrent(account)
            val current = local.find(account.userId, requireNotNull(saved.sessionId)) ?: saved
            if (current.syncState != WorkoutSyncState.SYNCED.name) upload(account, current)
            accounts.requireCurrent(account)
            val latest = local.find(account.userId, requireNotNull(saved.sessionId)) ?: saved
            accounts.requireCurrent(account)
            latest
        }
    }

    suspend fun syncCurrentUser(force: Boolean = false): SyncOutcome {
        val account = accounts.current() ?: return SyncOutcome.ACCOUNT_CHANGED
        return mutex.withLock {
            if (!isCurrent(account)) return@withLock SyncOutcome.ACCOUNT_CHANGED
            if (!enabled()) return@withLock SyncOutcome.DISABLED
            val last = attempts[account]
            if (!force && last != null && now() - last < AUTO_SYNC_COOLDOWN_MS) return@withLock SyncOutcome.SKIPPED
            attempts.clear() // only retain the currently signed-in account's bounded bookkeeping
            attempts[account] = now()
            try {
                withTimeout(SYNC_TIMEOUT_MS) {
                    var failed = false
                    val pending = local.pending(account.userId, UPLOAD_BATCH)
                    for (row in pending) {
                        accounts.requireCurrent(account)
                        if (!upload(account, row)) failed = true
                    }
                    var offset = offsets[account] ?: 0
                    repeat(MAX_PAGES) {
                        accounts.requireCurrent(account)
                        val page = remote.summaries(account, offset, PAGE_SIZE)
                        accounts.requireCurrent(account)
                        require(page.size <= PAGE_SIZE && page.all { it.userId == account.userId })
                        for (summary in page) {
                            accounts.requireCurrent(account)
                            local.merge(summary)
                        }
                        offset += page.size
                        offsets.clear()
                        offsets[account] = offset
                        if (page.size < PAGE_SIZE) {
                            offsets.remove(account)
                            return@withTimeout if (failed) SyncOutcome.FAILED
                            else if (local.pending(account.userId, 1).isNotEmpty()) SyncOutcome.PARTIAL
                            else SyncOutcome.SUCCESS
                        }
                    }
                    SyncOutcome.PARTIAL // explicit user retry continues the bounded summary cursor
                }
            } catch (_: TimeoutCancellationException) {
                SyncOutcome.FAILED
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (isCurrent(account)) SyncOutcome.FAILED else SyncOutcome.ACCOUNT_CHANGED
            }
        }
    }

    private suspend fun upload(account: WorkoutAccount, row: ExerciseRecord): Boolean {
        val id = requireNotNull(row.sessionId)
        try {
            accounts.requireCurrent(account)
            withTimeout(UPLOAD_TIMEOUT_MS) { remote.upload(account, row) }
            accounts.requireCurrent(account)
            local.state(account.userId, id, WorkoutSyncState.SYNCED)
            return true
        } catch (_: TimeoutCancellationException) {
            if (isCurrent(account)) local.state(account.userId, id, WorkoutSyncState.FAILED)
        } catch (cancelled: CancellationException) {
            throw cancelled // PENDING survives lifecycle cancellation and can retry the same ID
        } catch (_: Exception) {
            if (isCurrent(account)) local.state(account.userId, id, WorkoutSyncState.FAILED)
        }
        return false
    }

    suspend fun detailed(account: WorkoutAccount, record: ExerciseRecord): ExerciseRecord {
        accounts.requireCurrent(account)
        require(record.userId == account.userId)
        val id = record.sessionId ?: return record
        val cached = local.find(account.userId, id) ?: error("기록이 없습니다.")
        accounts.requireCurrent(account)
        if (cached.detailsCached) return cached
        check(enabled()) { "동기화가 비활성 상태입니다." }
        val detail = withTimeout(UPLOAD_TIMEOUT_MS) { remote.details(account, id) }
        accounts.requireCurrent(account)
        local.details(account.userId, id, detail)
        accounts.requireCurrent(account)
        val latest = checkNotNull(local.find(account.userId, id))
        accounts.requireCurrent(account)
        return latest
    }

    companion object {
        const val AUTO_SYNC_COOLDOWN_MS = 60_000L
        const val SYNC_TIMEOUT_MS = 45_000L
        const val UPLOAD_TIMEOUT_MS = 10_000L
        const val UPLOAD_BATCH = 20
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 5
    }
}
