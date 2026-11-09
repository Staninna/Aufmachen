package com.staninna.aufmachen.engine

import com.staninna.aufmachen.model.Action
import com.staninna.aufmachen.model.TellNewAddress
import com.staninna.aufmachen.model.Tier
import com.staninna.aufmachen.model.WhyPart

/**
 * One message's header data, as the scoring engine sees it. Pure Kotlin, no Android types.
 * Mirrors a row of the prototype's `msgs` table plus the List-Unsubscribe-Post flag.
 */
data class MailRecord(
    val dateEpochDay: Long?,       // LocalDate.toEpochDay(), null when the Date header was unusable
    val fromAddr: String,          // lowercased
    val fromName: String,
    val toAddrs: List<String>,     // To + Cc, lowercased
    val subject: String,
    val unsubHeader: String,       // raw List-Unsubscribe value
    val unsubOneClick: Boolean,    // List-Unsubscribe-Post: List-Unsubscribe=One-Click
    val bulk: Boolean,
    val attach: Boolean,
    val seen: Boolean,
    val flagged: Boolean,
    val important: Boolean,
    val sent: Boolean,
    val category: String,          // "", "primary", "promotions", "social", "updates", "forums"
)

/** Scored service (a registrable domain, or a merge group keyed by its primary domain). */
data class ServiceScore(
    val domain: String,                 // group key
    val domains: List<String>,          // every registrable domain in the group (group key first)
    val displayNames: List<String>,     // top sender display names, most common first (max 3 in the prototype; keep up to 10)
    val senderAddrs: List<String>,      // distinct sender addresses
    val category: String,               // "" when uncategorised
    val score: Int,                     // 0..100
    val tier: Tier,
    val why: List<WhyPart>,             // top components, |points| >= 3, max 4, sorted by |points| desc
    val components: List<WhyPart>,      // every component, for the detail screen
    val action: Action,
    val mails: Int,
    val last12: Int,
    val perYear: Map<Int, Int>,
    val perYearRate: Double,            // prototype "Per year": n / max(spanYears, 1), 1 decimal
    val firstEpochDay: Long?,
    val lastEpochDay: Long?,
    val daysSince: Long?,
    val activeYears: Int,
    val readPct: Double,                // 0..1
    val importantPct: Double,
    val starred: Int,
    val youSent: Int,
    val security: Int,
    val welcome: Int,
    val orders: Int,                    // prototype "txn"
    val billing: Int,
    val marketing: Int,
    val billMonths: Int,
    val regularity: Double?,            // gap CV, rounded to 2 decimals; null with < 4 dated mails
    val newsletterPct: Double,
    val promoPct: Double,
    val updatesPct: Double,
    val socialPct: Double,
    val attachments: Int,
    val noreply: Int,
    val unsubLinks: List<String>,       // every URI (http(s) and mailto) from the newest List-Unsubscribe
    val unsubOneClick: Boolean,         // newest List-Unsubscribe mail also had RFC 8058 List-Unsubscribe-Post
    val exampleSubject: String,
    val hasAccount: Boolean,
    val dormant: Boolean,
)

data class PersonScore(
    val address: String,
    val name: String,
    val youSent: Int,
    val theySent: Int,
    val twoWay: Boolean,
    val firstEpochDay: Long?,
    val lastEpochDay: Long?,
    val daysSince: Long?,
    val tell: TellNewAddress,
    val weight: Int,                    // prototype "_w", sort key (desc)
)

data class AnalysisResult(
    val services: List<ServiceScore>,   // sorted by (-score, -mails)
    val people: List<PersonScore>,      // sorted by -weight
    val total: Int,
    val perYear: Map<Int, Int>,
)
