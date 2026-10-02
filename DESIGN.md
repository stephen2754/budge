# Budge — Design Document

> Why the app is built the way it is: the data model, the layers, the rules that are easy
> to break, and the trade-offs behind them.

| | |
| --- | --- |
| **Applies to** | 0.1.0-alpha.1 and later |
| **Companion docs** | [README.md](README.md) for building and running, [CHANGELOG.md](CHANGELOG.md) for what changed |

---

## 1. Project Overview

| Item | Description |
| --- | --- |
| Product Name | Budge |
| Version | 0.1.0-alpha.2 (alpha channel) |
| Target Platform | Android 8.0+ (API 26+), mobile only |
| Core Positioning | Fully manual bookkeeping — no auto sync, no bank import, all data stays on device |
| Design Standard | Material Design 3 (Material You, dynamic color) |
| Data Storage | Local SQLite via Room, with JSON export/import |
| Network Permission | `INTERNET`, for the user-initiated update check and the update download; `REQUEST_INSTALL_PACKAGES`, to hand a verified download to the system installer — see §12.15 |
| minSdk | 26 (Android 8.0 Oreo) |
| targetSdk | 35 (Android 15) |
| compileSdk | 35 |

**Core value**: open and record instantly, one-second entry, clear monthly statistics, private and secure with no network access.

### 1.1 Internationalization (i18n)

