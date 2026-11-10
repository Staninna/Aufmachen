package com.staninna.aufmachen.data

import com.staninna.aufmachen.engine.Analyzer
import com.staninna.aufmachen.engine.MailRecord
import com.staninna.aufmachen.engine.ServiceScore
import com.staninna.aufmachen.engine.FREEMAIL
import com.staninna.aufmachen.engine.baseDomain
import com.staninna.aufmachen.model.Action
import com.staninna.aufmachen.model.Placement
import com.staninna.aufmachen.model.AddressBucket
import com.staninna.aufmachen.model.Status
import com.staninna.aufmachen.model.Tier
import com.staninna.aufmachen.model.WhyPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.LocalDate

val AppJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun List<String>.toJson(): String = AppJson.encodeToString(ListSerializer(String.serializer()), this)
fun String.jsonStrings(): List<String> =
    runCatching { AppJson.decodeFromString(ListSerializer(String.serializer()), this) }.getOrDefault(emptyList())
fun String.jsonInts(): List<Int> =
    runCatching { AppJson.decodeFromString(ListSerializer(Int.serializer()), this) }.getOrDefault(emptyList())
fun List<Int>.intsToJson(): String = AppJson.encodeToString(ListSerializer(Int.serializer()), this)

/** A service as the UI sees it: computed stats + user state + derived values. */
data class ServiceView(
    val stats: ServiceStatsEntity,
    val state: ServiceStateEntity,
    val newAddress: String?,
) {
    val domain: String get() = stats.domain
    val status: Status get() = runCatching { Status.valueOf(state.status) }.getOrDefault(Status.TODO)
    val tier: Tier get() = Tier.ofLevel(state.pinnedTier ?: stats.tier)
    val tierPinned: Boolean get() = state.pinnedTier != null
    val action: Action get() = runCatching { Action.valueOf(stats.action) }.getOrDefault(Action.IGNORE)
    val domains: List<String> get() = stats.domainsJson.jsonStrings()
    val displayNames: List<String> get() = stats.displayNamesJson.jsonStrings()
    val senderAddrs: List<String> get() = stats.senderAddrsJson.jsonStrings()
    val why: List<WhyPart> get() = runCatching { AppJson.decodeFromString(ListSerializer(WhyPart.serializer()), stats.whyJson) }.getOrDefault(emptyList())
    val components: List<WhyPart> get() = runCatching { AppJson.decodeFromString(ListSerializer(WhyPart.serializer()), stats.componentsJson) }.getOrDefault(emptyList())
    val perYear: Map<Int, Int> get() = runCatching { AppJson.decodeFromString(MapSerializer(Int.serializer(), Int.serializer()), stats.perYearJson) }.getOrDefault(emptyMap())
    val unsubLinks: List<String> get() = stats.unsubLinksJson.jsonStrings()
    val steps: List<String> get() = state.stepsJson.jsonStrings()
    val stepsDone: Set<Int> get() = state.stepsDoneJson.jsonInts().toSet()
    val gotchas: List<String> get() = state.gotchasJson.jsonStrings()
    val snoozed: Boolean get() = (state.snoozedUntil ?: 0) > System.currentTimeMillis()
    /** Display name for the service: the group key domain. */
    val name: String get() = stats.domain

    /** The tab the user moved this service to, or null when the app's guess applies. */
    val placement: Placement? get() = state.placement?.let { runCatching { Placement.valueOf(it) }.getOrNull() }

    /**
     * Whether this service is listed in [tab]. A user's move wins and puts it in exactly one tab.
     * Without one, the app's guess applies, and a service may then show in more than one tab.
     */
    fun inTab(tab: Placement): Boolean = placement?.let { it == tab } ?: when (tab) {
        Placement.MIGRATE -> stats.hasAccount
        Placement.UNSUBSCRIBE -> stats.newsletterLike
        Placement.DORMANT -> stats.hasAccount && stats.dormant
        Placement.DELETE, Placement.PEOPLE -> false
    }
}

/** Whether a person row belongs in People: not moved elsewhere by the user. */
fun PersonStateEntity?.inPeople(): Boolean =
    this?.placement == null || this.placement == Placement.PEOPLE.name

