# Budge — visual redesign handoff

You are taking over the visual layer of a working Android app. Your job is to make it look and
feel designed, without changing what it does.

Everything you need is in this repository. This file is the brief; four documents under `docs/`
describe the system as it is. Read them before you touch anything.

---

## 1. The task, in one paragraph

Budge is an offline expense tracker for Android: one local database, no account, no cloud, no
analytics, nine languages, and a working in-app update chain. It is functionally complete and
released (`0.1.1`, stable). It looks like what it is: a Material 3 app built by someone whose
attention was on correctness. The palette is the literal Material 3 baseline purple, the type
scale is untouched, shapes are defaults, spacing is ad-hoc literals, there are no illustrations
or brand assets, and almost nothing moves. Your task is to give it a deliberate visual identity
and a real interface design, screen by screen, while leaving the data model, the update chain,
the backup format and the app's promises exactly as they are.

## 2. Read these first, in this order

| Document | Why |
| --- | --- |
| `README.md` | What the app promises. Its constraints are product constraints, not suggestions. |
| `DESIGN.md` | The engineering decisions and their reasons. Skim §11 (testing) and read §12 (deliberate trade-offs). |
| `docs/ARCHITECTURE.md` | Layers, data model, threading, the update trust chain, the build and release routine. |
| `docs/UI-INVENTORY.md` | Every screen, its composition tree, every dimension written in the code, every state. This is your map of what exists. |
| `docs/DESIGN-SYSTEM.md` | The tokens as they are, the gaps as a numbered list, and the constraints a redesign must respect. |
| `docs/API.md` | Every interface and composable signature you may need to call or change. |
| `CONTRIBUTING.md` | The rules that keep the build green, and the voice user-facing copy must be written in. |

The repository is at `github.com/stephen2754/budge`, branch `main`. Build and test commands are
in `CONTRIBUTING.md`; the short version is `./gradlew :app:testDebugUnitTest` and
`./gradlew :app:assembleRelease` (JDK 17, Android SDK 35).

## 3. What "better" means here, concretely

Not "modern", not "clean", not "delightful". For a finance app the standard is: the number you
came to see is unmistakable, the list is effortless to scan, the app feels trustworthy and
quiet, and nothing about it looks accidental. Judge every decision against that.

The specific gaps, verified in `docs/DESIGN-SYSTEM.md`:

1. **No identity.** The palette is the M3 baseline purple (`#6750A4`) — the colour of a sample
   app. There is no brand mark, no custom colour, no recognisable accent.
2. **No typographic hierarchy.** The largest text in the app is `headlineSmall`. The balance —
   the one number the user opens the app for — is rendered in the same scale as everything
   else, and hierarchy is carried by colour instead of size. There is no display scale at all.
3. **No hero element.** On the home screen the balance competes with the list. A redesign should
   decide what the single most prominent thing on each screen is, and make it so.
4. **No design tokens.** Spacing is 8/16/12dp literals mixed with 2/4/6/20/24/32/40/48/96/180dp
   outliers. Radii are defaults plus ad-hoc `RoundedCornerShape` literals. Nothing is named.
5. **No motion.** One `animateContentSize`, three `AnimatedVisibility`, five `tween` calls in
   the entire app. State changes, list entry, deletion, and the swipe affordance all happen
   instantly.
6. **No empty or loading design.** An empty list is blank; a not-yet-loaded amount is `"--"`.
   These are the moments where a designed app shows care and this one shows nothing.
7. **No imagery.** `res/drawable/` is empty. The only artwork is the launcher icon: five raster
   WebPs, one per density, and **no adaptive icon** — `mipmap-anydpi-v26` is empty, so Android 8+
   treats it as a legacy square, adds white padding, and Android 13 cannot theme it. An adaptive
   icon with a background and foreground layer, plus a monochrome layer for themed icons, is one
   of the cheapest visible improvements available and should be in phase 2.
