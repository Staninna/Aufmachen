package com.staninna.aufmachen.engine

internal object MailTemplates {
    fun gdprErasure(serviceName: String, oldAddress: String, dutch: Boolean): Templates.Mail =
        if (dutch) Templates.Mail(
            subject = "Verzoek tot verwijdering van mijn gegevens (AVG artikel 17): $serviceName",
            body = """
                Geachte heer/mevrouw,

                Met een beroep op artikel 17 van de Algemene verordening gegevensbescherming (AVG) verzoek ik u om mijn account en alle bijbehorende persoonsgegevens bij $serviceName te verwijderen.

                Het account is geregistreerd op het e-mailadres $oldAddress. Dit adres gebruik ik niet meer.

                Wilt u binnen één maand na ontvangst van dit bericht bevestigen dat mijn gegevens zijn gewist? Als u iets nodig hebt om mijn identiteit te controleren, laat het me dan weten.

                Alvast bedankt.

                Met vriendelijke groet
            """.trimIndent(),
        ) else Templates.Mail(
            subject = "Request to erase my personal data (GDPR Article 17): $serviceName",
            body = """
                Dear Sir or Madam,

                Under Article 17 of the General Data Protection Regulation (GDPR), I ask you to delete my account and all personal data you hold about me at $serviceName.

                The account is registered under the email address $oldAddress. I no longer use this address.

                Please confirm within one month of receiving this message that my data has been erased. If you need anything to verify my identity, let me know.

                Thank you in advance.

                Kind regards
            """.trimIndent(),
        )

    fun moved(newAddress: String, dutch: Boolean): Templates.Mail =
        if (dutch) Templates.Mail(
            subject = "Mijn nieuwe e-mailadres",
            body = """
                Hoi,

                Ik heb een nieuw e-mailadres: $newAddress

                Mijn oude Gmail-adres stop ik binnenkort. Wil je dit nieuwe adres voortaan gebruiken?

                Groet
            """.trimIndent(),
        ) else Templates.Mail(
            subject = "My new email address",
            body = """
                Hi,

                I have a new email address: $newAddress

                I am closing my old Gmail address soon. Could you use the new one from now on?

                Cheers
            """.trimIndent(),
        )
}
