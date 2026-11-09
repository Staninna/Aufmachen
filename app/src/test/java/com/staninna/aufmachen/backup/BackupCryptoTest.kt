package com.staninna.aufmachen.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCryptoTest {
    private val plain = """{"hello":"world","n":1}""".toByteArray()

    @Test fun roundTrip() {
        val enc = BackupCrypto.encrypt(plain, "correct horse".toCharArray())
        assertEquals("AUFM1", String(enc, 0, 5, Charsets.US_ASCII))
        assertArrayEquals(plain, BackupCrypto.decrypt(enc, "correct horse".toCharArray()))
    }

    @Test fun twoEncryptionsDiffer() {
        val a = BackupCrypto.encrypt(plain, "pw".toCharArray())
        val b = BackupCrypto.encrypt(plain, "pw".toCharArray())
        assertTrue(!a.contentEquals(b))
    }

    @Test fun wrongPassphraseFails() {
        val enc = BackupCrypto.encrypt(plain, "right".toCharArray())
        try {
            BackupCrypto.decrypt(enc, "wrong".toCharArray())
            fail("expected WrongPassphraseException")
        } catch (e: WrongPassphraseException) {
        }
    }

    @Test fun tamperedCiphertextFails() {
        val enc = BackupCrypto.encrypt(plain, "right".toCharArray())
        enc[enc.size - 3] = (enc[enc.size - 3].toInt() xor 1).toByte()
        try {
            BackupCrypto.decrypt(enc, "right".toCharArray())
            fail("expected WrongPassphraseException")
        } catch (e: WrongPassphraseException) {
        }
    }

    @Test fun notABackupFails() {
        try {
            BackupCrypto.decrypt("just some text that is long enough to pass size checks".toByteArray(), "x".toCharArray())
            fail("expected NotABackupException")
        } catch (e: NotABackupException) {
        }
    }
}
