# Aufmachen

An Android app for moving off an old Gmail (or any IMAP) address onto a new Tuta address on your own domain.

It reads the headers of your old mailbox and scores every sender from Critical to Trash, so you change your bank and tax office first and the newsletters never. It walks you through each change and checks it with proof from Tuta. Then it keeps watching the old inbox for anything you missed.

## What it does

- Scores every service by registrable domain from subjects, flags, Gmail labels and categories. The engine is a port of a desktop prototype, `mail_triage.py`, and a unit test checks that both give the same output.
- Routes each service to an address bucket (`money@`, `gov@`, `shop@` and so on) on your domain.
- **Ask Claude:** copies a prompt with service facts only (no addresses, no subjects) and opens Claude. You paste the answer back and it becomes a checklist with a settings link.
- **Proof:** share a mail exported as PDF from the Tuta app to Aufmachen. It reads the PDF's text layer and matches the mail to a service.
- **Watchdog:** after every sync, new mail from a service you already moved raises an alert, however long ago you moved it.
- Bulk unsubscribe through RFC 8058 one-click, mailto or the web. GDPR Art. 17 erasure requests. An "I've moved" note to the people you mail with, sent from the Tuta app.
- English and Dutch.

## What it never does

- It never downloads message bodies. It fetches `BODY.PEEK[HEADER.FIELDS (...)]` from a folder opened read-only.
- It has no server and no analytics. Headers sit in a SQLCipher database whose key lives in the Android Keystore.
- It never changes, moves or deletes mail. The only outgoing actions are sends you confirm one by one.

## Build

```sh
JAVA_HOME=/path/to/jdk17 ./gradlew :app:assembleDebug
JAVA_HOME=/path/to/jdk17 ./gradlew :app:testDebugUnitTest   # needs python3 for the parity fixture
```

Every push to `my-main` builds a signed release through `.github/workflows/release.yml`.
