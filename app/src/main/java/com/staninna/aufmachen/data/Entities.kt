package com.staninna.aufmachen.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One message's headers from the old mailbox. Never holds a body.
 * [initialScan] is true for rows ingested by the first full scan; the watchdog only looks at
 * rows from later incremental syncs.
 */
@Entity(
    tableName = "headers",
    indices = [Index(value = ["source", "uid"], unique = true), Index("fromDomain"), Index("syncedAt")],
)
data class HeaderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,            // SOURCE_IMAP or SOURCE_MBOX
    val uid: Long,                 // IMAP UID, or running index in the mbox
    val dateEpochDay: Long?,
    val fromAddr: String,
    val fromName: String,
    val fromDomain: String,        // registrable domain of fromAddr ("" when none)
    val toAddrs: String,           // comma separated, lowercased (To + Cc)
    val subject: String,
    val unsubHeader: String,
    val unsubOneClick: Boolean,
    val bulk: Boolean,
    val attach: Boolean,
    val seen: Boolean,
    val flagged: Boolean,
    val important: Boolean,
    val sent: Boolean,
    val category: String,
    val syncedAt: Long,
    val initialScan: Boolean,
) {
    companion object {
        const val SOURCE_IMAP = "imap"
        const val SOURCE_MBOX = "mbox"
    }
}

/** Computed by the engine; the whole table is replaced on every rescore. Lists are JSON. */
@Entity(tableName = "service_stats", indices = [Index("score"), Index("tier")])
data class ServiceStatsEntity(
    @PrimaryKey val domain: String,
    val domainsJson: String,
    val displayNamesJson: String,
    val senderAddrsJson: String,
    val category: String,
    val score: Int,
    val tier: Int,                 // Tier.level computed by the engine (pins live in ServiceStateEntity)
    val whyJson: String,
    val componentsJson: String,
    val action: String,            // Action.name
    val mails: Int,
    val last12: Int,
    val perYearJson: String,
    val perYearRate: Double,
    val firstEpochDay: Long?,
    val lastEpochDay: Long?,
    val daysSince: Long?,
    val activeYears: Int,
    val readPct: Double,
    val importantPct: Double,
    val starred: Int,
    val youSent: Int,
    val security: Int,
    val welcome: Int,
    val orders: Int,
    val billing: Int,
    val marketing: Int,
    val billMonths: Int,
    val regularity: Double?,
    val newsletterPct: Double,
    val promoPct: Double,
    val updatesPct: Double,
    val socialPct: Double,
    val attachments: Int,
    val unsubLinksJson: String,
    val unsubOneClick: Boolean,
    val exampleSubject: String,
    val hasAccount: Boolean,
    val dormant: Boolean,
    val newsletterLike: Boolean,   // action == UNSUBSCRIBE || (newsletterPct > 0.5 && unsub link present)
)

/** User state per service group. Survives rescans. Keyed by the group key. */
@Entity(tableName = "service_state")
data class ServiceStateEntity(
    @PrimaryKey val domain: String,
    val status: String = "TODO",           // Status.name
    val statusChangedAt: Long? = null,     // epoch millis
    val failed: Boolean = false,           // open watchdog failure
    val pinnedTier: Int? = null,           // Tier.level override
    val assignedAddress: String? = null,   // full new address; null = derived from buckets
    val googleLogin: Boolean = false,
    val loginSwitched: Boolean = false,
    val snoozedUntil: Long? = null,
    val settingsUrl: String? = null,
    val privacyContact: String? = null,
    val stepsJson: String = "[]",          // List<String>
    val stepsDoneJson: String = "[]",      // List<Int> indexes ticked
    val gotchasJson: String = "[]",        // List<String>
    val claudeNotes: String? = null,       // unparsed answer text
    val unsubscribedAt: Long? = null,
    val unsubMethod: String? = null,       // "oneclick" | "mailto" | "web"
)

/** domain -> group key. A group key maps to itself implicitly (no row). */
@Entity(tableName = "merges")
data class MergeEntity(
    @PrimaryKey val domain: String,
    val intoDomain: String,
)

@Entity(tableName = "timeline", indices = [Index("domain")])
data class TimelineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domain: String,
    val at: Long,
    val kind: String,              // TimelineKind.*
    val text: String,
)

object TimelineKind {
    const val STATUS = "status"
    const val PROOF = "proof"
    const val WATCHDOG = "watchdog"
    const val NOTE = "note"
    const val SENT = "sent"        // unsubscribe / GDPR mail sent
    const val REPLY = "reply"      // mail from the service after a deletion request
}

@Entity(tableName = "proofs", indices = [Index("domain")])
data class ProofEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domain: String,
    val receivedDate: String?,     // as parsed from the PDF date line
    val recipient: String,
    val displayName: String,
    val replyTo: String?,
    val title: String,
    val pdfPath: String,           // file in app-private storage
    val linksJson: String,
    val createdAt: Long,
)

@Entity(tableName = "alerts", indices = [Index("domain"), Index(value = ["headerId"], unique = true)])
data class AlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domain: String,
    val headerId: Long,
    val dateEpochDay: Long?,
    val senderAddr: String,
    val subject: String,
    val createdAt: Long,
    val dismissed: Boolean = false,
    val dismissReason: String? = null,
)

/** Computed; replaced on every rescore. */
@Entity(tableName = "person_stats")
data class PersonStatsEntity(
    @PrimaryKey val address: String,
    val name: String,
    val youSent: Int,
    val theySent: Int,
    val twoWay: Boolean,
    val firstEpochDay: Long?,
    val lastEpochDay: Long?,
    val tell: String,              // TellNewAddress.name
    val weight: Int,
)

/** User state per person. Survives rescans. */
@Entity(tableName = "person_state")
data class PersonStateEntity(
    @PrimaryKey val address: String,
    val told: Boolean = false,
    val toldAt: Long? = null,
)

/** Scan/sync bookkeeping per source. */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val source: String,
    val folder: String? = null,
    val uidValidity: Long? = null,
    val highestUid: Long = 0,
    val initialDone: Boolean = false,
    val total: Int = 0,
    val processed: Int = 0,
    val startedAt: Long? = null,
    val lastSyncAt: Long? = null,
    val lastError: String? = null,
)
