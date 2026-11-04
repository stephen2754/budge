# Budge — the design system as it is

The visual layer of an Android expense tracker, read from the source rather than from a running
build. Paths are relative to `app/src/main/java/com/example/budge/` unless they begin with `app/`
or `docs/`, so both `ui/theme/Theme.kt:120` and `MainActivity.kt:90` are package-relative.

| Fact | Value |
| --- | --- |
| Theme files | 2 (`Color.kt` 84 lines, `Theme.kt` 190) |
| Type and shape files | none |
| Colour roles defined | 35, all stock Material 3 baseline |
| Typography roles used | 9 of 15 |
| Largest type | `headlineSmall`, 24sp |
| Spacing tokens | none; 117 inline `dp` literals, 14 values |
| Distinct icons | 9 vectors from the Material core set |
| Drawables | 0 (`res/drawable/` is empty) |
| Motion constructs authored | 4 |
| Dynamic colour | on by default, not exposed |

---

## 1. Where the theme lives

| File | Contents |
| --- | --- |
| `ui/theme/Color.kt` | 35 light literals, 35 dark literals, 2 income literals |
| `ui/theme/Theme.kt` | two `ColorScheme`s, `BudgeTheme`, `LocalIsDarkTheme`, `incomeColor()`, `pageWindowInsets` |
| `Type.kt` | does not exist |
| `Shape.kt` | does not exist |

`ui/theme/Theme.kt:105` records why `Type.kt` went: it was a verbatim copy of the Material 3
baseline, so passing it changed nothing. `Shape.kt` was never written. Neither omission is papered
over. `MaterialTheme` gets a scheme and content and nothing else (`ui/theme/Theme.kt:164`), so
`typography` and `shapes` resolve to the library defaults, `Typography()` and `Shapes()`.

```kotlin
@Composable
fun BudgeTheme(
    themeMode: String = "system",
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
)
```

Defined at `ui/theme/Theme.kt:109`.

**The one call site** is `MainActivity.kt:90`, inside the Compose root:

```kotlin
CompositionLocalProvider(LocalAppLocale provides appLocale) {
    BudgeTheme(themeMode = theme) {
        BudgeNavGraph()
    }
}
```

Only `themeMode` is passed. `dynamicColor` is never supplied by any caller, so it is `true` on
every build. No preview, test or secondary entry point constructs the theme.

**How the theme is selected.**

| Step | Where | What happens |
| --- | --- | --- |
| 1 | `data/prefs/Prefs.kt:45` | the stored value is `"system"`, `"light"` or `"dark"` under `Prefs.themeKey` |
| 2 | `MainActivity.kt:65` | collected as state, seeded from `appliedTheme` |
| 3 | `MainActivity.kt:122` | `attachBaseContext` writes `appliedTheme` from the same DataStore read that resolves the locale, so a dark choice never shows a light first frame |
| 4 | `ui/settings/SettingsViewModel.kt:96` | `cycleTheme()` rotates system → light → dark → system |
| 5 | `ui/theme/Theme.kt:114` | `"dark"` → true, `"light"` → false, anything else → `isSystemInDarkTheme()` |
| 6 | `ui/theme/Theme.kt:120` | dynamic scheme when `dynamicColor && SDK_INT >= S`, else the static light or dark one |
| 7 | `ui/theme/Theme.kt:143` | `SideEffect` paints `statusBarColor` from `colorScheme.surface` on API < 35, and always restates both bar icon appearances to `!isDark` |
| 8 | `ui/theme/Theme.kt:163` | provides `LocalIsDarkTheme`, then calls `MaterialTheme` |

A theme change recomposes. Only a language change calls `recreate()` (`MainActivity.kt:82`).
`pageWindowInsets` (`ui/theme/Theme.kt:190`) is `WindowInsets(0, 0, 0, 0)`, passed as
`contentWindowInsets` by all five `Scaffold`s: the nav bar below the pager owns the bottom inset.

---

## 2. Colour

`code` means the role is named in app source. `component` means it reaches the screen only through
a Material default (`Scaffold`, `Card`, `NavigationBar`, `FilterChip`, `TopAppBar`, `AlertDialog`,
`Snackbar`, `Button`, `FloatingActionButton`, `OutlinedTextField`, `HorizontalDivider`). `—` means
nothing renders it.

