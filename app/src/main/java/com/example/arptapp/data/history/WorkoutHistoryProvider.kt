package com.example.arptapp.data.history

import android.content.Context
import com.example.arptapp.data.AppDatabase
import com.example.arptapp.data.remote.AuthSessionStore
import com.example.arptapp.data.remote.SupabaseRepository

/** Composition root: activities depend on one repository, not Room/Supabase internals. */
object WorkoutHistoryProvider {
    val accounts = object : WorkoutAccounts {
        override fun current() = AuthSessionStore.workoutAccount.value
    }
    @Volatile private var instance: WorkoutHistoryRepository? = null
    fun get(context: Context): WorkoutHistoryRepository = instance ?: synchronized(this) {
        instance ?: WorkoutHistoryRepository(RoomWorkoutDataSource(AppDatabase.getDatabase(context).exerciseDao()),
            SupabaseRepository(accounts), accounts).also { instance = it }
    }
}
