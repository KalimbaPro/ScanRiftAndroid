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
- **API**: the own API (`Constants.Api.BASE_URL`, RiftCodex-compatible paths plus
  `/legality`), with `res/raw/cards.json` as an offline seed. The host is a placeholder
  (`riftbound-api.kalimbapro.workers.dev`) until the Cloudflare account exists.
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
- **Every merge to `main` is a release build.** The Release workflow signs an `.aab` and
  an `.apk` and publishes them on the GitHub release `v<versionName>`, replacing the
  previous build's files. CI sets `VERSION_CODE` to 100 + the workflow's run number, so
  each build is uploadable to Play without a manual bump; bump `versionName` by hand when
  a release should get a new name (and a new GitHub release).
- **…and goes to the Play track named by the repository variable `PLAY_TRACK`** (`internal`
  by default, `alpha` = closed testing) once the `PLAY_SERVICE_ACCOUNT_JSON` secret is
  set (the step is skipped without it). Releases are created as `draft` until the
  repository variable `PLAY_RELEASE_STATUS` is set to `completed`, which Play only allows
  after the app's first review.

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

**The catalogue updates when the API's `/version` tag changes, and only then.**
`GET /version` returns a `tag` that changes exactly when cards, sets or any region's
legality change. On every launch `DatabaseBootstrapper` reads it (5 s budget,
`Cache-Control: no-cache` so OkHttp's disk cache cannot hand back a stale tag) and
`CatalogueTagSync` decides:

- same as the stored `catalogueTag` preference → nothing is fetched or written;
- different, or none stored → full refresh: `/sets`, every `/cards` page, upsert every
  card (no `updated_on` skip — a legality or localisation edit can leave it alone), the
  dedup and orphan passes, then `/legality` for the current region;
- the tag is stored only after every step succeeded. Any failure, including the version
  check, is logged and leaves the old tag, so the next launch retries. The tag stored
  is the one read *before* the refresh, so an API change mid-refresh errs towards
  refreshing again.

The bundled seed carries its tag as an extra envelope field (`"tag"` next to `items`),
stored on a cold start, so a fresh install only hits the network for cards when the live
catalogue has moved on since the APK was built. Refresh the seed by copying
`RiftboundAPI/dist/seed/cards.json` over `res/raw/cards.json`. The Settings button forces
a full refresh; changing the competitive region re-applies legality only.

Do not bring back a TTL or a per-set count comparison. The `synced_sets` table is a
leftover of the count comparison: nothing reads or writes it, and it stays only because
dropping it would need a Room migration.

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

The scanner follows the same rule: from 600dp of *available* width it shows camera +
session sidebar (40% of the width, max 360dp), so an unfolded Fold in portrait gets it and
refolding drops it live. The binder-page review is photo | list side by side from 600dp
and stacked below that.

**A sheet that shows one card is an `AdaptiveCardSheet`, never a bare `ModalBottomSheet`.**
On a wide window a bottom sheet spans the screen, covers what it was opened from and opens
half-expanded, which crops the card. `AdaptiveCardSheet` (ui/components) is a centred
`Dialog` (`usePlatformDefaultWidth = false`, capped at `Dimens.CardPopUpMaxWidth`, outside
tap and back dismiss) from 600dp wide *and* 480dp tall, and the usual bottom sheet below.
This is one of the few decisions keyed off the **window** rather than the pane — a sheet
covers the whole window whichever pane opened it — and the height floor keeps a phone in
landscape on the sheet. `AdaptiveRules.useCardPopUp` is the tested rule. Put the card image
first and let the details scroll, so the whole card shows without scrolling. Card sheets
today: `DeckCardDetailSheet`, `CardCorrectionSheet` (also the last-scanned chip's target),
`CardVersionPicker` (its grid measures the pop-up's width, not the window's),
and the legend choosers via `GameSheet(showsCard = true)`. Forms — settings, filters,
import, add-to-list, end-of-game record — stay bottom sheets.

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

**The collection's divider is the library's pane expansion, not a custom splitter.**
`ListDetailPaneScaffold(paneExpansionState, paneExpansionDragHandle)` with
`rememberPaneExpansionState` and a material3 `VerticalDragHandle` (all in adaptive
1.2.0 / material3 1.4.0) — so the drag, fling, anchoring and the spacer all stay inside
the scaffold's own measure pass, and folding still just changes `scaffoldValue`. The
glue is `ui/collection/CollectionPaneSplit.kt`; the arithmetic is `ui/adaptive/PaneSplit`
(plain JUnit). Four things the library does not do for you:

1. *Floors.* It coerces a drag only to `[0, width]`, so dragging to the edge collapses a
   pane. Anchors are rebuilt from the measured width every time it changes, holding the
   list ≥ `Dimens.ListPaneMinWidth` and the detail ≥ `Dimens.DetailPaneMinWidth`, and
   `SplitDragClamp` clamps the live drag through `consumeDragDelta`. That hook sees only
   deltas, so the clamp mirrors the position itself, re-synced from the settled anchor on
   every press; keep it the **outermost** pointer modifier on the handle. Under ~586dp
   (260 + 300 + the 24dp spacer) both floors do not fit, and then the collection is **single-pane**:
   `PaneSplit.directiveFor` caps the directive at one partition for the width measured
   inside the insets. The Medium-width directive alone split a 7" tablet in portrait
   (600dp window, ~520dp after the rail) into a list so narrow its title wrapped a
   syllable per line, with no handle to widen it. Do not raise the floors past what an
   unfolded Fold held upright clears (~621dp beside the rail): that is the device the
   split exists for, and 280/320 once silently made it single-pane.
2. *Persistence.* The split is the list pane's share of the scaffold width (a
   proportion), saved as the `collectionListPaneProportion` float in `UserPreferences`.
   It is a preference, not a position: restore maps it to the nearest anchor at the
   current width, and `PaneSplit.toPersist` refuses to save that restored anchor back —
   otherwise rotating to a narrow screen would overwrite a 30% chosen on a wide one.
   Single-pane has no anchors, so folding ignores it and unfolding restores it.
3. *Right-to-left.* Shares are measured from the left edge, but the list is on the right
   in RTL. Store the list's share; mirror at the boundary.
4. *Taps.* Never put `onClick` in the handle's `semanticsProperties`: the library turns
   it into a `clickable`, so every tap would jump an anchor and eat the double-tap reset.
   TalkBack gets custom actions instead; the handle is `focusable` for arrow keys.

When the split is specified, it overrides the directive's hinge-aware split; the Fold
has no separating hinge when flat, so this costs nothing there.

**The pane scaffold focuses the pane it shows — give it something harmless to land on.**
`ThreePaneScaffold` (adaptive-layout 1.2) calls `requestFocus()` on the current
destination's pane whenever the destination changes. The request enters the pane and
lands on its first focusable node, and in touch mode that is a text field (clickables
are not focusable in touch mode). It focused the collection's search box at launch and
the stepper's quantity field on every card opened — keyboard up, and once an ANR. Each
pane's content root carries `Modifier.paneFocusLanding()` (a plain `focusable()`), so the
request stops on the pane. Not `onEnter { cancelFocusChange() }`: a tapped field requests
focus with the same `Enter` direction, so that makes every field in the pane untappable.
And not `ComposeUiFlags.isInitialFocusOnFocusableAvailable`: that is window focus, a
different path.

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

**The tap layer lives inside the rotation.** A seat is a header (name chip, deck or
legend name), then a body with the score between − and +, and the track along the bottom.
Put `ScoreTapLayer` *inside* both `RotatedContent` layers so "left" is the
player's own left — on the quarter-turned side seats of the three- and four-player layouts
that reads as a vertical split on screen, which is correct. This is the opposite of the
drag-and-drop rule above: touch hit-testing honours the transform, so taps are fine inside
a rotation; only drop targets are not.

**Scoring is one gesture, so it has to be one `pointerInput`.** Pressing the right two thirds (the left third is the undo zone)
opens the category dots in a row across the middle of the tile; the finger then slides onto one and lifts
to score it. That press-drag-release is a *single* gesture, so it cannot be split across a
`clickable` zone and a separate overlay with its own `clickable` dots — the gesture would
end the moment the finger left the zone. `ScoreTapLayer` owns the whole thing and
`ScorePickerDots` is pure drawing (`allowsHitTesting` equivalent: no input modifiers at
all). Releasing without moving deliberately leaves the dots up so they can be tapped
instead, which is the case when the phone is flat on the table.

`ScorePickerState.open` always lays the dots out in a horizontal row centred on the
tile, whatever the touch point — a deliberate divergence from iOS, which blooms them in
an arc from the finger. The spacing shrinks on a narrow tile so all three stay on it.

Z-order inside the seat is load-bearing. The tap layer covers only the body, below the
header, and the XP stepper is drawn after it so it is hit-tested first; the dots are drawn
last so they sit over the score and the track. While the dots are up, everything behind
them is blurred (`Modifier.blur`, API 31+; the scrim is the fallback on older devices).

**The scoring track draws one arrow cell per point, not a proportional summary.** It has
`max(VICTORY_POINTS, score)` equal slots, so it fills toward 8, and every slot gets
narrower once a game goes past that. A
"3 conquer, 2 hold" bar cannot show sequence, and sequence is the whole point: the single
decrement button takes the *last* point back, so the trailing cell has to be the one that
disappears. `PlayerState.orderedPoints()` reconciles `scoreLog` against the counts so the
track always has exactly `score` cells even when the log is stale.

**`PlayerState.scoreLog` is what makes a single decrement button unambiguous.** The
seat has one "take a point back" control and no way to ask which category lost it, so
every seat remembers the order its points were scored in and the undo pops the end. It
falls back to draining the
largest category when the log cannot answer (a roster restored from a build that had no
log), and clears the log when it does, because a log that disagrees with the counts has
already proven itself untrustworthy.

**A match is Bo1 or Bo3, and its timer spans every game.** Bo3 exists only with two
players, because the game record models two-player rounds (`RoundOutcome`); with three or
four players the next-game button goes straight to the summary. `nextGame` records the
round and clears the scores but keeps `matchStartedAt`. Once a match is decided the scores
stay on screen behind the pre-filled summary, so the record still has the last game's
breakdown; Save or Skip then resets the whole match. The timer is drawn twice in the
centre bar, each copy at its reader's left: the top copy is turned 180°.

**Unowned cards render in full colour**, no fade, exactly as iOS; the quantity badge marks ownership. The old
greyscale treatment and its "show unowned in colour" toggle are gone.

**The home search bar is global.** `CollectionHubScreen`'s search matches list names and
the *whole* catalogue (`CollectionQuery.searchCatalogue`), owned or not; tapping a card
navigates to `CollectionBrowseRoute(query, cardId)`, which shows all cards for that query
and opens the detail (with its Add FAB).

**A `DisplayCard.id` is not a stable identity.** It encodes the owned variant, so it
changes the moment a card is added to the collection. The collection navigator is keyed
on `card.id` for that reason — keying on the row id dropped the detail pane the instant
you pressed its own Add button.

**The own API keeps card ids stable forever, and the app depends on it.** Owned copies,
decks and lists point at `cards.id`. The API keeps every RiftCodex id and gives cards
RiftCodex lacks (all of Radiance, Secret Garden) `sha1(riftbound_id).hex[:24]`, frozen in
its id map. Never re-key cards locally, and never "fix" an id by name. Its extra card fields
(`banned`, `banned_formats`, `errata`, `errata_date`, `localizations`) are all optional;
absent means legal / no errata / no localisation. The bundled seed comes from the own API
and carries them too.

**The legality refresh never deletes.** Every full catalogue refresh and every change of the
"Competitive region" setting pulls `GET /legality?region=` and `CardDao.applyLegality`
rewrites the legality columns of the whole catalogue in one transaction: clear everything,
then UPDATE the listed cards (matched by `id`, falling back to `riftbound_id`). Not listed
means legal with no errata. It must run *after* the card sync, because an upserted card
carries the API's default-region `banned`. Bundled/fake sources return null and leave the
columns alone.

**Regions are wire format: `global`, `kr`, `tc`.** The API resolves the rule: a region
behind the global release schedule uses the global ban list as it stood at the end of the
season of the latest set released there (today KR and TC have only Origins, so nothing is
banned there). The app only passes the region; never re-derive bans client-side.
`DeckValidator`'s `BannedCards` violation (first in the list) reads the region-resolved
`card.banned`, and the BANNED band is drawn by `CardThumbnail` — the deck browser cell,
which draws its own image, reuses `CardBannedBand`.

**Card language is wire format and part of an owned copy's identity.** `CardLanguage`
stores `"en" "fr" "zh" "ko"`; the entry key everywhere (Room unique index, `EntryKey`,
import, snapshot merge, scan session) is `(cardId, isFoil, condition, language)`. It is a
property of the *copy*: zh/ko prints share codes with English. The snapshot always writes
`"language"` per entry and reads its absence as `"en"` (`version` stays 1); an unknown
value is normalised to `en` rather than stranding a stack. CSV imports are English and no
export gains a language column — those formats are byte-exact. Changing a copy's language
or condition merges into an existing stack (`CollectionRepository.rekey`).

Schema v2 came from `Migrations.MIGRATION_1_2`. `MigrationTest` builds the real v1 database
from `app/schemas/…/1.json` and lets Room validate the migrated schema on open (Robolectric
reads *debug* app assets, so `MigrationTestHelper` cannot see the schemas). Add a case
there for every future migration, and keep every `DEFAULT` clause identical to the entity's
`@ColumnInfo(defaultValue)` — Room compares them.

**The scan language pill is a setting, not a label.** Under the last-scanned banner the
pill row reads `[Normal|Foil] [SET #n] [flag CODE] [+1]`. The language chip *is*
`scanLanguage` (`auto` | `en` | `fr` | `zh` | `ko`, persisted): a fixed choice overrides
detection for every later scan and re-labels the card just scanned. Auto runs
`LanguageDetector` in this order: collector-line marker after the number (`EN`, `FR`,
`SC`/`CS`/`TC`→zh, `KR`→ko), Hangul then Han, French-vs-English stop words, English. The
Latin recognizer always runs (the code is Latin on every print); the unbundled Play
Services Chinese/Korean recognizers run only on a *matched* frame whose name band has no
Latin name, in Auto. Do not run them per frame — three recognizers per frame stalls the
scanner.

**Binder page geometry lives in `MultiCardScanner`, and it is pure.** Every collector-code
match is one card (two identical cards = two copies); codes need a separator and a known
set prefix, so rules text never becomes a card. Card width is the anchors' horizontal grid
pitch (else vertical pitch × aspect, else 2.5 × the code line). Boxes are normalised *per
axis*, so card height is `width × imageAspect ÷ (63/88)` — pass the photo's aspect or every
assignment band is wrong on a portrait shot. Each line belongs to the nearest anchor below
it within one card-width band. Fewer than nine anchors triggers the overlapping 3×3 tile
pass, merged by text + position. Android has no rectangle detector (iOS uses
`VNDetectRectanglesRequest`), so the nine-anchor rule is the only trigger here. Page mode
binds `ImageCapture` *instead of* the analyser — never all three use cases, which LEGACY
cameras refuse.

`SetNormalizer` pins `RAD` "Radiance" and `SGN` "Secret Garden". **SGN must never join
the promo merge**: its printed code `SGN-001/003` is what scans.

**A legend card id names a printing, not a legend.** Seats and game records store the
exact card the player picked, so the tile shows the art they brought — Irelia's Lunar
Revel promo, Lillia's Secret Garden print, a Metal. Anything that compares or groups
legends must go through `LegendPrintings.identity` / `isSameLegend` (the name with every
trailing `(...)` suffix removed, case- and diacritic-folded), never the id. Pickers show
one entry per `LegendGroup` and open `LegendPrintingChooser` when it has more than one
printing; never filter printings out of a picker again (the old `pickableLegends()` made
promo-only art unpickable). Lookups that resolve a stored legend id, like
`PointTrackerState.legendsById`, must cover **every** legend card, or a seat holding a
Metal shows no art. The default printing ranks Metal, judge, alternate art, signature,
overnumbered, special print, promo, any suffix and then supplemental sets (OGS, SGN) as
successive penalties, so a legend that only exists as a Starter still has a sensible
default. Same rule as iOS `LegendPrintings.swift`; keep the two in step.

**Live language detection runs on evidence, strongest first, and both CJK passes stay
apart.** `ScanIdentifier` is the one OCR → card → language path, shared by the scanner and
the instrumented benchmark. Once a card is matched, `LanguageDetector.detectMatched` tries:
the printed marker (Latin pass); the matched card's English name/subtitle vs its
`localizations.zh/ko.name` (bigram Dice, character Dice for CJK; ≥ 0.6 and 0.2 clear);
then script, comparing the Korean pass's Hangul with the Chinese pass's Han (ratio 0.8 —
measured, the Korean model turns a Chinese card into some stray Hangul and the Chinese one
a Korean card into some Han); then French only on accents + stop words outnumbering English
2:1. Never merge the two CJK passes back into one text: that is exactly what made every
Chinese card read as Korean. The CJK passes run unless the Latin pass already settles it
(marker or English name) — "no Latin name" is not that test, OCR finds Latin noise on CJK
cards. Binder pages go through the same rules: `PageIdentifier` treats each found card as a
`FocusDecision.Focused` view of the page (its own code first, the CJK lines inside its
outline, the art tie-break on its region of the photo).

**A CJK pass runs native code we cannot catch, so it is gated twice.** `OcrService` asks
`ModuleInstall.areModulesAvailable` before the first pass (no models → no pass, plus one
deferred download request), and every pass goes through `CjkOcrGuard`, which writes an
in-flight marker with a synchronous `commit()` before the recognizer runs and clears it
after. A marker found at the next launch means the process died inside a pass (SIGILL on
the arm64 emulator; not expected on real devices, which report their CPU features
truthfully, but not provable for every SoC and Play Services update): CJK OCR is then off
until the next app version and language falls back to the marker and the Latin name.

**A binder page is one anchor per pocket.** `MultiCardScanner` reads codes strictly: three
digits always (`UNL• 1.` from a clipped tile is not `UNL-001`), and a space-only separator
only when the printed total follows (`SFD 057/221`, OCR's usual reading of the bulleted
line). `dedupe` then collapses every read of one collector line — page pass and the
overlapping tiles — into one anchor (most-read code, then the one with its total), so a
card is never listed twice. A focused card matches its own code, then its own collector
line, and never the rest of its text, so a misread leaves the row for the user to correct
instead of naming a neighbour.

**Collector codes are read tolerantly, and a suffix-less code is a guess.**
`CollectorText.normalize` repairs `0GN`, `1l6`, `066 a`, `066o`, `227×`, a slash read as
`1`/`l`; a one-letter set typo (`SED`) is fixed only when the printed total confirms the set.
Never fall back from `23*` to card 23 — that is a signature missing a digit. A code read
without its suffix over a number with variants (`066` vs `066a`, `299` vs `299*`) is
ambiguous: `PrintingChooser` compares a dHash of the frame's art region with each
printing's catalogue art (`CatalogueArt`, through Coil's disk cache) and picks only with a
clear margin; otherwise the base printing is kept and flagged (`ScanResult.ambiguous`,
orange "?" on the set chip) until the user picks a version. Signature and overnumbered
twins share art, so those usually stay ambiguous; that is correct.

