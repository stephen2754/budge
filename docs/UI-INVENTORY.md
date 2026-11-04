# Budge — UI inventory

The interface as implemented in the source at this commit. Every dimension, colour role and
component is quoted from the file and line named beside it; nothing here is proposed.

Compose + Material 3, `compose-bom = "2024.12.01"` (`gradle/libs.versions.toml:13`), single module,
`minSdk 26`, `targetSdk 35` (`app/build.gradle.kts:42-43`), portrait-only
(`app/src/main/AndroidManifest.xml:55`). No custom `Typography` and no custom `Shapes` are passed to
`MaterialTheme` (`ui/theme/Theme.kt:164-167`), so every role and every component shape is the
Material 3 default; baseline sizes are marked *(baseline)*. 117 `.dp` tokens exist across `ui/`
(`grep -rho "[0-9]\+\(\.[0-9]\+\)\?\.dp" --include=*.kt ui/ | wc -l` → `117`). Paths below are
relative to `app/src/main/java/com/example/budge/` unless they start with `app/`.

## File map

| File | Main sections |
| --- | --- |
| `MainActivity.kt` | 46-48 locale; 50-95 `setContent` → `CompositionLocalProvider` → `BudgeTheme` → `BudgeNavGraph`; 109-131 blocking prefs read |
| `ui/navigation/NavGraph.kt` | 51-55 items; 58-61 `Overlay`; 75-79 saver; 93-115 `OverlayHost`; 122-290 `BudgeNavGraph` |
| `ui/navigation/Screen.kt` | 12-18 three page identities (no routes, no `NavHost`) |
| `ui/home/HomeScreen.kt` | 93 swipe distance; 103-319 `HomeScreen`; 328 `UNKNOWN_AMOUNT`; 342-380 `DayHeader`; 387-574 `TransactionItem` |
| `ui/stats/StatsScreen.kt` | 80-397 `StatsScreen`; 407-446 `CategoryDonutChart`; 452-482 `CategorySummaryItem` |
| `ui/settings/SettingsScreen.kt` | 104-162 `CorneredAlertDialog`; 172-180 `DialogButton`; 188-210 `ReleaseEntry`; 212-237 `updateStatusLabel`; 292-451 screen; 453-898 dialogs; 902-931 `ComponentLicence`; 949-964 `SettingsSection`; 971-1004 `SettingsItem` |
| `ui/entry/EntryScreen.kt` | 85-376 screen; 286-323 date dialog; 325-375 time dialog; 378-389 `pastDatesOnly` |
| `ui/category/CategoryScreen.kt` | 69-81 palette; 88-160 screen; 167-245 `CategoryItem`; 252-357 `CategoryDialog` |
| `ui/SummaryFigure.kt` | 20-45 shared figure |
| `ui/Formats.kt` | 47-57 `formatMoney`; 133-139 `amountTextStyle`; 145 `initialChar`; 186-187 `categoryInitialColor` |
| `ui/theme/Theme.kt` | 22-98 schemes; 108-169 `BudgeTheme`; 180 `incomeColor`; 190 `pageWindowInsets` |
| `ui/theme/Color.kt` | 6-40 light; 43-77 dark; 83-84 income colours |

# Shared shell — `MainActivity` + `NavGraph`

**Purpose.** One activity draws a three-page horizontal pager with a bottom navigation bar, and
stacks the entry form and category manager above the pager as animated full-screen overlays.

**Composition tree**

```
MainActivity.setContent → CompositionLocalProvider → BudgeTheme          MainActivity.kt:62-94
└─ BudgeNavGraph → Column(fillMaxSize)                                  NavGraph.kt:123, 216
   ├─ Box(fillMaxWidth, weight 1f)                                      NavGraph.kt:217
   │  ├─ HorizontalPager(fillMaxSize) — pages 0 Stats, 1 Home, 2 Settings  NavGraph.kt:223-246
   │  ├─ OverlayHost(ENTRY) → AnimatedVisibility → EntryScreen          NavGraph.kt:251-256
   │  └─ OverlayHost(CATEGORIES) → AnimatedVisibility → CategoryScreen  NavGraph.kt:258-262
   └─ NavigationBar(fillMaxWidth)                                       NavGraph.kt:265-288
      └─ NavigationBarItem × 3: Icon(contentDescription = null) + Text(label)
```

There is **no `NavHost`** (`Screen.kt:3-10`), no shell-level `Scaffold`, and **no FAB in the shell**:
the only two `FloatingActionButton` call sites are `HomeScreen.kt:144` and `CategoryScreen.kt:111`.
Tab order is Stats, Home, Settings; Home is the initial page (`NavGraph.kt:128-134`).

**Dimensions and spacing**

| Value | Where it is used | Line |
| --- | --- | --- |
| `weight(1f)` | pager `Box`, the height left above the nav bar | 221 |
| `fillMaxSize()` / `matchParentSize()` | pager; both `OverlayHost` modifiers, so an overlay covers the pager area exactly | 225; 251, 258 |
| `fillMaxWidth()` | `NavigationBar`, with no height set (component default) | 265 |
| `tween(300, FastOutSlowInEasing)` | overlay enter: `slideInVertically { it }` + `fadeIn` | 105-106 |
| `tween(250, FastOutSlowInEasing)` | overlay exit: `slideOutVertically { it }` + `fadeOut` | 110-111 |
| `tween(300)` / `tween(200)` | `animateScrollToPage`; 200 ms when `abs(currentPage - index) > 1` | 170, 179 |
| `alpha 1f` / `alpha 0.6f` | nav icon, selected vs unselected | 276 |
| **no `.dp` literal** | `NavGraph.kt` imports `dp` at line 40 and never uses it | `grep -n "dp" NavGraph.kt` |

**Typography.** The only shell text is `Text(item.label)` with no `style` (`NavGraph.kt:280`), so tab
labels take `NavigationBarItem`'s default `labelMedium` (12sp *(baseline)*).

**Colour.** Nothing is set in `NavGraph.kt`. Icon tint comes from `NavigationBarItem`'s
selected/unselected content colours, with the `graphicsLayer` alpha applied on top. No hardcoded
literal.

