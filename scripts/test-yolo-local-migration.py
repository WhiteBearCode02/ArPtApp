"""SQLite semantics of the exact additive Room SQL; NOT Android Room instrumentation."""
import re
import sqlite3
from pathlib import Path
import unittest


SOURCE = (Path(__file__).resolve().parents[1] / "app/src/main/java/com/example/arptapp/data/AppDatabase.kt").read_text(encoding="utf-8")
BLOCK = SOURCE.split("internal val MIGRATION_5_6", 1)[1].split("internal val MIGRATION_6_7", 1)[0]
SQL = re.findall(r'db\.execSQL\("([^"]+)"\)', BLOCK)
SYNC_BLOCK = SOURCE.split("internal val MIGRATION_6_7", 1)[1]
SYNC_SQL = re.findall(r'db\.execSQL\("([^"]+)"\)', SYNC_BLOCK)


class LocalMigrationSqlTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.addCleanup(self.db.close)
        self.db.execute("""CREATE TABLE exercise_records (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, userId TEXT NOT NULL,
            date TEXT NOT NULL, totalCount INTEGER NOT NULL, duration INTEGER NOT NULL,
            burnedCalories REAL NOT NULL, exerciseType TEXT NOT NULL, averageScore REAL NOT NULL,
            scoresJson TEXT NOT NULL, feedbackMessage TEXT NOT NULL)""")
        self.original = (7, "local-owner", "2026-10-15 20:00", 4, 80, 2.0, "SQUAT", 85.0, "[80,90]", "saved feedback")
        self.db.execute("INSERT INTO exercise_records VALUES (?,?,?,?,?,?,?,?,?,?)", self.original)

    def migrate(self):
        for statement in SQL:
            self.db.execute(statement)

    def test_exact_migration_is_additive_only(self):
        self.assertEqual(["ALTER TABLE exercise_records ADD COLUMN yoloDiagnosticsJson TEXT"], SQL)
        self.assertNotIn("fallbackToDestructiveMigration", SOURCE)

    def test_existing_columns_and_record_are_preserved(self):
        self.migrate()
        result = self.db.execute("SELECT * FROM exercise_records").fetchone()
        self.assertEqual(self.original, result[:-1])
        self.assertIsNone(result[-1])

    def test_new_nullable_text_column_and_local_round_trip(self):
        self.migrate()
        column = self.db.execute("PRAGMA table_info(exercise_records)").fetchall()[-1]
        self.assertEqual(("yoloDiagnosticsJson", "TEXT", 0, None), column[1:5])
        raw = '{"modelStatus":"MODEL_MISSING","processedFrames":0}'
        self.db.execute("UPDATE exercise_records SET yoloDiagnosticsJson=? WHERE id=7", (raw,))
        self.assertEqual(raw, self.db.execute("SELECT yoloDiagnosticsJson FROM exercise_records WHERE id=7").fetchone()[0])
        self.assertEqual("[80,90]", self.db.execute("SELECT scoresJson FROM exercise_records WHERE id=7").fetchone()[0])

    def migrate_sync(self):
        self.migrate()
        self.db.execute("UPDATE exercise_records SET yoloDiagnosticsJson='private-existing' WHERE id=7")
        for statement in SYNC_SQL:
            self.db.execute(statement)

    def test_sync_migration_preserves_every_original_field_and_diagnostics(self):
        self.migrate_sync()
        selected = self.db.execute("SELECT id,userId,date,totalCount,duration,burnedCalories,exerciseType,averageScore,scoresJson,feedbackMessage FROM exercise_records").fetchone()
        self.assertEqual(self.original, selected)
        state = self.db.execute("SELECT sessionId,syncState,cloudEligible,detailsCached,yoloDiagnosticsJson FROM exercise_records").fetchone()
        self.assertEqual((None, "LOCAL_ONLY", 0, 1, "private-existing"), state)

    def test_sync_unique_index_prevents_duplicate_stable_session(self):
        self.migrate_sync()
        self.db.execute("UPDATE exercise_records SET sessionId='stable-session' WHERE id=7")
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("INSERT INTO exercise_records(userId,date,totalCount,duration,burnedCalories,exerciseType,averageScore,scoresJson,feedbackMessage,sessionId) VALUES ('other','2026-11-01 20:00',2,30,1.0,'SQUAT',85,'[]','','stable-session')")
        self.assertEqual(1, self.db.execute("SELECT count(*) FROM exercise_records").fetchone()[0])

    def test_sync_nullable_legacy_ids_do_not_collapse_old_records(self):
        self.migrate_sync()
        self.db.execute("INSERT INTO exercise_records(userId,date,totalCount,duration,burnedCalories,exerciseType,averageScore,scoresJson,feedbackMessage) VALUES ('local-owner','2026-11-01 20:00',2,30,1.0,'SQUAT',85,'[]','')")
        self.assertEqual(2, self.db.execute("SELECT count(*) FROM exercise_records WHERE sessionId IS NULL").fetchone()[0])

    def test_sync_owner_filter_and_status_update_do_not_cross_accounts(self):
        self.migrate_sync()
        self.db.execute("UPDATE exercise_records SET sessionId='stable-session',cloudEligible=1,syncState='PENDING' WHERE id=7")
        self.db.execute("UPDATE exercise_records SET syncState='SYNCED' WHERE userId='other' AND sessionId='stable-session'")
        self.assertEqual("PENDING", self.db.execute("SELECT syncState FROM exercise_records").fetchone()[0])
        self.assertEqual(0, self.db.execute("SELECT count(*) FROM exercise_records WHERE userId='other'").fetchone()[0])


if __name__ == "__main__":
    unittest.main()