8. **Dynamic colour is on by default**, so on Android 12+ the palette comes from the user's
   wallpaper and cannot be art-directed. This is a decision you must make explicitly, not
   inherit (see §5).

9. **Two contrast failures exist today and are measured, not suspected.** The income green
   `#2E7D32` clears AA against the page (`surface`, 5.00:1) but not against the surfaces it is
   actually drawn on: 4.46:1 on the day-header band (`surfaceContainer`) and **3.96:1 on the
   filled card** (`surfaceContainerHighest`) — which is the summary card and every transaction
   row, the places the number is most likely to be read. The category selection ring
   (`Color.White.copy(alpha = 0.4f)` over the swatch) measures 1.1:1 to 1.5:1 against its own
   swatch, well under the 3:1 non-text minimum. Both are yours to fix, and fixing them is part
   of phase 1, not polish.

10. **Nothing is previewable.** There is no `@Preview` composable, no Compose UI test and no
    screenshot test anywhere in the repository, so no one can see a change without building and
    installing. Adding a preview per screen belongs in phase 2: it is what makes the rest of the
    work reviewable.

## 4. Work in phases, and stop for approval between them

Do not open with a full rewrite. Each phase is a pull request, and each ends with something the
owner can look at.

**Phase 0 — audit, then ask.** Read the docs and the code. Then produce two or three *named*
visual directions (e.g. "quiet ledger", "paper and ink", "precise instrument"), each with: a
palette (light and dark hex values, contrast ratios for body text), a type scale (roles and sp
values), a radius and spacing scale, one sentence on the character, and a mock or a description
of the home screen in that direction. **Stop and let the owner pick.** The direction is their
call; everything after it is yours.

**Phase 1 — tokens.** Introduce `ui/theme/Type.kt` and `ui/theme/Shape.kt`, a named spacing
scale (`BudgeSpacing` or a `CompositionLocal`), a real colour scheme built for this app, and
`incomeColor()`/`expenseColor()` as proper semantic tokens. Decide dynamic colour explicitly:
either keep it as an opt-in setting with a static palette as the default, or drop it. Record the
decision and the reason in `DESIGN.md`. No screen changes yet; the app must look the same except
for the palette and type, which is the point of the phase.

**Phase 2 — components.** Build the small set of components the screens actually repeat: a
card/section container, a list row, an amount display (see §6), a section header, a setting row,
an empty state, a dialog shell. Replace the ad-hoc M3 usage screen by screen with these. The
existing `CorneredAlertDialog` in `SettingsScreen.kt` is the only bespoke shell and it should
become a shared component. The summary card is built twice already (`HomeScreen.kt` and
`StatsScreen.kt`) and will drift; it is the first thing to unify.

**Phase 3 — screens.** Redesign each screen against the chosen direction, one commit per screen:
Home, Statistics, Entry, Categories, Settings, and the shell (pager, bottom bar, FAB, overlays).
Keep every behaviour, every string, every state. Add the missing states: empty, loading, and
the "no data for this period" case on Statistics.

**Phase 4 — motion and polish.** Add the transitions that carry meaning: list item entry and
removal, swipe-to-delete progress and confirmation, dialog entry, the pager, the period change
on Statistics. Keep durations in the 120–250 ms range for state changes; nothing should take
longer than 300 ms. Motion must be optional in effect: if the system animator scale is 0, the
app still works.

**Phase 5 — verify and hand back.** Run the checks in §7. Produce before/after screenshots for
every screen in light and dark, at default and large font scale. Update `DESIGN.md` with the new
design system and the reasons. Update the copy only where the redesign forces it, in the voice
`CONTRIBUTING.md` describes.

## 5. Decisions you must make explicitly

- **Dynamic colour.** Keep, make opt-in, or remove. If you keep it on by default, the identity
  you design will not be what most users see. State the trade-off and the choice.
