package com.staninna.aufmachen.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HeaderParserTest {
    private val today = LocalDate.of(2026, 11, 9)

    private fun parse(vararg lines: String) =
        HeaderParser.parseBlock((lines.joinToString("\r\n") + "\r\n").toByteArray(), today)

    @Test fun encodedWordSubjectAndName() {
        val f = parse(
            "From: =?UTF-8?B?w4lsw6huYSBFeGFtcGxl?= <News@Shop.Example.com>",
            "Subject: =?UTF-8?Q?Your_order_=E2=82=AC12?=",
            "Date: Tue, 03 Mar 2020 10:00:00 +0100",
        )
        assertEquals("Élèna Example", f.fromName)
        assertEquals("news@shop.example.com", f.fromAddr)
        assertEquals("example.com", f.fromDomain)
        assertEquals("Your order €12", f.subject)
        assertEquals(LocalDate.of(2020, 3, 3).toEpochDay(), f.dateEpochDay)
    }

    @Test fun foldedEncodedSubject() {
        val f = parse("From: a@example.org", "Subject: =?UTF-8?Q?Hello?=", " =?UTF-8?Q?_world?=")
        assertEquals("Hello world", f.subject)
    }

    @Test fun multiPartTldDomain() {
        assertEquals("example.co.uk", parse("From: x@mail.example.co.uk").fromDomain)
    }

    @Test fun missingDateIsNull() {
        assertNull(parse("From: a@example.com", "Subject: hi").dateEpochDay)
    }

    @Test fun futureAndAncientDatesAreNull() {
        assertNull(parse("From: a@example.com", "Date: Fri, 20 Nov 2026 10:00:00 +0000").dateEpochDay)
        assertNull(parse("From: a@example.com", "Date: Mon, 01 Jan 1990 10:00:00 +0000").dateEpochDay)
        // Two days ahead is still accepted.
        assertEquals(LocalDate.of(2026, 11, 11).toEpochDay(),
            parse("From: a@example.com", "Date: Wed, 11 Nov 2026 10:00:00 +0000").dateEpochDay)
    }

    @Test fun dateWithCommentAndNamedZone() {
        assertEquals(LocalDate.of(2019, 5, 6).toEpochDay(),
            parse("From: a@example.com", "Date: Mon, 6 May 2019 08:15:00 +0000 (UTC)").dateEpochDay)
        assertEquals(LocalDate.of(2019, 5, 6).toEpochDay(),
            parse("From: a@example.com", "Date: Mon, 6 May 2019 08:15:00 EST").dateEpochDay)
    }

    @Test fun precedenceBulk() {
        assertTrue(parse("From: a@example.com", "Precedence: bulk").bulk)
        assertTrue(parse("From: a@example.com", "Precedence: List").bulk)
        assertTrue(parse("From: a@example.com", "Precedence: junk").bulk)
        assertFalse(parse("From: a@example.com", "Precedence: first-class").bulk)
    }

    @Test fun listIdMakesBulk() {
        assertTrue(parse("From: a@example.com", "List-Id: <news.example.com>").bulk)
        assertFalse(parse("From: a@example.com").bulk)
    }

    @Test fun oneClickHeader() {
        val f = parse(
            "From: a@example.com",
            "List-Unsubscribe: <https://example.com/u/1>, <mailto:u@example.com>",
            "List-Unsubscribe-Post: List-Unsubscribe=One-Click",
        )
        assertTrue(f.unsubOneClick)
        assertEquals("<https://example.com/u/1>, <mailto:u@example.com>", f.unsubHeader)
        assertFalse(parse("From: a@example.com", "List-Unsubscribe: <https://example.com/u/1>").unsubOneClick)
    }

    @Test fun toAndCcMergeLowercased() {
        val f = parse(
            "From: a@example.com",
            "To: Me <Me@Example.com>, \"Doe, Jane\" <Jane@Example.org>",
            "Cc: third@example.org",
        )
        assertEquals("me@example.com,jane@example.org,third@example.org", f.toAddrs)
    }

    @Test fun multipartMixedMeansAttachment() {
        assertTrue(parse("From: a@example.com", "Content-Type: multipart/mixed; boundary=\"x\"").attach)
        assertFalse(parse("From: a@example.com", "Content-Type: multipart/alternative; boundary=\"x\"").attach)
    }

    @Test fun subjectIsCappedAndBlockStopsAtBlankLine() {
        val long = "s".repeat(500)
        val f = HeaderParser.parseBlock("From: a@example.com\nSubject: $long\n\nSubject: body\n".toByteArray(), today)
        assertEquals(300, f.subject.length)
    }

    @Test fun noFromGivesEmptyDomain() {
        val f = parse("Subject: x")
        assertEquals("", f.fromAddr)
        assertEquals("", f.fromDomain)
    }
}
