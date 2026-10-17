package com.example.arptapp.ui.camera

/** One explicit request consumes at most one borrowed frame. No frame is retained by this gate. */
class ManualValidationFrameGate<F>(private val timeoutMs: Long = 5_000) {
    private data class Request<F>(val generation: Long, val deadline: Long, val callback: (F?) -> Unit)
    private var pending: Request<F>? = null

    @Synchronized fun request(generation: Long, nowMs: Long, callback: (F?) -> Unit): Boolean {
        if (pending != null) return false
        pending = Request(generation, nowMs + timeoutMs, callback)
        return true
    }

    fun offer(frame: F, generation: Long, nowMs: Long) {
        val request = synchronized(this) { pending.also { pending = null } } ?: return
        // Ordinary storage/linkage failures are isolated; fatal VM errors must still escape.
        deliver(request.callback, frame.takeIf { generation == request.generation && nowMs <= request.deadline })
    }

    fun cancel() {
        val request = synchronized(this) { pending.also { pending = null } }
        request?.let { deliver(it.callback, null) }
    }

    private fun deliver(callback: (F?) -> Unit, frame: F?) {
        try { callback(frame) }
        catch (_: Exception) { /* Isolate ordinary debug callback failures, not fatal VM errors. */ }
        catch (_: LinkageError) { /* Optional native validation must not break MediaPipe. */ }
    }
}