**States.** Overlay closed: pager interactive (`userScrollEnabled = overlay == null`, 226), one item
selected. Overlay open: `selected = selectedTab == index && overlay == null` (281), so **no** item is
highlighted; pager scroll locked; system back closes the overlay (`BackHandler`, 212-214). The
overlay covers only the pager `Box`, so the `NavigationBar` stays visible and tappable while the
form or category manager is open, and tapping a tab there dismisses the overlay and scrolls to that
tab in one action (282-285). No loading, empty or error state exists at shell level.

**Interaction.** Three `NavigationBarItem` taps (highlight set on tap, page animated after);
horizontal swipe between pages; system back.

**Accessibility.** The three tab icons pass `contentDescription = null` (273) with the comment that
the label already names the tab, so each item is announced once via its text. No explicit role or
state description from app code; targets are `NavigationBarItem` defaults.

# HomeScreen — `ui/home/HomeScreen.kt`

**Purpose.** Shows one month's expense/income/balance summary and that month's transactions grouped
by day, with an add button and per-row edit and swipe-delete.

**Composition tree**

```
Scaffold → TopAppBar(title = Text(month)) + SnackbarHost + FloatingActionButton   :129-147
└─ Column(fillMaxSize)                                                            :152
   ├─ Card → Column: Row → SummaryFigure ×2, Spacer, Text(balance)                :159-228
   └─ [null] Box | [empty] Box → Text(home_empty) | [list] LazyColumn             :232-316
      ├─ DayHeader → Row → Text(date) + Row(Text("-…"), Spacer, Text("+…"))       :342-380
      └─ TransactionItem                                                          :387-574
         ├─ AlertDialog (confirm delete)                                          :449-468
         └─ SwipeToDismissBox → [background Box → Icon(Delete)] + [Card → Row]    :470-573
            Row: Box(circle) → Text(initial), Spacer, Column(Text(name), Text(note)),
                 Spacer, Text(amount)
```

**Dimensions and spacing**

| Value | Where it is used | Line |
| --- | --- | --- |
| `16dp` | card outer and inner padding; row padding; card horizontal padding | 163, 166, 521, 494 |
| `12dp` | `spacedBy` between figures; avatar→text spacer | 172, 538 |
| `8dp` | above balance; day expense→income; text column→amount; day header vertical | 197, 371, 556, 354 |
| `96dp` | `deleteSwipeDistance`, the swipe threshold and haptic trigger; `LazyColumn` bottom padding clearing the FAB | 93; 300 |
| `40dp` / `20dp` / `4dp` | avatar diameter; swipe background padding; card vertical padding | 527, 478, 494 |
| `2dp` / `1dp` | card elevations, summary vs row | 164, 515 |
| `weight(1f)` | both figures; text column; the null/empty `Box` (with `fillMaxSize`) | 187, 194, 539, 238/245 |

Arrangement/alignment: `Arrangement.spacedBy(12dp)` (172); `Arrangement.SpaceBetween` in the day
header (355); `Alignment.Start` / `End` for the two figures (186, 193); `Alignment.Center` for the
empty state (246); `Alignment.CenterVertically` for the row (522); `Alignment.CenterEnd` for the
swipe background (479).

**Typography**

| Element | Role | Size | Line |
| --- | --- | --- | --- |
| Month title, centred | `titleLarge` | 22sp | 135 |
| Balance line | `amountTextStyle` ladder + Bold | 24 / 16 / 14sp | 219 |
| Expense and income figures | `amountTextStyle` ladder | 24 / 16 / 14sp | SummaryFigure.kt:38 |
| Figure labels | `bodyMedium` | 14sp | SummaryFigure.kt:31 |
| Transaction amount; avatar letter | `titleMedium` | 16sp | 566, 535 |
| Category name; empty message | `bodyLarge` | 16sp | 542, 250 |
| Day date; day subtotals; note | `titleSmall` / `bodySmall` | 14 / 12sp | 359, 366/374, 549 |

Same role, different jobs: `titleMedium` is both the avatar letter and the row amount; `bodySmall`
is both the day subtotals and the note. Expense vs income is carried by colour only — both
subtotals are `bodySmall`, both amounts `titleMedium`. The largest text here is `headlineSmall`
(24sp), reached only when the formatted figure is ≤12 characters (`Formats.kt:136`).

**Colour.** `onSurfaceVariant` for figure labels, day date, note and empty message (360, 550, 251);
`error` for expense (185, 367, 567) and `incomeColor()` (`Color.kt:83-84`) for income in the same
three places (192, 375, 567); balance is `incomeColor()` above zero, `error` below,
`Color.Unspecified` at exactly zero (208-213); `surfaceContainer` for the day header (353);
`Color.Transparent` — a **hardcoded literal** — for the swipe background (477); `onErrorContainer`
for the delete icon (484); avatar `Color(transaction.categoryColor)` with a letter in
`Color(categoryInitialColor(...))` (529, 534).

**States**

| State | What the user sees | Distinguishable? |
| --- | --- | --- |
| First frames (`transactions == null`) | `--` for both figures and `Balance: --`; empty `Box` below, no message | Yes — placeholder, not zeros |
| Month with no transactions | `$0.00` figures and balance, centred "No transactions yet" | Yes — the message |
| Transactions present | Day headers with signed subtotals, then rows | — |
| Delete failed | Snackbar "Could not delete the transaction"; row still there | Yes |
| Read failure | Repository falls back to an empty list (`TransactionRepository.kt:35`), so the empty state shows | No — looks like an empty month |

No progress indicator and no retry on this screen.

**Interaction.** FAB opens the entry overlay with id `-1` (144); tapping a card opens it for that id
(`Modifier.clickable`, 495). Swipe left is `SwipeToDismissBox` with `enableDismissFromStartToEnd =
false` (488); `positionalThreshold = minOf(96dp, totalDistance)`, and the dialog opens only if the row
travelled ≥ 96dp (418-425). `HapticFeedbackType.LongPress` fires once per crossing and re-arms on the
way back (435-447). `confirmValueChange` always returns `false` (423), so the row snaps back and a
dialog opens; the accessibility action "Delete transaction" opens the same dialog (505-513); its
buttons are `TextButton` Delete / Cancel (455, 463). `animateContentSize()` (514) uses the default
spring. No pull-to-refresh, no month navigation, no long-press.

