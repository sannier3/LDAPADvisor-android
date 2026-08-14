package com.jbsan.ldapadvisor.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jbsan.ldapadvisor.data.database.dao.CustomCaDao
import com.jbsan.ldapadvisor.data.database.dao.DiagnosticRunDao
import com.jbsan.ldapadvisor.data.database.dao.FavoriteDnDao
import com.jbsan.ldapadvisor.data.database.dao.ProfileDao
import com.jbsan.ldapadvisor.data.database.dao.ReportMetaDao
import com.jbsan.ldapadvisor.data.database.dao.SearchHistoryDao
import com.jbsan.ldapadvisor.data.database.dao.TrustedCertDao
import com.jbsan.ldapadvisor.data.database.entity.CustomCaEntity
import com.jbsan.ldapadvisor.data.database.entity.DiagnosticRunEntity
import com.jbsan.ldapadvisor.data.database.entity.FavoriteDnEntity
import com.jbsan.ldapadvisor.data.database.entity.ProfileEntity
import com.jbsan.ldapadvisor.data.database.entity.ReportMetaEntity
import com.jbsan.ldapadvisor.data.database.entity.SearchHistoryEntity
import com.jbsan.ldapadvisor.data.database.entity.TrustedCertEntity

@Database(
    entities = [
        ProfileEntity::class,
        CustomCaEntity::class,
        DiagnosticRunEntity::class,
        ReportMetaEntity::class,
        TrustedCertEntity::class,
        FavoriteDnEntity::class,
        SearchHistoryEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
@TypeConverters(EnumConverters::class)
abstract class LdapAdvisorDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun customCaDao(): CustomCaDao
    abstract fun diagnosticRunDao(): DiagnosticRunDao
    abstract fun reportMetaDao(): ReportMetaDao
    abstract fun trustedCertDao(): TrustedCertDao
    abstract fun favoriteDnDao(): FavoriteDnDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        const val NAME = "ldapadvisor.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS favorite_dns (
                        id TEXT NOT NULL,
                        dn TEXT NOT NULL,
                        label TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS search_history (
                        id TEXT NOT NULL,
                        filter TEXT NOT NULL,
                        baseDn TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profiles ADD COLUMN authMethod TEXT NOT NULL DEFAULT 'SIMPLE'")
                db.execSQL("ALTER TABLE profiles ADD COLUMN kerberosRealm TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE profiles ADD COLUMN kerberosKdcHost TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE profiles ADD COLUMN kerberosKdcPort INTEGER NOT NULL DEFAULT 88")
                db.execSQL("ALTER TABLE profiles ADD COLUMN kerberosServicePrincipal TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE profiles SET trustMode = 'INSECURE_NO_VERIFY' WHERE trustMode = 'DIAGNOSTIC_ONLY'",
                )
            }
        }

        fun build(context: Context): LdapAdvisorDatabase =
            Room.databaseBuilder(context.applicationContext, LdapAdvisorDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                // No fallbackToDestructiveMigration — migrations must be explicit.
                .build()
    }
}
