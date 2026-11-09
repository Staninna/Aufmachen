package com.staninna.aufmachen.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small secrets (app password, database passphrase, PIN hash) encrypted with an AES-GCM key
 * that lives in the Android Keystore and never leaves it.
 */
class SecretStore(context: Context) {
    private val prefs = context.getSharedPreferences("secrets", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    fun put(name: String, value: String?) {
        if (value == null) {
            prefs.edit().remove(name).apply(); return
        }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val out = c.iv + c.doFinal(value.toByteArray())
        prefs.edit().putString(name, Base64.encodeToString(out, Base64.NO_WRAP)).apply()
    }

    fun get(name: String): String? {
        val raw = prefs.getString(name, null) ?: return null
        val bytes = Base64.decode(raw, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
        return String(c.doFinal(bytes, 12, bytes.size - 12))
    }

    var imapPassword: String?
        get() = get(IMAP_PASSWORD)
        set(v) = put(IMAP_PASSWORD, v?.replace(" ", ""))

    /** Random 32-byte passphrase for SQLCipher, created on first use. */
    fun databasePassphrase(): ByteArray {
        val existing = get(DB_PASSPHRASE)
        if (existing != null) return Base64.decode(existing, Base64.NO_WRAP)
        val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
        put(DB_PASSPHRASE, Base64.encodeToString(fresh, Base64.NO_WRAP))
        return fresh
    }

    fun setPin(pin: String?) = put(PIN_HASH, pin?.let { hashPin(it) })
    fun checkPin(pin: String): Boolean = get(PIN_HASH)?.let { it == hashPin(pin) } ?: false
    val hasPin: Boolean get() = prefs.contains(PIN_HASH)

    private fun hashPin(pin: String): String {
        val salt = get(PIN_SALT) ?: Base64.encodeToString(ByteArray(16).also { SecureRandom().nextBytes(it) }, Base64.NO_WRAP)
            .also { put(PIN_SALT, it) }
        val d = MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray())
        return Base64.encodeToString(d, Base64.NO_WRAP)
    }

    fun wipe() {
        prefs.edit().clear().apply()
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS) }
    }

    companion object {
        private const val ALIAS = "aufmachen_secrets"
        const val IMAP_PASSWORD = "imap_password"
        const val DB_PASSPHRASE = "db_passphrase"
        const val PIN_HASH = "pin_hash"
        const val PIN_SALT = "pin_salt"
    }
}
