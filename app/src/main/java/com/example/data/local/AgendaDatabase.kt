package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AudienciaEntity::class,
        AgendaCompartidaEntity::class,
        PendingSyncOpEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AgendaDatabase : RoomDatabase() {
    abstract fun agendaDao(): AgendaDao

    companion object {
        @Volatile
        private var INSTANCE: AgendaDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE agendas_compartidas ADD COLUMN appTitle TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE agendas_compartidas ADD COLUMN calendarTitle TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE agendas_compartidas ADD COLUMN directoryTitle TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE agendas_compartidas ADD COLUMN summaryTitle TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE agendas_compartidas ADD COLUMN pendingSync INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AgendaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AgendaDatabase::class.java,
                    "agenda_audiencias_room.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