**Accessibility.** FAB icon described (`cd_add_transaction`, 145); the swipe background's delete icon
is described too (`cd_delete`, 483) although it sits behind the card. The swipe is not in the
accessibility tree, hence the custom action (comment 502-504). Targets: FAB default 56dp, row ≥ 72dp
(40dp avatar + 2×16dp), dialog buttons `TextButton` defaults. The row's `clickable` passes no `role`.

# StatsScreen — `ui/stats/StatsScreen.kt`

**Purpose.** Shows one period (year, month or day) with previous/next navigation, a totals card, a
budget bar when the month has a budget, and per-category donut charts for expense and income.

**Composition tree**

```
Scaffold → topBar: Column → Row(IconButton prev, Text(period) titleLarge,
                                  IconButton next) + Text("Jump to today")     :92-168
└─ Column(verticalScroll)                                                       :169
   ├─ Row → FilterChip ×3                                                       :182-205
   ├─ monthlySummary?.let → Card → Column(Text(period name), Row(SummaryFigure ×2),
   │                                        Text(balance))                      :210-262
   ├─ budget?.let → Card → Column(Row(Text("Budget"), Text("x / y")),
   │                              LinearProgressIndicator, Text("… remaining"))   :267-324
   ├─ expenseCategories → Card → Column(Text("Expense"), CategoryDonutChart,      :330-359
   │                                    CategorySummaryItem ×N)
   │  └─ CategorySummaryItem → Row(Box(dot), Text(name), Text(total))            :452-482
   └─ incomeCategories → same card shape                                        :365-394
```

**Dimensions and spacing**

| Value | Where it is used | Line |
| --- | --- | --- |
| `16dp` | top-bar padding under the status bar; spacer between chips and each of the four cards; every card's inner padding | 105; 207/264/326/361; 215/273/335/370 |
| `16/16/8/16` | content padding (start, end, top, bottom) | 178 |
| `8dp` | `spacedBy` between period chips; above the balance; above and under the progress bar; after each category row; progress bar height | 184, 248, 293/311, 355/390, 307 |
| `12dp` | jump-link horizontal padding; spacer under card headings; `spacedBy` between figures; category dot diameter | 161, 226/341/351/376/386, 229, 464 |
| `4dp` | jump-link vertical padding; spacer under the progress bar | 161, 311 |
| `180dp` / `40dp` | donut height (both charts) / arc stroke (`40.dp.toPx()`) | 348, 383; 425 |
| `2dp` | elevation of all four cards | 213, 271, 333, 368 |
| `weight(1f)` | each `FilterChip` (thirds); period title; each figure; category name | 202; 129; 238/245; 472 |

Arrangement/alignment: `Arrangement.spacedBy(8dp)` (184); `Arrangement.SpaceBetween` in the budget
heading row (276); `Alignment.CenterHorizontally` (109); `Alignment.CenterVertically` (112, 459);
`Alignment.Start`/`End` for the figures (237, 244); `TextAlign.Center` on the title and chip labels
(130, 199).

**Typography**

| Element | Role | Size | Line |
| --- | --- | --- | --- |
| Period title | `titleLarge` | 22sp | 128 |
| Card headings (period name, Budget, Expense, Income) | `titleMedium` + Bold | 16sp | 223, 280, 338, 373 |
| Figures and balance | `amountTextStyle` ladder (+ Bold on balance) | 24 / 16 / 14sp | 233-253 |
| Figure labels; budget ratio; category name and total | `bodyMedium` | 14sp | SummaryFigure.kt:31, 290, 471/476 |
| "Jump to today" | `labelMedium` | 12sp | 151 |
| "… remaining" | `bodySmall` | 12sp | 318 |

`bodyMedium` carries three jobs: figure labels, the budget ratio, and both columns of a category row.
Largest text is `headlineSmall` (24sp), via `amountTextStyle`.

**Colour.** `primary` for "Jump to today" (152); `error` for expense (236, 477) and `incomeColor()`
for income (243, 477); balance is `incomeColor()` when `balance >= 0` — **including zero** — else
`error` (255); the bar is `error` above ratio 0.9 and `primary` below (309); `onSurfaceVariant` for
"… remaining" (319); arcs and dots are `Color(categoryColor)` (415, 466). No hardcoded literal here.

**States**

| State | What the user sees | Distinguishable? |
| --- | --- | --- |
| Before first emission (`StatsUiState()`, `monthlySummary == null`) | Only chips and the jump link; no cards | Yes — shorter layout, no message |
| Period with no transactions | `$0.00` figures and balance (`getSummary` maps a null aggregate to 0, `TransactionRepository.kt:57-64`) | No — identical to "spent nothing" |
| No categories in the period | Both donut cards absent | Yes, by absence |
| No budget | Card absent; `budget` is only queried on the monthly branch (`StatsViewModel.kt:114`), so year and day views never show it | Yes, by absence |
| Budget exceeded | Bar clamped to 1.0 (`coerceIn(0f, 1f)`), colour `error` above 0.9 | Colour only; same length once clamped |
| Donut total 0 | `CategoryDonutChart` returns before drawing (`if (total <= 0L) return`, 413), so no canvas is composed | — |
| Anchor is today | "Jump to today" keeps its height but `alpha 0f`, not clickable, semantics cleared | Yes — invisible, space reserved |

No loading indicator and no error state on this screen.

**Interaction.** Previous/next `IconButton` shift the anchor one unit of the active period (115, 132);
the three `FilterChip`s switch period, each `weight(1f)` with a centred label (187-203); "Jump to
today" is `clickable(enabled = !onToday)` with a target of the text plus 12dp/4dp padding (157-161).
Vertical scroll only (174). No animation.

**Accessibility.** Previous/next described (`cd_previous_period`, `cd_next_period`, 118, 135); the
hidden jump link is removed with `clearAndSetSemantics {}` (158); the `Canvas` carries
`contentDescription` from the `stats_donut_description` plural (421-423). The jump-link target is a
12sp line plus 8dp of padding — below 48dp. Expense vs income in category rows is colour only.

# SettingsScreen — `ui/settings/SettingsScreen.kt`

**Purpose.** Lists preferences and data actions in five sections and hosts nine dialogs (currency,
language, clear-all, changelog, update history, licences, components, update result, import
confirmation).

**Composition tree**