| Role | Light | Dark | Reached by |
| --- | --- | --- | --- |
| `primary` | `#6750A4` | `#D0BCFF` | code |
| `onPrimary` | `#FFFFFF` | `#381E72` | component (`Button`) |
| `primaryContainer` | `#E9DDFF` | `#4F378B` | component (`FloatingActionButton`) |
| `onPrimaryContainer` | `#21005D` | `#E9DDFF` | component (FAB icon) |
| `secondary` | `#625B71` | `#CCC2DC` | component (`FilterChip`, `NavigationBar`) |
| `onSecondary` | `#FFFFFF` | `#332D41` | — |
| `secondaryContainer` | `#E8DEF8` | `#4A4458` | component (nav indicator, selected chip) |
| `onSecondaryContainer` | `#1D192B` | `#E8DEF8` | component (nav indicator, selected chip) |
| `tertiary` | `#7D5260` | `#EFB8C8` | — |
| `onTertiary` | `#FFFFFF` | `#492532` | — |
| `tertiaryContainer` | `#FFD8E4` | `#633B48` | — |
| `onTertiaryContainer` | `#31111D` | `#FFD8E4` | — |
| `error` | `#B3261E` | `#F2B8B5` | code |
| `onError` | `#FFFFFF` | `#601410` | — |
| `errorContainer` | `#F9DEDC` | `#8C1D18` | — |
| `onErrorContainer` | `#410E0B` | `#F9DEDC` | code |
| `background` | `#FFFBFE` | `#1C1B1F` | component (`Scaffold` container) |
| `onBackground` | `#1C1B1F` | `#E6E1E5` | component (default content colour) |
| `surface` | `#FFFBFE` | `#1C1B1F` | code |
| `onSurface` | `#1C1B1F` | `#E6E1E5` | code |
| `surfaceVariant` | `#E7E0EC` | `#49454F` | — |
| `onSurfaceVariant` | `#49454F` | `#CAC4D0` | code |
| `outline` | `#79747E` | `#938F99` | component (field border, chip outline) |
| `outlineVariant` | `#CAC4D0` | `#49454F` | component (`HorizontalDivider`) |
| `inverseSurface` | `#313033` | `#E6E1E5` | component (`Snackbar`) |
| `inverseOnSurface` | `#F4EFF4` | `#313033` | component (`Snackbar` text) |
| `inversePrimary` | `#D0BCFF` | `#6750A4` | component (`Snackbar` action) |
| `surfaceTint` | `#6750A4` | `#D0BCFF` | component (tonal elevation, custom dialog) |
| `surfaceDim` | `#DED8E1` | `#141218` | — |
| `surfaceBright` | `#FFFBFE` | `#3B383E` | — |
| `surfaceContainerLowest` | `#FFFFFF` | `#0F0D13` | — |
| `surfaceContainerLow` | `#F7F2FA` | `#1D1B20` | component (`FilterChip` container) |
| `surfaceContainer` | `#F3EDF7` | `#211F26` | code |
| `surfaceContainerHigh` | `#ECE6F0` | `#2B2930` | code |
| `surfaceContainerHighest` | `#E6E0E9` | `#36343B` | component (`Card` container) |

Eight roles are named in code, 16 arrive through component defaults, 11 render nowhere.
`surfaceBright`, `background` and `surface` are the same light value `#FFFBFE`, so three role names
describe one colour. `contentColorFor(background)` is `onBackground`, and `Scaffold` wraps content
in a `Surface` using it, which makes `onBackground` the colour of every `Text` that names no colour,
including the category name at `ui/home/HomeScreen.kt:540`.

**The eight roles named in code, with call sites.**

| Role | Sites |
| --- | --- |
| `primary` | `ui/settings/SettingsScreen.kt:196`, `:484`, `:494`, `:525`, `:722`, `:923`, `:959`; `ui/stats/StatsScreen.kt:152`, `:309` |
| `onSurface` | `ui/settings/SettingsScreen.kt:131`, `:486`, `:527` |
| `onSurfaceVariant` | `ui/settings/SettingsScreen.kt:138`, `:202`, `:582`, `:695`, `:915`, `:1000`; `ui/home/HomeScreen.kt:251`, `:360`, `:550`; `ui/category/CategoryScreen.kt:230`; `ui/entry/EntryScreen.kt:168`, `:208`, `:240`; `ui/stats/StatsScreen.kt:319`; `ui/SummaryFigure.kt:32` |
| `error` | `ui/settings/SettingsScreen.kt:774`, `:784`, `:793`, `:802`; `ui/home/HomeScreen.kt:185`, `:211`, `:367`, `:567`; `ui/category/CategoryScreen.kt:240`; `ui/stats/StatsScreen.kt:236`, `:255`, `:477` |
| `onErrorContainer` | `ui/home/HomeScreen.kt:484` |
| `surface` | `ui/theme/Theme.kt:150` (status bar only) |
| `surfaceContainer` | `ui/home/HomeScreen.kt:353` (day-header band) |
| `surfaceContainerHigh` | `ui/settings/SettingsScreen.kt:126` (custom dialog container) |

`onErrorContainer` is used once, as the tint of a delete icon over a transparent swipe background
(`ui/home/HomeScreen.kt:484`). The colour comes from an error container, but no container is there.

