package com.example.budge.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.budge.data.local.BudgeDatabase
import com.example.budge.data.local.dao.BudgetDao
import com.example.budge.data.local.dao.CategoryDao
import com.example.budge.data.local.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    /**
     * Builds the single Room database instance ("budge.db") and applies the
     * schema migrations. Scoped to the singleton because Room is expensive to
     * create and should be shared by all DAOs.
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): BudgeDatabase =
        Room
            .databaseBuilder(
                context,
                BudgeDatabase::class.java,
                "budge.db",
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_1_3,
                // Downgrades. Room refuses to open a file whose version it has no path back
                // from, so installing an older build over a newer one used to crash the app
                // on every launch, with clearing app data — and so deleting the ledger — as
                // the only way back in. That is a step a user can really take: the app's own
                // update screen opens the releases page, where every older APK is still
                // downloadable.
                MIGRATION_3_2,
                MIGRATION_2_1,
                MIGRATION_3_1,
            ).build()

    // The DAOs are short-lived and tied to the database instance, so they are
    // provided as singletons via the component scope rather than re-created.
    @Provides
    fun provideTransactionDao(database: BudgeDatabase): TransactionDao = database.transactionDao()

    @Provides
    fun provideCategoryDao(database: BudgeDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun provideBudgetDao(database: BudgeDatabase): BudgetDao = database.budgetDao()

    // Versions 1, 2 and 3 all share the exact same schema (identity hash
    // a2e348b51e4e109c54bf133f527a271d), so these migrations are no-ops.
    private val MIGRATION_1_2 =
        object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {}
        }

    private val MIGRATION_2_3 =
        object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {}
        }

    private val MIGRATION_1_3 =
        object : Migration(1, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {}
        }

    private val MIGRATION_3_2 = noOpMigration(3, 2)

    private val MIGRATION_2_1 = noOpMigration(2, 1)

    private val MIGRATION_3_1 = noOpMigration(3, 1)

    /**
     * A migration between two of the three identical versions.
     *
     * Versions 1, 2 and 3 declare the same tables, columns, indexes and foreign keys — the
     * exported schemas carry one identity hash between them — so there is nothing to move
     * in either direction. These exist so that the *version number* can go down as well as
     * up without Room throwing.
     */
    private fun noOpMigration(
        from: Int,
        to: Int,
    ) = object : Migration(from, to) {
        override fun migrate(db: SupportSQLiteDatabase) {}
    }
}
