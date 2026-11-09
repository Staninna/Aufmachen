package com.staninna.aufmachen.backup

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import androidx.sqlite.db.SimpleSQLiteQuery
import com.staninna.aufmachen.container
import com.staninna.aufmachen.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.util.Base64

@Serializable
data class BackupFile(
    val version: Int = 1,
    val createdAt: Long,
    val settings: AppSettings,
    /** Table name to rows, each row column name to value. */
    val tables: Map<String, List<JsonObject>>,
    /** Proof row id to PDF bytes, base64. */
    val proofFiles: Map<String, String> = emptyMap(),
)

/**
 * Encrypted backup of everything the user created. Header records are not included: they are
 * re-scanned from the old mailbox after an import. The IMAP password is never included.
 */
object Backup {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Tables that hold user state. Computed tables (service_stats, person_stats) are rebuilt by rescore. */
    val TABLES = listOf("service_state", "merges", "timeline", "proofs", "alerts", "person_state")

    suspend fun export(context: Context, passphrase: CharArray): ByteArray = withContext(Dispatchers.IO) {
        val c = context.container
        val db = c.db.openHelper.readableDatabase
        val tables = TABLES.associateWith { t -> db.query(SimpleSQLiteQuery("SELECT * FROM $t")).use(::rows) }
        val proofFiles = HashMap<String, String>()
        tables.getValue("proofs").forEach { row ->
            val id = row["id"]?.let { (it as JsonPrimitive).contentOrNull } ?: return@forEach
            val path = (row["pdfPath"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
            val f = File(path)
            if (f.isFile) proofFiles[id] = Base64.getEncoder().encodeToString(f.readBytes())
        }
        val file = BackupFile(1, System.currentTimeMillis(), c.settings.get(), tables, proofFiles)
        BackupCrypto.encrypt(json.encodeToString(BackupFile.serializer(), file).toByteArray(Charsets.UTF_8), passphrase)
    }

    /** Replaces the user-state tables and proof files, then rescores. Throws [BackupException] on a bad file. */
    suspend fun import(context: Context, data: ByteArray, passphrase: CharArray) = withContext(Dispatchers.IO) {
        val plain = BackupCrypto.decrypt(data, passphrase)
        val file = try {
            json.decodeFromString(BackupFile.serializer(), String(plain, Charsets.UTF_8))
        } catch (e: Exception) {
            throw NotABackupException()
        }
        val c = context.container
        val proofDir = File(context.filesDir, "proofs")
        proofDir.deleteRecursively()
        proofDir.mkdirs()
        val proofRows = file.tables["proofs"].orEmpty().map { row ->
            val id = (row["id"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val bytes = file.proofFiles[id]?.let { Base64.getDecoder().decode(it) }
            val oldName = File((row["pdfPath"] as? JsonPrimitive)?.contentOrNull.orEmpty()).name
            val safe = oldName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "proof.pdf" }
            val out = File(proofDir, "$id-$safe")
            if (bytes != null) out.writeBytes(bytes)
            JsonObject(row + ("pdfPath" to JsonPrimitive(out.absolutePath)))
        }
        val tables = file.tables + ("proofs" to proofRows)
        val db = c.db.openHelper.writableDatabase
        db.beginTransaction()
        try {
            TABLES.forEach { db.execSQL("DELETE FROM $it") }
            TABLES.forEach { t ->
                tables[t].orEmpty().forEach { row -> db.insert(t, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, values(row)) }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        val cur = c.settings.get()
        c.settings.replace(
            file.settings.copy(
                onboardingDone = true,
                mboxUri = cur.mboxUri, mboxName = cur.mboxName,
                lockMode = cur.lockMode, imap = file.settings.imap ?: cur.imap,
            ),
        )
        c.repo.rescore()
    }

    private fun rows(cur: Cursor): List<JsonObject> {
        val out = ArrayList<JsonObject>()
        while (cur.moveToNext()) {
            val m = LinkedHashMap<String, kotlinx.serialization.json.JsonElement>()
            for (i in 0 until cur.columnCount) {
                m[cur.getColumnName(i)] = when (cur.getType(i)) {
                    Cursor.FIELD_TYPE_INTEGER -> JsonPrimitive(cur.getLong(i))
                    Cursor.FIELD_TYPE_FLOAT -> JsonPrimitive(cur.getDouble(i))
                    Cursor.FIELD_TYPE_STRING -> JsonPrimitive(cur.getString(i))
                    else -> JsonNull
                }
            }
            out += JsonObject(m)
        }
        return out
    }

    private fun values(row: JsonObject): ContentValues {
        val v = ContentValues()
        row.forEach { (k, e) ->
            val p = e as? JsonPrimitive
            when {
                p == null || p is JsonNull -> v.putNull(k)
                p.isString -> v.put(k, p.content)
                p.longOrNull != null -> v.put(k, p.longOrNull)
                p.doubleOrNull != null -> v.put(k, p.doubleOrNull)
                else -> v.put(k, p.content)
            }
        }
        return v
    }
}