**Roles never rendered:** `onSecondary`, `tertiary`, `onTertiary`, `tertiaryContainer`,
`onTertiaryContainer`, `onError`, `errorContainer`, `surfaceVariant`, `surfaceDim`, `surfaceBright`,
`surfaceContainerLowest`. The four `tertiary` roles are a complete unused pink family.

**Colours that bypass the scheme.**

| Literal | Where | Why it matters |
| --- | --- | --- |
| income `#2E7D32` / `#81C784` | `ui/theme/Color.kt:83`, exposed by `incomeColor()` at `ui/theme/Theme.kt:180` | no Material role exists for money in |
| 10 category ARGB literals | `ui/category/CategoryScreen.kt:69` | stored per category, so they are user data and ignore the theme |
| `0xFFFFFFFF` / `0xFF000000` | `ui/Formats.kt:189`, chosen by `categoryInitialColor()` `ui/Formats.kt:186` | the letter on a swatch, picked by measured contrast |
| `Color.White.copy(alpha = 0.4f)` | `ui/category/CategoryScreen.kt:335` | selection ring on the chosen swatch |
| `Color.Transparent` | `ui/home/HomeScreen.kt:477` | swipe-to-delete background |
| `Color.Unspecified` | `ui/home/HomeScreen.kt:212` | break-even balance keeps the inherited colour |

The category palette is rendered at `ui/home/HomeScreen.kt:529`, `ui/category/CategoryScreen.kt:212`,
`:217`, `:326`, `ui/entry/EntryScreen.kt:227`, `ui/stats/StatsScreen.kt:415`, `:466`.

**What `incomeColor()` is for.** Money out uses `error`. Money in had no role, so screens
hardcoded a green, which produced six copies and a light green on a light surface. `Color.kt:79`
records the measurement: about 1.9:1, well under 4.5:1 for text. `incomeColor()` reads
`LocalIsDarkTheme` and returns one of two literals. It is a composable function, not a composition
local, so it can only be read inside a composable. Call sites: `ui/home/HomeScreen.kt:192`, `:210`,
`:375`, `:567`; `ui/stats/StatsScreen.kt:243`, `:255`, `:477`.

Measured WCAG ratios over the static values (relative luminance, sRGB):

| Pair | Ratio | 4.5:1 |
| --- | --- | --- |
| income light on `surface` | 5.00 | pass |
| income light on `surfaceContainer` (day-header band) | 4.46 | fail |
| income light on `surfaceContainerHighest` (a filled `Card`) | 3.96 | fail |
| income dark on `surface` | 8.51 | pass |
| income dark on `surfaceContainerHighest` | 6.10 | pass |
| the replaced light green `#81C784` on `surface` | 1.96 | fail |
| `error` light on `surface` / on a card | 6.38 / 5.04 | pass |
| `error` dark on `surface` | 10.03 | pass |
| `onSurfaceVariant` light on `surface` | 9.11 | pass |

The light income green clears AA on the page and misses it on the two surfaces it is actually drawn
on: a filled card (the summary card and the transaction rows) and the day-header band. The 1.9:1 in
the comment reproduces as 1.96:1.

---

## 3. Typography

No `Type.kt`, and no `typography` argument to `MaterialTheme`. The scale is `Typography()` from
Compose Material 3 1.3.1, which is the Material 3 type scale unchanged. Nine of fifteen roles are
used. The sizes below are the library defaults.