```
Scaffold → TopAppBar(Text(settings_title)) + SnackbarHost                          :348-356
└─ Column(verticalScroll, 16dp)                                                    :357-450
   ├─ SettingsSection(currency) → SettingsItem                                    :367-373
   ├─ HorizontalDivider(vertical 8dp)                                             :375
   ├─ SettingsSection(appearance) → SettingsItem ×2                               :377-393
   ├─ HorizontalDivider(8dp) → SettingsSection(categories) → SettingsItem         :395-406
   ├─ HorizontalDivider(8dp) → SettingsSection(data) → SettingsItem ×3            :408-426
   └─ HorizontalDivider(8dp) → SettingsSection(about) → SettingsItem ×3           :428-449
      SettingsSection = Column → Text(title) + content()                          :949-964
      SettingsItem    = Column(clickable) → Text(title) + Text(subtitle)          :971-1004
```

Dialogs, in file order (all values on the lines cited):

```
AlertDialog currency → Column(verticalScroll) → Row(selectable, RadioButton, vertical 12dp):
  Text(token) titleLarge primary/onSurface, Spacer(weight 1f), if selected Text("✓")   :454-506
AlertDialog language → Text("label" or "label  ✓") bodyLarge, clickable, vertical 12dp  :513-545
AlertDialog clear-all                                                                   :549-566
CorneredAlertDialog changelog                                                           :570-601
└─ BasicAlertDialog → Surface(IntrinsicSize.Max, shapes.extraLarge,
                              surfaceContainerHigh, tonalElevation 6dp) → Column(24dp):
      title (headlineSmall/onSurface), Spacer(16dp), Box(weight 1f, fill = false) text
      (bodyMedium/onSurfaceVariant), Spacer(8dp), Row(SpaceBetween) start/end buttons  :116-161
   DialogButton = TextButton → Text(maxLines 1, Ellipsis)                              :172-180
CorneredAlertDialog update history → ReleaseEntry ×N, HorizontalDivider(vertical 12dp)  :608-658
  ReleaseEntry: Text("version · channel") titleSmall primary; [Text(current) labelSmall];
                Spacer(6dp); Text(notes) bodyMedium                                    :188-210
CorneredAlertDialog licences                                                            :660-680
CorneredAlertDialog components → ComponentLicence ×N, Spacer(8dp), footnote             :682-703
  ComponentLicence: Text(name) bodyMedium; Text(license) bodySmall onSurfaceVariant;
                    Text(url) bodySmall primary underline, clickable, vertical 4dp      :902-931
CorneredAlertDialog update result: Text(status) bodyMedium; if Available Text("old → new")
  titleSmall primary; when(download): Downloading (bodySmall + bar), Verifying (bar + bodySmall),
  Ready / DownloadFailed / VerificationFailed / InstallFailed (bodySmall, failures in error),
  Idle (notes bodySmall)                                                               :705-880
AlertDialog import overwrite                                                            :882-898
```

**Dimensions and spacing**

| Value | Where it is used | Line |
| --- | --- | --- |
| `16dp` | page padding, all sides | 365 |
| `12dp` | `SettingsItem` vertical padding — the only vertical rhythm in a row; currency and language dialog rows; divider between history entries; `ComponentLicence` bottom; six spacers in the update dialog | 990; 476, 535; 622; 907; 718/729/754/764/780/789 |
| `8dp` | `HorizontalDivider` vertical padding ×4; section title bottom; dialog text→buttons; start↔end button; spacer before the components footnote; four update-dialog spacers | 375/395/408/428; 960; 147; 156; 691; 742/756/770/808 |
| `24dp` / `IntrinsicSize.Max` | `CorneredAlertDialog` content padding / surface width | 129; 124 |
| `6dp` | dialog tonal elevation; `ReleaseEntry` version→notes spacer | 127, 205 |
| `16dp` / `4dp` | dialog title→text spacer; `ComponentLicence` URL vertical padding | 136; 928 |
| `weight(1f, fill = false)` / `weight(1f)` | dialog text box, bounded height; `Spacer` pushing "✓" right | 145; 489 |

Arrangement/alignment: `Arrangement.SpaceBetween` for the dialog button row (150);
`Alignment.CenterVertically` for currency rows and the button row (151, 477); nothing else explicit.

**Typography**

| Element | Role | Size | Line |
| --- | --- | --- | --- |
| Section titles | `titleSmall` + primary | 14sp | 958 |
| Settings row title / subtitle | `bodyLarge` / `bodyMedium` | 16 / 14sp | 994, 999 |
| `CorneredAlertDialog` title / body | `headlineSmall` / `bodyMedium` via `LocalTextStyle` | 24 / 14sp | 132, 139 |
| Currency symbol rows | `titleLarge` | 22sp | 481 |
| Language rows; "✓" in the currency dialog | `bodyLarge` | 16sp | 522, 493 |
| Release version; version→new line; "Current" marker | `titleSmall` / `labelSmall` | 14 / 11sp | 195, 721; 201 |
| Release notes; update status | `bodyMedium` | 14sp | 208, 715 |
| Download and failure lines; footnote; licence and URL; component name | `bodySmall` (name: `bodyMedium`) | 12 (name 14)sp | 740, 774, 694, 914, 922; 910 |

The section title (`titleSmall`, 14sp) is smaller than the row titles beneath it (`bodyLarge`,
16sp). The same "pick one from a list" role is `titleLarge` (22sp) in the currency dialog and
`bodyLarge` (16sp) in the language dialog.

**Colour.** `primary` for section titles, selected rows and "✓", the release version, the
version→new line and component URLs (959, 484/494, 525, 196, 722, 923); `onSurfaceVariant` for row
subtitles, status lines, the channel note and licences (1000, 582, 915); `onSurface` for unselected
dialog rows (486, 527); `surfaceContainerHigh` for the dialog surface (126), with `onSurface` /
`onSurfaceVariant` through `LocalContentColor` (131, 138); `error` for download and install failures
(774, 784, 793, 802); URLs underlined (924). No hardcoded colour literal; "✓" is text, not an icon.

**States**