- The app ships **nine languages**: Simplified Chinese, English, French, German, Spanish, Russian, Japanese, Italian and Portuguese.
- The language is **user-configurable** in Settings: **Follow System** plus those nine, each listed under its own name and ordered by language code — `de, en, es, fr, it, ja, pt, ru, zh` — which is an order a reader can predict: the Latin names come out alphabetical and the two written in other scripts sit where their code puts them, instead of the app's own language being first (defaults to Follow System). Following the system means the device language is used when the app has it, and English otherwise — including when the locale cannot be read at all.
- The choice is stored in DataStore (`language` key). It is applied in `MainActivity.attachBaseContext` so the Activity's resources are localized while `LocalContext` remains the Activity (required by Hilt's `hiltViewModel`). A `LocalAppLocale` CompositionLocal drives the date/time formatting helpers.
- All UI strings live in resource files: `values/strings.xml` (English, the default) plus one `values-<language>/strings.xml` per translation, 132 keys each (148 in English and Chinese, which also carry the release-note summaries) and two plurals. `LocaleResourcesTest` holds that in step: same keys in every locale, same format specifiers in the same order, and every plural defined with the forms its language needs (Russian's `one`/`few`/`many` included, so a count of one no longer reads "1 операций").
- **Anything that carries a count is a `<plurals>`**, never a `%1$d` inside a plain string, so a language that inflects gets its own forms.
- **Dates and times come from the locale**, not from a fixed pattern: the day header, the month title, the statistics date and the clock all ask the platform for the arrangement a CLDR skeleton resolves to (`ui/LocalizedPatterns.kt`), and the time picker follows the device's own 12/24-hour setting. The old fixed patterns read "août 11, mar." in French where French writes "mar. 11 août", and showed a 24-hour clock to everyone. Chinese and Japanese keep their year-first `2026年8月11日` style.
- Changing the language **does not** touch category names: the built-ins are seeded in the device language on first launch and are ordinary user-editable categories afterwards (see §12.4).

### 1.2 Currency Handling

- Amounts are stored internally as **Long integer cents** (e.g., `12.34` → `1234L`).
- A user-configurable currency token is stored in DataStore (`currency_symbol`). Settings offers **nine signs and nothing else** (`Currencies.choices`): `$`, `€`, `¥`, `£`, `₽`, `₹`, `₩`, `₺` and `R$`. The picker used to print the currencies sharing each sign beside it ("USD / CAD / AUD / …"), which read as though the row were choosing a currency rather than a sign: most of these signs are shared, no such list can be complete, and an amount shows the sign alone in any case (`¥1,234.56`). The set stops at signs that are both distinct and common — the sterling-pegged territory pounds, the peso and dollar families that only prefix a `$`, and the currencies whose sign is a word (`kr`, `zł`, `R`, `CHF`) are not offered, and the list is deliberately short because every extra row is another choice for a reader who only wants their own. A sign a stored value no longer decodes to is repaired to the locale default at startup (§12).
- On **first launch** the token is derived from the **system language**; the stored value is never overwritten afterwards:

  | System language | First-launch token |
  | --- | --- |
  | Chinese | `¥` |
  | Japanese | `¥` |
  | Russian | `₽` |
  | other European languages | `€` |
  | English, unlisted languages, unreadable locale | `$` |

  Chinese and Japanese get the *same* sign: on every screen the figure reads `¥1,234.56`, and which of the two currencies it is is stated once, in the picker's `CNY / JPY` label.
- Amounts are formatted as `sign + grouped number` with US grouping, so the display is consistent regardless of device locale. The sign always attaches directly to the number (`€1,234.56`). A token stored by an older build that this one no longer offers is rewritten from the device language at startup, so the amount can never show a sign the picker cannot produce.

---

## 2. Functional Requirements

### 2.1 Feature Modules

| Module | Description |
| --- | --- |
| Transaction Entry | Choose type (expense / income), enter amount, pick category, set date & time, add a note |
| Transaction List | Chronological list of the current month grouped by day with daily subtotals |
| Quick Entry | Floating action button on Home opens the entry screen instantly |
| Statistics | Year / Month / Day summaries, balance, category-share donut charts, optional budget progress |
| Category Management | Built-in categories seeded on first launch in the device language, plus custom ones; every category is renameable, re-typeable, re-colourable and deletable |
| Transaction Edit | Swipe to delete; tap a transaction to open the edit screen |
| Budget (display only) | Monthly budget with remaining-amount and progress bar on Statistics (no UI to set a budget yet) |
| Data Backup | Export / import a JSON backup file, with an overwrite confirmation dialog |
| Settings | Currency sign, theme (system / light / dark), language, category management, data export/import/clear, and an About section (release history, licences, update check) |

**MVP scope (implemented)**: Transaction Entry, Transaction List, Quick Entry, Statistics, Category Management, Transaction Edit, Settings, Data Backup, Language & Theme settings.
**Stretch goals (not yet implemented)**: editable budget, encrypted backup, custom period start day, daily trend bar chart.

### 2.2 Core User Flow

```
Open app → Home (transaction list)
   ├─ Tap "+" → Entry screen → pick type/category → enter amount → set date/time → save → back to refreshed home
   ├─ Swipe left/right → Statistics / Settings tabs
   └─ Tap a transaction → edit / delete (swipe to delete)
```

---

## 3. Technology Stack

### 3.1 Stack Overview

| Layer | Choice | Notes |
| --- | --- | --- |
| Language | Kotlin | Official recommendation, coroutine-friendly |
| UI Framework | Jetpack Compose + Material 3 | Modern declarative UI with first-class M3 support |
| Architecture | MVVM + Repository | Single direction of data flow, layered responsibilities |
| Local Database | Room (SQLite) | Official Google ORM, type-safe, reactive Flow queries |
| Async | Kotlin Coroutines + Flow | Main-thread safe, stream-driven UI |
| Dependency Injection | Hilt | Official DI solution, testable |
| Navigation | HorizontalPager + NavigationBar + overlay screens | Custom bottom navigation; `navigation-compose` is declared but not used for the main flow |
| State Management | ViewModel + StateFlow | Centralized UI state |
| Charts | Custom `Canvas` donut chart | Keeps the dependency footprint small |
| Update check | `HttpURLConnection` + Gson against the GitHub releases API | One read-only GET does not justify an HTTP client dependency |
| Preferences | DataStore Preferences | Stores theme, currency symbol, and language |
| Build | Gradle Kotlin DSL + Version Catalog (libs.versions.toml) | Single module |
| Testing | JUnit 4 (unit) | 31 JVM unit tests in `app/src/test`; Compose UI test dependencies are declared but no instrumentation tests exist yet |

### 3.2 Key Dependencies

```
- org.jetbrains.kotlin:kotlin-stdlib:2.0.21
- androidx.compose:compose-bom:2024.12.01
- androidx.compose.material3:material3
- androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7
- androidx.lifecycle:lifecycle-runtime-compose:2.8.7
- androidx.hilt:hilt-navigation-compose:1.2.0  (brings navigation-compose transitively)
- androidx.room:room-runtime:2.6.1
- androidx.room:room-ktx:2.6.1
- androidx.room:room-compiler:2.6.1  (KSP processor)
- com.google.dagger:hilt-android:2.52
- com.google.dagger:hilt-compiler:2.52  (KSP processor)
- androidx.hilt:hilt-navigation-compose:1.2.0
- androidx.datastore:datastore-preferences:1.1.1
- com.google.code.gson:gson:2.11.0  (JSON backup)
- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0
```

**Note**: Both Room and Hilt use KSP (not kapt) for annotation processing, which significantly speeds up builds. The catalog is kept to what the app actually builds against: the unused `vico`, `material-icons-extended`, `ui-graphics` and duplicate `ui-test-*` entries have been removed (charts are drawn with `Canvas`).

### 3.3 Rationale

- **Compose + Material 3**: dynamic color and adaptive dark/light themes work out of the box; it is the current mainstream direction for Android UI.
- **Room over handwritten SQLite**: type-safe, built-in migration mechanism, and returns `Flow` directly so lists refresh automatically.
- **Pure local, no network**: no backend, lower complexity and fewer permission risks, matching the "fully manual" positioning.

---

## 4. Data Model

### 4.1 Entities

**Transaction** (`transactions`)

| Field | Type | Description |
| --- | --- | --- |
| id | Long (PK, autoGenerate) | Primary key |
| type | Int (0 = expense, 1 = income) | Transaction type |
| amount | Long (in cents) | Integer cents; the *range* is unsigned 32-bit (`Amount.MAX_CENTS`), enforced on input — see §12.11 |
| categoryId | Long (FK → categories.id, RESTRICT) | Referenced category |
| note | String? | Optional note |
| timestamp | Long | Epoch milliseconds; used for grouping and statistics |
| createdAt | Long | Creation time |
| updatedAt | Long | Last update time |

**Category** (`categories`)

| Field | Type | Description |
| --- | --- | --- |
| id | Long (PK, autoGenerate) | Primary key |
| name | String | Category name (localized for built-ins) |
| icon | String | Icon identifier (Material Symbols name) |
| color | Long | Category theme color (ARGB) |
| type | Int | Owning type of this category (expense / income) |
| isDefault | Boolean | System built-in (built-ins cannot be deleted) |
| sortOrder | Int | Sort order |

**Budget** (`budgets`, display only)

| Field | Type | Description |
| --- | --- | --- |
| id | Long (PK, autoGenerate) | Primary key |
| month | Long | Month code (yyyyMM, e.g. 202608) |
| amount | Long (cents) | Budget amount; same unsigned 32-bit range as a transaction amount |

### 4.2 Relationships

```
Category 1 ──── * Transaction
```

- `transactions.categoryId` uses a RESTRICT foreign key, so a category referenced by transactions cannot be deleted.
- Statistics SQL groups by `timestamp` ranges and aggregates by `categoryId` **and the direction the transaction was recorded with** (`GROUP BY c.id, t.type`, see `TransactionDao.kt`). The direction cannot come from the category: a category can be re-typed after it has transactions, and every transaction keeps its own type, so labelling a category's sum from `c.type` moved money to the other side of the screen as soon as a category was re-typed — the donut and the totals above it stopped agreeing. A category used in both directions appears once per breakdown, which is what the two charts want.

---

## 5. System Architecture

### 5.1 Layered Structure

```
┌────────────────────────────────────────────┐
│  UI Layer (Compose)                         │
│  Screens, components, theme, navigation     │
├────────────────────────────────────────────┤
│  ViewModel Layer                            │
│  Exposes StateFlow, handles user interactions│
├────────────────────────────────────────────┤
│  Repository Layer                           │
│  Single data entry point, aggregates DAOs   │
├────────────────────────────────────────────┤
│  Data Layer (Room)                          │
│  Database / DAO / Entity                    │
└────────────────────────────────────────────┘
```

- Single Activity; navigation is a `HorizontalPager` (Statistics / Home / Settings) with a bottom `NavigationBar` and full-screen overlay screens (Entry, Categories) animated in from the bottom.
- Data flow: `Room DAO returns Flow → Repository → ViewModel (StateFlow) → Compose collectAsState`, so any database change automatically refreshes the UI.
- The nav-bar highlight follows the pager: on a manual swipe it switches once the target page passes the 50% midpoint; on a nav-bar tap it jumps straight to the destination (programmatic scroll is guarded so intermediate pages are never highlighted).

### 5.2 Module Layout

A single module (`app`) keeps everything simple; the packages mirror the layers and could be split later.

---

## 6. UI/UX Design (Material Design 3)

### 6.1 Design Guidelines

- **Theme**: Material 3 theme with dynamic color enabled on Android 12+ and a fixed color scheme fallback. Theme modes: Follow System / Light / Dark.
- **Layout principles**: 8dp grid, card-based surfaces, M3 corner radii.
- **Icons**: Material Icons, combined with per-category custom colors.
- **Navigation**: bottom Navigation Bar (Statistics / Home / Settings) + FAB on Home; entry and category screens are bottom slide-up overlays.

### 6.2 Screen List

| Screen | Layout Highlights |
| --- | --- |
| Home | TopAppBar (current month), monthly summary card (expense / income / balance, the balance coloured by its sign), day-grouped transaction list, FAB add, swipe left to delete (with a haptic at the threshold) |
| Transaction entry | Type FilterChips (expense/income), amount field with currency prefix, content-sized category chips (`FlowRow`), separate **Date** and **Time** buttons (date restricted to today & past, future times clamped), note field, save |
| Statistics | Its own top area rather than Material's `TopAppBar`: previous/next arrows around the date, 16dp below the status bar, with the "jump to today" line laid out directly under it whether or not there is anywhere to go back from; then the period selector (Year / Month / Day, centered, equal widths) over a single date anchor, so switching period keeps the same date; summary card, budget progress (if set), expense & income donut charts with per-category lists |
| Category management | List + add/edit dialog (name, type, color picker) |
| Settings | Sections for Currency, Appearance (theme + language), Categories (the entry point into category management), Data (export / import / clear all), About (version + channel → what this build changed, with the earlier releases behind an "update history" button and, for a beta build, a button that adds the alphas to that list; licences, with the open-source components on a page of their own behind a bottom-left button; update check, which offers a stable build a way onto the beta line when one is ahead of it) |

### 6.3 Interaction Details

- Amount input accepts digits and one decimal point, limited to two decimal places.
- Save/delete/import/export outcomes are surfaced via Snackbars.
- **The balance wears its sign.** Money in is the green the income figure uses, money out
  is the theme's error red, and break-even keeps the page's own colour: the balance decides
  whether the month is a surplus or a debt, and a reader should not have to parse a minus
  sign to find out.
- **Swipe-to-dismiss deletes transactions, but only a deliberate swipe.** The row buzzes
  the moment a left swipe passes 96dp — the haptic is what says the gesture has gone far
  enough — and releasing there asks for confirmation; releasing before it snaps back with
  nothing happening. The dialog is the confirmation, so the box itself never dismisses. The
  buzz is measured the same way the decision is — the distance the row has actually
  travelled — so the two cannot disagree in either direction: watching what the gesture is
  about to settle to buzzed for a quick flick that the settle carried past the threshold on
  speed alone and then deleted nothing, and taking the settle alone as the decision showed
  the dialog with no buzz behind it. The magnitude of the offset is used rather than a
  signed one so the two cannot drift apart over which way is negative.
- **A row does not swipe to the next page, and must not grow one.** `SwipeToDismissBox`
  owns horizontal drags for the whole row, so a rightward swipe on a row does nothing. It
  was given a detector of its own once, which *looked* right and broke the left swipe: a
  horizontal drag detector consumes the touch slop before the box below it ever sees the
  gesture. Turning the page from a row would mean dropping the box and writing the row's
  own drag — including its threshold, its animation and its settling — which is a rewrite
  of the gesture that already works, on the part of the screen that is easiest to get
  wrong. The pager still turns from the empty space around the rows and from the bottom
  bar.
- **"Jump to today" appears only when there is something to go back from.** It moves the
  anchor to today's whole date while leaving the period alone, so the year view still shows
  a year, and switching to month or day afterwards is already on today's month and day. It
  is drawn **below** the app bar rather than inside it: the bar is a fixed height, so a
  second line within it pushed the date upwards the moment the hint appeared. Under the bar
  it sits directly beneath the date and the date never moves.
- Editing an existing transaction reuses the entry screen pre-filled with its values.

---

## 7. Project Structure (single module)

```
budge/
├── app/
│   ├── schemas/com.example.budge.data.local.BudgeDatabase/   # exported Room schemas
│   └── src/
│       ├── main/
│       │   ├── java/com/example/budge/
│       │   │   ├── BudgeApplication.kt    # @HiltAndroidApp; seeds categories, default currency
│       │   │   ├── MainActivity.kt        # applies locale in attachBaseContext
│       │   │   ├── ui/
│       │   │   │   ├── AppLocale.kt       # LocalAppLocale CompositionLocal
│       │   │   │   ├── Formats.kt         # money + date formatting, date-picker conversion
│       │   │   │   ├── theme/             # Color / Theme (M3 palettes, income color)
│       │   │   │   ├── navigation/        # NavGraph.kt (pager + overlays), Screen.kt (page identity)
│       │   │   │   ├── home/              # HomeScreen.kt, HomeViewModel.kt
│       │   │   │   ├── entry/             # EntryScreen.kt, EntryViewModel.kt
│       │   │   │   ├── stats/             # StatsScreen.kt, StatsViewModel.kt
│       │   │   │   ├── settings/          # SettingsScreen.kt, SettingsViewModel.kt
│       │   │   │   └── category/          # CategoryScreen.kt, CategoryViewModel.kt
│       │   │   ├── data/
│       │   │   │   ├── backup/            # BackupCodec.kt (JSON wire format + validation)
│       │   │   │   ├── update/            # AppVersion/ReleaseChannel, Changelog, licences, GitHub reader
│       │   │   │   ├── prefs/             # Prefs.kt (DataStore keys + language -> Locale)
│       │   │   │   ├── local/
│       │   │   │   │   ├── BudgeDatabase.kt
│       │   │   │   │   ├── entity/        # Transaction/Category/Budget entities + summary rows
│       │   │   │   │   └── dao/           # TransactionDao, CategoryDao, BudgetDao
│       │   │   │   └── repository/        # Transaction/Category/Budget/Backup repositories
│       │   │   ├── di/                    # AppModule.kt (DataStore), DatabaseModule.kt (Room + migrations)
│       │   │   └── model/                 # Transaction, Category, Budget, TransactionType, Summary
│       │   ├── res/
│       │   │   ├── values/                # strings.xml, colors.xml, themes.xml
│       │   │   ├── values-zh/strings.xml
│       │   │   ├── values-{fr,de,es,ru,ja,it,pt}/strings.xml
│       │   │   └── (every locale here must also be listed in resourceConfigurations)
│       │   │   └── xml/                   # backup_rules.xml (API <=30), data_extraction_rules.xml (API 31+)
│       │   └── AndroidManifest.xml
│       └── test/java/com/example/budge/   # JVM unit tests (Formats, BackupCodec, repositories)
├── gradle/libs.versions.toml              # version catalog
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── README.md                              # build, run, release, conventions
├── CHANGELOG.md                           # release notes, mirrored in-app
└── DESIGN.md                              # this document
```

---

## 8. Development Status

### Implemented
- Compose + Material 3 + Hilt + Room (KSP) project setup; theme system (dark/light/dynamic).
- Transaction/Category data layer, repositories, seeded default categories.
- Entry screen (type, amount, category, date & time, note) with edit support.
- Home list (day-grouped, monthly summary, FAB, swipe-to-delete).
- Statistics (period selector, summaries, custom donut charts).
- Category management CRUD with color picker and delete guards.
- Settings: currency, theme, language, JSON export/import, clear-all.
- First-launch defaults: category seeding, language-based default currency.
- Room migrations for schema versions 1–3 (identical schemas, no-op).
- JVM unit tests for money/date formatting, the backup wire format, category
  seeding/localization rules and the month-range conversion.
- Backups carry transactions, categories **and** budgets; the import validates the
  document before touching the database and re-homes records whose category is
  missing instead of dropping them.
- Nine UI languages, with a per-language built-in category table and the
  first-launch currency rule of §1.2.
- Category management is reachable: Settings → Categories. (It used to have no entry
  point at all — the Stats screen accepted an `onNavigateToCategories` callback it
  never invoked.)

### Not yet implemented (stretch goals)
- **An update source other than GitHub.** The check itself is live — it reads the
  releases of `stephen2754/budge`, channel-aware, with the release history and licences
  behind it (§12.15) — and pointing it somewhere else is a one-line change to
  `UpdateConfig.GITHUB_REPOSITORY`. A second `ReleaseSource` implementation is all a
  different host would take.
- Editable monthly budget. The table, DAO, repository and stats card all exist, but
  nothing writes a budget, so the card never renders.
- Per-category icons. `CategoryEntity.icon` is stored and plumbed through to the
  domain model, but no screen draws it: lists show the name's first character in the
  category color instead.
- Daily trend bar chart.
- Custom period start day.
- Encrypted backup.
- Instrumentation / Compose UI tests (the dependencies are declared, the tests are not
  written). Unit tests exist — see §11.
- Per-app language via the platform API (API 33+ `LocaleManager` + `android:localeConfig`).
  The language is still applied through `attachBaseContext`, which needs one blocking
  preference read at startup.

---

## 9. Error Handling Strategy

| Layer | Error Type | Handling |
| --- | --- | --- |
| Data Layer | Database write failure | Wrapped in try-catch; surfaced as a UI message |
| Data Layer | Invalid query | Room returns empty Flow/list |
| ViewModel | Repository error | Exposed as a `message`/`error` in UI state, shown via Snackbar |
| UI Layer | Input validation | Inline validation (invalid amount, missing category, blank name) |
| UI Layer | Network (update check only) | Failures surface as "could not reach the update source", never as "up to date" |

---

## 10. ProGuard / R8 Rules

See `proguard-rules.pro`. Key rules keep Room, Hilt, Gson, and Coroutines classes intact for release builds.

---

## 11. Testing Strategy

| Type | Coverage | Tools | Status |
| --- | --- | --- | --- |
| Unit tests | Money parsing/formatting and precision (including both decimal separators), currency rules, category colour contrast, date-picker conversion, backup wire format, format marker and validation, imported values brought back into range, category seeding/localization, the nine translation files against each other, month-range conversion, stats period anchoring, money widths, version parsing/ordering, the release-channel rules, the update check against a captured live payload, a failing preference read reported as an empty store, and the bounded read that keeps a foreign file from deciding how much memory the import costs | JUnit 4 (`app/src/test`) | **Implemented** — 135 tests, run with `./gradlew :app:testDebugUnitTest` |
| Data layer | DAO CRUD, statistics queries, migration validation | In-memory Room / Robolectric | Planned |
| UI tests | Entry flow, list rendering, theme/language switching | Compose UI Test | Planned (dependencies declared) |
| Integration | Full flow: entry → list → stats refresh | Compose UI Test + in-memory Room | Planned |
| Manual | Multi-size/multi-version, dark mode, dynamic color | Real devices + emulators | Ongoing |

The unit tests deliberately target logic that is pure JVM code: the functions under
test take no Android or Compose dependency, so they need neither a device nor a
shadow. Anything that requires a `Context` is kept out of this layer by design.

That design has a cost, and it is worth naming rather than leaving implied: **nothing
below is covered by a test today** — a DAO's SQL statement (including the statistics
grouping of §12.11), anything Compose actually draws or measures, and the two helpers in
`ui/LocalizedPatterns.kt` that need the Android framework. The statistics fix in
0.1.0-alpha.4 was verified by running its two queries against a real SQLite by hand, and
by the comment in `TransactionDao` that records why the grouping has to include the
direction; a regression there would be caught by a user, not by the build. Closing that
gap is what the "Data layer" and "UI tests" rows above are for.