| Role | Size / line height / weight | Uses | Where |
| --- | --- | --- | --- |
| `headlineSmall` | 24 / 32 / Regular | 2 | `ui/Formats.kt:136` (amount ≤ 12 chars), `ui/settings/SettingsScreen.kt:132` (dialog title) |
| `titleLarge` | 22 / 28 / Regular | 3 | `ui/home/HomeScreen.kt:135` (month), `ui/stats/StatsScreen.kt:128` (period), `ui/settings/SettingsScreen.kt:481` (currency glyph) |
| `titleMedium` | 16 / 24 / Medium | 8 | `ui/Formats.kt:137` (amount 13–18 chars), `ui/home/HomeScreen.kt:535` (category initial), `:566` (row amount), `ui/stats/StatsScreen.kt:223`, `:280`, `:338`, `:373` (card headings), `ui/category/CategoryScreen.kt:218` (category initial) |
| `titleSmall` | 14 / 20 / Medium | 9 | `ui/home/HomeScreen.kt:359` (day header), `ui/settings/SettingsScreen.kt:195`, `:721`, `:958` (section title), `ui/category/CategoryScreen.kt:277`, `:297` (field labels), `ui/entry/EntryScreen.kt:167`, `:207`, `:239` (field labels) |
| `bodyLarge` | 16 / 24 / Regular | 6 | `ui/home/HomeScreen.kt:250` (empty state), `:542` (category name), `ui/category/CategoryScreen.kt:225`, `ui/settings/SettingsScreen.kt:493`, `:522`, `:994` (setting row) |
| `bodyMedium` | 14 / 20 / Regular | 11 | `ui/SummaryFigure.kt:31` (figure label), `ui/Formats.kt:138` (amount > 18 chars), `ui/settings/SettingsScreen.kt:139`, `:208`, `:670`, `:715`, `:910`, `:999`, `ui/stats/StatsScreen.kt:290`, `:471`, `:476` |
| `bodySmall` | 12 / 16 / Regular | 17 | `ui/home/HomeScreen.kt:366`, `:374` (day totals), `:549` (note), `ui/category/CategoryScreen.kt:229` (type), `ui/stats/StatsScreen.kt:318` (remaining), `ui/settings/SettingsScreen.kt:581`, `:694`, `:740`, `:759`, `:767`, `:773`, `:783`, `:792`, `:801`, `:813`, `:914`, `:922` (dialog body text) |
| `labelMedium` | 12 / 16 / Medium | 1 | `ui/stats/StatsScreen.kt:151` (jump to today) |
| `labelSmall` | 11 / 16 / Medium | 1 | `ui/settings/SettingsScreen.kt:201` (current-version marker) |

**Never used:** `displayLarge` (57sp), `displayMedium` (45), `displaySmall` (36), `headlineLarge`
(32), `headlineMedium` (28), `labelLarge` (14).

**The observed gap.** The largest text in the app is 24sp, and it appears twice. There is no display
scale in use at all, so no screen has a typographic hero: the month on Home, the period on Stats and
the summary figures are `titleLarge` or `headlineSmall`. The top of the working scale is
24sp, 22sp, 16sp. Hierarchy is carried by three other means instead of size.

1. Colour: `error` and `incomeColor()` for direction, `primary` for section titles and links,
   `onSurfaceVariant` for secondary text.
2. Weight: `FontWeight.Bold` at six sites, all on money or card headings
   (`ui/home/HomeScreen.kt:220`, `ui/stats/StatsScreen.kt:224`, `:254`, `:281`, `:339`, `:374`).
3. A length-driven size switch. `amountTextStyle()` (`ui/Formats.kt:134`) picks `headlineSmall` at
   12 characters or fewer, `titleMedium` at 18 or fewer, `bodyMedium` above that. It is the only
   responsive type rule in the app, and it can shrink a figure to the size of its own label.

No custom font family, no letter-spacing override, and not one `sp` literal outside the library.

---

## 4. Shape

No `Shapes` argument, so every shape is the Material 3 default: `extraSmall` 4dp, `small` 8dp,
`medium` 12dp, `large` 16dp, `extraLarge` 28dp, `full` fully round.

| Component | Sites | Shape | Corner |
| --- | --- | --- | --- |
| `Card` | 7 | `medium` | 12dp |
| `AlertDialog` | 8 | `extraLarge` | 28dp |
| `BasicAlertDialog` + `Surface` | 1 | explicit `extraLarge` | 28dp |
| `FloatingActionButton` | 2 | `large` | 16dp |
| `FilterChip` | 6 | `small` | 8dp |
| `OutlinedTextField` | 3 | `extraSmall` | 4dp |
| `Button`, `OutlinedButton`, `TextButton` | 1 / 2 / 17 | `full` | pill |
| `NavigationBar`, `TopAppBar` (4) | 5 | none | square |

Nine explicit shape references exist. No `RoundedCornerShape` appears anywhere.

| Site | Shape | Applied to |
| --- | --- | --- |
| `ui/home/HomeScreen.kt:528` | `CircleShape` | 40dp category avatar |
| `ui/category/CategoryScreen.kt:211` | `CircleShape` | 40dp category avatar |
| `ui/category/CategoryScreen.kt:312` | `CircleShape` | 48dp colour-picker target |
| `ui/category/CategoryScreen.kt:325` | `CircleShape` | 32dp colour swatch |
| `ui/category/CategoryScreen.kt:334` | `CircleShape` | 24dp selection ring |
| `ui/entry/EntryScreen.kt:226` | `CircleShape` | 16dp dot in a category chip |
| `ui/stats/StatsScreen.kt:465` | `CircleShape` | 12dp dot in a breakdown row |
| `ui/settings/SettingsScreen.kt:125` | `MaterialTheme.shapes.extraLarge` | dialog container |
| `ui/stats/StatsScreen.kt:308` | `MaterialTheme.shapes.small` | clips a progress bar to 8dp |

In practice the system has two shape values: a 12dp rounded rectangle for every card, and a circle
for every category mark. The 28dp dialog and the pill buttons come from the library.