| State | What the user sees | Distinguishable? |
| --- | --- | --- |
| Update never checked (`Idle`) | "Check for updates" shows no subtitle (`""` at 217; empty subtitles hidden at 996) | No — same as rows with no subtitle by design |
| Checking / up to date / available / unreachable | One subtitle line, same `bodyMedium` size for every outcome | Only by reading the words |
| Changelog, current build not in the shipped list | `changelog_empty` text instead of a `ReleaseEntry` | Yes |
| Update history empty | `changelog_empty` text | Yes |
| Download running | `LinearProgressIndicator` with a fraction when `total > 0`, indeterminate otherwise; percentage appended to the label | Yes |
| Download failed / hash mismatch / install failed | `bodySmall` line in `error`; the end button becomes "Retry" | Yes |
| Import picked | Confirmation `AlertDialog` over the list | Yes |

No loading state for preference reads (DataStore failures fall back silently), no empty state for the
list, and no disabled rows: `onClick` is non-null at all ten `SettingsItem` call sites.

**Interaction.** Ten `SettingsItem` rows, all `clickable(role = Role.Button)` (985): currency (371),
theme — `cycleTheme()` in place, no dialog (386), language (391), categories → overlay (404), export
/ import → system document pickers (414, 419), clear all → confirmation (424), version → changelog
(434), licences (439), check for updates (445-447). Currency rows use `selectable(role =
RadioButton)` and close on pick (469-475); language rows use plain `clickable` and close on pick
(532-535); component URLs open a browser (927). No long-press, no swipe, no drag; no animation beyond
ripples and the progress bars.

**Accessibility.** Every row is a clickable `Column` with `Role.Button` and no explicit
`contentDescription`, so the announcement is title plus subtitle concatenated. Currency rows expose
`Role.RadioButton` and a selected state; language rows expose neither (the "✓" is inside the text).
The component URL is a 12sp line with 4dp padding — well below 48dp. A single-line `SettingsItem` is
16sp text plus 12dp above and below, about 48dp; two-line rows are taller.

# EntryScreen — `ui/entry/EntryScreen.kt`

**Purpose.** Add or edit one transaction — type, amount, category, date, time, note — with two save
affordances and two picker dialogs.

**Composition tree**

```
Scaffold(fillMaxSize)                                                       EntryScreen.kt:122
├─ TopAppBar: title Text(entry_new / entry_edit); navigationIcon IconButton(back, cd_back);
│             actions IconButton(enabled = !isSaving) → Icon(Check, cd_save)  :125-142
├─ SnackbarHost                                                             :144
└─ Column(imePadding, verticalScroll, 16dp padding)                         :149
   ├─ Text("Type") titleSmall onSurfaceVariant; Spacer(8dp)
   ├─ Row(spacedBy 8dp): FilterChip Expense (weight 1f) + FilterChip Income (weight 1f)
   ├─ Spacer(16dp)
   ├─ OutlinedTextField(amount, prefix = currencySymbol, KeyboardType.Decimal, singleLine)
   ├─ Spacer(16dp)
   ├─ Text("Category") titleSmall onSurfaceVariant; Spacer(8dp)
   ├─ FlowRow(spacedBy 8dp both axes) → FilterChip ×N
   │  └─ leadingIcon: Box(16dp, CircleShape, categoryColor)                 :211-232
   ├─ Spacer(16dp)
   ├─ Text("Date & time") titleSmall onSurfaceVariant; Spacer(8dp)
   ├─ Row(spacedBy 8dp): OutlinedButton(date, weight 1f) + OutlinedButton(time, weight 1f)
   ├─ Spacer(16dp)
   ├─ OutlinedTextField(note, minLines 2, maxLines 4)
   ├─ Spacer(24dp)
   └─ Button(fillMaxWidth, enabled = !isSaving) → Text(save / saving)       :276
+ DatePickerDialog → DatePicker(state, DisplayMode.Picker)                   :300-322
+ AlertDialog → text: TimePicker(state)                                       :340-374
```

**Dimensions and spacing**

| Value | Where it is used | Line |
| --- | --- | --- |
| `16dp` | page padding, all sides; spacer between field groups ×4 | 162; 189/202/234/261 |
| `8dp` | spacer under each of the three field labels; `spacedBy` in the type row, date/time row, and both axes of the category `FlowRow` | 170/210/242; 173, 213-214, 245 |
| `24dp` | spacer before the save button — the largest gap on the screen | 273 |
| `16dp` | category swatch diameter inside `leadingIcon` | 225 |
| `weight(1f)` / `minLines 2` / `maxLines 4` | two type chips and two date/time buttons; note field height in lines | 179, 185, 249, 255; 269-270 |

`Arrangement.spacedBy(8dp)` at 173, 213-214, 245. No explicit `Alignment` value in this file.

**Typography.** Only three elements carry an explicit style: the three field labels, all
`titleSmall` (14sp) in `onSurfaceVariant` (167, 207, 239). Everything else is a component default
(`TopAppBar` title, `OutlinedTextField` label and value, `FilterChip`, `OutlinedButton`, `Button`).
The labels (14sp) are smaller than the field values they introduce.

**Colour.** Field labels `onSurfaceVariant`; chip swatch `Color(category.color)`; nothing else
explicit. Selected chips take the `FilterChip` defaults. No hardcoded literal.

**States**

| State | What the user sees | Distinguishable? |
| --- | --- | --- |
| New / editing | Title "New transaction" or "Edit transaction"; new form has an empty amount, the first category of the type preselected and date/time now, the edit form is populated from the row | Yes — title |
| Editing, row still loading | Blank fields for a frame or two, no indicator | No — looks like an empty new form |
| Saving | Toolbar check and bottom button disabled; bottom label "Saving…" | Yes |
| Invalid amount / no category / save failed | Snackbar only; no field-level error, no error colour on the field | Yes, transient |
| No categories at all | `FlowRow` renders nothing under "Category"; the view model re-seeds a default after 750 ms (`EntryViewModel.kt:35, 175-188`) | No — a gap with no explanation |
| Future time / future date | Time clamped to now with a snackbar; future dates are not selectable (`pastDatesOnly`, 384-389) | Yes |

