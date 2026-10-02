package com.lungelo.macrodime.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The app's only database, stored in the app's private storage. Version 1 is the
 * first shipped schema; its exported JSON lives in app/schemas, and any change
 * after release is a written migration, never a destructive rebuild of the
 * user's data.
 */
@Database(
    entities = [
        UserProfileEntity::class,
        BodyMeasurementEntity::class,
        FoodItemEntity::class,
        MealPlanEntity::class,
        PlannedMealEntity::class,
        MealPortionEntity::class,
        GroceryItemEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class MacroDimeDatabase : RoomDatabase() {

    abstract fun dao(): MacroDimeDao

    companion object {
        const val FILE_NAME = "macrodime.db"

        fun open(context: Context): MacroDimeDatabase =
            Room.databaseBuilder(context, MacroDimeDatabase::class.java, FILE_NAME).build()

        /** For tests: nothing touches the disk. */
        fun inMemory(context: Context): MacroDimeDatabase =
            Room.inMemoryDatabaseBuilder(context, MacroDimeDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
