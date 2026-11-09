package com.staninna.aufmachen

import android.app.Application
import android.content.Context
import com.staninna.aufmachen.data.AppDatabase
import com.staninna.aufmachen.data.Repo
import com.staninna.aufmachen.data.SecretStore
import com.staninna.aufmachen.data.SettingsStore

/** Manual dependency container. One per process. */
class AppContainer(context: Context) {
    val secrets = SecretStore(context)
    val settings = SettingsStore(context)
    val db: AppDatabase by lazy { AppDatabase.open(context, secrets.databasePassphrase()) }
    val repo: Repo by lazy { Repo(db, settings) }
}

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        AppInit.onCreate(this)
    }
}

val Context.container: AppContainer get() = (applicationContext as App).container
