package it.avanzi.spese.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import it.avanzi.spese.domain.Frequency
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MigrationTest {
    @Test fun bimonthlyBecomesEveryTwoMonthsAndChargesGetSpreadMonths() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val file = context.getDatabasePath("migration.db").apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            V1_SCHEMA.forEach(db::execSQL)
            db.execSQL("INSERT INTO categories VALUES (1, 'Casa', 0, 'home', NULL, 0, 0)")
            db.execSQL("INSERT INTO recurrings VALUES (1, 'Luce', 9000, 1, 'home', 'BIMONTHLY', 10, NULL, NULL, 20454, 1, 1, 1, 20454)")
            db.execSQL("INSERT INTO recurrings VALUES (2, 'Assicurazione', 60000, 1, 'home', 'YEARLY', 10, NULL, 3, 20522, 0, 1, 1, 20522)")
            db.execSQL("INSERT INTO recurrings VALUES (3, 'Affitto', 80000, 1, 'home', 'MONTHLY', 1, NULL, NULL, 20454, 0, 1, 1, 20454)")
            db.execSQL("INSERT INTO expenses VALUES (1, 9000, 1, 'Luce', 20454, NULL, 1, 'CONFIRMED', '1970-01-01T00:00:00Z')")
            db.execSQL("INSERT INTO expenses VALUES (2, 60000, 1, 'Assicurazione', 20522, NULL, 2, 'CONFIRMED', '1970-01-01T00:00:00Z')")
            db.execSQL("INSERT INTO expenses VALUES (3, 80000, 1, 'Affitto', 20454, NULL, 3, 'CONFIRMED', '1970-01-01T00:00:00Z')")
            db.execSQL("INSERT INTO expenses VALUES (4, 300, 1, NULL, 20454, NULL, NULL, 'CONFIRMED', '1970-01-01T00:00:00Z')")
            db.version = 1
        }
        val database = Room.databaseBuilder(context, SpeseDatabase::class.java, file.absolutePath)
            .addMigrations(SpeseDatabase.MIGRATION_1_2).allowMainThreadQueries().build()
        try {
            val recurrings = database.dao().recurrings().first().associateBy { it.id }
            assertEquals(Frequency.MONTHLY to 2, recurrings.getValue(1).let { it.frequency to it.every })
            assertEquals(Frequency.YEARLY to 1, recurrings.getValue(2).let { it.frequency to it.every })
            assertEquals(1, recurrings.getValue(3).every)
            val spread = database.dao().expenses().first().associate { it.id to it.spreadMonths }
            assertEquals(mapOf(1L to 2, 2L to 12, 3L to null, 4L to null), spread)
        } finally {
            database.close()
        }
    }

    private companion object {
        val V1_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `colorArgb` INTEGER NOT NULL, `iconKey` TEXT NOT NULL, `monthlyLimitCents` INTEGER, `sortOrder` INTEGER NOT NULL, `isSystem` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amountCents` INTEGER NOT NULL, `categoryId` INTEGER NOT NULL, `description` TEXT, `date` INTEGER NOT NULL, `time` INTEGER, `recurringId` INTEGER, `status` TEXT NOT NULL, `createdAt` TEXT NOT NULL, FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
            "CREATE INDEX IF NOT EXISTS `index_expenses_categoryId` ON `expenses` (`categoryId`)",
            "CREATE INDEX IF NOT EXISTS `index_expenses_date` ON `expenses` (`date`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_expenses_recurringId_date` ON `expenses` (`recurringId`, `date`)",
            "CREATE TABLE IF NOT EXISTS `recurrings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `amountCents` INTEGER NOT NULL, `categoryId` INTEGER NOT NULL, `iconKey` TEXT NOT NULL, `frequency` TEXT NOT NULL, `dayOfMonth` INTEGER, `dayOfWeek` TEXT, `monthOfYear` INTEGER, `anchorDate` INTEGER NOT NULL, `isVariable` INTEGER NOT NULL, `autoRecord` INTEGER NOT NULL, `active` INTEGER NOT NULL, `lastGeneratedDate` INTEGER, FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
            "CREATE INDEX IF NOT EXISTS `index_recurrings_categoryId` ON `recurrings` (`categoryId`)",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'd1ebfa6b499af1240d2987543d6ae479')",
        )
    }
}
