package com.example.arptapp.presentation.report

/** UI authorization gate only; the repository retains its independent disabled-by-default gate. */
object ReportAiUiPolicy {
    fun canRequest(enabled: Boolean, hasMeasuredReps: Boolean, loading: Boolean, completed: Boolean): Boolean =
        enabled && hasMeasuredReps && !loading && !completed
}
