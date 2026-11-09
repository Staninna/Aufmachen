package com.staninna.aufmachen.backup

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

open class BackupException(message: String) : Exception(message)

/** Raised when the passphrase is wrong or the file was changed after export. */
class WrongPassphraseException : BackupException("Wrong passphrase, or the file is damaged.")

/** Raised when the file is not an Aufmachen backup. */
class NotABackupException : BackupException("This file is not an Aufmachen backup.")

/**
 * File format: magic "AUFM1", 16-byte salt, 12-byte IV, AES-256-GCM ciphertext (with tag).
 * Key: PBKDF2WithHmacSHA256, 210000 iterations, from the user passphrase.
 */
object BackupCrypto {
    private val MAGIC = "AUFM1".toByteArray(Charsets.US_ASCII)
    const val ITERATIONS = 210_000
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val TAG_BITS = 128

    private fun key(pass: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(pass, salt, ITERATIONS, 256)
        try {
            val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(raw, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun encrypt(plain: ByteArray, pass: CharArray): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(SALT_LEN).also(rnd::nextBytes)
        val iv = ByteArray(IV_LEN).also(rnd::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(pass, salt), GCMParameterSpec(TAG_BITS, iv))
        c.updateAAD(MAGIC)
        return MAGIC + salt + iv + c.doFinal(plain)
    }

    fun decrypt(data: ByteArray, pass: CharArray): ByteArray {
        val head = MAGIC.size + SALT_LEN + IV_LEN
        if (data.size < head + TAG_BITS / 8 || !data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw NotABackupException()
        }
        val salt = data.copyOfRange(MAGIC.size, MAGIC.size + SALT_LEN)
        val iv = data.copyOfRange(MAGIC.size + SALT_LEN, head)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(pass, salt), GCMParameterSpec(TAG_BITS, iv))
        c.updateAAD(MAGIC)
        try {
            return c.doFinal(data, head, data.size - head)
        } catch (e: AEADBadTagException) {
            throw WrongPassphraseException()
        } catch (e: GeneralSecurityException) {
            throw WrongPassphraseException()
        }
    }
}