### 11.2 CI/CD (GitHub Actions)

```yaml
# .github/workflows/android.yml
- Lint check          # blocked today — see §12.11
- Unit tests (JUnit)  # ./gradlew :app:testDebugUnitTest
- Build debug APK
- Compose UI tests (optional)
```

---

### 11.1 What beta asserts, and how it was checked

The release channels are described in §10 and in the in-app strings; the promise a **beta**
makes is that there are no *known security problems*, while crashes and other major
problems are still expected. Moving from alpha to beta is therefore a claim that has to be
earned rather than declared, and these are the checks behind it:

| Claim | How it was checked |
| --- | --- |
| The hot queries are served by indexes | The exported schema (`app/schemas/**/3.json`) was replayed into SQLite and every DAO query run under `EXPLAIN QUERY PLAN`: the month list, the month totals and the category summaries all `SEARCH` on `index_transactions_timestamp`, the per-category count uses the covering `index_transactions_categoryId`, and the only temporary sorts are over one period's rows or over the twelve-row category table |
| A failing read does not crash and does not lose settings | Preference reads go through `safeData()` (§12.14) and are covered by `SafePreferencesTest`; the first-launch seeding and currency derivation skip entirely on a read failure rather than treating it as an empty store |
| Nothing in the app trusts a remote address | The update check only ever parses the payload; the release page it opens has to be `https://github.com/…` (§12.15) |
| The ledger cannot be silently destroyed | The import still validates before it wipes, the empty-document refusal and format marker are pinned by tests, and an export is now a single transaction so it cannot reference a category missing from itself (§12.7) |
| The shipped artefact is what was measured | Release APK measured at 1,637,673 bytes at the time of writing, of which the `classes.dex` entry is 2.73 MB uncompressed, `resources.arsc` 96 KB for nine locales, and eight native libraries (four ABIs, two libraries) totalling ~60 KB — of which ~30 KB is the x86/x86_64 pair that no phone uses. R8 and resource shrinking are on; the removed-code report (`app/build/outputs/mapping/release/usage.txt`) shows no Compose tooling at all, because it is scoped to `debugImplementation` and is never on the release classpath |

