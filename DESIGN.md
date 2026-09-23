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
| Network Permission | `INTERNET`, for the user-initiated update check only — see §12.12 |
| minSdk | 26 (Android 8.0 Oreo) |
| targetSdk | 35 (Android 15) |
| compileSdk | 35 |

**Core value**: open and record instantly, one-second entry, clear monthly statistics, private and secure with no network access.

### 1.1 Internationalization (i18n)

- The app ships **nine languages**: Simplified Chinese, English, French, German, Spanish, Russian, Japanese, Italian and Portuguese.
- The language is **user-configurable** in Settings: **Follow System** plus those nine, each listed under its own name (defaults to Follow System). Following the system means the device language is used when the app has it, and English otherwise — including when the locale cannot be read at all.
- The choice is stored in DataStore (`language` key). It is applied in `MainActivity.attachBaseContext` so the Activity's resources are localized while `LocalContext` remains the Activity (required by Hilt's `hiltViewModel`). A `LocalAppLocale` CompositionLocal drives the date/time formatting helpers.
- All UI strings live in resource files: `values/strings.xml` (English, the default) plus one `values-<language>/strings.xml` per translation, 90 keys each. A unit test asserts the key sets and format specifiers stay in step.
- Dates use the locale's own CLDR format where a shared pattern would be wrong (the entry screen's date button); Chinese and Japanese share the year-first `2026年8月11日` style.
- Changing the language **does not** touch category names: the built-ins are seeded in the device language on first launch and are ordinary user-editable categories afterwards (see §12.4).

### 1.2 Currency Handling

- Amounts are stored internally as **Long integer cents** (e.g., `12.34` → `1234L`).
- A user-configurable currency token is stored in DataStore (`currency_symbol`). Settings offers five signs, each labelled with the **common** currencies that share it (`Currencies.labels`): `$` → `USD / CAD / AUD / NZD / HKD / SGD / TWD`, `£` → `GBP / EGP / LBP / SYP / SSP / SDG`, `¥` → `CNY / JPY`. `€` and `₽` stay single — no other currency uses the euro sign, and the ruble sign is the Russian ruble's own (Belarus writes `Br`). The cut is deliberate: currencies that merely *contain* a `$` (`R$`, `Mex$`) do not share the sign, and the sterling-pegged territory pounds are left off. An amount shows the sign alone (`¥1,234.56`); naming the currency is the picker's job, not each figure's.
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
- Statistics SQL groups by `timestamp` ranges and aggregates by `categoryId` (see the DAO queries in `TransactionDao.kt`).

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
| Home | TopAppBar (current month), monthly summary card (expense / income / balance), day-grouped transaction list, FAB add, swipe-to-delete |
| Transaction entry | Type FilterChips (expense/income), amount field with currency prefix, content-sized category chips (`FlowRow`), separate **Date** and **Time** buttons (date restricted to today & past, future times clamped), note field, save |
| Statistics | Period selector (Year / Month / Day, centered text) over a single date anchor, so switching period keeps the same date; summary card, budget progress (if set), expense & income donut charts with per-category lists |
| Category management | List + add/edit dialog (name, type, color picker) |
| Settings | Sections for Currency, Appearance (theme + language), Categories (the entry point into category management), Data (export / import / clear all), About (version + channel → release history, licences, update check) |

### 6.3 Interaction Details

- Amount input accepts digits and one decimal point, limited to two decimal places.
- Save/delete/import/export outcomes are surfaced via Snackbars.
- Swipe-to-dismiss deletes transactions (with a confirmation dialog).
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
- **A configured update source.** The check itself is implemented (channel-aware, with
  the release history and licences behind it), but `UpdateConfig.GITHUB_REPOSITORY` is
  still the placeholder `OWNER/REPO`. While it is, no request is made and the row says
  so; filling in `owner/name` is the whole of the setup. Checking any *other* host is
  not implemented (§12.12).
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
| Unit tests | Money parsing/formatting and precision, currency rules, date-picker conversion, backup wire format and validation, category seeding/localization and the per-language built-in tables, month-range conversion, stats period anchoring, money widths, version parsing/ordering and the release-channel rules | JUnit 4 (`app/src/test`) | **Implemented** — 91 tests, run with `./gradlew :app:testDebugUnitTest` |
| Data layer | DAO CRUD, statistics queries, migration validation | In-memory Room / Robolectric | Planned |
| UI tests | Entry flow, list rendering, theme/language switching | Compose UI Test | Planned (dependencies declared) |
| Integration | Full flow: entry → list → stats refresh | Compose UI Test + in-memory Room | Planned |
| Manual | Multi-size/multi-version, dark mode, dynamic color | Real devices + emulators | Ongoing |