**The scan-accuracy benchmark is opt-in and never runs in CI.** It is an instrumented test
over official EN/ZH/KO images (`app/src/androidTest/assets/scan-benchmark.json`):

```bash
./gradlew connectedDebugAndroidTest -PbundledCjkOcr \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.scanrift.android.benchmark.ScanAccuracyBenchmark \
  -Pandroid.testInstrumentationRunnerArguments.benchmark=true
adb pull /sdcard/Android/data/com.scanrift.android.debug/files/scan-benchmark.txt
```

`-PbundledCjkOcr` swaps in the bundled Chinese/Korean ML Kit models (same API) because the
Play Services TFLite dies with SIGILL on Apple-silicon emulators; never ship with it. The
emulator's 6GB data partition fills up after a few runs: `-wipe-data -partition-size 8192`.

**Single-mode scanning focuses on the guide when several cards are in view.** Matching the
whole frame takes the first collector code, which on a binder page is often the card above.
`ScanFocusPolicy` decides per processed frame: wide (today's whole-frame match) while at most
one collector code is read; focus once `FOCUS_MIN_CARDS` codes are read, where each code
becomes a card region via `MultiCardScanner.scan` and only a region at least
`FOCUS_MIN_OVERLAP` inside the guide counts — none inside means no match, never a
neighbour. It releases to wide after `FOCUS_RELEASE_FRAMES` consecutive frames with no card
outside the guide. The guide reaches the frame through `scanGuideRect` (the one formula the
overlay also draws with) and `OcrGeometry.guideInImage` (FILL_CENTER crop + ImageProxy
rotation); that mapping assumes preview and analysis share a field of view, which is why the
single-mode `Preview` is given the analysis stream's 16:9 aspect — do not drop it. In focus,
the match uses the focused card's own code first, and language detection (and the CJK
lines) read only that card's region. No UI: no indicator, no toggle.