What it does **not** claim: there are no instrumented or Robolectric tests (§11), so the
gestures, the haptics, rendering and the DAO SQL are verified by reading and by device
testing rather than by a test run. That gap is the largest single risk in this release and
is stated again in §12.

## 12. Notes & Risks

1. **Amount precision**: always store as integer cents (Long) and format only at display time. Formatting goes through `BigDecimal`, never `cents / 100.0`; `FormatsTest` pins a value that a `Double` would round to the wrong cent.
2. **Time zones**: grouping and statistics use the local timezone (`ZoneId.systemDefault()`). The date picker identifies a day by its **UTC midnight**, so `datePickerMillisFor` / `localDateFromPickerMillis` in `Formats.kt` are the only sanctioned conversions — handing the picker a raw local instant shifts the day.
3. **Category deletion**: any category may be deleted, including a seeded one — the built-ins are ordinary rows. A category still referenced by transactions is refused (with a message) because the RESTRICT foreign key would reject the delete anyway.
4. **Language & categories**: the built-ins are seeded **once**, at first launch, named in the device language of that moment and recorded by the `categories_seeded` flag — not by "the table is empty", so deleting every category keeps them deleted. Nothing re-localizes them afterwards: they are the user's rows from then on, and every field (name, type, colour) and the row itself are editable and deletable. The per-language tables in `DefaultCategories.kt` still have to keep the same slots in the same order (a unit test asserts it), because that is what the backup-restore re-homing and the clear-all re-seed rely on.
5. **First-launch currency**: derived once from the system language per the table in §1.2 and never auto-overwritten. Changing the app language afterwards does **not** change it — the two settings are independent on purpose. The one exception is self-healing: a stored sign that is no longer offered (from an older build) is replaced by the current default, so the amount cannot show a token the picker can no longer produce.
6. **DB migration**: schema versions 1, 2 and 3 are identical (same identity hash), so `Migration(1,2)`, `Migration(2,3)` and `Migration(1,3)` are no-ops that upgrade in place without data loss. Any future schema change must add a real migration.
7. **Data safety**: the ledger never leaves the device on its own; the requests the app can make are the update check and, when asked, the download of a release APK (§12.15), and it carries no ledger data and no identifier. Backups are local JSON files, and an export is taken in **one transaction**: read separately, the three reads can be interleaved by a write and produce a file whose transactions point at a category that is missing from its own categories, which restores into a ledger with rows quietly re-homed onto other categories. Note that `allowBackup="true"` means the *system* may include the database in a cloud backup or device transfer — the rules for that live in `res/xml/data_extraction_rules.xml` (API 31+) and `res/xml/backup_rules.xml` (API ≤ 30), and are the place to add an `<exclude>` if that is not wanted.
8. **Preference and IO failures are not fatal.** DataStore reports an unreadable settings file by throwing from its flow, and every reader here collects that flow from a view model or from composition, where an exception is a crash on every launch. Reads therefore go through `DataStore<Preferences>.safeData()` (`data/prefs/SafePreferences.kt`), which reports a failed read as empty preferences; `SafePreferencesTest` pins both halves of that rule; *writes* are wrapped where they happen and leave the stored value alone, so the UI follows the store rather than the tap. A **corrupt** file is repaired rather than survived: the store is built with a corruption handler that replaces it (`di/AppModule.kt`). The first-launch seeding deliberately does *not* treat a read failure as an empty store — it skips, because that path writes defaults for whatever it cannot find.
9. **A corrupt database is not repaired.** The preference file is replaced when corrupt, but a damaged SQLite database is not: Room would throw while opening it, and the app cannot start. Recovering automatically would mean deleting a ledger the user may not have backed up, so this is left as a known limitation rather than a silent repair. The import path is the recovery route, and the export is the insurance against it.
10. **R8/ProGuard and the backup document**: Gson keys JSON by field name, so any class it reflects over must either be kept or annotated with `@SerializedName`. R8 renamed `BackupData`'s fields to `a`/`b` in release builds, which made a release build unable to read its own export — and the import, which wipes before restoring, then destroyed all records while reporting success. `data/backup` is now both annotated and kept; `BackupCodecTest` pins the on-disk keys. Do not remove either without re-checking `app/build/outputs/mapping/release/mapping.txt`.

    The document also carries `format`, and a file that declares a higher format than this build writes is **refused**. Without it, a backup from a later build — whose records may have moved to keys this build does not know — decoded as an empty backup, and since an import deletes every table before it writes, restoring it erased the ledger and reported success. For the same reason a document that names the keys but carries no records at all is refused: an empty ledger exports to exactly that shape, but so does a document that merely mentions the keys, and clearing the records is a deliberate action of its own in Settings.
