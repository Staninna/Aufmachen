package com.staninna.aufmachen.mail

import android.content.Context
import com.staninna.aufmachen.R
import com.staninna.aufmachen.container
import com.staninna.aufmachen.data.ImapConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date
import javax.activation.CommandMap
import javax.activation.MailcapCommandMap
import javax.mail.Message
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/** Implementations behind [MailAccess]. */
internal object MailAccessImpl {
    suspend fun testLogin(config: ImapConfig, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            MailConnection.open(config, password.replace(" ", "")).close()
            Result.success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(MailErrors.toFailure(e))
        }
    }

    suspend fun send(context: Context, to: String, subject: String, body: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val c = context.container
                val cfg = c.settings.get().imap
                val pw = c.secrets.imapPassword
                if (cfg == null || pw.isNullOrEmpty()) {
                    throw MailFailure(MailText.get(R.string.mail_err_not_configured), retryable = false)
                }
                ensureTextHandler()
                val (session, proto) = MailConnection.smtpSession(cfg)
                val msg = MimeMessage(session).apply {
                    setFrom(InternetAddress(cfg.user))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, false))
                    setSubject(subject, "UTF-8")
                    setText(body, "UTF-8")
                    sentDate = Date()
                    saveChanges()
                }
                val t = session.getTransport(proto)
                try {
                    t.connect(cfg.smtpHost, cfg.smtpPort, cfg.user, pw)
                    t.sendMessage(msg, msg.allRecipients)
                } finally {
                    runCatching { t.close() }
                }
                Result.success(Unit)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(MailErrors.toFailure(e))
            }
        }

    /** The APK drops META-INF/mailcap, so register the text/plain writer in code. */
    private fun ensureTextHandler() {
        val cur = CommandMap.getDefaultCommandMap()
        val map = (cur as? MailcapCommandMap) ?: MailcapCommandMap()
        map.addMailcap("text/plain;; x-java-content-handler=com.sun.mail.handlers.text_plain")
        CommandMap.setDefaultCommandMap(map)
    }

    /** RFC 8058: POST `List-Unsubscribe=One-Click`, https only, success on 2xx. */
    suspend fun oneClick(url: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://", ignoreCase = true)) {
            return@withContext Result.failure(MailFailure(MailText.get(R.string.mail_err_unsub_https), false))
        }
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            conn.outputStream.use { it.write("List-Unsubscribe=One-Click".toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code in 200..299) Result.success(Unit)
            else Result.failure(MailFailure(MailText.get(R.string.mail_err_unsub_status, code), false))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(MailErrors.toFailure(e))
        } finally {
            conn?.disconnect()
        }
    }
}
