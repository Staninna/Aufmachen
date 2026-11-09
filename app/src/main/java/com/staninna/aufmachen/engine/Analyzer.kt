package com.staninna.aufmachen.engine

import com.staninna.aufmachen.model.Action
import com.staninna.aufmachen.model.TellNewAddress
import com.staninna.aufmachen.model.Tier
import com.staninna.aufmachen.model.WhyPart
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ln1p
import kotlin.math.sqrt

/**
 * Streaming port of analyze() + score_service() from mail_triage.py.
 *
 * Feed every record with [add], then call [result]. Runs on any thread; holds per-service
 * aggregates only, so 150k+ records are fine.
 *
 * @param today the "TODAY" of the prototype (injectable for tests)
 * @param ownAddresses the user's own addresses (old + new), lowercased
 * @param groupOf maps a registrable domain to its merge-group key (identity when not merged)
 */
class Analyzer(
    private val today: LocalDate,
    ownAddresses: Set<String>,
    private val groupOf: (String) -> String = { it },
) {
    private val own = ownAddresses.map { it.lowercase() }.toSet()
    private val todayDay = today.toEpochDay()
    private val oneYearAgo = todayDay - 365

    private class Svc(val key: String) {
        var n = 0
        val years = HashMap<Int, Int>()
        val billMonths = HashSet<Int>()
        val dates = ArrayList<Long>()
        var seen = 0
        var flagged = 0
        var important = 0
        val cat = HashMap<String, Int>()
        var unsub = 0
        var unsubHeader = ""
        var unsubDate: Long? = null
        var unsubOneClick = false
        var bulk = 0
        var attach = 0
        val sig = HashMap<String, Int>()
        val examples = HashMap<String, String>()
        val senders = LinkedHashMap<String, Int>()
        val names = LinkedHashMap<String, Int>()
        var noreply = 0
        var youSent = 0
        var last12 = 0
        val domains = LinkedHashSet<String>().apply { add(key) }
    }

    private class Person {
        var recv = 0
        var sent = 0
        var name = ""
        var first: Long? = null
        var last: Long? = null
    }

    private val svc = LinkedHashMap<String, Svc>()
    private val ppl = LinkedHashMap<String, Person>()
    private val perYear = HashMap<Int, Int>()
    private var total = 0

    private fun svcFor(registrable: String): Svc {
        val key = groupOf(registrable)
        val s = svc.getOrPut(key) { Svc(key) }
        s.domains.add(registrable)
        return s
    }

    private fun touch(p: Person, d: Long?) {
        if (d == null) return
        p.first = p.first?.let { minOf(it, d) } ?: d
        p.last = p.last?.let { maxOf(it, d) } ?: d
    }

    fun add(r: MailRecord) {
        total++
        val d = r.dateEpochDay
        val year = d?.let { LocalDate.ofEpochDay(it).year }
        if (year != null) perYear.merge(year, 1, Int::plus)

        val frm = r.fromAddr
        // Mail you sent: engagement signal for people and services.
        if (r.sent || frm in own) {
            for (a in r.toAddrs) {
                if (a.isEmpty() || a in own) continue
                val p = ppl.getOrPut(a) { Person() }
                p.sent++
                touch(p, d)
                val dom = a.substringAfterLast('@')
                if (dom !in FREEMAIL) svcFor(baseDomain(dom)).youSent++
            }
            return
        }
        if ('@' !in frm) return

        val dom = frm.substringAfter('@')
        val p = ppl.getOrPut(frm) { Person() }
        p.recv++
        if (p.name.isEmpty()) p.name = r.fromName
        touch(p, d)
        if (dom in FREEMAIL) return

        val s = svcFor(baseDomain(dom))
        s.n++
        s.senders.merge(frm, 1, Int::plus)
        if (r.fromName.isNotEmpty()) s.names.merge(r.fromName, 1, Int::plus)
        if (NOREPLY_RE.containsMatchIn(frm)) s.noreply++
        if (r.seen) s.seen++
        if (r.flagged) s.flagged++
        if (r.important) s.important++
        if (r.attach) s.attach++
        if (r.bulk) s.bulk++
        s.cat.merge(r.category.ifEmpty { "?" }, 1, Int::plus)
        val hasUnsub = r.unsubHeader.isNotBlank()
        if (hasUnsub) {
            s.unsub++
            val ud = s.unsubDate
            if (ud == null || (d != null && d >= ud)) {
                s.unsubHeader = r.unsubHeader
                s.unsubDate = d
                s.unsubOneClick = r.unsubOneClick
            }
        }
        val sig = signalsOf(r.subject)
        for (k in sig) {
            s.sig.merge(k, 1, Int::plus)
            s.examples.putIfAbsent(k, r.subject.take(90))
        }
        if (d != null && year != null) {
            s.dates.add(d)
            s.years.merge(year, 1, Int::plus)
            if ("billing" in sig || "txn" in sig) {
                s.billMonths.add(year * 12 + LocalDate.ofEpochDay(d).monthValue)
            }
            if (d >= oneYearAgo) s.last12++
        }
    }

    fun result(): AnalysisResult {
        val services = svc.values
            .filter { it.n > 0 || it.youSent > 0 }
            .map { score(it) }
            .sortedWith(compareBy<ServiceScore>({ -it.score }, { -it.mails }))

        val people = ArrayList<PersonScore>()
        for ((addr, p) in ppl) {
            val dom = addr.substringAfterLast('@')
            if (p.sent == 0 && dom !in FREEMAIL) continue
            if (NOREPLY_RE.containsMatchIn(addr) && p.sent == 0) continue
            val twoWay = p.sent > 0 && p.recv > 0
            val weight = p.sent * 3 + p.recv + (if (twoWay) 10 else 0)
            val last = p.last
            val recent = last != null && todayDay - last < 730
            val tell = when {
                twoWay && recent -> TellNewAddress.YES
                twoWay || p.sent >= 2 -> TellNewAddress.MAYBE
                else -> TellNewAddress.NO
            }
            people.add(
                PersonScore(
                    address = addr, name = p.name, youSent = p.sent, theySent = p.recv, twoWay = twoWay,
                    firstEpochDay = p.first, lastEpochDay = last,
                    daysSince = last?.let { todayDay - it }, tell = tell,
                    weight = weight + (if (recent) 20 else 0),
                ),
            )
        }
        people.sortByDescending { it.weight }
        return AnalysisResult(services, people, total, perYear.toMap())
    }

    private fun lg(x: Int, cap: Int): Double = minOf(1.0, ln1p(maxOf(x, 0).toDouble()) / ln1p(cap.toDouble()))

    private fun roundHalfEven(x: Double, places: Int): Double =
        BigDecimal(x).setScale(places, RoundingMode.HALF_EVEN).toDouble()

    private fun score(s: Svc): ServiceScore {
        val n = s.n
        val nameCounts = s.names.entries.sortedByDescending { it.value }.map { it.key }
        val cat = categorize(s.key, nameCounts.take(3))
        val catName = cat.first
        val dates = s.dates.sorted()
        val first = dates.firstOrNull()
        val last = dates.lastOrNull()
        val daysSince = last?.let { todayDay - it }
        val spanYears = if (first != null && last != null) (last - first) / 365.25 else 0.0
        val activeYears = s.years.size
        fun rate(x: Int) = if (n > 0) x.toDouble() / n else 0.0
        val readR = rate(s.seen)
        val impR = rate(s.important)
        val newsR = rate(maxOf(s.unsub, s.bulk))
        val promoR = rate(s.cat["promotions"] ?: 0)
        val updR = rate(s.cat["updates"] ?: 0)
        val socR = rate(s.cat["social"] ?: 0)
        fun sg(k: String) = s.sig[k] ?: 0
        val mktR = rate(sg("marketing"))

        // Regularity: coefficient of variation of gaps between mails (low = like clockwork).
        var cv: Double? = null
        if (dates.size >= 4) {
            var sum = 0L
            var sumSq = 0L
            for (i in 1 until dates.size) {
                val g = dates[i] - dates[i - 1]
                sum += g
                sumSq += g * g
            }
            val m = (dates.size - 1).toLong()
            val mu = sum.toDouble() / m
            if (mu > 0) {
                val variance = (m * sumSq - sum * sum).toDouble() / (m * m).toDouble()
                cv = sqrt(variance) / mu
            }
        }
        val billMonths = s.billMonths.size
        val hasAccount = sg("security") > 0 || sg("welcome") > 0 || sg("txn") > 0 ||
            sg("billing") > 0 || s.youSent > 0

        val pts = LinkedHashMap<String, Double>()
        pts[if (catName.isEmpty()) "category" else catName] = cat.second.toDouble()
        pts["security mails"] = 15 * lg(sg("security"), 5)
        pts["welcome/verify"] = 6 * lg(sg("welcome"), 3)
        pts["orders/invoices"] = 10 * lg(sg("txn"), 20)
        pts["billing"] = 10 * lg(sg("billing"), 12)
        pts["recurring bills"] = if (billMonths >= 6 && daysSince != null && daysSince < 400) 10.0
        else if (billMonths >= 3) 4.0 else 0.0
        pts["clockwork"] = if (cv != null && cv < 0.6 && n >= 6) 5.0 else 0.0
        pts["you emailed them"] = 15 * lg(s.youSent, 10)
        pts["you read it"] = if (n >= 3) 8 * readR else 0.0
        pts["Gmail important"] = 10 * impR
        pts["starred"] = 6 * lg(s.flagged, 3)
        pts["attachments"] = 4 * lg(s.attach, 10)
        pts["recency"] = when {
            daysSince == null -> 0.0
            daysSince < 365 -> 10.0
            daysSince < 3 * 365 -> 4.0
            daysSince > 5 * 365 -> -6.0
            else -> 0.0
        }
        pts["newsletter"] = -18 * newsR
        pts["promotions tab"] = -15 * promoR
        pts["marketing subjects"] = -10 * mktR
        pts["ignored (unread)"] = if (n >= 20) -12 * (1 - readR) else 0.0
        pts["spammy volume"] = if (s.last12 > 150 && newsR > 0.5) -6.0 else 0.0

        var sum = 0.0
        for (v in pts.values) sum += v
        val score = maxOf(0, minOf(100, BigDecimal(sum).setScale(0, RoundingMode.HALF_EVEN).toInt()))
        val tier = Tier.ofScore(score)
        val dormant = daysSince != null && daysSince > 3 * 365
        val action = when {
            tier == Tier.CRITICAL -> Action.CHANGE_FIRST
            hasAccount && dormant -> Action.DORMANT_DELETE
            hasAccount && (tier == Tier.IMPORTANT || tier == Tier.ACCOUNT) -> Action.CHANGE
            newsR > 0.5 || promoR > 0.5 -> Action.UNSUBSCRIBE
            hasAccount -> Action.CHANGE_LOW_OR_DELETE
            else -> Action.IGNORE
        }
        val components = pts.map { (k, v) -> WhyPart(k, v) }
        val why = components.filter { abs(it.points) >= 3 }.sortedByDescending { abs(it.points) }.take(4)
        val example = listOf("security", "billing", "txn", "welcome", "marketing")
            .firstNotNullOfOrNull { s.examples[it] } ?: ""
        val links = unsubLinks(s.unsubHeader)

        return ServiceScore(
            domain = s.key,
            domains = s.domains.toList(),
            displayNames = nameCounts.take(10),
            senderAddrs = s.senders.keys.toList(),
            category = catName,
            score = score,
            tier = tier,
            why = why,
            components = components,
            action = action,
            mails = n,
            last12 = s.last12,
            perYear = s.years.toMap(),
            perYearRate = roundHalfEven(n / maxOf(spanYears, 1.0), 1),
            firstEpochDay = first,
            lastEpochDay = last,
            daysSince = daysSince,
            activeYears = activeYears,
            readPct = readR,
            importantPct = impR,
            starred = s.flagged,
            youSent = s.youSent,
            security = sg("security"),
            welcome = sg("welcome"),
            orders = sg("txn"),
            billing = sg("billing"),
            marketing = sg("marketing"),
            billMonths = billMonths,
            regularity = cv?.let { roundHalfEven(it, 2) },
            newsletterPct = newsR,
            promoPct = promoR,
            updatesPct = updR,
            socialPct = socR,
            attachments = s.attach,
            noreply = s.noreply,
            unsubLinks = links,
            unsubOneClick = s.unsubOneClick && s.unsub > 0,
            exampleSubject = example,
            hasAccount = hasAccount,
            dormant = dormant,
        )
    }
}
