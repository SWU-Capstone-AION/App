package com.aion.hrtest

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** DB에 남는 심박 한 건. 원본 그대로 저장하고, 이상치 필터는 4단계에서 읽을 때 적용한다 */
@Entity(tableName = "hr_record")
data class HrRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bpm: Int,
    /** 워치가 잰 시각 */
    val at: Long,
    /** 태블릿이 받은 시각 */
    val receivedAt: Long,
    /** 워치 노드 id (여러 아동/워치 구분용) */
    val sourceNode: String,
    /** 이상치 필터 통과 여부 */
    @ColumnInfo(defaultValue = "1") val valid: Boolean = true,
    /**
     * 기준선에 실제로 들어간 값인지 — 재시작 때 기준선 복원에 쓴다.
     * 처음엔 false로 저장하고, 보류(90초)를 무사히 넘겨 기준선에 들어갈 때 true로 바꾼다.
     * 그래서 보류 중에 앱이 죽어도 확정 안 된 값이 기준선으로 복원되지 않는다.
     */
    @ColumnInfo(defaultValue = "1") val quiet: Boolean = false
)

/** 기준선 복원용 한 줄 */
data class QuietRow(val receivedAt: Long, val bpm: Int)

@Dao
interface HrDao {
    @Insert
    suspend fun insert(record: HrRecord)

    /** 최근 n건, 오래된 것부터 (버퍼 복원용) */
    @Query("SELECT * FROM (SELECT * FROM hr_record ORDER BY receivedAt DESC LIMIT :n) ORDER BY receivedAt ASC")
    suspend fun latest(n: Int): List<HrRecord>

    /** 기준선 복원용: 기준선에 들어갔던 값만, 최근 n건 (오래된 것부터) */
    @Query("SELECT receivedAt, bpm FROM (SELECT receivedAt, bpm FROM hr_record WHERE valid = 1 AND quiet = 1 ORDER BY receivedAt DESC LIMIT :n) ORDER BY receivedAt ASC")
    suspend fun latestQuiet(n: Int): List<QuietRow>

    /** 보류를 넘겨 기준선에 들어간 구간 표시. 보류 값은 시간순으로 연속해서 나오므로 범위로 표시한다 */
    @Query("UPDATE hr_record SET quiet = 1 WHERE valid = 1 AND receivedAt BETWEEN :from AND :to")
    suspend fun markQuiet(from: Long, to: Long)

    @Query("SELECT * FROM hr_record ORDER BY receivedAt DESC LIMIT :n")
    fun recentFlow(n: Int): Flow<List<HrRecord>>

    @Query("SELECT COUNT(*) FROM hr_record")
    fun countFlow(): Flow<Int>

    /** 보관 기간이 지난 기록 정리 */
    @Query("DELETE FROM hr_record WHERE receivedAt < :before")
    suspend fun deleteBefore(before: Long): Int

    @Query("DELETE FROM hr_record")
    suspend fun clear()
}

@Database(entities = [HrRecord::class], version = 2, exportSchema = false)
abstract class HrDatabase : RoomDatabase() {
    abstract fun hrDao(): HrDao

    companion object {
        @Volatile private var instance: HrDatabase? = null

        /** v1 → v2: 4단계 필터/구간 정보 추가. 기존 기록은 유효·조용함으로 간주 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE hr_record ADD COLUMN valid INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE hr_record ADD COLUMN quiet INTEGER NOT NULL DEFAULT 1")
            }
        }

        fun get(context: Context): HrDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, HrDatabase::class.java, "aion_hr.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
