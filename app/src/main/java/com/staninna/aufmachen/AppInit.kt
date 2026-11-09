package com.staninna.aufmachen

import android.app.Application

/**
 * Process start hooks: notification channels, periodic sync scheduling, PdfBox init.
 * Owned by the sync worker area; keep it fast.
 */
object AppInit {
    fun onCreate(app: Application) {
    }
}