The unit tests deliberately target logic that is pure JVM code: the functions under
test take no Android or Compose dependency, so they need neither a device nor a
shadow. Anything that requires a `Context` is kept out of this layer by design.

### 11.1 CI/CD (planned GitHub Actions)

```yaml
# .github/workflows/android.yml
- Lint check          # blocked today — see §12.9
- Unit tests (JUnit)  # ./gradlew :app:testDebugUnitTest
- Build debug APK
- Compose UI tests (optional)
```

---

## 12. Notes & Risks

1. **Amount precision**: always store as integer cents (Long) and format only at display time. Formatting goes through `BigDecimal`, never `cents / 100.0`; `FormatsTest` pins a value that a `Double` would round to the wrong cent.
2. **Time zones**: grouping and statistics use the local timezone (`ZoneId.systemDefault()`). The date picker identifies a day by its **UTC midnight**, so `datePickerMillisFor` / `localDateFromPickerMillis` in `Formats.kt` are the only sanctioned conversions — handing the picker a raw local instant shifts the day.
3. **Category deletion**: any category may be deleted, including a seeded one — the built-ins are ordinary rows. A category still referenced by transactions is refused (with a message) because the RESTRICT foreign key would reject the delete anyway.
4. **Language & categories**: the built-ins are seeded **once**, at first launch, named in the device language of that moment and recorded by the `categories_seeded` flag — not by "the table is empty", so deleting every category keeps them deleted. Nothing re-localizes them afterwards: they are the user's rows from then on, and every field (name, type, colour) and the row itself are editable and deletable. The per-language tables in `DefaultCategories.kt` still have to keep the same slots in the same order (a unit test asserts it), because that is what the backup-restore re-homing and the clear-all re-seed rely on.
5. **First-launch currency**: derived once from the system language per the table in §1.2 and never auto-overwritten. Changing the app language afterwards does **not** change it — the two settings are independent on purpose. The one exception is self-healing: a stored sign that is no longer offered (from an older build) is replaced by the current default, so the amount cannot show a token the picker can no longer produce.
6. **DB migration**: schema versions 1, 2 and 3 are identical (same identity hash), so `Migration(1,2)`, `Migration(2,3)` and `Migration(1,3)` are no-ops that upgrade in place without data loss. Any future schema change must add a real migration.
7. **Data safety**: the ledger never leaves the device on its own; the one request the app can make is the update check of §12.12, and it carries no ledger data and no identifier. Backups are local JSON files. Note that `allowBackup="true"` means the *system* may include the database in a cloud backup or device transfer — the rules for that live in `res/xml/data_extraction_rules.xml` (API 31+) and `res/xml/backup_rules.xml` (API ≤ 30), and are the place to add an `<exclude>` if that is not wanted.
8. **R8/ProGuard and the backup document**: Gson keys JSON by field name, so any class it reflects over must either be kept or annotated with `@SerializedName`. R8 renamed `BackupData`'s fields to `a`/`b` in release builds, which made a release build unable to read its own export — and the import, which wipes before restoring, then destroyed all records while reporting success. `data/backup` is now both annotated and kept; `BackupCodecTest` pins the on-disk keys. Do not remove either without re-checking `app/build/outputs/mapping/release/mapping.txt`.
9. **Release builds are gated on lint**: `:app:assembleRelease` runs `lintVitalRelease`, and the pinned AGP 8.7.3 cannot parse a modern Android SDK `platform-tools` revision (it fails with `NumberFormatException: For input string: "37.0"` on platform-tools 37.x). Until AGP is upgraded — or an older platform-tools is installed — release builds and all lint checks fail, so `lintDebug` cannot catch manifest/resource regressions either.
10. **Window insets and the bottom bar**: the app calls `enableEdgeToEdge()` and the
    `NavigationBar` owns the bottom system inset. Two rules follow, and both are
    required for the bottom bar to look right on every Android release:
    the window must actually be edge-to-edge (otherwise the decor insets the Compose
    root *and* the bar pads itself again, which is what made the bar measure taller
    than the row it draws in), and a pager page must not reserve the bottom inset a
    second time — that is what `pageWindowInsets` is for. A page's `TopAppBar` clears
    the status bar on its own.