11. **Release builds are gated on lint**: `:app:assembleRelease` runs `lintVitalRelease`, and the pinned AGP 8.7.3 cannot parse a modern Android SDK `platform-tools` revision (it fails with `NumberFormatException: For input string: "37.0"` on platform-tools 37.x). Until AGP is upgraded — or an older platform-tools is installed — release builds and all lint checks fail, so `lintDebug` cannot catch manifest/resource regressions either.
12. **Window insets and the bottom bar**: the app calls `enableEdgeToEdge()` and the
    `NavigationBar` owns the bottom system inset. Two rules follow, and both are
    required for the bottom bar to look right on every Android release:
    the window must actually be edge-to-edge (otherwise the decor insets the Compose
    root *and* the bar pads itself again, which is what made the bar measure taller
    than the row it draws in), and a pager page must not reserve the bottom inset a
    second time — that is what `pageWindowInsets` is for. A page's `TopAppBar` clears
    the status bar on its own. The **keyboard** is the one inset a page has to take for
    itself: with edge-to-edge drawing the window is no longer resized for it, so the entry
    form reserves `WindowInsets.ime` (`imePadding()`) or its save action ends up behind the
    keyboard with no way to scroll it into view. `ime` already contains the navigation-bar
    area, so using it does not reserve the bar a second time.