/** Picks the bucket for a service: category match first, then tier, else the main bucket. */
fun bucketFor(buckets: List<AddressBucket>, category: String, tier: Tier): AddressBucket? =
    buckets.firstOrNull { category.isNotEmpty() && category in it.categories }
        ?: buckets.firstOrNull { tier.level in it.tiers }
        ?: buckets.firstOrNull { it.isMain }
        ?: buckets.firstOrNull()

class Repo(val db: AppDatabase, val settings: SettingsStore) {
    private val rescoreLock = Mutex()

    val services: Flow<List<ServiceView>> =
        combine(db.services().allStats(), db.services().allStates(), settings.flow) { stats, states, s ->
            val byDomain = states.associateBy { it.domain }
            stats.map { st -> view(st, byDomain[st.domain] ?: ServiceStateEntity(st.domain), s) }
        }.flowOn(Dispatchers.Default)

    fun service(domain: String): Flow<ServiceView?> =
        combine(db.services().stats(domain), db.services().state(domain), settings.flow) { st, state, s ->
            st?.let { view(it, state ?: ServiceStateEntity(domain), s) }
        }

    private fun view(st: ServiceStatsEntity, state: ServiceStateEntity, s: AppSettings): ServiceView {
        val tier = Tier.ofLevel(state.pinnedTier ?: st.tier)
        val addr = state.assignedAddress ?: bucketFor(s.buckets, st.category, tier)?.let { s.addressOf(it) }
        return ServiceView(st, state, addr)
    }

    suspend fun state(domain: String): ServiceStateEntity =
        db.services().stateOnce(domain) ?: ServiceStateEntity(domain)

    suspend fun updateState(domain: String, block: (ServiceStateEntity) -> ServiceStateEntity) =
        db.services().upsertState(block(state(domain)))

    suspend fun timeline(domain: String, kind: String, text: String) {
        db.timeline().insert(TimelineEntity(domain = domain, at = System.currentTimeMillis(), kind = kind, text = text))
    }

    /** Sets status, records it in the timeline, and clears an open failure flag on a fresh change. */
    suspend fun setStatus(domain: String, status: Status, note: String? = null) {
        val now = System.currentTimeMillis()
        updateState(domain) {
            it.copy(
                status = status.name,
                statusChangedAt = now,
                failed = if (status == Status.TODO || status == Status.IGNORED) false else it.failed,
                unsubscribedAt = if (status == Status.UNSUBSCRIBED) now else it.unsubscribedAt,
                snoozedUntil = null,
            )
        }
        timeline(domain, TimelineKind.STATUS, status.name + (note?.let { ": $it" } ?: ""))
    }

    suspend fun pinTier(domain: String, level: Int?) = updateState(domain) { it.copy(pinnedTier = level) }

    suspend fun snooze(domain: String, days: Int = 7) =
        updateState(domain) { it.copy(snoozedUntil = System.currentTimeMillis() + days * 86_400_000L) }

    /** Merges [domain] (and anything already merged into it) into [into], then rescores. */
    suspend fun merge(domain: String, into: String) {
        val merges = db.services().mergesOnce()
        val target = merges.firstOrNull { it.domain == into }?.intoDomain ?: into
        merges.filter { it.intoDomain == domain }.forEach { db.services().upsertMerge(it.copy(intoDomain = target)) }
        db.services().upsertMerge(MergeEntity(domain, target))
        timeline(target, TimelineKind.NOTE, "Merged $domain")
        rescore()
    }

    /** Moves services to the [to] tab; null puts them back where the app guesses. Survives rescans. */
    suspend fun moveServices(domains: Collection<String>, to: Placement?) {
        for (d in domains) {
            updateState(d) { it.copy(placement = to?.name) }
            timeline(d, TimelineKind.NOTE, if (to == null) "Moved back to the automatic tab" else "Moved to ${to.name}")
        }
    }

