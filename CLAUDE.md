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

**`NavigableListDetailPaneScaffold` does not react to a fold.** It renders from
`navigator.scaffoldState`, which the navigator builds once and re-syncs *only* on
navigation. Folding updates `scaffoldDirective` and therefore `scaffoldValue`, but
nothing pushes that into `scaffoldState`, so the panes keep the old arrangement until
you next navigate — switching tabs, which disposes the navigator, is what made it
appear to fix itself. Use the `ListDetailPaneScaffold(directive, value = ...)`
overload: `scaffoldValue` is derived state, and that overload animates to it on every
change. The trade is the predictive-back preview, so keep an explicit `BackHandler`.

Note the asymmetry that makes this easy to misdiagnose: unfolding *looks* fine,
because a stale single-pane value on a wide screen just shows a wider list. Only
refolding is visibly wrong.

**Enum raw values are a wire format.** `CardCondition` stores `"Near Mint"`,
`DeckSection` stores `"mainDeck"`, `GameResult` stores `"win"`. They go into the
database, the backup snapshot and every export verbatim. Never store `name` or ordinal.

**Compose drag-and-drop has three traps, all of them silent.** The seat drag in
`PointTrackerScreen` hit every one.

1. *Drop targets must live outside any rotation.* Compose locates a target from
   `positionInRoot()` plus the node's **untransformed** size, so a target inside a
   rotated `graphicsLayer` reports a rectangle that has been moved but not turned. Touch
   hit-testing does honour the transform, so taps look fine while drops land on the
   wrong element. Put `dragAndDropTarget` on a plain box wrapping the rotated content.
2. *Neither the target nor the source handler is refreshed on recomposition.* Both are
   captured once. Anything they read that can change — here, which player occupies the
   seat — has to come through `rememberUpdatedState`, or the first value is frozen in
   and every later gesture no-ops.
3. *`clickable` and `dragAndDropSource(transferData)` cannot share an element.* That
   overload's start detector runs a tap gesture with `onTap = null` and consumes the
   press, and the detector is not a public parameter. Whichever modifier is innermost
   wins and the other never fires. The deprecated suspend overload
   (`dragAndDropSource(block = ...)`) hands over the pointer scope so one
   `detectTapGestures` can own both gestures; pass `block =` explicitly, because a bare
   trailing lambda is ambiguous against the `transferData` overload. It bypasses
   accessibility, so add the tap action back via `semantics { onClick(...) }`.

**Decklists spell a card's name differently from the card data.** The catalogue writes
`Ornn - Fire Below the Mountain`; every decklist in the wild — riftbound.gg, TTS, the
paste box — writes `Ornn, Fire Below the Mountain`. `CollectionExporter.listName` swaps
`" - "` for `", "` on the way out and `DeckListParser.nameVariants` offers both spellings
on the way in, so an export round-trips through the importer. Do not "fix" the exporter
back to `card.name`, and do not drop the raw spelling from the front of the variant list:
`Allay, Eager Admirer` is a real card name with a real comma in it, so the literal
spelling has to be tried first and win.

The legend line used to be written as `"1 $firstTag, $name"`, which on real data emitted
`1 Ornn, Ornn - Fire Below the Mountain`. The tag is gone; the comma now comes from the
name itself.

**Importing a decklist replaces the deck, and must do it in one transaction.** Go through
`DeckDao.replaceContents`, never a loop over `DeckRepository.addCard` — that adds one card
at a time, silently returns `false` past a copy limit, and would leave a half-built deck
behind when a later line failed. The delete and the insert are atomic on purpose: a crash
between them would leave the user staring at an empty deck with no way back.

Two import traps worth knowing. The champion is a **slot**, and a text list names it under
`Champion:` *and* again in `MainDeck:` — count it in both places and every champion
doubles. A TTS export emits one extra token for the champion slot on top of its main-deck
copies, which is what the `- 1` in the TTS branch takes back off. And TTS carries no
sections at all, so a sideboard cannot survive that round trip; the summary says so.

**The point tracker has two layouts, and the tap layer lives inside the rotation.** A seat
reads `ScoreInputMode` (`tapZones` by default, `categoryButtons` for the old three-circle
layout). Put `ScoreTapLayer` *inside* both `RotatedContent` layers so "left" is the
player's own left — on the quarter-turned side seats of the three- and four-player layouts
that reads as a vertical split on screen, which is correct. This is the opposite of the
drag-and-drop rule above: touch hit-testing honours the transform, so taps are fine inside
a rotation; only drop targets are not.

**Scoring is one gesture, so it has to be one `pointerInput`.** Pressing the right half
blooms the category dots out of the touch point; the finger then slides onto one and lifts
to score it. That press-drag-release is a *single* gesture, so it cannot be split across a
`clickable` zone and a separate overlay with its own `clickable` dots — the gesture would
end the moment the finger left the zone. `ScoreTapLayer` owns the whole thing and
`ScorePickerDots` is pure drawing (`allowsHitTesting` equivalent: no input modifiers at
all). Releasing without moving deliberately leaves the dots up so they can be tapped
instead, which is the case when the phone is flat on the table.

`ScorePickerState.open` clamps the anchor so the whole fan stays on the tile. Without it,
pressing near an edge throws two of the three dots off the tile and the drag has nothing
to land on — the fan shifts rather than the dots reordering, so left-to-right order stays
the same wherever you press.

Z-order inside the seat is load-bearing. The tap layer goes first, so the name chip and
the XP pill are hit-tested before it; the dots are drawn last so they sit over the score
and the track.

**The scoring track draws one cell per point, not a proportional summary.** A
"3 conquer, 2 hold" bar cannot show sequence, and sequence is the whole point: the single
decrement button takes the *last* point back, so the trailing cell has to be the one that
disappears. `PlayerState.orderedPoints()` reconciles `scoreLog` against the counts so the
track always has exactly `score` cells even when the log is stale.

**`PlayerState.scoreLog` is what makes a single decrement button unambiguous.** The
tap-zone layout has one "take a point back" control and no way to ask which category lost
it, so every seat remembers the order its points were scored in and the undo pops the end.
Maintain the log in **both** layouts — `scored` appends, `unscored` drops that category's
last entry — or switching mode mid-game desynchronises it. It falls back to draining the
largest category when the log cannot answer (a roster restored from a build that had no
log), and clears the log when it does, because a log that disagrees with the counts has
already proven itself untrustworthy.

**A `DisplayCard.id` is not a stable identity.** It encodes the owned variant, so it
changes the moment a card is added to the collection. The collection navigator is keyed
on `card.id` for that reason — keying on the row id dropped the detail pane the instant
you pressed its own Add button.

**Export formats are byte-exact.** riftbound.gg parses the CSV it emitted, spaces after
commas and all, in the header *and* the data rows. Golden tests pin this.

## Deck rules

40+ main deck, exactly 12 runes, exactly 3 battlefields, max 10 sideboard, max 3 copies
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
