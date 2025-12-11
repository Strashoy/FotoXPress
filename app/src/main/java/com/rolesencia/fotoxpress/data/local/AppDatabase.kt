package com.rolesencia.fotoxpress.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.rolesencia.fotoxpress.data.local.dao.SesionDao
import com.rolesencia.fotoxpress.data.local.entity.FotoEntity
import com.rolesencia.fotoxpress.data.local.entity.SesionEntity

@Database(entities = [SesionEntity::class, FotoEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sesionDao(): SesionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fotoxpress_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}