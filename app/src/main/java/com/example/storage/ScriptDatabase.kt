package com.example.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.auth.AuditLogEntity
import com.example.auth.AuthDao
import com.example.auth.UserEntity

@Database(
    entities = [ScriptEntity::class, UserEntity::class, AuditLogEntity::class],
    version = 2,
    exportSchema = false
)
abstract class ScriptDatabase : RoomDatabase() {

    abstract fun scriptDao(): ScriptDao
    abstract fun authDao(): AuthDao

    companion object {
        @Volatile
        private var INSTANCE: ScriptDatabase? = null

        fun getDatabase(context: Context): ScriptDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ScriptDatabase::class.java,
                    "replica_app.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
