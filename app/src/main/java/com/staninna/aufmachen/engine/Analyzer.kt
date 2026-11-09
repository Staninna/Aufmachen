package com.staninna.aufmachen.engine

import java.time.LocalDate

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

    fun add(r: MailRecord) {
        TODO("engine worker")
    }

    fun result(): AnalysisResult {
        TODO("engine worker")
    }
}
