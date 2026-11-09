package com.staninna.aufmachen.ui.people

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.staninna.aufmachen.R

const val TUTA_PACKAGE = "de.tutao.tutanota"

/** Opens the mail app (Tuta first, else a chooser) with a pre-filled draft. Nothing is sent from here. */
fun openMailDraft(ctx: Context, to: List<String>, bcc: List<String>, subject: String, body: String) {
    val uri = if (to.size == 1) Uri.fromParts("mailto", to[0], null) else Uri.parse("mailto:")
    val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
        if (to.size > 1) putExtra(Intent.EXTRA_EMAIL, to.toTypedArray())
        if (bcc.isNotEmpty()) putExtra(Intent.EXTRA_BCC, bcc.toTypedArray())
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    try {
        ctx.startActivity(Intent(intent).setPackage(TUTA_PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        try {
            ctx.startActivity(
                Intent.createChooser(intent, ctx.getString(R.string.lists_moved_chooser))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(ctx, R.string.lists_moved_no_mail_app, Toast.LENGTH_SHORT).show()
        }
    }
}
