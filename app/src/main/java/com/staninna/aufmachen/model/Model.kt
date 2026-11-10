package com.staninna.aufmachen.model

import kotlinx.serialization.Serializable

/** Score tiers. [level] 1 is most important. Thresholds match mail_triage.py. */
enum class Tier(val level: Int, val minScore: Int) {
    CRITICAL(1, 65), IMPORTANT(2, 45), ACCOUNT(3, 28), LOW(4, 12), TRASH(5, 0);

    companion object {
        fun ofScore(score: Int): Tier = entries.first { score >= it.minScore }
        fun ofLevel(level: Int): Tier = entries.first { it.level == level }
    }
}

/** Migration status of a service (group). */
enum class Status {
    TODO, CHANGED, VERIFIED, DELETED, DELETION_REQUESTED, UNSUBSCRIBED, IGNORED;

    /** Statuses after which any new mail to the old address is a watchdog failure. */
    val watched: Boolean get() = this in setOf(CHANGED, VERIFIED, DELETED, DELETION_REQUESTED, UNSUBSCRIBED)
    val open: Boolean get() = this == TODO
}

/** Suggested action, as in score_service() of the prototype. */
enum class Action {
    CHANGE_FIRST,      // "Change email FIRST + check 2FA/recovery"
    DORMANT_DELETE,    // "Dormant: delete account (or ignore)"
    CHANGE,            // "Change email"
    UNSUBSCRIBE,       // "Unsubscribe"
    CHANGE_LOW_OR_DELETE, // "Change email (low prio) or delete account"
    IGNORE,            // "Ignore"
}

enum class TellNewAddress { YES, MAYBE, NO }

/** The list (bottom tab) a service or person sits in, when the user moved it there. */
enum class Placement { MIGRATE, UNSUBSCRIBE, DORMANT, DELETE, PEOPLE }

/** One named score component, e.g. "Finance" +40. */
@Serializable
data class WhyPart(val key: String, val points: Double)

/** A named new address, e.g. money@ for Finance services. */
@Serializable
data class AddressBucket(
    val name: String,          // display label, e.g. "Money"
    val localPart: String,     // e.g. "money"
    val tiers: List<Int> = emptyList(),        // Tier.level values routed here
    val categories: List<String> = emptyList(), // engine category names routed here
    val isMain: Boolean = false,
)

object Categories {
    const val GOVERNMENT = "Government"
    const val FINANCE = "Finance"
    const val CRYPTO = "Crypto/Investing"
    const val INSURANCE = "Insurance/Health"
    const val UTILITIES = "Utilities/Telecom"
    const val IDENTITY = "Identity/Tech"
    const val HOUSING = "Housing"
    const val WORK = "Work/Education"
    const val TRAVEL = "Travel/Mobility"
    const val SHOPPING = "Shopping"
    val ALL = listOf(GOVERNMENT, FINANCE, CRYPTO, INSURANCE, UTILITIES, IDENTITY, HOUSING, WORK, TRAVEL, SHOPPING)
}

object DefaultBuckets {
    fun create(): List<AddressBucket> = listOf(
        AddressBucket("Main", "me", isMain = true),
        AddressBucket("Money", "money", categories = listOf(Categories.FINANCE, Categories.CRYPTO, Categories.INSURANCE)),
        AddressBucket("Government", "gov", categories = listOf(Categories.GOVERNMENT, Categories.HOUSING)),
        AddressBucket("Accounts", "accounts", tiers = listOf(1, 2, 3),
            categories = listOf(Categories.IDENTITY, Categories.UTILITIES, Categories.WORK)),
        AddressBucket("Shopping", "shop", categories = listOf(Categories.SHOPPING, Categories.TRAVEL)),
        AddressBucket("News", "news", tiers = listOf(5)),
        AddressBucket("Social", "social", tiers = listOf(4)),
    )
}
