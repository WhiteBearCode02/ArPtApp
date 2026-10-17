package com.example.arptapp.data

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.Process
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arptapp.BuildConfig
import com.example.arptapp.R
import com.example.arptapp.databinding.ItemReportMetricGroupBinding
import com.example.arptapp.databinding.ItemReportMetricRowBinding
import com.example.arptapp.presentation.report.ReportNavigation
import com.example.arptapp.presentation.report.WorkoutReportData
import com.example.arptapp.presentation.report.YoloSessionDiagnosticsPresentation
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in, read-only verification of ONE workout physically saved by the user, never a DB dump.
 * Run each method separately with a unique -e sessionEvidenceRun <safe_run_id> argument.
 * The baseline observes MAX(id) only; capture fails if zero or multiple new rows exist.
 * No accounts, profiles, tokens, raw analyses, images, landmarks or user IDs leave local memory.
 * Evidence is exclusively created inside the target DEBUG app's private noBackupFilesDir.
 * This class never inserts a workout or changes a counter/score/contract gate.
 */
@RunWith(AndroidJUnit4::class)
class YoloSessionPersistenceDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val databaseFile get() = context.getDatabasePath("arpt_database")

    @Test fun readBaseline() {
        requireDebug()
        val maxId = if (databaseFile.isFile) withReadOnlyDatabase { db ->
            db.rawQuery("SELECT COALESCE(MAX(id), 0) FROM exercise_records", null).use { cursor ->
                check(cursor.moveToFirst()); cursor.getInt(0)
            }
        } else 0
        writeEvidence("baseline.json", JSONObject().put("schemaVersion", 1)
            .put("source", "READ_ONLY_MAX_RECORD_ID")
            .put("baselineMaxRecordId", maxId).put("databaseExisted", databaseFile.isFile)
            .put("processId", Process.myPid()))
    }

    @Test fun captureNewlySavedRecord() {
        requireDebug()
        val baseline = readEvidence("baseline.json").getInt("baselineMaxRecordId")
        val selected = withReadOnlyDatabase { db ->
            db.rawQuery("SELECT id FROM exercise_records WHERE id > ? ORDER BY id ASC LIMIT 2",
                arrayOf(baseline.toString())).use { cursor ->
                check(cursor.moveToFirst()) { "No newly user-saved record after baseline; do not create a synthetic workout." }
                val id = cursor.getInt(0)
                check(!cursor.moveToNext()) { "Multiple new records; start a new unambiguous baseline." }
                readSelectedRecord(db, id)
            }
        }
        writeEvidence("selected_record.json", evidence(selected).put("baselineMaxRecordId", baseline)
            .put("newRowCount", 1).put("processId", Process.myPid()))
    }

    @Test fun verifySameRecordAfterRestart() {
        requireDebug()
        val saved = readEvidence("selected_record.json")
        check(saved.getInt("processId") != Process.myPid()) {
            "Use a fresh app process after force-stop; same-process readback is not restart evidence."
        }
        val selected = withReadOnlyDatabase { db -> readSelectedRecord(db, saved.getInt("recordId")) }
        val observed = evidence(selected)
        assertEquals(saved.getInt("recordId"), observed.getInt("recordId"))
        assertEquals(saved.getInt("totalCount"), observed.getInt("totalCount"))
        assertEquals(saved.getLong("durationSeconds"), observed.getLong("durationSeconds"))
        assertEquals(saved.getString("diagnosticsState"), observed.getString("diagnosticsState"))
        assertEquals(saved.getString("diagnosticsSha256"), observed.getString("diagnosticsSha256"))
        assertEquals(saved.optJSONObject("diagnostics")?.toString(), observed.optJSONObject("diagnostics")?.toString())
        // History uses this same mapper and navigation envelope, not a regenerated live snapshot.
        val report = WorkoutReportData.fromRecord(selected)
        val readback = ReportNavigation.read(ReportNavigation.intent(context, report))
        assertEquals(report, readback)
        assertEquals(selected.yoloDiagnosticsJson?.let(YoloSessionDiagnosticsCodec::decode),
            readback.yoloDiagnosticsJson?.let(YoloSessionDiagnosticsCodec::decode))
        writeEvidence("restart_verification.json", observed
            .put("capturedProcessId", saved.getInt("processId")).put("processId", Process.myPid())
            .put("sameRecord", true).put("freshProcessObserved", true)
            .put("historyReportMapperRoundTrip", true))
    }

    @Test fun openSelectedReport() {
        requireDebug()
        val saved = readEvidence("selected_record.json")
        val selected = withReadOnlyDatabase { db -> readSelectedRecord(db, saved.getInt("recordId")) }
        assertEquals(saved.getString("diagnosticsSha256"), evidence(selected).getString("diagnosticsSha256"))
        val report = WorkoutReportData.fromRecord(selected)
        val activity = instrumentation.startActivitySync(ReportNavigation.intent(context, report)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        val diagnostics = YoloSessionDiagnosticsCodec.decode(report.yoloDiagnosticsJson)
        instrumentation.runOnMainSync {
            val section = activity.findViewById<View>(R.id.yoloShadowSection)
            val details = activity.findViewById<View>(R.id.yoloShadowDetails)
            if (diagnostics == null) {
                assertEquals(View.GONE, section.visibility)
            } else {
                assertEquals(View.VISIBLE, section.visibility)
                assertEquals(View.GONE, details.visibility)
                val note = context.getString(R.string.yolo_report_developer_note)
                assertTrue(note.contains("운동 점수와 자세 분석 판정에는 사용되지 않습니다"))
                activity.findViewById<View>(R.id.btnToggleYoloShadow).performClick()
                assertEquals(View.VISIBLE, details.visibility)
                val container = activity.findViewById<android.view.ViewGroup>(R.id.yoloShadowMetricGroups)
                val expected = YoloSessionDiagnosticsPresentation.groups(diagnostics)
                assertEquals(expected.size, container.childCount)
                expected.forEachIndexed { groupIndex, expectedGroup ->
                    val group = ItemReportMetricGroupBinding.bind(container.getChildAt(groupIndex))
                    assertEquals(expectedGroup.title, group.tvMetricGroupTitle.text.toString())
                    assertEquals(expectedGroup.rows.size, group.metricRows.childCount)
                    expectedGroup.rows.forEachIndexed { rowIndex, expectedRow ->
                        val row = ItemReportMetricRowBinding.bind(group.metricRows.getChildAt(rowIndex))
                        assertEquals(expectedRow.label, row.tvMetricLabel.text.toString())
                        assertEquals(expectedRow.value, row.tvMetricValue.text.toString())
                    }
                }
            }
        }
        writeEvidence("report_render_verification.json", JSONObject().put("schemaVersion", 1)
            .put("source", "SELECTED_PERSISTED_RECORD_HISTORY_REPORT_MAPPER")
            .put("recordId", selected.id).put("debugOnly", true).put("initiallyCollapsed", diagnostics != null)
            .put("savedDiagnosticGroupsRendered", diagnostics != null)
            .put("legacyNullHidden", diagnostics == null)
            .put("historyRowTapPerformed", false))
        // Intentionally leave this selected local report open for the owner's visual inspection.
    }

    private fun requireDebug() = check(BuildConfig.DEBUG) { "Private local evidence requires a DEBUG app." }

    private fun <T> withReadOnlyDatabase(block: (SQLiteDatabase) -> T): T {
        check(databaseFile.isFile) { "Local workout database is absent." }
        return SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use(block)
    }

    private fun readSelectedRecord(db: SQLiteDatabase, id: Int): ExerciseRecord {
        require(id > 0)
        return db.rawQuery("SELECT id, userId, date, totalCount, duration, burnedCalories, exerciseType, " +
            "averageScore, scoresJson, feedbackMessage, yoloDiagnosticsJson " +
            "FROM exercise_records WHERE id = ? LIMIT 1", arrayOf(id.toString())).use { cursor ->
            check(cursor.moveToFirst()) { "Selected local record no longer exists." }
            fun string(name: String) = cursor.getString(cursor.getColumnIndexOrThrow(name))
            ExerciseRecord(id = id, userId = string("userId"), date = string("date"),
                totalCount = cursor.getInt(cursor.getColumnIndexOrThrow("totalCount")),
                duration = cursor.getLong(cursor.getColumnIndexOrThrow("duration")),
                burnedCalories = cursor.getDouble(cursor.getColumnIndexOrThrow("burnedCalories")),
                exerciseType = string("exerciseType"), averageScore = cursor.getFloat(cursor.getColumnIndexOrThrow("averageScore")),
                scoresJson = string("scoresJson"), feedbackMessage = string("feedbackMessage"),
                yoloDiagnosticsJson = cursor.nullableString("yoloDiagnosticsJson"))
        }
    }

    private fun evidence(record: ExerciseRecord): JSONObject {
        val raw = record.yoloDiagnosticsJson
        val diagnostics = YoloSessionDiagnosticsCodec.decode(raw)
        check(raw == null || diagnostics != null) { "Selected row contains malformed diagnostics; do not expose raw contents." }
        val normalized = YoloSessionDiagnosticsCodec.encode(diagnostics)
        return JSONObject().put("schemaVersion", 1).put("source", "READ_ONLY_SELECTED_USER_CREATED_ROOM_ROW")
            .put("recordId", record.id).put("totalCount", record.totalCount).put("durationSeconds", record.duration)
            .put("diagnosticsState", if (normalized == null) "ABSENT_LEGACY_COMPATIBLE" else "PRESENT_VALIDATED")
            .put("diagnosticsSha256", normalized?.let(::sha256) ?: "ABSENT")
            .put("diagnostics", normalized?.let(::JSONObject) ?: JSONObject.NULL)
            .put("recordWrittenByTest", false).put("inferenceExecutedByThisTest", false)
            .put("accountProfileAndAnalysisDataExported", false)
    }

    private fun evidenceDirectory(): File {
        val run = InstrumentationRegistry.getArguments().getString("sessionEvidenceRun").orEmpty()
        require(Regex("[a-z0-9][a-z0-9_-]{0,63}").matches(run)) { "Pass a unique safe -e sessionEvidenceRun argument." }
        val root = context.noBackupFilesDir.canonicalFile
        val parent = File(root, "yolo_session_validation")
        val directory = File(parent, run)
        check(parent.canonicalPath == parent.absolutePath && directory.canonicalPath == directory.absolutePath) {
            "Symlink evidence directories are not allowed."
        }
        check(directory.isDirectory || directory.mkdirs()) { "Could not create private evidence directory." }
        return directory
    }

    private fun readEvidence(name: String): JSONObject {
        val file = File(evidenceDirectory(), name)
        check(file.canonicalPath == file.absolutePath && file.isFile && file.length() in 1L..65_536L) {
            "Expected bounded private evidence file is absent or invalid."
        }
        return JSONObject(file.readText(Charsets.UTF_8))
    }

    private fun writeEvidence(name: String, value: JSONObject) {
        val file = File(evidenceDirectory(), name)
        check(file.canonicalPath == file.absolutePath && file.createNewFile()) { "Refusing to overwrite existing private evidence." }
        file.setReadable(false, false); file.setWritable(false, false)
        check(file.setReadable(true, true) && file.setWritable(true, true)) { "Could not restrict private evidence permissions." }
        file.writeText(value.toString(2), Charsets.UTF_8)
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun Cursor.nullableString(name: String): String? = getColumnIndexOrThrow(name).let { if (isNull(it)) null else getString(it) }
}