**The scan session is one row per scan, newest first, and never merges while scanning.**
Scanning a card already in the session adds a new row (ManaBox does the same): a binder's
English and French copies each need their own row to fix language or version. Language,
foil, correction and version changes edit that row only. Rows carry a per-row UUID that
keys every LazyColumn and every edit — never key on the card id. Identical rows sum only on
write-out, through `ScanResult.collectionStacks()` on the entry key; `CollectionRepository
.addCopies` would merge them anyway, but the summing is explicit and tested. "+1" is the one
deliberate exception: it grows the last-scanned row. Auto-add mode is unchanged (it writes
straight to the database, which sums on the entry key).

**A card's versions are grouped by name, for every type.** `CardPrintings` groups printings
on the base identity (name minus trailing `(...)` suffixes, case- and accent-folded), so
`Kai'Sa - Daughter of the Void (Signature)` is a version of the Rare. `LegendPrintings` is
the same thing filtered to legends and only delegates. The scanner builds a `PrintingIndex`
once when its catalogue snapshot loads (on `@DefaultDispatcher`) and carries it in
`ScannerUiState`; never regroup per frame or per recomposition. The set chip under the last
scan, each session row's set chip and each binder-page row open `CardVersionPicker` only when
the card has more than one printing; `CardCorrectionSheet` shows the same printings as
"Other versions". The rules live in `ScanVersions.kt` as pure functions: a pick keeps
quantity and language, foil follows `versionFoil` (new card's `isAlwaysFoil` unless the user
had toggled foil on), and in auto-add mode the copies the scan wrote (`lastAutoAdded`: the
scan plus every "+1") are re-keyed with `CollectionEntryDao.moveCopies`, which moves only
those copies and merges into an existing database stack. Auto-add writes go through one `Mutex` so a
re-key never overtakes the scan it moves.

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

