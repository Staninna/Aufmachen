package com.staninna.aufmachen.engine

import com.staninna.aufmachen.model.Action
import com.staninna.aufmachen.model.TellNewAddress
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

class ScoringParityTest {
    private val fx: JsonObject = run {
        val stream = javaClass.classLoader!!.getResourceAsStream("parity/fixture.json")
        assertNotNull("parity/fixture.json missing from test classpath", stream)
        Json.parseToJsonElement(stream!!.reader(Charsets.UTF_8).readText()).jsonObject
    }

    private fun JsonObject.str(k: String) = getValue(k).jsonPrimitive.content
    private fun JsonObject.int(k: String) = getValue(k).jsonPrimitive.int
    private fun JsonObject.dbl(k: String) = getValue(k).jsonPrimitive.double
    private fun JsonObject.isoDay(k: String): Long? =
        getValue(k).let { if (it is JsonNull) null else LocalDate.parse(it.jsonPrimitive.content).toEpochDay() }

    private fun records(): List<MailRecord> = fx.getValue("rows").jsonArray.map {
        val r = it.jsonObject
        MailRecord(
            dateEpochDay = r.isoDay("date"),
            fromAddr = r.str("from_addr"),
            fromName = r.str("from_name"),
            toAddrs = r.str("to_addrs").split(',').filter { a -> a.isNotEmpty() },
            subject = r.str("subject"),
            unsubHeader = r.str("unsub"),
            unsubOneClick = false,
            bulk = r.int("bulk") != 0,
            attach = r.int("attach") != 0,
            seen = r.int("seen") != 0,
            flagged = r.int("flagged") != 0,
            important = r.int("important") != 0,
            sent = r.int("sent") != 0,
            category = r.str("category"),
        )
    }

    private fun fmt(v: Double): String {
        val r = BigDecimal(v).setScale(0, RoundingMode.HALF_EVEN).toLong()
        return (if (v < 0 && r == 0L) "-" else if (r >= 0) "+" else "") + r
    }

    private fun action(a: Action) = when (a) {
        Action.CHANGE_FIRST -> "Change email FIRST + check 2FA/recovery"
        Action.DORMANT_DELETE -> "Dormant: delete account (or ignore)"
        Action.CHANGE -> "Change email"
        Action.UNSUBSCRIBE -> "Unsubscribe"
        Action.CHANGE_LOW_OR_DELETE -> "Change email (low prio) or delete account"
        Action.IGNORE -> "Ignore"
    }