**Interaction.** Back `IconButton` closes the overlay (128). Save exists twice — toolbar `IconButton`
(135-140) and full-width `Button` (276-281) — both calling `viewModel.save()`, both disabled while
saving. Type chips switch expense/income and reselect a category of that type (175-186); category
chips select one category (217-230); date and time `OutlinedButton`s open `DatePickerDialog` and an
`AlertDialog` with `TimePicker` (247-258). Vertical scroll plus `imePadding()` (159-161). No
long-press, no swipe, no drag; no animation beyond dialog transitions and the overlay slide.

**Accessibility.** Back and save icons described (`cd_back`, `cd_save`); the save action exists twice
with the same effect. Validation errors appear only as snackbars, not field errors; the amount field
has a label, a currency prefix and a decimal keyboard. Chips have no explicit size modifier — their
height is the component default.

# CategoryScreen — `ui/category/CategoryScreen.kt`

**Purpose.** Lists categories with edit and delete actions, and hosts the add/edit dialog with a name
field, a type choice and a ten-swatch colour picker.

**Composition tree**

```
Scaffold(fillMaxSize)                                                       CategoryScreen.kt:98
├─ TopAppBar: title Text(category_title); navigationIcon IconButton(back, cd_back)  :101-108
├─ FloatingActionButton → Icon(Add, cd_add_category)                        :110
├─ SnackbarHost                                                             :115
└─ LazyColumn(contentPadding 16/16/16/96, spacedBy 8dp)                     :120
   └─ CategoryItem                                                          :167
      ├─ AlertDialog (delete confirm) when open                             :175
      └─ Card(fillMaxWidth, 1dp) → Row(16dp, CenterVertically):
         Box(40dp circle, categoryColor) → Text(initial) titleMedium;
         Spacer(12dp); Column(weight 1f): Text(name) bodyLarge,
         Text(expense/income) bodySmall onSurfaceVariant;
         IconButton(Edit, cd_edit); IconButton(Delete, cd_delete, tint error)
+ CategoryDialog (AlertDialog) when showDialog                                :252
   └─ Column: OutlinedTextField(name, singleLine), Spacer(12dp), Text("Type") titleSmall,
      Row(spacedBy 8dp) FilterChip ×2, Spacer(12dp), Text("Colour") titleSmall,
      FlowRow(spacedBy 8dp both axes):
        Box(48dp, selectable(RadioButton), semantics contentDescription)
        ├─ Box(32dp, CircleShape, colour)
        └─ if selected → Box(24dp, CircleShape, Color.White @ 0.4f)
```

**Dimensions and spacing**

| Value | Where it is used | Line |
| --- | --- | --- |
| `16/16/16/96` | `LazyColumn` content padding; the 96dp clears the FAB | 128 |
| `8dp` | `spacedBy` between cards; in the type row; both axes of the colour `FlowRow` | 129, 281, 301-302 |
| `16dp` | card inner padding | 204 |
| `12dp` | avatar→text spacer; dialog spacers under the name field and above the colour label | 221, 274, 294 |
| `40dp` / `48dp` / `32dp` / `24dp` | avatar / swatch touch target (comment: the Material minimum) / swatch / selection marker | 210, 311, 324, 333 |
| `1dp` | card elevation | 198 |
| `weight(1f)` | text column | 222 |

`Arrangement.spacedBy(8dp)` at 129, 281, 301-302; `Alignment.CenterVertically` (205);
`Alignment.Center` for the avatar and swatch boxes (213, 319).

**Typography**

| Element | Role | Size | Line |
| --- | --- | --- | --- |
| Category name / avatar letter | `bodyLarge` / `titleMedium` | 16 / 16sp | 225, 218 |
| Type line ("Expense" / "Income") | `bodySmall` | 12sp | 229 |
| Dialog labels ("Type", "Colour") | `titleSmall` | 14sp | 277, 297 |
| Dialog title | `AlertDialog` default (`headlineSmall`) | 24sp | 264 |

`bodyLarge` and `titleMedium` are both 16sp; the avatar letter differs only in weight and
letter-spacing.

**Colour.** Avatar `Color(category.color)` with its letter in `Color(categoryInitialColor(...))`
(212, 217); `onSurfaceVariant` for the type line (230); `error` for the delete icon (240); swatches
`Color(color)` from the fixed palette (326); the selection marker is `Color.White.copy(alpha = 0.4f)`
— a **hardcoded literal** (335). The palette is ten hardcoded literals (71-80: `0xFFE57373`,
`0xFF81C784`, `0xFF64B5F6`, `0xFFFFB74D`, `0xFFBA68C8`, `0xFF4DB6AC`, `0xFF90A4AE`, `0xFFFFF176`,
`0xFF7986CB`, `0xFFA1887F`), duplicated as `DEFAULT_CATEGORY_COLOR = 0xFFE57373`
(`CategoryViewModel.kt:47`).

**States**

| State | What the user sees | Distinguishable? |
| --- | --- | --- |
| Categories present | One card per category, 8dp apart | — |
| No categories | Empty `LazyColumn`; no message, only the FAB | No — blank area with no explanation |
| Add / edit dialog | Title "New category" / "Edit category", fields empty or prefilled | Yes — title |
| Saving | Save `TextButton` disabled | Yes |
| Validation error | Snackbar from the view model | Yes, transient |
| Colour selected | 24dp white 40%-alpha circle over the 32dp swatch | Colour and opacity only |

No loading state and no error state for the list itself.

**Interaction.** Back closes the overlay (104); FAB opens the add dialog (`startEditing(null)`, 111);
Edit and Delete `IconButton`s (233, 236) open the dialog and a confirmation respectively; dialog
Save/Cancel are `TextButton`s (344, 352); type chips switch direction (283, 288); colour swatches are
`selectable(role = Role.RadioButton)` (313-317). No swipe, no long-press, no drag; no animation on
the screen beyond the dialog's default transition.

**Accessibility.** All four icon-only controls are described (`cd_back`, `cd_add_category`,
`cd_edit`, `cd_delete`). Each swatch has `contentDescription` "Colour N" (1-based, string
`category_color_option`), `Role.RadioButton` and a selected state from `selectable`, and selection is
also drawn as a translucent circle. Swatch targets are exactly 48dp; `IconButton` 48dp; FAB 56dp.

# Cross-screen observations

Only patterns visible in the source; each claim names the grep behind it.

**Corner radii — three named in app code, everything else default.**
`grep -rn "MaterialTheme.shapes\|RoundedCornerShape\|CircleShape" --include=*.kt ui/`:

| Radius | Sites | Count |
| --- | --- | --- |
| `CircleShape` | HomeScreen 528; CategoryScreen 211, 312, 325, 334; EntryScreen 226; StatsScreen 465 | 7 |
| `MaterialTheme.shapes.small` (8dp) | StatsScreen 308 (progress bar clip) | 1 |
| `MaterialTheme.shapes.extraLarge` (28dp) | SettingsScreen 125 (dialog surface) | 1 |

`grep -rn "RoundedCornerShape" --include=*.kt ui/` returns nothing. Everything else is a component
default: seven `Card` call sites, eight `AlertDialog` sites, five `CorneredAlertDialog` sites, three
`OutlinedTextField`, six `FilterChip`, one `Button`, two `OutlinedButton`, two `FloatingActionButton`
(`grep -rn "^\s*Card(\|^\s*AlertDialog(\|^\s*CorneredAlertDialog("`). No `Card` sets `shape`, and
`grep -rn "cardColors\|containerColor\|contentColor\|ButtonDefaults\|FilterChipDefaults\|OutlinedTextFieldDefaults"`
returns nothing, so no component's colours or shape are overridden anywhere.

**Spacing — 14 distinct dp values, 117 tokens, no scale object.**
`grep -rho "[0-9]\+\(\.[0-9]\+\)\?\.dp" --include=*.kt ui/ | sort | uniq -c`:

| Value | Count | Role |
| --- | --- | --- |
| 8dp | 35 | the default gap: spacers, `spacedBy`, section padding |
| 16dp | 29 | screen padding, card padding, row padding |
| 12dp | 26 | inner card spacers, dialog rows, avatar gap |
| 2dp / 1dp | 5 / 2 | elevation: summary and stats cards / row cards |
| 4dp | 4 | `Stats` spacer, swipe card vertical padding, jump-link padding, URL padding |
| 96dp | 3 | FAB clearance ×2, swipe threshold |
| 24dp | 3 | dialog padding, pre-save gap, colour selection marker |
| 6dp | 2 | `ReleaseEntry` spacer, dialog tonal elevation |
| 180dp / 40dp | 2 / 3 | donut height / avatar diameter ×2 and donut stroke |
| 48dp, 32dp, 20dp | 1 each | swatch target, swatch, swipe background padding |

The distribution is 4dp multiples plus two exceptions: `6.dp` once (`SettingsScreen.kt:205`) and
`20.dp` once (`HomeScreen.kt:478`). There is no `Spacing` object, no theme extension and no
`Dimens.kt`; every value is written at its use site.

**The same visual role is implemented differently on different screens.**

| Role | Home | Stats | Settings | Entry | Category |
| --- | --- | --- | --- | --- | --- |
| Small label | `titleSmall` + `onSurfaceVariant` (day header, 359) | — | `titleSmall` + `primary` (section titles, 958) | `titleSmall` + `onSurfaceVariant` (field labels, 167) | `titleSmall`, no colour (dialog, 277) |
| Signed amount | `titleMedium` row, `bodySmall` day | `bodyMedium` category row | — | — | — |
| Card elevation | 2dp summary / 1dp row | 2dp (all four) | — | — | 1dp |
| Empty state | message "No transactions yet" | cards absent, no message | none | blank chip row | blank list |
| Top bar | `TopAppBar`, title centred | no `TopAppBar`; hand-built `Column` with `statusBars` padding | `TopAppBar`, left-aligned | `TopAppBar`, back + action | `TopAppBar`, back |
| Balance at exactly 0 | `Color.Unspecified` (212) | `incomeColor()`, because `balance >= 0` (255) | — | — | — |
| Dialog buttons | `AlertDialog` right-aligned default | — | 8 `AlertDialog` right-aligned; 5 `CorneredAlertDialog` with `SpaceBetween` | `AlertDialog` default | `AlertDialog` default |
| List horizontal padding | per-item `padding(horizontal = 16.dp)` (494) | — | — | — | `contentPadding` start/end 16dp (128) |

Greps behind it: `grep -rn "typography\." --include=*.kt ui/` (58 hits);
`grep -rn "CardDefaults.cardElevation"` (7 hits: 1dp at HomeScreen 515 and CategoryScreen 198, 2dp at
HomeScreen 164 and StatsScreen 213/271/333/368);
`grep -rn "^\s*AlertDialog(\|^\s*CorneredAlertDialog("` (13 hits); `grep -rn "TopAppBar("` (4 hits,
none in StatsScreen); `grep -rn "incomeColor()"` (10 hits).

**Typography roles — 9 of 15 used explicitly.**
`grep -rno "typography\.[a-zA-Z]*" --include=*.kt ui/ | awk -F: '{print $3}' | sort | uniq -c`:

| Role | Uses | Size *(baseline)* |
| --- | --- | --- |
| `bodySmall` | 17 | 12sp |
| `bodyMedium` | 11 | 14sp |
| `titleSmall` | 9 | 14sp |
| `titleMedium` | 8 | 16sp |
| `bodyLarge` | 6 | 16sp |
| `titleLarge` | 3 | 22sp |
| `headlineSmall` | 2 | 24sp |
| `labelSmall` / `labelMedium` | 1 each | 11sp / 12sp |

`grep -rn "displayLarge\|displayMedium\|displaySmall\|headlineLarge\|headlineMedium\|labelLarge"
--include=*.kt ui/` returns nothing: `displayLarge` (57sp), `displayMedium` (45sp), `displaySmall`
(36sp), `headlineLarge` (32sp), `headlineMedium` (28sp) and `labelLarge` (14sp) never appear
explicitly. `labelLarge` is still used implicitly by `Button` and `FilterChip` labels. The largest
explicit size anywhere is `headlineSmall` (24sp), only in `amountTextStyle` (`Formats.kt:136`) and the
`CorneredAlertDialog` title (`SettingsScreen.kt:132`). 45 of the 58 explicit role uses are
`bodySmall`, `bodyMedium`, `titleSmall` or `titleMedium`.

