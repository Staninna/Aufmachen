package com.staninna.aufmachen.mail

import android.content.Context
import androidx.annotation.StringRes
import com.staninna.aufmachen.R
import com.staninna.aufmachen.data.ImapConfig
import com.sun.mail.gimap.GmailFolder
import com.sun.mail.imap.IMAPFolder
import kotlinx.coroutines.CancellationException
import java.io.Closeable
import java.io.IOException
import java.net.UnknownHostException
import java.util.Properties
import javax.mail.AuthenticationFailedException
import javax.mail.Folder
import javax.mail.MessagingException
import javax.mail.Provider
import javax.mail.Session
import javax.mail.Store
import javax.net.ssl.SSLException

/** Application context holder so contract functions without a Context can resolve strings. */
internal object MailText {
    @Volatile var app: Context? = null
    fun get(@StringRes id: Int, vararg args: Any): String =
        app?.getString(id, *args) ?: "mail error $id"
}

/** A mail failure with a message safe to show. [retryable] is false for bad credentials and setup. */
class MailFailure(message: String, val retryable: Boolean, cause: Throwable? = null) : Exception(message, cause)

internal object MailErrors {
    /** Maps any exception to a user-presentable [MailFailure]. Never includes addresses or server text. */
    fun toFailure(e: Throwable): MailFailure {
        if (e is MailFailure) return e
        val chain = generateSequence(e) { it.cause ?: (it as? MessagingException)?.nextException }.take(8).toList()
        return when {
            chain.any { it is AuthenticationFailedException } ->
                MailFailure(MailText.get(R.string.mail_err_auth), retryable = false, cause = e)
            chain.any { it is SSLException } ->
                MailFailure(MailText.get(R.string.mail_err_tls), retryable = false, cause = e)
            chain.any { it is IOException || it is UnknownHostException } ->
                MailFailure(MailText.get(R.string.mail_err_offline), retryable = true, cause = e)
            else -> MailFailure(MailText.get(R.string.mail_err_generic), retryable = true, cause = e)
        }
    }

    inline fun <T> wrap(block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw toFailure(e)
    }
}

/** An open, read-only IMAP folder (All Mail, or INBOX as fallback). */
internal class ImapConnection(val store: Store, val folder: IMAPFolder, val gmail: Boolean) : Closeable {
    override fun close() {
        runCatching { if (folder.isOpen) folder.close(false) }
        runCatching { store.close() }
    }
}

internal object MailConnection {
    private const val VERSION = "1.6.8"

    private fun session(props: Properties): Session {
        val s = Session.getInstance(props)
        // Register providers in code: the merged META-INF/javamail.providers of the APK is not
        // reliable when several jars ship one.
        fun add(t: Provider.Type, proto: String, cls: String) =
            s.addProvider(Provider(t, proto, cls, "Oracle", VERSION))
        add(Provider.Type.STORE, "imap", "com.sun.mail.imap.IMAPStore")
        add(Provider.Type.STORE, "imaps", "com.sun.mail.imap.IMAPSSLStore")
        add(Provider.Type.STORE, "gimap", "com.sun.mail.gimap.GmailStore")
        add(Provider.Type.STORE, "gimaps", "com.sun.mail.gimap.GmailSSLStore")
        add(Provider.Type.TRANSPORT, "smtp", "com.sun.mail.smtp.SMTPTransport")
        add(Provider.Type.TRANSPORT, "smtps", "com.sun.mail.smtp.SMTPSSLTransport")
        return s
    }

    /** STARTTLS for the classic plain ports, implicit TLS otherwise. */
    fun usesStartTls(port: Int) = port == 143 || port == 587

    fun imapSession(cfg: ImapConfig): Pair<Session, String> {
        val tls = usesStartTls(cfg.port)
        val proto = (if (cfg.isGmail) "gimap" else "imap") + (if (tls) "" else "s")
        val p = Properties()
        p["mail.$proto.connectiontimeout"] = "20000"
        p["mail.$proto.timeout"] = "60000"
        p["mail.$proto.writetimeout"] = "60000"
        p["mail.$proto.peek"] = "true"
        p["mail.$proto.ssl.checkserveridentity"] = "true"
        if (tls) {
            p["mail.$proto.starttls.enable"] = "true"
            p["mail.$proto.starttls.required"] = "true"
        }
        return session(p) to proto
    }

    fun smtpSession(cfg: ImapConfig): Pair<Session, String> {
        val tls = usesStartTls(cfg.smtpPort)
        val proto = if (tls) "smtp" else "smtps"
        val p = Properties()
        p["mail.$proto.connectiontimeout"] = "20000"
        p["mail.$proto.timeout"] = "60000"
        p["mail.$proto.writetimeout"] = "60000"
        p["mail.$proto.auth"] = "true"
        p["mail.$proto.ssl.checkserveridentity"] = "true"
        if (tls) {
            p["mail.$proto.starttls.enable"] = "true"
            p["mail.$proto.starttls.required"] = "true"
        }
        return session(p) to proto
    }

    /** Logs in and opens All Mail read-only (EXAMINE, so nothing can change). */
    fun open(cfg: ImapConfig, password: String): ImapConnection {
        val (session, proto) = imapSession(cfg)
        val store = session.getStore(proto)
        try {
            store.connect(cfg.host, cfg.port, cfg.user, password)
            val folder = findAllMail(store, cfg.isGmail)
            folder.open(Folder.READ_ONLY)
            return ImapConnection(store, folder, cfg.isGmail && folder is GmailFolder)
        } catch (e: Throwable) {
            runCatching { store.close() }
            throw e
        }
    }

    /**
     * Port of `find_all_mail`: the folder with the `\All` special-use attribute (works in any UI
     * language). IMAP LIST sends the attributes, javax.mail exposes them as getAttributes().
     */
    private fun findAllMail(store: Store, gmail: Boolean): IMAPFolder {
        val all = runCatching {
            store.defaultFolder.list("*").filterIsInstance<IMAPFolder>().firstOrNull { f ->
                f.attributes.any { it.equals("\\All", ignoreCase = true) }
            }
        }.getOrNull()
        return all ?: (store.getFolder("INBOX") as IMAPFolder)
    }
}