13. **Money widths**: amounts are unsigned 32-bit cents (`Amount.MAX_CENTS` = 4,294,967,295 ≈ 42.9 million units) and the balance is signed 64-bit. Input past the ceiling is **refused** rather than truncated, and an aggregate that somehow exceeds it **saturates**, so no figure can wrap into a small number that looks like real data. Two consequences: a total at the ceiling is displayed rather than clipped (`amountTextStyle` steps the font down with the string's length, and the summary figures never wrap), and a stored token from an older build is normalised at startup. SQLite has no unsigned type, so the column stays `INTEGER`/`Long` — the range is what makes an amount u32, and no migration is involved. Typed amounts accept both `.` and `,` as the decimal separator and take the **last** one as the decimal point, which is what lets `12,50` mean 12.50 and `1.234,56` mean 1234.56: six of the nine shipped locales put a comma on the decimal key of their keyboard, and treating the comma as noise (which the field used to do) saved `12,50` as `1250.00`. A leading minus survives the field so the parser can still refuse a negative amount rather than flipping its sign.
14. **A damaged settings file must not be fatal**: the preferences DataStore is built in `di/AppModule.kt` with `ReplaceFileCorruptionHandler { emptyPreferences() }` rather than left to the `preferencesDataStore` delegate, which cannot take one. DataStore reports corruption by throwing from `data`, `data` is read on the startup path and collected in composition, and the file stays damaged — so without the handler every launch dies the same way and the only way back in is to clear the app's data from system settings. Losing the theme, language and currency choice is a much smaller loss than the app.

15. **The update check and release channels**: `UpdateConfig.GITHUB_REPOSITORY` names the published releases repository, `stephen2754/budge`, and `UpdateConfig.PLACEHOLDER` (`OWNER/REPO`) is the sentinel `isConfigured` compares against — a checkout that has not been pointed at a repository reports "not configured" and issues **no request at all**. The request is unauthenticated (`GET /repos/<repo>/releases`), so that repository has to stay public for the check to mean anything. A build's channel comes from its own `versionName` (`1.1.0-beta.2` is a beta) and cannot be changed at runtime, which is what makes the rules trustworthy: alpha is an unstable test build that may have major and security problems, beta a stable test build with no security issues but crashes still possible, a **release candidate** is the stable release with nothing changed but the version number, and stable is a release with no known problems. The rules are: a stable build only ever sees and installs stable releases, a candidate sees the candidate and the release it leads to, a beta build sees beta, candidate and stable, and an alpha build sees everything. Version ordering is semantic, so `1.0.0-beta.1 < 1.0.0-rc.1 < 1.0.0` — the candidate's rung is what makes the last step of a release testable — and each build is moved onto the next by ordinary comparison rather than a special case. A stable build is never shown a candidate: the candidate exists to find problems before the release, not to put stable users on a test stream. A failed check leaves freshness **unknown** — there is deliberately no state meaning "could not check, therefore up to date", and the rate limit on unauthenticated GitHub requests is one of the ways a check can fail without the app learning anything. A failed read of the API is not the end of the check: the release feed at `github.com/<repo>/releases.atom` is read as a fallback, because the API is metered at sixty anonymous requests an hour for everyone behind one address and is the endpoint most often interfered with, while the feed shares the host that serves the release page in a browser and is not metered at all. The feed cannot say that a release was flagged as a pre-release, so the tag suffix is the only channel signal it offers — the signal this app already treats as authoritative — and it carries notes as rendered HTML, which is why releases read from it carry none. Failures are also reported for what they are rather than as one sentence: nothing reachable ([`UpdateFailure.NETWORK`]), no answer in time ([`UpdateFailure.TIMEOUT`]), a refusal that passes on its own ([`UpdateFailure.RATE_LIMITED`]: a 429 always, a 403 only when the response itself says the quota is gone — `x-ratelimit-remaining: 0` — because GitHub sends 403 for a repository it will not serve as well, and the app does not announce a cause it has not seen), a repository that is not there ([`UpdateFailure.NOT_FOUND`]), any other status code ([`UpdateFailure.HTTP`], named with its code), and an answer that arrived but could not be read ([`UpdateFailure.PARSE`]). The last one used to be reported as a failure to connect, which is the kind of wrong answer this document keeps arguing against. The release history on the version row is filtered by the same rule, so it can never mention a version the check would refuse to offer, and **an update is downloaded and installed in the app, but only one that can be checked.** When the release carries an APK *and* the SHA-256 GitHub records for it, `selectUpdate`'s answer comes with both, and the update window offers "download and install" instead of a page. The file is streamed straight to app-private storage (`data/update/ApkDownloader.kt` — the bytes never become a string or a byte array), hashed with `sha256`, and compared against the published digest by `matchesSha256`, which treats every way of *not* knowing — no digest, an empty one, another algorithm — as a refusal rather than a pass. A mismatch deletes the file and installs nothing. Only then does `AndroidApkInstaller` hand a `FileProvider` content URI to the system installer, which asks the user to confirm; the platform also requires `REQUEST_INSTALL_PACKAGES` and the per-app "install unknown apps" grant, and the app sends the user to that setting rather than pretending it can proceed without it. Two things are deliberately *not* this app's doing: nothing is installed without the user's approval in the system dialog, and the platform's own signature check is what stops a file signed by anybody else from replacing the app at all — which is also why an update installs in place, keeping every record, rather than replacing the application. Downloaded files are deleted on the next launch, by which time a successful install has restarted the app, and are never appended to. When the answer names no APK or no digest — the release feed cannot name either — the button opens the release page instead, exactly as before. For a release page, only an `https://github.com/…` address is opened: the URL comes from a remote response, and a release list is not a place to accept an address from and pass on. Two size decisions in `CorneredAlertDialog` are the difference between a dialog and a mess. It is sized by **max** intrinsic width, not min: a paragraph's *minimum* intrinsic width is its longest single word, so sizing to that left the buttons fighting over a few centimetres — in German "Open-Source-Komponenten" squeezed "Schliessen" onto three lines. The maximum is the width the content actually wants, capped by the window. And the body takes `weight(1f, fill = false)` of the height, so a long release history scrolls inside itself instead of growing the dialog past the bottom of the screen and taking the buttons off it; `fill = false` keeps a short body short (and is also the only weighted form intrinsic measurement supports). Button labels go through `DialogButton`, which pins them to one line: a label squeezed into "Sche / liess / en" is not a label. A window whose extra action belongs in the dialog's own bottom-left corner uses `CorneredAlertDialog` (`ui/settings/SettingsScreen.kt`): Material's dialog markup is internal to the library, so the shell is rebuilt from `BasicAlertDialog` with Material 3's own container, shape, elevation, spacing and typography, and only the button row differs — two ends instead of one right-aligned cluster, because "the releases before this one" is not the same kind of action as "close". The line under the statistics date is the same idea in reverse: it is drawn whether or not there is anywhere to go back from, and only its visibility, clickability and accessibility change, so it reserves exactly its own height at any font scale instead of pushing the period selector down whenever it appears. The version window says what the installed build is — the channel's promise and that build's own record — and the earlier releases are one button away in a second window, filtered by exactly the rule above, so the two can never disagree about what exists; the shipped list is compiled into the app, so a release added to it must also be added to `Changelog.RELEASES`. **Joining the test line** is the one place a stable build is pointed at a release its own channel would not hand it: `selectBetaOffer` returns the newest *beta* strictly newer than the running stable build, and only for a stable build — a beta or alpha is already on a test channel — so the button cannot appear there. It is re-decided on every check rather than remembered, which is what makes it disappear once the stable release catches up with the betas; a failed check offers nothing, because a failed check found out nothing. Tapping it opens that beta's release page, like every other download here. That history is compiled into the app, one **one-line summary** per release, written in English and Chinese only — every other locale falls back to the English string — while the full per-release detail lives in `CHANGELOG.md`.

---

16. **A database that cannot be read is answered, not thrown.** A Room flow reports a failure by throwing from the flow, and these flows are collected in `viewModelScope` or from composition, where an exception is a crash the reader cannot get past — the app would die at the same point on every launch with no way back in. Every read that the screens collect therefore ends in `fallingBackTo(...)` (`data/repository/ReadFallback.kt`), which answers with an empty list, a zeroed summary or a null budget. That is not the same as pretending the ledger is empty: it is what the screen draws when nothing could be read, and writes still report their own failures where they happen. **A corrupt database file is still not repaired** — the app survives it and shows nothing, and the recovery route is importing a backup, because repairing silently would mean deleting a ledger the user may not have backed up.
17. **Installing an older build over a newer one is no longer fatal.** Room refuses to open a file whose version it has no path back from, and the app's own update screen opens the releases page — where every older APK is still downloadable. Versions 1, 2 and 3 declare one schema between them (one identity hash across all three exported schemas), so `MIGRATION_3_2`, `MIGRATION_2_1` and `MIGRATION_3_1` are no-ops that let the version number go down as well as up. Anything that makes the three schemas differ ends this: the reverse migrations must then be real, or removed and the downgrade left to fail loudly.
18. **An import is bounded and its values are brought into range.** The file picker will hand over anything, so the read is capped (`MAX_BACKUP_CHARS`, twenty megabytes) by the same helper the update check uses for a response body (`data/BoundedRead.kt`) — reading a foreign input whole makes the app's memory whatever that input is, and an `OutOfMemoryError` is an `Error`, which escapes every `catch (Exception)` on the path. The document is parsed once rather than twice, and `BackupData.sanitized()` puts the two values that could make the ledger disagree with itself back into range: a direction that is neither expense nor income (mapped through `TransactionType.fromValue`, the app's one rule, because the list renders a fallback while the SQL totals count only 0 and 1) and an amount outside `1..Amount.MAX_CENTS`. A day's subtotals are clamped like the month's are.
19. **Two concurrency races that shared a shape: read-then-write across a suspension point.** Category seeding keeps the count and the insert in one `@Transaction` (`CategoryDao.insertAllIfEmpty`), and so does `ensureCategoryOfType` (`getFirstByTypeOrInsert`), which the entry form can otherwise lose against itself — two coroutines reading an empty table before either inserts leave duplicate built-in categories that the once-only seed flag never repairs. The first-launch currency default is decided *inside* the write it belongs to rather than against a snapshot taken before a slow database open, because Settings can be used while that runs and a stale check would overwrite the sign the user had just chosen.
20. **Startup work cannot kill the process before the first screen.** `BudgeApplication`'s first-launch work opens the database and writes preferences on an application scope with no handler; none of it has a reader waiting on it, so a failure there (an unopenable database, a full disk) is caught and left for the next launch rather than crashing every launch while the condition lasts.
21. **Measured, and deliberately left alone.** These were found by audit and are recorded as decisions, not oversights: the `runBlocking` preference read in `MainActivity.attachBaseContext` stays, because the language has to be applied before any resource exists and caching it in memory would apply a *stale* language after a language change (the one path that triggers `recreate()`), while the warm re-reads are a dispatcher hop rather than a file read; `budgets.month` has no index and does not need one while nothing writes budgets — the first budget editor needs a real migration; the per-day-header date formatters are rebuilt when a header scrolls back in, which measures below a frame for a screenful and would need a process-wide formatter cache to improve; `.animateContentSize()` stays on the transaction rows because an edited row's amount *can* change width through `amountTextStyle`; and the `libdatastore_shared_counter.so` payload (22.9 KB across four ABIs) stays because excluding a native library DataStore may load is not worth 1.4% of the APK.

