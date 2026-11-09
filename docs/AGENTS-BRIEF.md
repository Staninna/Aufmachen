# Aufmachen: rules for every worker

Product brief: /home/thc/code/android-apps/Aufmachen/aufmachen_prompt.md (read the sections your task names).
Prototype scoring engine: `mail_triage.py` in the repo root.

## Build and test

- `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64` before any Gradle call. The system JDK 21 has no javac.
- Always run `./gradlew --no-daemon -Pkotlin.compiler.execution.strategy=in-process <task>`. Other builds run
  in parallel on this machine, and leftover daemons eat its memory.
- Compile: `:app:compileDebugKotlin`. Tests: `:app:testDebugUnitTest` (or `--tests 'pkg.Class'`).
- If `local.properties` is missing, create it with `sdk.dir=/home/thc/Android/Sdk`.
- You are done only when `:app:assembleDebug` and your tests pass.

## Architecture (in place, read it before writing)

- Kotlin, Jetpack Compose, Material 3, Navigation Compose with string routes. No DI framework:
  `context.container` gives `secrets`, `settings` (SettingsStore: `flow`, `get()`, `update {}`), `db` (Room on
  SQLCipher) and `repo`.
- `data/Entities.kt`, `data/Daos.kt`: all tables and queries. `data/Repo.kt`: `ServiceView` (stats, state, new
  address), `setStatus` (writes the timeline), `pinTier`, `snooze`, `merge`, `unmerge`, `rescore()`.
- `data/Settings.kt`: `AppSettings` with buckets, own addresses, IMAP config and toggles.
- `model/Model.kt`: Tier, Status, Action, AddressBucket, categories.
- `engine/EngineTypes.kt`, `engine/Contracts.kt`, `mail/MailContracts.kt`: fixed cross-area signatures with
  `TODO()` bodies. Call them freely. Implement only the ones your area owns.
- `ui/nav/AppNav.kt`: routes (`Routes.*`) and the `Selection` hand-over for multi-select flows.
  `ui/common/Common.kt`: `TabTopBar` for tab screens, `BackTopBar` for pushed screens, `AlertsBell`.
  `ui/theme/Theme.kt`: `Tier.accent()`.
- ViewModels: plain `ViewModel` subclasses created with `viewModel { FooViewModel(ctx.container) }`, exposing
  `StateFlow`s. Heavy work runs on `Dispatchers.Default` or `IO`, never the main thread. Lists use `LazyColumn`
  with keys.

## File ownership

- Edit only the files and packages your task lists. Do NOT edit shared files: `data/*`, `model/*`,
  `engine/EngineTypes.kt`, `ui/nav/*`, `ui/common/*`, `ui/theme/*`, `App.kt`, `MainActivity.kt`,
  `AndroidManifest.xml`, `app/build.gradle.kts`, `res/values/strings.xml`.
- Contract files (`engine/Contracts.kt`, `mail/MailContracts.kt`): keep every signature. For the functions your
  area owns, put the implementation in your own file and replace the `TODO()` body with a one-line delegation.
  Touch no other line of a contract file.
- Need an extra query or repo helper? Write it in your own package, for example an extension function or a raw
  query through `db.openHelper.readableDatabase`. Need a dependency or a shared-file change? Ask in your report.
- Strings: every user-visible string goes in `res/values/strings_<area>.xml`, keys prefixed `<area>_`. English
  only; Dutch comes later in one pass. No hardcoded UI text.

## Product rules that bind code

- Headers only, read-only mailbox (`Folder.READ_ONLY`, `BODY.PEEK[HEADER.FIELDS (...)]`). Never fetch bodies.
- Nothing leaves the device except sends the user confirmed. No analytics. Never log personal data.
- Every destructive or outgoing action shows a confirmation listing exactly what will happen.
- Tier colors only as accents: chips, side stripes, progress segments.
- Empty and error states on every screen.

## No real personal data

The repo is public. Never put a real person's address, domain or name in code, tests, fixtures or comments.
Use `example.com`, `example.org` and made-up names (`me@example.com`, `Alex Example`). The product brief uses
the owner's real addresses as examples; do not copy them. For the Tuta PDF fixture use title
`me@example.com - Tuta`, display name `Tuta Team`, `to: me.other@example.org` and reply-to `no-reply@tutanota.de`.

## Writing

Plain, short UI copy. No em dashes or en dashes anywhere, in code, strings or comments (a commit hook rejects
them). Use periods or commas. Do not commit; the orchestrator merges your worktree.

## Report

End with: the files you changed, what you built, test results with the exact command, anything you could not
do, and any shared-file change you need.
