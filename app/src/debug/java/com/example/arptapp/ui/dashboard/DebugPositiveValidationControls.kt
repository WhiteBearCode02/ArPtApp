package com.example.arptapp.ui.dashboard

import android.graphics.Bitmap
import android.graphics.Color
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.arptapp.ui.camera.yolo.validation.DebugPositiveValidationFactory
import com.example.arptapp.ui.camera.yolo.validation.Exp02PositiveValidation
import com.example.arptapp.ui.camera.yolo.validation.PositiveCandidateInventory

/** Explicit manual capture only; no subscription, frame loop, upload, or contract promotion. */
object DebugPositiveValidationControls {
    fun attach(activity: AppCompatActivity, panel: YoloDiagnosticsPanel,
        requestFrame: ((Bitmap?) -> Unit) -> Boolean, cancelFrame: () -> Unit): DebugValidationControls? {
        val validation = DebugPositiveValidationFactory.create(activity) ?: return null
        return Controls(activity, panel, validation, requestFrame, cancelFrame)
    }

    private class Controls(
        private val activity: AppCompatActivity, panel: YoloDiagnosticsPanel,
        private val validation: Exp02PositiveValidation,
        private val requestFrame: ((Bitmap?) -> Unit) -> Boolean, private val cancelFrame: () -> Unit
    ) : DebugValidationControls {
        private val handler = Handler(Looper.getMainLooper())
        private val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        private val localOnly = CheckBox(activity).apply {
            text = "로컬 검증 세션 · 업로드 안 함"
            setTextColor(Color.WHITE)
            isChecked = false
        }
        private val status = TextView(activity).apply {
            text = "수동 촬영만 저장 · private noBackup · 최대 8장\n실시간 YOLO는 NOT_VERIFIED 유지"
            textSize = 11f
            setTextColor(Color.WHITE)
        }
        private val capture = Button(activity).apply { text = "3초 후 검증 프레임 저장" }
        private val list = Button(activity).apply { text = "저장한 검증 프레임 목록" }
        private val delete = Button(activity).apply { text = "검증 프레임 모두 삭제" }
        private var timer: CountDownTimer? = null
        private var deleteDialog: AlertDialog? = null
        private val admissionLock = Any()
        @Volatile private var closed = false
        private var captureEpoch = 0L
        private var awaitingFrame = false
        private var busy = false
        private var sessionActive = false
        private val timeout = Runnable {
            val cancelled = synchronized(admissionLock) {
                if (!awaitingFrame) false else { captureEpoch++; awaitingFrame = false; true }
            }
            if (cancelled) { cancelFrame(); finishBusy("촬영 시간 초과 · 다시 눌러 주세요") }
        }

        init {
            root.addView(localOnly)
            root.addView(status)
            root.addView(capture)
            root.addView(list)
            root.addView(delete)
            panel.attachValidationControls(root)
            capture.setOnClickListener {
                if (busy || closed) return@setOnClickListener
                setBusy(true)
                val epoch = synchronized(admissionLock) { awaitingFrame = true; ++captureEpoch }
                timer = object : CountDownTimer(3_000, 1_000) {
                    override fun onTick(remaining: Long) { status.text = "촬영까지 ${(remaining + 999) / 1000}초 · 전신을 보여 주세요" }
                    override fun onFinish() {
                        timer = null
                        status.text = "한 프레임 대기 중…"
                        handler.postDelayed(timeout, 5_000)
                        val accepted = requestFrame { frame ->
                            // This callback is on the analyzer thread: copy via the port before returning.
                            synchronized(admissionLock) {
                            if (closed || epoch != captureEpoch || !awaitingFrame) return@synchronized
                            handler.removeCallbacks(timeout)
                            awaitingFrame = false
                            if (frame == null) postFinish(epoch, "촬영 취소 · 화면/카메라 상태를 확인하세요")
                            else if (!validation.saveAndAnalyze(frame) { result ->
                                if (!closed) {
                                    val saved = result.candidate
                                    finishBusy(buildString {
                                        append("${result.status} · native=${result.nativeInferenceExecuted}\n")
                                        if (saved != null) append("${saved.privatePath}\n${saved.width}×${saved.height} · ${saved.bytes} bytes\nSHA256 ${saved.sha256}\n")
                                        append("detections=${result.detections.size}\n")
                                        result.detections.forEach { append("${it.className} (${it.classId}) conf=${it.confidence} bbox=${it.bbox}\n") }
                                        append(result.message)
                                    })
                                }
                            }) postFinish(epoch, "검증 모듈 사용 중 · 저장하지 않았습니다")
                            }
                        }
                        if (!accepted) {
                            synchronized(admissionLock) { awaitingFrame = false }
                            handler.removeCallbacks(timeout); finishBusy("카메라 프레임 요청 불가")
                        }
                    }
                }.start()
            }
            list.setOnClickListener {
                setBusy(true)
                if (!validation.list(::showInventory)) finishBusy("검증 모듈 사용 중")
            }
            delete.setOnClickListener {
                if (closed || busy || sessionActive) return@setOnClickListener
                deleteDialog = AlertDialog.Builder(activity).setTitle("검증 프레임 삭제")
                    .setMessage("이 앱의 수동 검증 사진과 해당 JSON만 삭제합니다. 모델과 운동 기록은 유지합니다.")
                    .setNegativeButton("취소", null).setPositiveButton("삭제") { _, _ ->
                        if (closed || busy || sessionActive) return@setPositiveButton
                        setBusy(true)
                        if (!validation.deleteAll(::showInventory)) finishBusy("검증 모듈 사용 중")
                    }.show()
            }
        }

        private fun showInventory(inventory: PositiveCandidateInventory) {
            if (closed) return
            finishBusy(buildString {
                append("${inventory.status} · ${inventory.candidates.size}/8장 · 삭제 ${inventory.deletedFiles}개\n")
                inventory.candidates.forEach { append("${it.privatePath}\n${it.width}×${it.height} · ${it.bytes} bytes\nSHA256 ${it.sha256}\n") }
            })
        }
        private fun setBusy(value: Boolean) {
            busy = value
            capture.isEnabled = !value
            list.isEnabled = !value
            delete.isEnabled = !value && !sessionActive
        }
        private fun finishBusy(message: String) { if (!closed) { status.text = message; setBusy(false) } }
        private fun postFinish(epoch: Long, message: String) {
            handler.post { synchronized(admissionLock) { if (epoch == captureEpoch) finishBusy(message) } }
        }
        override fun freezeLocalOnlyForSession(): Boolean = localOnly.isChecked
        override fun setSessionActive(active: Boolean) {
            sessionActive = active
            localOnly.isEnabled = !active
            delete.isEnabled = !busy && !active
        }
        override fun onHostPaused() {
            val wasAwaiting = synchronized(admissionLock) {
                captureEpoch++
                awaitingFrame.also { awaitingFrame = false }
            }
            timer?.cancel(); timer = null
            deleteDialog?.dismiss(); deleteDialog = null
            handler.removeCallbacks(timeout)
            cancelFrame()
            if (wasAwaiting) finishBusy("촬영 취소 · 다시 눌러 주세요")
        }
        override fun close() {
            synchronized(admissionLock) { closed = true }
            onHostPaused()
            validation.close()
            (root.parent as? ViewGroup)?.removeView(root)
        }
    }
}
