package com.staninna.aufmachen.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.staninna.aufmachen.model.AddressBucket
import com.staninna.aufmachen.model.DefaultBuckets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class ImapProvider(val host: String, val port: Int, val smtpHost: String, val smtpPort: Int) {
    GMAIL("imap.gmail.com", 993, "smtp.gmail.com", 465),
    OUTLOOK("outlook.office365.com", 993, "smtp.office365.com", 587),
    YAHOO("imap.mail.yahoo.com", 993, "smtp.mail.yahoo.com", 465),
    CUSTOM("", 993, "", 465),
}

@Serializable
data class ImapConfig(
    val provider: ImapProvider = ImapProvider.GMAIL,
    val host: String = ImapProvider.GMAIL.host,
    val port: Int = 993,
    val smtpHost: String = ImapProvider.GMAIL.smtpHost,
    val smtpPort: Int = 465,
    val user: String = "",       // the old address; password lives in SecretStore
) {
    val isGmail: Boolean get() = host.equals("imap.gmail.com", true)
}

@Serializable
enum class LockMode { NONE, BIOMETRIC, PIN }

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** All user settings, stored as one JSON blob in DataStore. */
@Serializable
data class AppSettings(
    val onboardingDone: Boolean = false,
    val domain: String = "",                       // e.g. "example.com"
    val buckets: List<AddressBucket> = DefaultBuckets.create(),
    val otherOwnAddresses: List<String> = emptyList(), // e.g. me@tuta.example
    val imap: ImapConfig? = null,
    val mboxUri: String? = null,
    val mboxName: String? = null,
    val wifiOnly: Boolean = true,
    val syncIntervalHours: Int = 6,
    val weeklyDigest: Boolean = false,
    val lockMode: LockMode = LockMode.NONE,
    val language: String = "",                     // "" = follow system, "en", "nl"
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val takeoutDone: Boolean = false,
    val readinessOverride: Boolean = false,
    val endgameTicks: List<String> = emptyList(),  // ids of ticked "Close Gmail" guide items
) {
    val mainBucket: AddressBucket? get() = buckets.firstOrNull { it.isMain }
    val mainAddress: String? get() = mainBucket?.let { addressOf(it) }
    fun addressOf(b: AddressBucket): String? = if (domain.isBlank()) null else "${b.localPart}@$domain"

    /** Every new address the user owns: all buckets plus other own addresses. Lowercased. */
    val newAddresses: Set<String>
        get() = (buckets.mapNotNull { addressOf(it) } + otherOwnAddresses).map { it.lowercase() }.toSet()

    /** The old mailbox address, when known. */
    val oldAddress: String? get() = imap?.user?.takeIf { it.isNotBlank() }?.lowercase()

    /** True when [addr] is one of the new addresses, including catch-all on the custom domain. */
    fun isNewAddress(addr: String): Boolean {
        val a = addr.lowercase()
        return a in newAddresses || (domain.isNotBlank() && a.endsWith("@${domain.lowercase()}"))
    }
}

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private val key = stringPreferencesKey("settings_json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        p[key]?.let { runCatching { json.decodeFromString<AppSettings>(it) }.getOrNull() } ?: AppSettings()
    }

    suspend fun get(): AppSettings = flow.first()

    suspend fun update(block: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val cur = p[key]?.let { runCatching { json.decodeFromString<AppSettings>(it) }.getOrNull() } ?: AppSettings()
            p[key] = json.encodeToString(AppSettings.serializer(), block(cur))
        }
    }

    suspend fun replace(s: AppSettings) = update { s }

    suspend fun clear() = context.dataStore.edit { it.clear() }
}
