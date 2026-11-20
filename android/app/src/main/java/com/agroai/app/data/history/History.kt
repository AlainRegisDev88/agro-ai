package com.agroai.app.data.history

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.agroai.app.ml.Diagnosis
import com.agroai.app.ml.DiagnosisStatus
import com.agroai.app.ml.LabelScore
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Entity(tableName = "scans")
data class Scan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val crop: String,
    val label: String,
    val confidence: Float,
    /** JSON list of [LabelScore], best first. */
    val topJson: String,
    val imagePath: String,
    /** "v3 int8", "local", or "demo" when no real model was installed. */
    val modelVersion: String,
    /** null = not asked yet, true = farmer agreed, false = farmer said it was wrong. */
    val feedback: Boolean? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val DEMO = "demo"
        fun encodeTop(top: List<LabelScore>): String = Json.encodeToString(top)
    }
}

// Derived values live outside the entity so Room never tries to map them to columns.
val Scan.top: List<LabelScore> get() = Json.decodeFromString(topJson)
val Scan.status: DiagnosisStatus get() = Diagnosis.status(top)
val Scan.isDemo: Boolean get() = modelVersion == Scan.DEMO

@Dao
interface ScanDao {
    @Query("SELECT * FROM scans ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Scan>>

    @Query("SELECT * FROM scans ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<Scan>>

    @Query("SELECT * FROM scans WHERE id = :id")
    fun observe(id: Long): Flow<Scan?>

    @Query("SELECT * FROM scans WHERE id = :id")
    suspend fun get(id: Long): Scan?

    @Insert
    suspend fun insert(scan: Scan): Long

    @Query("UPDATE scans SET feedback = :feedback WHERE id = :id")
    suspend fun setFeedback(id: Long, feedback: Boolean)

    @Query("DELETE FROM scans WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM scans")
    suspend fun deleteAll()
}

@Database(entities = [Scan::class], version = 1, exportSchema = false)
abstract class AgroDatabase : RoomDatabase() {
    abstract fun scans(): ScanDao

    companion object {
        fun create(context: Context): AgroDatabase =
            Room.databaseBuilder(context, AgroDatabase::class.java, "agro.db").build()
    }
}

/** Scans plus their photos in filesDir/scans. */
class HistoryRepository(private val dao: ScanDao, val scansDir: File) {
    fun all() = dao.observeAll()
    fun recent(limit: Int = 3) = dao.observeRecent(limit)
    fun observe(id: Long) = dao.observe(id)
    suspend fun get(id: Long) = dao.get(id)
    suspend fun add(scan: Scan): Long = dao.insert(scan)
    suspend fun setFeedback(id: Long, value: Boolean) = dao.setFeedback(id, value)

    /** Removes the row only; the photo stays until [purgePhoto] so Undo can restore it. */
    suspend fun delete(id: Long) = dao.delete(id)

    fun purgePhoto(scan: Scan) {
        File(scan.imagePath).delete()
    }

    suspend fun clear() {
        dao.deleteAll()
        scansDir.listFiles()?.forEach { it.delete() }
    }

    fun bytesUsed(): Long = scansDir.listFiles()?.sumOf { it.length() } ?: 0L
}
