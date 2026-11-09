package com.staninna.aufmachen.ui.readiness

import com.staninna.aufmachen.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

/** Live readiness over services with an account, open alerts, people and settings. */
fun AppContainer.readinessFlow(): Flow<Readiness> =
    combine(repo.services, db.alerts().open(), db.people().all(), db.people().states(), settings.flow) { svcs, alerts, people, states, s ->
        val told = states.filter { it.told }.map { it.address }.toSet()
        ReadinessCalc.compute(svcs.filter { it.stats.hasAccount }, alerts, people, told, s)
    }.flowOn(Dispatchers.Default)