**Colour instead of size.** Signed amounts never change size with sign, only colour (`error` vs
`incomeColor()`). The balance line on both screens uses the same `amountTextStyle` ladder and the same
Bold weight; the only difference is the rule at zero. Selection in the currency and language dialogs
is `primary` text plus a "✓" character; selection of a colour swatch is a translucent white overlay.
`Color.Unspecified` appears once (`HomeScreen.kt:212`), `Color.Transparent` once
(`HomeScreen.kt:477`), `Color.White` once (`CategoryScreen.kt:335`), and ten palette literals in
`CategoryScreen.kt:71-80`; `grep -rn "0x[0-9A-Fa-f]\{6,8\}" --include=*.kt ui/` finds nothing else
outside `ui/theme/Color.kt` and those two files.

**Material components used nowhere.** `grep -rl "<name>" --include=*.kt ui/` returns no file for:
`Badge`, `ModalBottomSheet` / `BottomSheet`, `PullToRefresh`, `Checkbox`, `Slider`, `ListItem`,
`TabRow`, `SegmentedButton`, `DropdownMenu` / `ExposedDropdownMenuBox`, `SearchBar`, `Tooltip`,
`CircularProgressIndicator`, `ElevatedCard`, `OutlinedCard`, `NavigationRail`, `NavigationDrawer`,
`ExtendedFloatingActionButton` / `LargeFloatingActionButton`, `LazyVerticalGrid`, `stickyHeader`.
`Switch` has no call site either — its two matches are the word "Switches" in comments
(`EntryViewModel.kt:233`, `StatsViewModel.kt:201`). Only two groups of `LinearProgressIndicator` exist:
the budget bar (`StatsScreen.kt:302`) and the update download/verify bars (`SettingsScreen.kt:744,
749, 755`).

**Loading and error presentation.** No spinner anywhere (`CircularProgressIndicator` → 0 files).
Loading is expressed three ways only: the `--` placeholder (`HomeScreen.kt:328`), the absent card
(`monthlySummary == null`), and the blank form while an edit loads. Errors are expressed two ways: a
`SnackbarHost` on four of the five screens (Home, Entry, Category, Settings — Stats has none) and
`error`-coloured text inside the update dialog. No screen has an inline field error, an error
illustration, or a retry control.

**Motion.** `grep -rn "animateContentSize\|AnimatedVisibility\|tween\|animate[A-Za-z]*AsState\|Crossfade\|animateItem"`:

| Animation | Spec | Site |
| --- | --- | --- |
| Overlay slide + fade in / out | `tween(300)` in, `tween(250)` out, both `FastOutSlowInEasing` | NavGraph.kt:105-111 |
| Pager tap scroll | `tween(300)`, `tween(200)` when skipping a page | NavGraph.kt:170, 179 |
| `animateContentSize()` on the transaction card | default spring (no spec passed) | HomeScreen.kt:514 |

Nothing else animates: no `animateItem` on any `LazyColumn`, no `Crossfade`, no `animate*AsState`, no
shared-element transition, and no swipe animation beyond `SwipeToDismissBox`'s own.

**Window insets.** `grep -rn "windowInsetsPadding\|imePadding\|pageWindowInsets\|WindowInsets"`: all
five screens pass `contentWindowInsets = pageWindowInsets`, which is `WindowInsets(0, 0, 0, 0)`
(`Theme.kt:190`); only Stats applies `WindowInsets.statusBars` (`StatsScreen.kt:102`) and only Entry
applies `imePadding()` (`EntryScreen.kt:159`). The bottom inset belongs to the `NavigationBar`
(`NavGraph.kt:265`), outside the pager.

**Haptics.** One site: `HapticFeedbackType.LongPress` on the Home swipe threshold
(`HomeScreen.kt:442`). `grep -rn "HapticFeedback" --include=*.kt ui/` → two lines, the accessor and
that call.

**Tap targets written in app code.** One explicit target size exists: `48.dp` for the colour swatch
(`CategoryScreen.kt:311`), with the 32dp swatch inside it. No `minimumInteractiveComponentSize`,
`sizeIn` or `defaultMinSize` appears anywhere
(`grep -rn "minimumInteractiveComponentSize\|sizeIn\|defaultMinSize"` → no hits), so every other
target is whatever its Material component provides, and the two text-only targets ("Jump to today",
the licence URL) are below 48dp.

**Screen structure repetition.** Five screens, five `Scaffold`s, four `SnackbarHost`s, four
`TopAppBar`s (Stats builds its own), two `FloatingActionButton`s, three `verticalScroll` columns
(StatsScreen 174, SettingsScreen 364, EntryScreen 161) and two `LazyColumn`s (HomeScreen 292,
CategoryScreen 120).

## Coverage

Read in full, line by line: `MainActivity.kt` (145), `ui/navigation/NavGraph.kt` (290),
`ui/navigation/Screen.kt` (18), `ui/home/HomeScreen.kt` (577), `ui/home/HomeViewModel.kt` (135),
`ui/stats/StatsScreen.kt` (486), `ui/stats/StatsViewModel.kt` (249), `ui/settings/SettingsScreen.kt`
(1004), `ui/entry/EntryScreen.kt` (389), `ui/entry/EntryViewModel.kt` (355),
`ui/category/CategoryScreen.kt` (357), `ui/SummaryFigure.kt` (45), `ui/Formats.kt` (356),
`ui/theme/Theme.kt` (190), `ui/theme/Color.kt` (84).

Skimmed only, for the state values they feed the screens: `ui/category/CategoryViewModel.kt` (only
`CategoryUiState`, 32-44, and `DEFAULT_CATEGORY_COLOR`, 47); `ui/settings/SettingsViewModel.kt` and
`ui/settings/UpdateViewModel.kt` (not read at all — the states above are described from the branches
`SettingsScreen.kt:212-237, 705-880` renders); `data/repository/TransactionRepository.kt` (only
`getSummary` / `getCategorySummaries`, 28-90, to establish that an empty period yields a zero summary
rather than null); `data/local/dao/TransactionDao.kt` (only the two aggregate queries, 75-118);
`app/src/main/res/values/strings.xml` (only keys whose text appears in a state above).

Not read at all: backup, update-download, APK-install and preferences internals, the Room entities,
the DI modules, and the tests. Nothing above depends on them. Every `.dp` literal, typography role,
colour literal, `contentDescription` and animation spec in the five screens was enumerated from the
source rather than sampled; the greps behind the cross-screen claims are quoted beside each claim.
