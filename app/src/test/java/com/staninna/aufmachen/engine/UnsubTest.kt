package com.staninna.aufmachen.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class UnsubTest {
    @Test fun linksInOrder() {
        assertEquals(
            listOf("mailto:u@example.com?subject=x", "https://example.com/u/1"),
            unsubLinks("<mailto:u@example.com?subject=x>, <https://example.com/u/1>"),
        )
    }

    @Test fun bareValueIsOneLink() {
        assertEquals(listOf("https://example.com/u"), unsubLinks("  https://example.com/u "))
    }

    @Test fun blankIsEmpty() {
        assertEquals(emptyList<String>(), unsubLinks("   "))
        assertEquals(emptyList<String>(), unsubLinks(""))
    }

    @Test fun oneClickNeedsHttps() {
        assertEquals(UnsubMethod.ONE_CLICK, unsubMethod(listOf("mailto:u@example.com", "https://example.com/u"), true))
        assertEquals(UnsubMethod.EMAIL, unsubMethod(listOf("mailto:u@example.com", "https://example.com/u"), false))
        assertEquals(UnsubMethod.EMAIL, unsubMethod(listOf("mailto:u@example.com", "http://example.com/u"), true))
        assertEquals(UnsubMethod.WEB, unsubMethod(listOf("http://example.com/u"), true))
        assertEquals(UnsubMethod.WEB, unsubMethod(listOf("https://example.com/u"), false))
        assertEquals(UnsubMethod.NONE, unsubMethod(emptyList(), true))
        assertEquals(UnsubMethod.NONE, unsubMethod(listOf("ftp://example.com"), false))
    }
}