---

## 5. Spacing and layout

Every dimension is an inline literal. 117 `dp` literals over 14 distinct values.

| Value | Count | Roles |
| --- | --- | --- |
| 8 | 35 | gaps between fields, chips, rows, icon and text |
| 16 | 29 | page padding, card padding, row padding, section gaps |
| 12 | 26 | gaps inside cards, between figures and labels, dialog row padding |
| 4 | 4 | small vertical paddings, one spacer |
| 2 | 5 | `Card` elevation, not spacing |
| 96 | 3 | list bottom reserve, swipe threshold |
| 40 | 3 | avatar size, donut stroke width |
| 24 | 3 | dialog padding, spacer, selection ring |
| 6 | 2 | tonal elevation, one spacer |
| 180 | 2 | donut chart height |
| 1 | 2 | `Card` elevation |
| 48 | 1 | colour-picker touch target |
| 32 | 1 | colour swatch |
| 20 | 1 | swipe background inset |

The 8 / 16 / 12 group is 90 of 117 literals, about 77%.

**The rhythm.** A 4dp grid. Every spacing value is a multiple of 4 except `6.dp`, and every gap is
8, 12 or 16. The recurring structure is 16dp from the page edge, 16dp inside a card, 12dp between
elements inside a card, 8dp between a label and its control. There is no named scale. The only
named dimension in the visual layer is `deleteSwipeDistance = 96.dp` (`ui/home/HomeScreen.kt:93`).

**Where it breaks.**

| Value | Site | Why it is off the scale |
| --- | --- | --- |
| `6.dp` | `ui/settings/SettingsScreen.kt:205` | the only spacing value that is not a multiple of 4 |
| `20.dp` | `ui/home/HomeScreen.kt:478` | an inset used nowhere else |
| `96.dp` | `ui/home/HomeScreen.kt:300`, `ui/category/CategoryScreen.kt:128` | FAB clearance, tied to FAB size plus margins |
| `96.dp` | `ui/home/HomeScreen.kt:93` | the same number again, as a gesture threshold |
| `180.dp` | `ui/stats/StatsScreen.kt:348`, `:383` | a fixed chart height, the only fixed height in the app |
| `1.dp`, `2.dp`, `6.dp` | `ui/home/HomeScreen.kt:515`, `:164`, `ui/category/CategoryScreen.kt:198`, `ui/stats/StatsScreen.kt:213`, `:271`, `:333`, `:368`, `ui/settings/SettingsScreen.kt:127` | three elevation values, all inline |

**The page skeleton.**

| Page | Top area | Horizontal padding | First gap | Scroll container | Bottom reserve |
| --- | --- | --- | --- | --- | --- |
| Home | `TopAppBar`, title centred | 16 | 16 | `LazyColumn` | 96 content padding |
| Stats | hand-built `Column` + `statusBars` | 16 | 8 | `verticalScroll` | 16 |
| Settings | `TopAppBar`, title at start | 16 on the column | 16 | `verticalScroll` | 16 |
| Entry | `TopAppBar`, back and check | 16 | 16 | `verticalScroll` + `imePadding` | 16 |
| Category | `TopAppBar`, back | 16 content padding | 16 | `LazyColumn`, 8 between rows | 96 content padding |

Stats is the only screen that builds its own top bar (`ui/stats/StatsScreen.kt:98`), to place the
date and the jump-to-today line at an exact height. The trade is that it is the only screen whose
title is not a `TopAppBar` title. Card inner padding is 16dp everywhere. List rows are 16dp, with
4dp vertical spacing between cards on Home and 8dp on Category.

---

## 6. Motion

Four constructs are authored. Nothing else animates unless a component does it.

| Construct | Where | Spec |
| --- | --- | --- |
| Overlay enter | `ui/navigation/NavGraph.kt:102` | `slideInVertically` from full height + `fadeIn`, `tween(300, FastOutSlowInEasing)` |
| Overlay exit | `ui/navigation/NavGraph.kt:107` | `slideOutVertically` + `fadeOut`, `tween(250, FastOutSlowInEasing)` |
| Pager jump | `ui/navigation/NavGraph.kt:175` | `animateScrollToPage`, `tween(300)`, or `tween(200)` past one page (`:170`) |
| Card resize | `ui/home/HomeScreen.kt:514` | `animateContentSize()` with no arguments, so the default spring |

Five `tween` calls: two for the enter, two for the exit, one for the pager. All use
`FastOutSlowInEasing`. The two overlays are one composable, `OverlayHost`, instantiated twice
(`ui/navigation/NavGraph.kt:251`, `:258`).

