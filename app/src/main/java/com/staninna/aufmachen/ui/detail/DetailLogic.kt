package com.staninna.aufmachen.ui.detail

import com.staninna.aufmachen.model.WhyPart
import kotlin.math.abs
import kotlin.math.roundToInt

/** Service name from a domain: the first label, lowercased, safe characters only. "example.co.uk" gives "example". */
fun serviceLabel(domain: String): String {
    val first = domain.trim().lowercase().substringBefore('.')
    val safe = first.filter { it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' }.trim('-', '_')
    return safe.ifEmpty { "service" }
}

/** Prefill for "Give this service its own address". */
fun ownAddressSuggestion(domain: String, myDomain: String): String =
    serviceLabel(domain) + "@" + myDomain.trim().lowercase()

private val ADDRESS = Regex("^[A-Za-z0-9._%+'-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+$")

fun looksLikeAddress(s: String): Boolean = ADDRESS.matches(s.trim())

/** Score components with |points| >= 1, biggest effect first. */
fun whyRows(components: List<WhyPart>): List<WhyPart> =
    components.filter { abs(it.points) >= 1.0 }.sortedByDescending { abs(it.points) }

/** "+40" or "-12". */
fun signedPoints(p: Double): String {
    val n = p.roundToInt()
    return if (n >= 0) "+$n" else n.toString()
}

fun toggleStep(done: Set<Int>, index: Int, checked: Boolean): List<Int> =
    (if (checked) done + index else done - index).sorted()

/** Bar heights 0..1 for the per-year sparkline, one bar per year from first to last, gaps as 0. */
fun sparkBars(perYear: Map<Int, Int>): List<Pair<Int, Float>> {
    if (perYear.isEmpty()) return emptyList()
    val max = perYear.values.max().coerceAtLeast(1)
    return (perYear.keys.min()..perYear.keys.max()).map { y -> y to (perYear[y] ?: 0).toFloat() / max }
}