11. **Money widths**: amounts are unsigned 32-bit cents (`Amount.MAX_CENTS` = 4,294,967,295 ≈ 42.9 million units) and the balance is signed 64-bit. Input past the ceiling is **refused** rather than truncated, and an aggregate that somehow exceeds it **saturates**, so no figure can wrap into a small number that looks like real data. Two consequences: a total at the ceiling is displayed rather than clipped (`amountTextStyle` steps the font down with the string's length, and the summary figures never wrap), and a stored token from an older build is normalised at startup. SQLite has no unsigned type, so the column stays `INTEGER`/`Long` — the range is what makes an amount u32, and no migration is involved.
12. **The update check and release channels**: `UpdateConfig.GITHUB_REPOSITORY` names the published releases repository, `stephen2754/budge`, and `UpdateConfig.PLACEHOLDER` (`OWNER/REPO`) is the sentinel `isConfigured` compares against — a checkout that has not been pointed at a repository reports "not configured" and issues **no request at all**. The request is unauthenticated (`GET /repos/<repo>/releases`), so that repository has to stay public for the check to mean anything. A build's channel comes from its own `versionName` (`1.1.0-beta.2` is a beta) and cannot be changed at runtime, which is what makes the rules trustworthy: alpha is an unstable test build that may have major and security problems, beta a stable test build with no security issues but crashes still possible, and stable a release with no known problems. The rules are: a stable build only ever sees and installs stable releases, a beta build sees beta and stable, an alpha build sees everything. Version ordering is semantic, so `1.0.0-beta.1 < 1.0.0` and a beta user is moved onto the stable release by ordinary comparison rather than a special case. A failed check leaves freshness **unknown** — there is deliberately no state meaning "could not check, therefore up to date", and the rate limit on unauthenticated GitHub requests is one of the ways a check can fail without the app learning anything. A failed read of the API is not the end of the check: the release feed at `github.com/<repo>/releases.atom` is read as a fallback, because the API is metered at sixty anonymous requests an hour for everyone behind one address and is the endpoint most often interfered with, while the feed shares the host that serves the release page in a browser and is not metered at all. The feed cannot say that a release was flagged as a pre-release, so the tag suffix is the only channel signal it offers — the signal this app already treats as authoritative — and it carries notes as rendered HTML, which is why releases read from it carry none. Failures are also reported for what they are rather than as one sentence: nothing reachable ([`UpdateFailure.NETWORK`]), no answer in time ([`UpdateFailure.TIMEOUT`]), a refusal that passes on its own ([`UpdateFailure.RATE_LIMITED`], which is what GitHub sends as 403 or 429), a repository that is not there ([`UpdateFailure.NOT_FOUND`]), any other status code ([`UpdateFailure.HTTP`], named with its code), and an answer that arrived but could not be read ([`UpdateFailure.PARSE`]). The last one used to be reported as a failure to connect, which is the kind of wrong answer this document keeps arguing against. The release history on the version row is filtered by the same rule, so it can never mention a version the check would refuse to offer, and downloads hand the release page to the browser rather than installing an APK. That history is compiled into the app, one **one-line summary** per release, written in English and Chinese only — every other locale falls back to the English string — while the full per-release detail lives in `CHANGELOG.md`.

---

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
  is read from the installed build at runtime — see §12.12
- Publishing a release: bump `versionCode`/`versionName`, add the entry to
  `RELEASES` in `data/update/Changelog.kt` **and** its `release_notes_*` string in
  `values/` and `values-zh/` (the other locales fall back to English), mirror it in
  `CHANGELOG.md`, then tag `v<versionName>` and publish on GitHub. A unit test fails if
  the changelog and the version name disagree
- Keep `resourceConfigurations` in `app/build.gradle.kts` in step with the `values-*/`
  folders: a locale missing from that list is silently dropped from the APK