**What is not animated:** the navigation icon alpha (`graphicsLayer` at
`ui/navigation/NavGraph.kt:275`, recomposed instantly between 1f and 0.6f), every dialog, list
items, and any content state change. No `Crossfade`, no `AnimatedContent`.

**What a redesign may assume.**

- No transition framework. No `AnimatedContent`, no `Crossfade`, no `SharedTransitionLayout`, no
  `AnimatedNavHost`, and no `navigation-compose` dependency (`app/build.gradle.kts`).
- The pager is the only large-scale motion in the product. Its drag, settle and fling come from
  `PagerDefaults`, not from app code.
- `animateContentSize` is the only size animation, used once, on the transaction card.
- Gesture and component motion (ripple, `SwipeToDismissBox` drag, snackbar enter and exit) is
  inherited and unconfigured.
- No reduced-motion handling, no duration token, no easing token. Those four durations are the
  entire motion vocabulary.

---

## 7. Iconography and imagery

`androidx.compose.material:material-icons-core` only. The extended set is not a dependency. Seven
icons form the working set, plus two auto-mirrored arrows, nine vectors in total.

| Icon | Sites | Meaning |
| --- | --- | --- |
| `Icons.Default.Add` | 2 | new transaction (FAB), new category (FAB) |
| `Icons.Default.Delete` | 2 | category row action, swipe-to-delete reveal |
| `Icons.Default.Edit` | 1 | edit a category |
| `Icons.Default.Check` | 1 | save, in the entry top bar |
| `Icons.Default.Home` | 1 | bottom-nav tab |
| `Icons.Default.Settings` | 1 | bottom-nav tab |
| `Icons.AutoMirrored.Filled.List` | 1 | bottom-nav "Statistics" tab |
| `Icons.AutoMirrored.Filled.ArrowBack` | 3 | back from entry and categories, previous period |
| `Icons.AutoMirrored.Filled.ArrowForward` | 1 | next period |

Three of the nine are auto-mirrored. `supportsRtl="true"` is set, but no RTL locale ships, so
mirroring is untested. Every icon has a `contentDescription` from a string resource except the
three bottom-nav icons, where the label below names the tab (`ui/navigation/NavGraph.kt:272`).

**Imagery.** `app/src/main/res/drawable/` is empty: no illustration, no logo, no empty-state
graphic, no decorative vector. The launcher icon is five `webp` bitmaps, and `mipmap-anydpi-v26/`
is empty, so there is no adaptive icon, no vector foreground and no `roundIcon`. Inside the app the
string `Budge` appears nowhere: Home shows a month, Settings shows "Settings", and the name exists
only as the launcher label. The only visual identity in the product is the ten-colour category
palette, which is data, and the category initial drawn on it.

**What an empty list renders.**

| Screen | Condition | What appears |
| --- | --- | --- |
| Home | no transactions this month | one centred line, "No transactions yet", `bodyLarge`, `onSurfaceVariant`, in a `Box` filling the remaining height (`ui/home/HomeScreen.kt:240`) |
| Home | first frame, nothing read | an empty `Box` (`ui/home/HomeScreen.kt:233`), no claim before the query answers |
| Stats | no categories for a direction | the whole card is skipped (`ui/stats/StatsScreen.kt:330`, `:365`) |
| Stats | no budget | the budget card is skipped (`:267`) |
| Category | no categories | an empty `LazyColumn`, no message (`ui/category/CategoryScreen.kt:120`) |
| Entry | no categories | an empty `FlowRow`; saving fails with "Please select a category" |

The Home empty state is one sentence with no icon, no heading and no action. The summary card above
stays on screen, so an empty month shows `$0.00` twice and `Balance: $0.00`, then the sentence, then
a FAB whose only label is a plus sign.

---

## 8. Dark theme and dynamic colour

**How dark is chosen.** Three user states and the system default. The same boolean drives the
scheme, `LocalIsDarkTheme`, the status bar colour and the bar icon appearance, so the four cannot
disagree.

**The `values-night` resources.** One file: `app/src/main/res/values-night/themes.xml`. It defines
`Theme.Budge` with the dark platform parent, where `values/themes.xml` uses the light one. This is
the window shown before Compose draws, and without it a device in dark mode would flash white
between the splash and the first frame. It carries no colours of its own.

**Dynamic colour is on and cannot be turned off in the app.** `BudgeTheme(dynamicColor = true)` is
the default, and the sole call site passes only `themeMode`. Settings offers three theme states and
no dynamic-colour switch. Nothing in `Prefs` stores such a preference.

On Android 12 and later the scheme comes from `dynamicLightColorScheme(context)` or
`dynamicDarkColorScheme(context)`, derived from the user's wallpaper. The 70 hex values in section 2
are unused at runtime on those devices. They apply to Android 8 through 11, and to any build that
disables dynamic colour.

