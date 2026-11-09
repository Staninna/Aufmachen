package com.staninna.aufmachen.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.LocalDate

class MboxReaderTest {
    private val today = LocalDate.of(2026, 11, 9)

    private val mbox = """
From 1@xxx Mon Mar 02 10:00:00 +0000 2020
From: Shop <news@shop.example.com>
To: me@example.com
Subject: Sale
Date: Mon, 2 Mar 2020 10:00:00 +0000
X-Gmail-Labels: Category Promotions,Unread,Inbox
List-Id: <news.shop.example.com>

Hello,
>From the desk of the shop, a quoted line.
From the start of a line without a quote would split, but Takeout escapes it.
Bye

From 2@xxx Tue Mar 03 10:00:00 +0000 2020
From: Alex Example <alex@example.org>
To: me@example.com
Subject: Re: lunch
Date: Tue, 3 Mar 2020 10:00:00 +0000
X-Gmail-Labels: Sent,Important,Starred

On Tue, someone wrote:
> hi

From 3@xxx Wed Mar 04 10:00:00 +0000 2020
From: Bank <bank@example.net>
Subject: =?UTF-8?Q?Statement_=E2=82=AC?=
Date: Wed, 4 Mar 2020 10:00:00 +0000
X-Gmail-Labels: Inbox,Important

Body
""".trimStart().replace("\n", "\r\n")

    private fun read(text: String = mbox) =
        MboxReader.read(ByteArrayInputStream(text.toByteArray()), today).toList()

    @Test fun splitsThreeMessagesAndSkipsQuotedFromLine() {
        val r = read()
        assertEquals(listOf(0, 1, 2), r.map { it.first })
        assertEquals(listOf("news@shop.example.com", "alex@example.org", "bank@example.net"),
            r.map { it.second.fields.fromAddr })
        assertEquals("Statement €", r[2].second.fields.subject)
    }

    @Test fun mapsGmailLabels() {
        val (promo, sent, plain) = read().map { it.second }
        assertEquals("promotions", promo.category)
        assertFalse(promo.seen)
        assertFalse(promo.important)
        assertTrue(promo.fields.bulk)

        assertTrue(sent.sent)
        assertTrue(sent.important)
        assertTrue(sent.flagged)
        assertTrue(sent.seen)
        assertEquals("primary", sent.category)

        assertEquals("primary", plain.category)
        assertTrue(plain.important)
        assertFalse(plain.sent)
    }

    @Test fun noLabelsMeansNoCategory() {
        val r = read("From x\nFrom: a@example.com\nSubject: s\n\nbody\n")
        assertEquals(1, r.size)
        assertEquals("", r[0].second.category)
        assertTrue(r[0].second.seen)
    }

    @Test fun reportsBytesAndHandlesNoTrailingNewline() {
        var bytes = 0L
        val text = "From x\nFrom: a@example.com\n\nbody"
        val r = MboxReader.read(ByteArrayInputStream(text.toByteArray()), today, { bytes = it }).toList()
        assertEquals(1, r.size)
        assertEquals(text.length.toLong(), bytes)
    }

    @Test fun emptyInput() {
        assertEquals(0, read("").size)
    }
}
