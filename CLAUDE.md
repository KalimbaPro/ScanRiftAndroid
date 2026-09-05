# CLAUDE.md

Guidance for Claude Code working in this repository.

## Build & test

```bash
# No java on PATH here; Android Studio's JBR is the JDK.
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"

./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew testDebugUnitTest --tests '*DeckValidatorTest*'
./gradlew bundleRelease          # signed AAB for Play
./gradlew installDebug           # to a connected device
```

**The repo lives inside iCloud-synced `~/Documents`.** iCloud syncs `app/build/` and
resolves conflicts by creating `"file 2.xml"` duplicates, which then fail resource
linking with `Failed file name validation`. The build directories carry the
`com.apple.fileprovider.ignore#P` xattr to stop that, but `./gradlew clean` deletes and
recreates them, losing it. If a build fails on a `" 2"` filename:

```bash
rm -rf app/build && mkdir -p app/build
xattr -w "com.apple.fileprovider.ignore#P" 1 app/build
```

Moving the repo out of `~/Documents` would fix this permanently.

## Project overview

Android companion app for the **Riftbound** TCG: scan cards by camera OCR, manage a
collection, build decks, track games. It is a port of the iOS app at `../ScanRift`,
which is the **reference implementation** — when the two disagree, iOS is right unless
this file says otherwise.

- **Application id**: `com.scanrift.android` (debug builds get a `.debug` suffix)
- **API**: `https://api.riftcodex.com`, with `res/raw/cards.json` as an offline seed
- **compileSdk 36 / targetSdk 36 / minSdk 26**

## Toolchain is pinned to what Android Studio accepts

**Do not raise AGP past 9.0.0 without updating Android Studio first.** Studio 2025.3
refuses to sync anything higher ("incompatible version of the Android Gradle plugin"),
and several AndroidX libraries have since moved to an AGP 9.1.0 floor. The version
catalog therefore holds the newest release of each that still works with AGP 9.0:

| Library | Pinned | Newest | Why |
|---|---|---|---|
| AGP | 9.0.0 | 9.4.0 | Studio 2025.3's ceiling |
| Compose BOM | 2026.06.01 | 2026.08.00 | Compose 1.12 needs AGP 9.1 |
| material3-adaptive | 1.2.0 | 1.3.0 | 1.3.0 needs AGP 9.1 + compileSdk 37 |
| core-ktx | 1.18.0 | 1.19.0 | 1.19 needs AGP 9.1 |
| lifecycle | 2.10.0 | 2.11.0 | 2.11 needs AGP 9.1 |
| navigation-compose | 2.9.8 | 2.10.0 | 2.10 needs AGP 9.1 |
| hilt-navigation-compose | 1.3.0 | 1.4.0 | 1.4 needs AGP 9.1 |
| Coil | 3.5.0 | 3.6.2 | 3.6 pulls Compose Multiplatform 1.12, which drags all of Compose to 1.12 |
| OkHttp | 5.4.0 | 5.5.0 | 5.5 needs compileSdk 37 |

Nothing is lost by staying here. In particular `adaptive` 1.2.0 still exposes
`calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth`, which the foldable two-pane
layout depends on. After updating Studio, raise all of these together.

Note the Coil trap: it is a Kotlin Multiplatform library, so its version silently
controls the whole `androidx.compose.*` stack through `org.jetbrains.compose`. If the
Compose version moves unexpectedly, check Coil first.

## Git workflow

- `main` is protected; work on `dev` and open PRs. The `pre-push` hook enforces this —
  activate it once per clone with `git config core.hooksPath .githooks`.
- Commit style: `feat(scanRift):` / `fix(scanRift):` / `chore(scanRift):`, matching iOS.

## Architecture

Single `:app` module, layered by package. Hilt for injection.

```
core/       Constants, Iso8601, StringExtensions, Log
domain/     Pure Kotlin models. No Room, no Android imports.
data/       local/ (Room), remote/ (Retrofit + DTOs), prefs/, repository/
service/    sync/ backup/ export/ importer/ deck/ scanning/ feedback/
ui/         adaptive/ navigation/ theme/ components/ + one package per feature
```

**Entities are dumb table rows; `domain.model` carries the computed properties.** That
split is what lets `DeckValidator`, the set-ordering comparator and the de-duplication
logic run on plain JUnit with no Robolectric.

## Rules that will bite you

**Never reintroduce a bulk catalogue delete.** `CardDao` has no `deleteAll()` and no
`OnConflictStrategy.REPLACE` on purpose. REPLACE is DELETE+INSERT, which fires the
foreign-key cascades. The previous version of this app cleared the catalogue on every
sync and took the user's collection with it. Sync is `@Upsert`-only, and the child FKs
are `ON DELETE SET NULL` so the failure mode cannot recur.

**Backup timestamps must never carry fractional seconds.** Swift decodes
`collection-snapshot.json` with `.iso8601`, which rejects them, and one bad timestamp
makes iOS throw while decoding the *entire* file — the restore then fails with no
visible cause. `Iso8601.format` truncates; there is a test asserting no `.` appears in
any emitted timestamp. The snapshot schema is **additive only**; keep `version = 1`.

**`public_code` is null on every card the API returns.** `CardDto.resolvedPublicCode`
derives it from `riftbound_id`, and the scanner matches against it — so a bug there
means nothing scans at all, silently.

**Locale.ROOT on every `"%03d"`.** Card codes are zero-padded in several places. Without
`Locale.ROOT`, an Arabic-locale device formats Arabic-Indic digits and no scan matches.

**Adaptive layout keys off the pane, not the window.** The window size class picks only
the navigation container and the pane count. Everything inside a pane reads
`LocalContentWidth`, because at Medium window width the detail pane is ~350dp. Use
`calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth` — the default directive only
splits at 840dp, and an unfolded Fold in portrait is ~700dp, so the feature would never
appear on the target device.

**Enum raw values are a wire format.** `CardCondition` stores `"Near Mint"`,
`DeckSection` stores `"mainDeck"`, `GameResult` stores `"win"`. They go into the
database, the backup snapshot and every export verbatim. Never store `name` or ordinal.

**Export formats are byte-exact.** riftbound.gg parses the CSV it emitted, spaces after
commas and all, in the header *and* the data rows. Golden tests pin this.

## Deck rules

40+ main deck, exactly 12 runes, exactly 3 battlefields, max 8 sideboard, max 3 copies
per `cleanName`, max 3 signature cards. The carve-outs are the easy part to get wrong
and each has a test: empty-domain cards are exempt from domain identity, the signature
cap counts the **main deck only** while the copy limit spans the sideboard, and runes
get a copy limit of 12.

## Conventions

- **Logging**: `Log.<category>` (`Log.scanning`, `Log.ocr`, `Log.database`, …). Never
  `println`.
- **Constants**: everything tunable in `core/Constants.kt`, mirroring iOS's
  `Constants.swift`.
- **Serialization**: kotlinx.serialization, not Gson. Gson reflects over field names and
  R8 renames them, so it produces all-null DTOs in release builds only.
- **Dispatchers**: inject `@IoDispatcher` / `@DefaultDispatcher`; never reference
  `Dispatchers.IO` directly, so tests can substitute.
- **Images**: Coil 3 through the shared `OkHttpClient`.

## Not ported from iOS

Cardmarket pricing (`PricingService`, `CardmarketAPI`, `CardPriceSection`,
`CollectionValueChart`) — it needs a paid RapidAPI key and is dormant on iOS too.

iCloud has no Android equivalent, so both iOS sync systems are replaced by
export/restore of the same `collection-snapshot.json`, plus Android Auto Backup.