**The consequence, plainly.** A redesign cannot art-direct the palette. Any colour system it defines
will be replaced, on most devices, by tones generated from a wallpaper. The stock `#6750A4` primary
is not what an Android 12 phone shows today. Two things survive dynamic colour: the category palette,
because it is stored user data, and `incomeColor()`, because it is a literal with no Material role.
The result on Android 12+ is a mixed palette, wallpaper-derived containers and text with one fixed
green and ten fixed category colours on top.

The trade-off is brand control against platform fit. Keeping the default gives system-level
coherence and zero art direction. Changing it to `false` gives a designed palette on every device
and gives up the platform look. Either way it is a one-line change at `MainActivity.kt:90` or at
`ui/theme/Theme.kt:111`. The section 2 ratios describe the static scheme only: on a dynamic scheme
the light income green sits on surfaces whose luminance nobody has measured.

---

## 9. The gaps

1. **The theme is colour only.** `MaterialTheme` gets a scheme and content
   (`ui/theme/Theme.kt:164`). No type file, no shape file, no spacing scale, no elevation scale, no
   motion token.
2. **The palette is the stock Material 3 baseline.** Primary is `#6750A4`, the default purple. No
   colour in `Color.kt` was chosen for this product. The only authored colour is the income green.
3. **Eleven of 35 roles never render,** including a complete unused tertiary family. `errorContainer`
   is defined and never used, despite 8 dialogs and 4 snackbar hosts.
4. **The income green misses AA where it is used:** 3.96:1 on a filled card and 4.46:1 on the
   day-header band, against 5.00:1 on the page. It was added to fix a 1.96:1 and fixed the page only.
5. **The chosen-swatch indicator is nearly invisible.** A white ring at 40% opacity measures 1.06:1
   to 1.58:1 against its own swatch. WCAG asks for 3:1 for non-text indicators.
6. **`onErrorContainer` is borrowed out of context,** as the tint of a delete icon on a transparent
   background (`ui/home/HomeScreen.kt:484`), where no container exists.
7. **The type scale tops out at 24sp.** Six roles are unused, including all three display sizes. No
   screen has a typographic hero, and hierarchy rests on colour and six `Bold` overrides.
8. **The only responsive type rule shrinks money.** `amountTextStyle()` steps 24 → 16 → 14 by
   character count, so a long amount can end up the same size as its label.
9. **Spacing is 117 literals with no scale:** 14 values, 77% of them 8, 12 or 16, and `6.dp` off the
   4dp grid. The only named dimension is a swipe threshold.
10. **Elevation is three inline values,** 1dp, 2dp and 6dp, chosen per call site with no relation to
    the colour roles beneath them.
11. **Shape is effectively two values.** Every card is a 12dp rectangle, every category mark a
    circle. No `RoundedCornerShape` exists in the codebase.
12. **Empty states are not designed.** Home is one sentence with no icon and no action. Category
    management renders nothing at all. Two Stats cards disappear instead of saying they are empty.
13. **There is no icon system.** Nine core vectors, no extended set, no brand mark, and a launcher
    icon of five bitmaps with no adaptive variant.
14. **Motion is four constructs:** 300ms and 250ms tweens on one easing curve, one default spring,
    no reduced-motion handling. The nav icon alpha change is not animated at all.
15. **The same card is built twice,** as two compositions with small differences:
    `ui/home/HomeScreen.kt:159` and `ui/stats/StatsScreen.kt:211`. The day header
    (`ui/home/HomeScreen.kt:343`), the breakdown row (`ui/stats/StatsScreen.kt:453`) and the
    category avatar (`ui/category/CategoryScreen.kt:207`, `ui/home/HomeScreen.kt:524`) repeat too.
16. **Two title treatments.** Stats builds its own top bar. The other four screens use `TopAppBar`.
    Home centres its title with `fillMaxWidth` and `TextAlign.Center` (`ui/home/HomeScreen.kt:136`),
    the rest do not.
17. **There is no way to look at the system.** No `@Preview`, no Compose UI test, no screenshot test.
    A visual change can only be checked by building and running the app on a device.

---

## 10. Hard constraints for any redesign