    @Test fun servicesAndPeopleMatchPrototype() {
        val a = Analyzer(
            LocalDate.parse(fx.str("today")),
            fx.getValue("own").jsonArray.map { it.jsonPrimitive.content }.toSet(),
        )
        records().forEach(a::add)
        val res = a.result()

        assertEquals(fx.int("total"), res.total)
        val exp = fx.getValue("services").jsonArray.map { it.jsonObject }
        assertEquals(exp.map { it.str("Service") }, res.services.map { it.domain })
        assertTrue(exp.size >= 15)

        for ((e, s) in exp.zip(res.services)) {
            val n = s.domain
            fun eq(field: String, actual: Any?, expected: Any?) = assertEquals("$n $field", expected, actual)
            fun pct(field: String, actual: Double) = assertEquals("$n $field", e.dbl(field), actual, 1e-12)
            eq("Score", s.score, e.int("Score"))
            eq("Tier", "${s.tier.level}", e.str("Tier").take(1))
            eq("Action", action(s.action), e.str("Action"))
            eq("Mails", s.mails, e.int("Mails"))
            eq("Last 12m", s.last12, e.int("Last 12m"))
            eq("Days since", s.daysSince, e.getValue("Days since").jsonPrimitive.content.toLongOrNull())
            eq("Active years", s.activeYears, e.int("Active years"))
            pct("Read %", s.readPct)
            pct("Important %", s.importantPct)
            eq("Starred", s.starred, e.int("Starred"))
            eq("You sent", s.youSent, e.int("You sent"))
            eq("Security", s.security, e.int("Security"))
            eq("Welcome", s.welcome, e.int("Welcome"))
            eq("Orders/inv", s.orders, e.int("Orders/inv"))
            eq("Billing", s.billing, e.int("Billing"))
            eq("Bill months", s.billMonths, e.int("Bill months"))
            eq("Marketing", s.marketing, e.int("Marketing"))
            pct("Newsletter %", s.newsletterPct)
            pct("Promo %", s.promoPct)
            pct("Updates %", s.updatesPct)
            pct("Social %", s.socialPct)
            eq("Attachments", s.attachments, e.int("Attachments"))
            val reg = e.getValue("Regularity")
            eq("Regularity", s.regularity, if (reg is JsonNull) null else reg.jsonPrimitive.double)
            eq("Senders", s.senderAddrs.size, e.int("Senders"))
            eq("Per year", s.perYearRate, e.dbl("Per year"))
            eq("First", s.firstEpochDay, e.isoDay("First"))
            eq("Last", s.lastEpochDay, e.isoDay("Last"))
            eq("Category", s.category, e.str("Category"))
            eq("Example subject", s.exampleSubject, e.str("Example subject"))
            val why = s.why.joinToString(", ") {
                val key = if (it.key == s.category) "category" else it.key
                "$key ${fmt(it.points)}"
            }
            eq("Why", why, e.str("Why"))
            val years = e.getValue("_years").jsonObject.map { (k, v) -> k.toInt() to v.jsonPrimitive.int }.toMap()
            eq("perYear", s.perYear, years)
            assertEquals("$n hasAccount", e.getValue("_has_account").jsonPrimitive.booleanOrNull, s.hasAccount)
            assertEquals("$n dormant", e.getValue("_dormant").jsonPrimitive.booleanOrNull, s.dormant)
        }

        val pe = fx.getValue("people").jsonArray.map { it.jsonObject }
        assertEquals(pe.map { it.str("Contact") }, res.people.map { it.address })
        for ((e, p) in pe.zip(res.people)) {
            val n = p.address
            assertEquals("$n sent", e.int("You sent"), p.youSent)
            assertEquals("$n recv", e.int("They sent"), p.theySent)
            assertEquals("$n _w", e.int("_w"), p.weight)
            val tell = when (p.tell) {
                TellNewAddress.YES -> "YES"
                TellNewAddress.MAYBE -> "maybe"
                TellNewAddress.NO -> "no"
            }
            assertEquals("$n tell", e.str("Tell new address?"), tell)
        }
    }

    @Test fun mergeGroupCollectsDomains() {
        val a = Analyzer(LocalDate.of(2026, 11, 9), setOf("me@example.com")) {
            if (it == "example.org") "example.com" else it
        }
        fun mail(from: String) = MailRecord(
            LocalDate.of(2026, 10, 1).toEpochDay(), from, "Shop", listOf("me@example.com"), "Your order", "",
            false, false, false, false, false, false, false, "",
        )
        a.add(mail("a@shop.example.org"))
        a.add(mail("b@mail.shop.example.com"))
        val s = a.result().services.single()
        assertEquals("example.com", s.domain)
        assertEquals(listOf("example.com", "example.org"), s.domains)
        assertEquals(2, s.mails)
        assertEquals(listOf("Shop"), s.displayNames)
    }

    @Test fun newestUnsubHeaderWins() {
        val a = Analyzer(LocalDate.of(2026, 11, 9), setOf("me@example.com"))
        fun mail(day: Int, unsub: String, oc: Boolean) = MailRecord(
            LocalDate.of(2026, 1, day).toEpochDay(), "n@list.example.com", "", listOf("me@example.com"), "x", unsub,
            oc, false, false, false, false, false, false, "",
        )
        a.add(mail(20, "<https://list.example.com/new>", true))
        a.add(mail(5, "<https://list.example.com/old>", false))
        val s = a.result().services.single()
        assertEquals(listOf("https://list.example.com/new"), s.unsubLinks)
        assertTrue(s.unsubOneClick)
    }
}