## Store screenshots

Play screenshots live outside the repo, in `../ScanRift-store-assets/android/` (`phone/`,
`tablet-7/`, `tablet-10/`, `foldable/`, with a `README.md` of captions). To regenerate
them, seed a debug build with the demo data set:

```bash
./gradlew assembleDebug
scripts/screenshot-demo.sh <adb-serial>   # demo-mode status bar, install, seed
adb -s <serial> shell cmd uimode night yes   # the current set is shot in dark mode
```

The seeder (`app/src/debug/.../demo/`) is **debug source set only** — the activity is
declared in `src/debug/AndroidManifest.xml`, so release builds contain neither. It
replaces the collection, decks, game history, wishlist and tracker roster with
`DemoData`, which names cards by `riftbound_id`; `DemoDataTest` fails if a catalogue
refresh drops one, if a deck stops validating, or if a deck needs more copies than the
collection owns. It waits for the launch bootstrap before writing, because the
catalogue's de-duplication can swap the rows the demo points at.

Things the emulator needs that a phone does not:

- **Scores are never persisted**, so the tracker always restores at 0–0. Tap points in
  (press a seat's right half, then a dot) before capturing — or, for the four-player
  shot, run `scripts/screenshot-demo.sh <serial> --four-players`: it seeds
  `DemoData.fourPlayerSeats` with scores, logs and XP, and the debug-only `DemoScores`
  (bound through the `Optional<RestoredScoresPolicy>` seam in `PointTrackerViewModel`)
  keeps them on restore for the rest of that process. Release builds bind nothing there.
- **A debug build from another checkout can carry a higher versionCode**, and the
  script's `install -r` then fails with `INSTALL_FAILED_VERSION_DOWNGRADE`;
  `adb uninstall com.scanrift.android.debug` first (the seed replaces the data anyway).
- **The Fold's rotation lock is per display state**: `settings get system user_rotation`
  can say 1 while the inner display sits at ROTATION_0. Use
  `cmd window user-rotation -d 0 lock 1` and check `dumpsys window displays`.
- **The binder-page shot** comes from a photo, not the camera: compose a 3×3 page from
  nine card images, `adb push` it to `/sdcard/Pictures/`, run
  `adb shell content call --uri content://media/external/file --method scan_volume --arg external_primary`,
  then Scan → Page → the photo button. The seed forces the scan language to English:
  with Auto, the Chinese/Korean OCR pass loads Play Services' TFLite module, which dies
  with SIGILL on the arm64 emulator and takes the app with it. `CjkOcrGuard` notices
  that on the next launch and turns the CJK pass off for that app version, but it cannot
  stop the first crash. The page-scan log (`adb logcat -s OCR:V Scanning:V`) prints
  every recognised line with its box — that is how `BinderPageFixtureTest` was built.
- **A foldable has two displays**: `adb exec-out screencap -d <id> -p`, with the id from
  `adb shell dumpsys SurfaceFlinger --display-id`.
- Use `adb exec-out screencap`, not the emulator window, so no device frame is captured.
- **Play rejects a screenshot whose long side is more than twice its short side**, so a
  1080×2400 phone capture will not upload. Run `adb shell wm size 1080x2160` on the phone
  emulator before capturing (and `wm size reset` after).
- The 7" set is shot in landscape (`settings put system user_rotation 1` with
  `accelerometer_rotation 0`); in portrait the collection is single-pane.
- **The errata shots must fit pills and Errata card above the floating stepper.** On
  the phone that needs `wm density 400` (reset after) with Zhonya's Hourglass; on the 7"
  detail pane an owned card's "In Your Collection" block leaves no room, so it uses the
  unowned Emperor's Dais (banned, errata with icons). Re-send the demo-mode broadcasts
  after a density change, or the status bar shows two Wi-Fi icons.

## Not ported from iOS

Cardmarket pricing (`PricingService`, `CardmarketAPI`, `CardPriceSection`,
`CollectionValueChart`) — it needs a paid RapidAPI key and is dormant on iOS too.

iCloud has no Android equivalent, so both iOS sync systems are replaced by
export/restore of the same `collection-snapshot.json`, plus Android Auto Backup.