| Constraint | Evidence | What it means |
| --- | --- | --- |
| Nine locales with identical key sets | `app/src/test/java/com/example/budge/LocaleResourcesTest.kt:64`; `LOCALES` in `app/build.gradle.kts` | A new string goes into `values/` and all eight translations or the test fails. English and Chinese carry 151 strings, the other seven 132. |
| `release_notes_*` in English and Chinese only | `LocaleResourcesTest.kt:80`; 19 keys from `values/strings.xml:160` | The other seven locales must not define them. They fall back to English on purpose. |
| Format specifiers match across locales, in order | `LocaleResourcesTest.kt:93` | `%1$s` in English stays `%1$s`, and stays first. |
| No unescaped ASCII apostrophe in a string resource | `CONTRIBUTING.md:52` | The resource compiler rejects it. Rephrase rather than escape. No translatable string currently contains one. |
| Counted text is a `<plurals>` | `CONTRIBUTING.md:54`; two plurals at `strings.xml:37`, `:41` | "3 transactions" must be a plural, with the forms each language needs. |
| Contrast ≥ 4.5:1 for body text in both themes | `ui/theme/Color.kt:79`; `ui/Formats.kt:169` computes WCAG ratios | Two static schemes apply, and dynamic colour adds a third set. The income green exists because of this rule. |
| Portrait only | `AndroidManifest.xml`, `android:screenOrientation="portrait"` | No landscape, no two-pane, no tablet layout. Widths are phone widths. |
| minSdk 26, targetSdk 35, compileSdk 35 | `app/build.gradle.kts` | Appearance APIs must exist on API 26. Dynamic colour is 31+. `statusBarColor` is ignored from 35. |
| No new permissions | `AndroidManifest.xml` declares `INTERNET` and `REQUEST_INSTALL_PACKAGES` only | A redesign adds no capability. |
| Strings stay in resources | `LocaleResourcesTest.kt` counts XML keys; every `Text` uses `stringResource` | No hardcoded label, in any language, in Kotlin. |
| The build stays green | `CONTRIBUTING.md:41`; CI runs `./gradlew :app:testDebugUnitTest` | The suite includes the locale tests, so a string change can fail the build. |

Two more are recorded as product promises rather than tests: no analytics and no third-party UI
dependency (`app/build.gradle.kts` dependencies), and the two-network-request guarantee in
`README.md`.

---

## 11. What a redesign can change, and what it cannot

**Open.**

- Theme tokens: colour roles, palette values, whether dynamic colour stays on, the scheme
  construction at `ui/theme/Theme.kt:22` and `:61`.
- A typography layer: a `Type.kt`, a scale, a font, the six unused roles.
- A shape layer: a `Shape.kt`, a corner scale, per-component overrides.
- Spacing and elevation tokens, and the 117 literals that would consume them.
- Component composition: cards, rows, headers, dialogs, the two duplicated summary cards.
- Screen layouts: what each of the five screens is made of, and the top-bar treatment.
- Motion: durations, easing, transitions, the pager.
- Assets: icons, an app mark, empty-state graphics, the launcher icon.
- Strings: wording in all nine locales, within section 10.

**Closed.**

- The data model: three entities, two summary projections, the migration path.
- The money representation: integer cents, `Amount` saturation at the aggregate edges, the
  `#,##0.00` formatter.
- The update trust chain: tag parsing and SHA-256 verification before install.
- The backup format: the JSON codec and its sanitisation.
- The channel ladder: alpha, beta, rc, stable, and which releases each build may see.

---

## What I did not verify

- I did not build or run the app, and I did not run `./gradlew :app:testDebugUnitTest`. Running
  Gradle writes into `app/build/`, which was out of scope. The suite's greenness is taken from
  `CONTRIBUTING.md:41` and from reading the tests.
- All contrast ratios are computed with the WCAG relative-luminance formula over the static hex
  values in `Color.kt`. Nothing was sampled from a rendered screen. With dynamic colour on, those
  values are not what most devices show.
- The role-to-component mapping in section 2 comes from the Material 3 1.3.1 token classes
  (`FilledCardTokens`, `FilterChipTokens`, `NavigationBarTokens`, `SnackbarTokens`, `DialogTokens`)
  read out of the Gradle cache, not from a screenshot.
- I did not check how `dynamicLightColorScheme` fills the `surfaceContainer*` roles on API 31 to 33,
  where the platform surface roles are newer than the library's.
- I did not verify RTL rendering. `supportsRtl` is true and three icons are auto-mirrored, but no RTL
  locale ships, so nothing exercises it.
- Counts in sections 3, 4, 5 and 7 are grep counts over
  `app/src/main/java/com/example/budge/ui`. A composable invoked inside a lambda counts once per
  call site, not per rendered instance.
- Two counts from the brief are refined here rather than repeated: there is one `AnimatedVisibility`
  construct (`OverlayHost`) instantiated twice, not three, and the icon set is seven non-arrow icons
  plus two auto-mirrored arrows, nine vectors in total.
- I did not read the git history, so I cannot say why the tertiary family was defined and never used,
  or whether the spacing literals were ever meant to become a scale.
- I did not check a large font scale on any device. The money rows are already `maxLines = 1` with
  `softWrap = false`, which is where a problem would surface first.
- I did not measure what a dynamic-colour scheme does to the ten fixed category swatches placed on
  wallpaper-derived surfaces.