    /**
     * Moves people out of (or back into) People. A person moved to a service tab takes their
     * domain's service along, when the domain is a company and not a freemail provider.
     * @return how many of [addresses] had no service to move (freemail), so they were only hidden.
     */
    suspend fun movePeople(addresses: Collection<String>, to: Placement?): Int {
        val states = db.people().statesOnce().associateBy { it.address }
        val merges = db.services().mergesOnce().associate { it.domain to it.intoDomain }
        val known = db.services().allStatsOnce().map { it.domain }.toSet()
        val placement = to?.takeIf { it != Placement.PEOPLE }
        val serviceKeys = mutableSetOf<String>()
        var withoutService = 0
        db.people().upsertStates(addresses.map { a ->
            (states[a] ?: PersonStateEntity(a)).copy(placement = placement?.name)
        })
        if (placement != null) {
            for (a in addresses) {
                val dom = baseDomain(a.substringAfterLast('@'))
                val key = merges[dom] ?: dom
                if (dom !in FREEMAIL && key in known) serviceKeys += key else withoutService++
            }
            moveServices(serviceKeys, placement)
        }
        return withoutService
    }

    suspend fun unmerge(domain: String) {
        val m = db.services().mergesOnce().firstOrNull { it.domain == domain } ?: return
        db.services().deleteMerge(domain)
        timeline(m.intoDomain, TimelineKind.NOTE, "Unmerged $domain")
        rescore()
    }

    /** Re-runs the scoring engine over every stored header and replaces the computed tables. */
    suspend fun rescore(today: LocalDate = LocalDate.now()) = withContext(Dispatchers.Default) {
        rescoreLock.withLock {
            val s = settings.get()
            val merges = db.services().mergesOnce().associate { it.domain to it.intoDomain }
            val own = buildSet {
                addAll(s.newAddresses)
                s.oldAddress?.let { add(it) }
            }
            val analyzer = Analyzer(today, own) { merges[it] ?: it }
            var after = 0L
            while (true) {
                val page = db.headers().page(after, 5000)
                if (page.isEmpty()) break
                page.forEach { analyzer.add(it.toRecord()) }
                after = page.last().id
            }
            val res = analyzer.result()
            db.services().replaceStats(res.services.map { it.toEntity() })
            db.people().replaceAll(res.people.map {
                PersonStatsEntity(it.address, it.name, it.youSent, it.theySent, it.twoWay,
                    it.firstEpochDay, it.lastEpochDay, it.tell.name, it.weight)
            })
        }
    }
}

fun HeaderEntity.toRecord() = MailRecord(
    dateEpochDay = dateEpochDay, fromAddr = fromAddr, fromName = fromName,
    toAddrs = toAddrs.split(',').filter { it.isNotBlank() }, subject = subject,
    unsubHeader = unsubHeader, unsubOneClick = unsubOneClick, bulk = bulk, attach = attach,
    seen = seen, flagged = flagged, important = important, sent = sent, category = category,
)

fun ServiceScore.toEntity() = ServiceStatsEntity(
    domain = domain,
    domainsJson = domains.toJson(),
    displayNamesJson = displayNames.toJson(),
    senderAddrsJson = senderAddrs.toJson(),
    category = category,
    score = score,
    tier = tier.level,
    whyJson = AppJson.encodeToString(ListSerializer(WhyPart.serializer()), why),
    componentsJson = AppJson.encodeToString(ListSerializer(WhyPart.serializer()), components),
    action = action.name,
    mails = mails,
    last12 = last12,
    perYearJson = AppJson.encodeToString(MapSerializer(Int.serializer(), Int.serializer()), perYear),
    perYearRate = perYearRate,
    firstEpochDay = firstEpochDay,
    lastEpochDay = lastEpochDay,
    daysSince = daysSince,
    activeYears = activeYears,
    readPct = readPct,
    importantPct = importantPct,
    starred = starred,
    youSent = youSent,
    security = security,
    welcome = welcome,
    orders = orders,
    billing = billing,
    marketing = marketing,
    billMonths = billMonths,
    regularity = regularity,
    newsletterPct = newsletterPct,
    promoPct = promoPct,
    updatesPct = updatesPct,
    socialPct = socialPct,
    attachments = attachments,
    unsubLinksJson = unsubLinks.toJson(),
    unsubOneClick = unsubOneClick,
    exampleSubject = exampleSubject,
    hasAccount = hasAccount,
    dormant = dormant,
    newsletterLike = action == Action.UNSUBSCRIBE || (newsletterPct > 0.5 && unsubLinks.isNotEmpty()),
)