22. **A download is written under a scratch name and renamed only after its hash passes.** `UpdateFileStore` keeps two names per version: `…​.apk.part` while it is arriving, and `…​.apk` once it is verified, which is the only name the installer is ever given. Writing both attempts to one name meant a retry after a cancelled download could interleave with the abandoned writer and produce a mixture that failed its own checksum — a tampered-download report for a download that was fine. The version is in the name, which is also what makes cleanup safe: `clearStale` removes scratch files and anything **not newer** than the running build, so the file the installer is reading (the app is still the old version while it reads it) is never deleted, and the file left after a successful install is removed by the next launch.
23. **Cancelling a download has to be observed by the loop, not just by the job.** `read` blocks on a socket and `cancel()` cannot interrupt it, so the loop calls `ensureActive()` every iteration; the progress callback runs on the download's own thread and therefore writes only while the state is still `Downloading`, so a late callback cannot put a finished progress bar back on screen after the reader cancelled. Without both, Cancel kept downloading and the window froze at 100% with no way out but a second Cancel.
24. **A verified download is not thrown away on "an activity started".** `install()` returning true means only that the system installer was launched; it says nothing about whether the user installed, refused or cancelled. The ready state and the file are kept until a new download, a new offer, or the next launch — otherwise cancelling the system dialog cost another whole download.
25. **The release checklist has three traps worth naming.** A stable tag GitHub flags as a *pre-release* is read by the app as a beta, which neither a beta-and-above build nor a candidate accepts — the release becomes invisible and must be deleted and recreated. A release without an attached APK with a digest gets the release page instead of the in-app install. And an APK built from a dirty tree stamps the *previous* commit in `META-INF/version-control-info.textproto`, so it matches no commit at all: rebuild from a clean tree and check the stamp against the tag.
26. **The update directory is excluded from Android's backup** (`backup_rules.xml`, `data_extraction_rules.xml`, both destinations). A downloaded APK can be fetched again, would consume the backup quota, and is meant to be installed rather than restored. Both rules files are also covered by a test that parses them and asserts the schema the platform expects — because `lintVitalRelease` is switched off for this project (§12.11) and that class of defect has shipped here once.
27. **Two things the update path deliberately does not claim.** The SHA-256 is not an independent trust anchor: it and the asset URL arrive in the same response, so it defends against truncation, corruption, a substituted CDN copy and redirect abuse, not against a compromised release response — the platform installer's signature check is the backstop, and the app relies on it rather than duplicating it. And `REQUEST_INSTALL_PACKAGES` used for self-update is outside Google Play's permitted uses for that permission: fine for distribution through GitHub releases, a blocker if this app ever ships on Play.

