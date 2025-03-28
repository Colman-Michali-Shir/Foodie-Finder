package com.example.foodie_finder.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.foodie_finder.base.MyApplication
import com.example.foodie_finder.data.local.dao.PostDao
import com.example.foodie_finder.data.local.dao.SavedPostDao
import com.example.foodie_finder.data.model.Converters

@Database(entities = [Post::class, User::class, SavedPost::class], version = 1)
@TypeConverters(Converters::class)
abstract class AppLocalDbRepository : RoomDatabase() {
    abstract fun postDao(): PostDao
    abstract fun savedPostDao(): SavedPostDao
}

object AppLocalDb {

    val database: AppLocalDbRepository by lazy {
        val context = MyApplication.Globals.context
            ?: throw IllegalStateException("Application context not available")

        Room.databaseBuilder(
            context,
            AppLocalDbRepository::class.java,
            "dbFileName.db"
        ).fallbackToDestructiveMigration()
            .build()

    }
}