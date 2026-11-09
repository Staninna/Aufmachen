package com.staninna.aufmachen.engine

import com.staninna.aufmachen.model.Status

// Contracts shared between worker areas. Signatures are fixed; bodies are filled by the owning area.
// Owning area is noted per declaration. Do not change a signature without the orchestrator.

// ---------------------------------------------------------------- watchdog (engine area)

/** A service group as the watchdog sees it. */
data class WatchedService(
    val groupKey: String,
    val domains: Set<String>,          // every registrable domain in the merge group
    val status: Status,
    val statusChangedAt: Long?,        // epoch millis
    val unsubscribedAt: Long?,         // epoch millis
)

/** A newly synced header (from an incremental sync, never the first scan). */
data class NewMail(
    val headerId: Long,
    val fromAddr: String,
    val fromDomain: String,            // registrable domain
    val toAddrs: List<String>,
    val subject: String,
    val dateEpochDay: Long?,
    val sent: Boolean,
)

data class WatchdogFailure(val groupKey: String, val mail: NewMail)

/** Watchdog failure rules (§6). Owner: engine area. */
object Watchdog {
    const val UNSUBSCRIBE_GRACE_DAYS = 14L

    /**
     * Returns one failure per new mail that hit the OLD mailbox from a watched service.
     * @param oldAddress the old address; mail not addressed to it still counts when it landed in the
     *   old mailbox unless it was addressed only to one of [isNewAddress] (forwarded copies).
     * @param nowMillis current time, for the unsubscribe grace period.
     */
    fun failures(
        services: List<WatchedService>,
        newMail: List<NewMail>,
        oldAddress: String?,
        isNewAddress: (String) -> Boolean,
        nowMillis: Long,
    ): List<WatchdogFailure> = watchdogFailures(services, newMail, oldAddress, isNewAddress, nowMillis)
}

// ---------------------------------------------------------------- unsubscribe method (engine area)

enum class UnsubMethod { ONE_CLICK, EMAIL, WEB, NONE }

/** Picks the method for a service: one-click POST > mailto > web link. Owner: engine area. */
fun unsubMethod(links: List<String>, oneClick: Boolean): UnsubMethod = pickUnsubMethod(links, oneClick)

/** Splits a raw List-Unsubscribe header into its URIs, in order. Owner: engine area. */
fun unsubLinks(header: String): List<String> = parseUnsubLinks(header)

// ---------------------------------------------------------------- Ask Claude (parsers area)

/** Service facts only. Never addresses, subjects or other personal data. */
data class PromptFacts(
    val name: String,
    val domains: List<String>,
    val displayNames: List<String>,
    val category: String,
    val hasSecurityMails: Boolean,
    val hasBilling: Boolean,
    val hasOrders: Boolean,
    val googleLoginSuspected: Boolean,
    val dormantYears: Int?,            // whole years since last mail when dormant, else null
)

data class ClaudeAnswer(
    val settingsUrl: String?,
    val privacyContact: String?,
    val steps: List<String>,
    val gotchas: List<String>,
)

/** Owner: parsers area. */
object ClaudePrompt {
    fun migrate(f: PromptFacts): String = ClaudePromptBuilder.migrate(f)
    fun delete(f: PromptFacts): String = ClaudePromptBuilder.delete(f)
}

/** Owner: parsers area. Returns null when the block format is not found. */
object ClaudeAnswerParser {
    fun parse(text: String): ClaudeAnswer? = ClaudeAnswerParse.parse(text)
}

// ---------------------------------------------------------------- Tuta PDF proof (parsers area)

data class TutaPdfFields(
    val title: String?,                // PDF Title metadata, "<account> - Tuta"
    val account: String?,              // logged-in Tuta account from the title
    val displayName: String,           // first text line
    val to: String?,                   // "to: <address>"
    val dateLine: String?,             // e.g. "Thu, Nov 5, 2026 • 22:14"
    val replyTo: String?,              // address from the "Reply to" block
    val bodyText: String,
    val links: List<String>,           // URI annotations
)

data class ProofCandidate(val groupKey: String, val domains: Set<String>, val displayNames: List<String>)

data class ProofMatch(val groupKey: String, val score: Double, val reasons: List<String>)

/** Owner: parsers area. */
object TutaPdfParser {
    /** [text] is the PDF text layer in reading order; null when it is not a Tuta export. */
    fun parse(title: String?, text: String, links: List<String>): TutaPdfFields? =
        TutaPdfParse.parse(title, text, links)
}

/** Owner: parsers area. Highest score first; [HIGH_CONFIDENCE] and above may skip the picker. */
object ProofMatcher {
    const val HIGH_CONFIDENCE = 0.75
    fun rank(fields: TutaPdfFields, candidates: List<ProofCandidate>): List<ProofMatch> =
        ProofRanker.rank(fields, candidates)
}

// ---------------------------------------------------------------- Google sign-in (parsers area)

data class NameMatch(val name: String, val groupKey: String, val score: Double)

/** Owner: parsers area. */
object GoogleConnections {
    /** App and site names from text copied off myaccount.google.com/connections. */
    fun parse(clipboard: String): List<String> = GoogleConn.parse(clipboard)

    /** Best service per name, above a fuzzy threshold. */
    fun match(names: List<String>, candidates: List<ProofCandidate>): List<NameMatch> =
        GoogleConn.match(names, candidates)

    /** True when a subject from a Google account/security sender hints at third-party access. */
    fun isThirdPartyAccessHint(fromAddr: String, subject: String): Boolean =
        GoogleConn.isThirdPartyAccessHint(fromAddr, subject)

    /** Service names mentioned in such a subject, matched to candidates. */
    fun candidatesFromSubject(subject: String, candidates: List<ProofCandidate>): List<NameMatch> =
        GoogleConn.candidatesFromSubject(subject, candidates)
}

// ---------------------------------------------------------------- mail templates (parsers area)

/** Owner: parsers area. Dutch when [dutch] (service TLD .nl/.be), else English. */
object Templates {
    data class Mail(val subject: String, val body: String)

    /** GDPR / AVG Art. 17 erasure request, sent from the old address. */
    fun gdprErasure(serviceName: String, oldAddress: String, dutch: Boolean): Mail =
        MailTemplates.gdprErasure(serviceName, oldAddress, dutch)

    /** "I've moved" note to people, with the new main address. */
    fun moved(newAddress: String, dutch: Boolean): Mail = MailTemplates.moved(newAddress, dutch)

    fun isDutchDomain(domain: String): Boolean = domain.endsWith(".nl") || domain.endsWith(".be")
}

// ---------------------------------------------------------------- PDF text layer (parsers area)

data class PdfExtract(val title: String?, val text: String, val links: List<String>)

/** Owner: parsers area. Text layer + URI link annotations via PdfBox-Android; no OCR. */
interface PdfTextSource {
    /** Null when the file is not a readable PDF. Empty [PdfExtract.text] means no text layer. */
    fun extract(path: String): PdfExtract?
}