## 13. Version & Build Recommendations

- **minSdk 26**, **targetSdk 35**, **compileSdk 35**
- JDK 17; Gradle 8.9 (wrapper) with AGP 8.7.3, Kotlin 2.0.21, KSP 2.0.21-1.0.27
- `libs.versions.toml` is the single source of truth for dependency versions
- R8 plus resource shrinking for release builds. Signing is **opt-in**: a git-ignored
  `keystore.properties` supplies the keystore and its passwords, and a clone without one
  builds an unsigned (uninstallable) release APK rather than failing
- `lint { checkReleaseBuilds = false }`, because AGP 8.7.3 cannot parse a `platform-tools`
  revision of `37.0` and `lintVitalRelease` would otherwise take `assembleRelease` down
  with it. Remove that block when the toolchain is upgraded
- **Version and channel live in `versionName`** (`0.1.0-alpha.1`, `1.0.0`,
  `1.1.0-beta.2`). The channel decides which releases a build may see and install, and it
  is read from the installed build at runtime — see §12.15
- Publishing a release: bump `versionCode`/`versionName`, add the entry to
  `RELEASES` in `data/update/Changelog.kt` **and** its `release_notes_*` string in
  `values/` and `values-zh/` (the other locales fall back to English), mirror it in
  `CHANGELOG.md`, then tag `v<versionName>` and publish on GitHub. A unit test fails if
  the changelog and the version name disagree
- Keep `resourceConfigurations` in `app/build.gradle.kts` in step with the `values-*/`
  folders: a locale missing from that list is silently dropped from the APK