- **Brand palette.** Budge is about money the user controls. Avoid the two clichés: finance
  green (implies investing) and alarm red as an accent. The sign colours are already semantic
  (income green, expense red via `error`); the accent should be a third thing.
- **Density.** This is a list-heavy app. Choose a density and apply it consistently, and state
  how it survives a 200% font scale.
- **Numbers.** Decide how amounts are set: tabular figures so digits do not shift, right
  alignment, decimal alignment within a column, and a single treatment for the sign. This is the
  highest-value typographic decision in the app and it is currently unaddressed.

## 6. Non-negotiables

These are load-bearing. Breaking one is a bug, not a design choice.

1. **Money stays integer cents.** Never introduce a floating-point amount anywhere.
2. **No data leaves the device** except the two user-initiated requests (update check, update
   download). No analytics, no crash reporting, no fonts fetched at runtime, no telemetry.
3. **No new permissions.** No new dependencies without asking first.
4. **The update chain is untouchable**: hash-before-install, the channel ladder, the scratch
   file and rename, the cleanup rule.
5. **Nine locales** keep identical key sets: `values/`, `values-zh`, `values-fr`, `values-de`,
   `values-es`, `values-ru`, `values-ja`, `values-it`, `values-pt`. `release_notes_*` is the one
   exception and exists in English and Chinese only.
6. **No unescaped ASCII apostrophe** in a string resource; the resource compiler rejects it.
   Counted text is a `<plurals>`, never a `<string>` with a number in it.
7. **Strings live in resources.** Never hardcode user-visible text in Kotlin.
8. **Portrait only**, minSdk 26.
9. **Body text contrast ≥ 4.5:1** in both themes. The income green exists because a lighter one
   measured 1.9:1.
10. **The build stays green**: `./gradlew :app:testDebugUnitTest` passes, `:app:assembleRelease`
    succeeds. A design that breaks the tests is not finished.

## 7. How your work will be checked

Run these before every pull request:

```bash
export JAVA_HOME=/path/to/jdk-17
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease
```

Then check by hand, and say in the pull request that you did:

- every screen in light and dark;
- every screen at 200% font scale — nothing clipped, nothing overlapping, no button pushed off
  a dialog;
- all nine locales, at least the longest strings (German and Russian are the long ones);
- contrast of body text in both themes, measured, not eyeballed;
- with the system animator scale set to 0;
- one full update flow: check, download, verify, install, and the records still there.

Add a `@Preview` for every screen in light, dark and at 200% font scale, and say in the pull
request that you looked at them.

Acceptance for the whole project: the app has a stated visual direction, tokens that a
contributor can follow, components that make the next screen cheap to build, no regression in
behaviour, and no untranslated or hardcoded string.

## 8. How to behave

- Ask before a decision that is the owner's: the visual direction, the palette, dynamic colour,
  density, anything that changes a promise.
- Do not add features. A redesign that adds a chart, a budget editor or a new setting has
  overstepped; the README's "Not implemented yet" list is deliberate.
- Do not rename files or packages for taste. `git log` is part of the project's documentation.
- Commit in the repo's style: a short imperative subject, a body that says *why*, no trailers.
- Keep comments to *why*. A comment explaining what the next line does will be removed.
- If you are unsure whether something is load-bearing, read `DESIGN.md` §12 before changing it.
- Report honestly. If a screen still looks worse than before in some state, say so.

## 9. Where to start

1. `README.md`, then `docs/UI-INVENTORY.md` — understand what exists.
2. `docs/DESIGN-SYSTEM.md` — understand what is missing.
3. Build the app and run it on a device or emulator. Look at it for ten minutes before deciding
   anything.
4. Write phase 0: two or three directions, with hex values and a home-screen mock. Stop there and
   ask.

The repository has one commit per release and an honest changelog. Read `git log` — the reasons
behind most of the odd-looking corners are recorded there, and several of them are accessibility
or layout fixes that a redesign must not undo.
