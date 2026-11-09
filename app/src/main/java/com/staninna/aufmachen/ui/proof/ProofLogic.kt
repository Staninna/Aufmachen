package com.staninna.aufmachen.ui.proof

import com.staninna.aufmachen.data.ServiceView
import com.staninna.aufmachen.engine.GoogleConnections
import com.staninna.aufmachen.engine.NameMatch
import com.staninna.aufmachen.engine.ProofCandidate

/** How the mail's recipient relates to the user's new addresses and the chosen service. */
enum class AddressVerdict {
    /** The mail did not go to a new address. Needs an explicit "Use anyway". */
    NOT_NEW,
    /** A new address, and the service already uses it. */
    MATCHES,
    /** A new address, but different from the service's address. */
    DIFFERS,
}

fun addressVerdict(to: String?, isNewAddress: (String) -> Boolean, serviceAddress: String?): AddressVerdict {
    if (to.isNullOrBlank() || !isNewAddress(to)) return AddressVerdict.NOT_NEW
    return if (serviceAddress != null && serviceAddress.equals(to, ignoreCase = true)) AddressVerdict.MATCHES
    else AddressVerdict.DIFFERS
}

fun ServiceView.toCandidate() = ProofCandidate(domain, domains.toSet(), displayNames)

/** Name shown for a service: first display name, else the group key. */
fun ServiceView.label(): String = displayNames.firstOrNull()?.takeIf { it.isNotBlank() } ?: domain

/** File name for a stored proof, safe for any group key. */
fun proofFileName(groupKey: String, timestamp: Long): String =
    groupKey.replace(Regex("[^A-Za-z0-9._-]"), "_") + "-" + timestamp + ".pdf"

/** The failure flag clears only when the service has no open alerts. */
fun failedAfterProof(wasFailed: Boolean, openAlerts: Int): Boolean = wasFailed && openAlerts > 0

/** Case-insensitive search over service labels and group keys. */
fun List<ServiceView>.search(query: String): List<ServiceView> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return this
    return filter { s -> s.domain.lowercase().contains(q) || s.displayNames.any { it.lowercase().contains(q) } }
}

/**
 * Group keys that Google mails hint at, from (fromAddr, subject) rows.
 * Keeps first-seen order, drops [exclude].
 */
fun suggestionKeys(
    rows: List<Pair<String, String>>,
    candidates: List<ProofCandidate>,
    exclude: Set<String> = emptySet(),
): List<String> {
    val out = LinkedHashSet<String>()
    for ((from, subject) in rows) {
        if (!GoogleConnections.isThirdPartyAccessHint(from, subject)) continue
        for (m: NameMatch in GoogleConnections.candidatesFromSubject(subject, candidates)) {
            if (m.groupKey !in exclude) out += m.groupKey
        }
    }
    return out.toList()
}

/** One parsed name from the clipboard with its auto match. */
data class ReviewRow(val name: String, val groupKey: String?, val checked: Boolean)

fun reviewRows(names: List<String>, matches: List<NameMatch>): List<ReviewRow> {
    val byName = matches.associateBy { it.name }
    return names.map { n -> byName[n].let { ReviewRow(n, it?.groupKey, it != null) } }
}
