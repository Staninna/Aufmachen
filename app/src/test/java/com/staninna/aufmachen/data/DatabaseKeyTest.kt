package com.staninna.aufmachen.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DatabaseKeyTest {
    private lateinit var store: SharedPreferences

    @Before
    fun setUp() {
        store = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(SecretStore.DB_KEY_FILE, Context.MODE_PRIVATE)
        store.edit().clear().commit()
    }

    @Test
    fun movesTheKeystoreCopyOverAndKeepsThePassphrase() {
        val old = ByteArray(32) { it.toByte() }
        var dropped = false
        var fresh = false
        val first = loadDatabasePassphrase(
            store,
            legacy = { Base64.encodeToString(old, Base64.NO_WRAP) },
            dropLegacy = { dropped = true },
            onFresh = { fresh = true },
        )
        assertArrayEquals(old, first)
        assertTrue(dropped)
        assertFalse(fresh)

        // Next start reads the plain copy and never touches the Keystore.
        val second = loadDatabasePassphrase(store, legacy = { error("not read") }, dropLegacy = {}, onFresh = { error("no reset") })
        assertArrayEquals(old, second)
    }

    @Test
    fun restoredPassphraseIsUsedAsIs() {
        val restored = ByteArray(32) { 7 }
        store.edit().putString("passphrase", Base64.encodeToString(restored, Base64.NO_WRAP)).commit()
        val got = loadDatabasePassphrase(store, legacy = { error("not read") }, dropLegacy = {}, onFresh = { error("no reset") })
        assertArrayEquals(restored, got)
    }

    @Test
    fun noPassphraseAnywhereResetsTheDatabaseAndSavesANewOne() {
        var fresh = 0
        val first = loadDatabasePassphrase(store, legacy = { null }, dropLegacy = { error("nothing to drop") }, onFresh = { fresh++ })
        assertEquals(32, first.size)
        assertEquals(1, fresh)
        val second = loadDatabasePassphrase(store, legacy = { null }, dropLegacy = {}, onFresh = { fresh++ })
        assertArrayEquals(first, second)
        assertEquals(1, fresh)
    }
}
