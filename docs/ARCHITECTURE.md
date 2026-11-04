# Budge — architecture

> What the system is, how the parts depend on each other, and which rules the code enforces.
> Written for someone about to change the visual layer without changing what the app does.

Everything below was read in the source at commit `f48a778` (`v0.1.1`). Inferences are listed in
the last section.

---

## 1. What the app is

Budge is a manual expense tracker for Android. It stores one row per money movement in a local
Room database — direction, amount in integer cents, category, note, timestamp — plus the
categories those rows point at and an optional monthly budget. It has no account, no cloud, no
ads and no analytics, and no dependency that would add any of them
(`app/build.gradle.kts:134-183`). Records leave the device only through a JSON file the user
picks, or through Android's own system backup, which `android:allowBackup="true"` permits and
whose scope is set by `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml`. Two
network operations exist, both started by the user: the update check, and the download of a
release APK. The check reads the public release list from `api.github.com` and, when that fails,
from the `releases.atom` feed on `github.com`. Neither sends ledger data or an identifier.

Single Gradle module (`:app`), Kotlin 2.0.21, Jetpack Compose with Material 3, Room, Hilt,
DataStore. minSdk 26, compileSdk and targetSdk 35, JDK 17.

---

## 2. Layers and dependency direction

| Package | Files | Role |
| --- | --- | --- |
| `model/` | `Amount`, `Budget`, `Category`, `Summary`, `Transaction`, `TransactionType` | Domain types. No Android import, no app import. |
| `data/local/` | `BudgeDatabase.kt`, `entity/*`, `dao/*` | Room entities, DAOs, database. |
| `data/repository/` | `TransactionRepository`, `CategoryRepository`, `BudgetRepository`, `BackupRepository`, `ReadFallback`, `DefaultCategories` | One repository per concern, the flow error policy, the built-in category tables. |
| `data/backup/` | `BackupCodec.kt` | JSON wire format, validation, sanitiser. |
| `data/prefs/` | `Prefs.kt`, `SafePreferences.kt` | DataStore keys, language and currency rules, the read-failure wrapper. |
| `data/update/` | `AppVersion`, `UpdateCheck`, `GithubReleaseSource`, `ApkDownloader`, `VerifiedDownload`, `UpdateFileStore`, `ApkInstaller`, `Changelog`, `OpenSourceComponent` | Version and channel rules, GitHub reader, download, hash check, installer handoff, in-app history. |
| `data/` | `BoundedRead.kt` | `readAtMost`, shared by the import and the HTTP read. |
| `di/` | `AppModule`, `DatabaseModule`, `UpdateModule` | Hilt wiring. |
| `ui/` | `navigation/`, `home/`, `entry/`, `stats/`, `settings/`, `category/`, `theme/`, `Formats.kt`, `LocalizedPatterns.kt`, `SummaryFigure.kt`, `AppLocale.kt` | Screens, view models, formatting, theme, pager shell. |
| root | `BudgeApplication.kt`, `MainActivity.kt` | Startup and the single Activity. |

```
ui ──▶ data/repository ──▶ data/local ──▶ entity
 │          │
 │          └──▶ model
 ├──▶ data/prefs        (imports nothing outside the package)
 ├──▶ data/backup ──▶ data/local/entity, model
 └──▶ data/update ──▶ data/BoundedRead.kt, R
```

`ui/` never touches a DAO or an entity: no file under `ui/` imports
`com.example.budge.data.local`. No file under `data/` imports `com.example.budge.ui`. The one
reverse-looking edge is `data/backup/BackupCodec.kt` importing the three entities, because the
backup document is the entity list. `model/` imports nothing from the app or from Android, which
is why the money and formatting tests run on a plain JVM. `data/update` imports no domain type at
all: its versions, releases and download states are its own, and `Changelog.kt` reaches
`R.string.release_notes_*` directly. `di/` is where the database, the DataStore, the HTTP client,
the downloader, the installer and the file store are wired, with one exception noted in §5:
`MainActivity` reaches `settingsDataStore` itself, before injection is available.

There is one module, so no compiler check stops a `ui/` file from importing a DAO. The rules hold
by convention, and the import graph is the whole of it. `internal` marks helpers that tests reach
and screens do not (`BoundedRead.kt:18`, `Formats.kt:157`, `GithubReleaseSource.kt:273`,
`DefaultCategories.kt:114`, `Changelog.kt:75`). A redesign may call a repository from a screen. It
may not call a DAO, and may not construct a `DataStore` or a `RoomDatabase`.

The shell is `BudgeNavGraph` (`ui/navigation/NavGraph.kt:123`): a `HorizontalPager` with three
pages (Statistics, Home, Settings) and a `NavigationBar` below it, Home in the middle and initial.
Entry and Categories are full-screen overlays drawn above the pager by `OverlayHost`, animated in
over 300 ms and out over 250 ms (`:99-115`), with their state saved by `OverlaySaver` (`:75-79`).
There is no `NavHost` and no route string.

---

## 3. Data model

### 3.1 Tables

Three entities, exported to `app/schemas/com.example.budge.data.local.BudgeDatabase/{1,2,3}.json`.
The types are the `createSql` from `3.json`.

**`transactions`** (`data/local/entity/TransactionEntity.kt:17-39`)

| Column | Type | Null | Notes |
| --- | --- | --- | --- |
| `id` | INTEGER | no | `PRIMARY KEY AUTOINCREMENT` |
| `type` | INTEGER | no | 0 expense, 1 income. No CHECK. |
| `amount` | INTEGER | no | integer cents. No CHECK. |
| `categoryId` | INTEGER | no | FK → `categories.id`, `ON DELETE RESTRICT`, `ON UPDATE NO ACTION` |
| `note` | TEXT | yes | free text |
| `timestamp` | INTEGER | no | epoch millis, the user-facing date |
| `createdAt`, `updatedAt` | INTEGER | no | epoch millis |

Indices `index_transactions_categoryId` and `index_transactions_timestamp`, both non-unique.

**`categories`** (`CategoryEntity.kt:14-24`): `id` INTEGER PK autoincrement, `name` TEXT NOT
NULL, `icon` TEXT NOT NULL (stored, never drawn), `color` INTEGER NOT NULL (ARGB as `Long`),
`type` INTEGER NOT NULL (0/1), `isDefault` INTEGER NOT NULL (`Boolean`, provenance only),
`sortOrder` INTEGER NOT NULL. No indices, no foreign keys.

**`budgets`** (`BudgetEntity.kt:17-23`): `id` INTEGER PK autoincrement, `month` INTEGER NOT NULL
(month code `yyyyMM`, e.g. `202608`), `amount` INTEGER NOT NULL (cents). No indices, nothing
enforces one row per month, and `BudgetRepository` has no writer, so this build never creates a
row and the statistics budget card never renders.

`SummaryEntities.kt` holds two projections that are not tables: `TransactionWithCategory` (an
`@Embedded` transaction plus `categoryName`, `categoryIcon`, `categoryColor`) and
`CategorySummaryEntity` (`categoryId`, `categoryName`, `categoryIcon`, `categoryColor`, `total`,
`type`).

### 3.2 DAOs

**`TransactionDao`** (`data/local/dao/TransactionDao.kt`)

| Method | Kind | Query |
| --- | --- | --- |
| `insert`, `insertAll` | suspend | `@Insert(REPLACE)`; `insert` returns the id |
| `update` | suspend | `@Update` |
| `deleteById`, `deleteAll` | suspend | by id / whole table |
| `getById` | suspend | |
| `getByDateRange` | `Flow` | `LEFT JOIN categories` with `COALESCE` on the three display columns, `timestamp >= :start AND < :end`, `ORDER BY timestamp DESC` |
| `getAllOnce` | suspend | export only |
| `getMonthlySummary` | `Flow<MonthlyTotal?>` | one pass, `SUM(CASE WHEN type = 0 …)` and `type = 1` |
| `getCategorySummaries` | `Flow<List<CategorySummaryEntity>>` | `INNER JOIN categories`, `GROUP BY c.id, t.type`, `ORDER BY total DESC` |
| `getCountByCategoryId` | suspend | |

Two properties matter to what a screen draws. The list is a `LEFT JOIN`, so a transaction whose
category row is gone still appears, with an empty name and colour 0. The breakdown is an
`INNER JOIN`, so that row appears in the list and in the totals but not in the donut. The grouping
is `c.id, t.type`, so a category re-typed after it has transactions keeps each row on the side it
was recorded on (`:105-124`, comment at `:88-104`).

**`CategoryDao`** (`data/local/dao/CategoryDao.kt`): `insert`, `insertAll` (`@Insert(REPLACE)`),
`update`, `delete`, `deleteById` (returns rows removed), `getById`, `getAllOnce`, `deleteAll`,
`getCount` — all suspend. Flows: `getByType` and `getAll`, both ordered by `sortOrder` then
`name`, `getAll` also by `type`. Two `@Transaction` helpers keep a count and an insert together:
`getFirstByTypeOrInsert` (`:79-83`) and `insertAllIfEmpty` (`:93-96`).

**`BudgetDao`** (`data/local/dao/BudgetDao.kt`): `insert`, `insertAll`, `update`, `getAllOnce`,
`deleteAll` — suspend. `getByMonth(month)` is a `Flow<BudgetEntity?>`.

### 3.3 Version and migrations

`BudgeDatabase` declares `version = 3` and `exportSchema = true`
(`data/local/BudgeDatabase.kt:24-32`). The three exported schemas carry one identity hash,
`a2e348b51e4e109c54bf133f527a271d`, and differ only in their `version` field.

`DatabaseModule.provideDatabase` (`di/DatabaseModule.kt:28-49`) builds `budge.db` and registers
six migrations: `MIGRATION_1_2`, `MIGRATION_2_3`, `MIGRATION_1_3` as anonymous objects (`:64-77`),
and `MIGRATION_3_2`, `MIGRATION_2_1`, `MIGRATION_3_1` built by `noOpMigration(from, to)`
(`:79-98`). All six bodies are empty. They exist so the version number can move in either
direction without Room refusing to open the file, which is what makes installing an older build
over a newer one survivable (`:40-48`). The reverse migrations are correct only while the three
schemas stay identical. The first real schema change has to ship genuine SQL both ways, or the
downgrade has to be allowed to fail loudly.

### 3.4 Money

Money is integer cents in a `Long`, from the input field to the `INTEGER` column and back.
`model/Amount.kt` is the single definition of the widths: `MAX_CENTS = 0xFFFF_FFFFL` =
4,294,967,295, about 42.9 million currency units (`:22`); `isValid` is `cents in 1..MAX_CENTS`
(`:25`), so zero and negatives are refused; `coerceToAmount` is `coerceIn(0L, MAX_CENTS)` (`:33`),
so aggregates saturate.

| Rule | Site |
| --- | --- |
| An amount above the ceiling is refused at input | `ui/Formats.kt:74`, `takeIf { Amount.isValid(it) }` |
| The field cannot hold more than two decimals | `sanitizeAmountInput`, `ui/Formats.kt:116-124` |
| SQL totals are clamped into the amount range | `TransactionRepository.kt:61-62`, `:86` |
| A day's subtotal is clamped the same way | `HomeScreen.kt:280-287` |
| A foreign file's amount is clamped on import | `BackupCodec.kt:135` |
| The balance is a signed `Long`, never clamped | `HomeScreen.kt:198`, `StatsScreen.kt:249` |
| Formatting goes through `BigDecimal` | `ui/Formats.kt:56` |

`formatMoney` pins grouping and the decimal separator to `Locale.US` (`Formats.kt:37-38`), so a
figure reads the same on every device, and attaches the chosen sign directly to the number. The
balance is `totalIncome - totalExpense` in `Long`. Both operands are at most 2³²−1, so the
difference fits in 64 bits and cannot wrap (`AmountTest.kt:51-58`).

---

## 4. Repositories

Four repositories, all `@Singleton` with `@Inject constructor`. A `Flow` method returns at once
and re-emits on every write. A `suspend` method performs one read or one write.

`TransactionRepository` — `Flow`: `getByDateRange`, `getMonthlySummary(yearMonth)`,
`getSummary(start, end)`, `getCategorySummaries(yearMonth)`,
`getCategorySummariesByDateRange(start, end)`. Suspend: `getById`, `insert`, `update`,
`deleteById`, `getTransactionCountForCategory`. Its companion holds
`yearMonthToRange(yearMonth)` (`:110-126`), the half-open `[start, end)` conversion in
`ZoneId.systemDefault()` that every monthly query goes through.

`CategoryRepository` — `Flow`: `getAll`, `getByType`. Suspend: `getAllOnce`, `getById`, `insert`,
`update`, `deleteById(): Int`, `getCount`, `seedDefaultsIfEmpty(locale)`,
`insertDefaultCategories(locale)`, `ensureCategoryOfType(type, locale): Long`. The seed delegates
to the transactional `insertAllIfEmpty`, so it seeds only when the table is empty.
`insertDefaultCategories` inserts unconditionally and is used by clear-all after the table has
been emptied. `ensureCategoryOfType` returns an existing category of that direction or inserts the
locale's first built-in one, in one transaction, and is the re-homing path for a restore.

`BudgetRepository` — one method, `getByMonth(month): Flow<Budget?>`. No writer. Its comment says
the writer belongs there when a screen needs one, and must look the row up first because the table
has no unique index (`:11-17`).

`BackupRepository` — three suspend methods, no flows: `export(): BackupData`,
`import(data, locale): ImportReport`, `clearAll(locale)`. It takes the `BudgeDatabase` itself
rather than DAOs, because all three operations are one transaction. `ImportReport` carries
`transactions`, `categories`, `budgets`, `reassigned`.

**Error policy.** `ReadFallback.kt:21-25` defines the one rule:

```kotlin
fun <T> Flow<T>.fallingBackTo(fallback: T): Flow<T> =
    catch { cause ->
        if (cause is CancellationException) throw cause
        emit(fallback)
    }
```

A Room flow reports a failure by throwing, and these flows are collected in `viewModelScope` or
straight from composition, where a throw is a crash on every launch. Every flow-returning read
therefore ends in `fallingBackTo`: `getByDateRange` → `emptyList()`, `getSummary` →
`MonthlySummary(0L, 0L)`, `getCategorySummariesByDateRange` → `emptyList()`,
`CategoryRepository.getAll` and `getByType` → `emptyList()`, `BudgetRepository.getByMonth` →
`null`. Cancellation is rethrown, so a cancelled collection stops.

The `suspend` reads are not wrapped by the repository. They throw, and the caller decides:
`EntryViewModel.launchGuarded` (`:200-210`), the `try` in `CategoryViewModel.delete` (`:168-185`),
the `try` in `HomeViewModel.deleteTransaction` (`:120-134`). Writes throw too, and the same
callers turn a failure into a message. An empty answer is not a claim that the ledger is empty:
`HomeUiState.transactions` is nullable so "not read yet" and "read, and there is nothing" stay
different states (`HomeViewModel.kt:38-44`).

---

## 5. Dependency injection

Three modules, all `@InstallIn(SingletonComponent::class)`.

| Type | Provided as | Scope | Module |
| --- | --- | --- | --- |
| `DataStore<Preferences>` | `settingsDataStore(context)` | `@Singleton` | `AppModule:54-58` |
| `BudgeDatabase` | `Room.databaseBuilder(…, "budge.db").addMigrations(…)` | `@Singleton` | `DatabaseModule:26-49` |
| `TransactionDao` | `database.transactionDao()` | none | `DatabaseModule:53-54` |
| `CategoryDao` | `database.categoryDao()` | none | `DatabaseModule:56-57` |
| `BudgetDao` | `database.budgetDao()` | none | `DatabaseModule:59-60` |
| `ReleaseSource` | `GithubReleaseSource` | `@Singleton` | `UpdateModule:30-32` (`@Binds`) |
| `HttpGet` | `UrlConnectionGet` | `@Singleton` | `UpdateModule:34-36` |
| `ApkDownloader` | `UrlConnectionApkDownloader` | `@Singleton` | `UpdateModule:38-40` |
| `ApkInstaller` | `AndroidApkInstaller` | `@Singleton` | `UpdateModule:42-44` |
| `UpdateFileStore` | `InternalUpdateFileStore` | `@Singleton` | `UpdateModule:46-48` |

The DAO providers carry no scope annotation. The comment above them says they are provided as
singletons, but the code is unscoped, so each injection site gets a new DAO instance. They are
thin wrappers over the shared database, so this costs an allocation and nothing else.

The repositories and view models are not in a module: `@Singleton class X @Inject constructor(…)`
and `@HiltViewModel class X @Inject constructor(…)`. `BudgeApplication` carries `@HiltAndroidApp`,
`MainActivity` carries `@AndroidEntryPoint`.

The DataStore is built by a file-level function with a process-wide `instance` behind a lock
(`di/AppModule.kt:20-45`), and Hilt hands out that same instance. The function exists because
`MainActivity.attachBaseContext` reads the stored language before injection is available
(`MainActivity.kt:115`). Both paths reach one store.

---

## 6. State and threading

| View model | State | Exposed as | Built by |
| --- | --- | --- | --- |
| `HomeViewModel` | `HomeUiState` | `StateFlow` | `combine` → `flatMapLatest` → `stateIn(WhileSubscribed(5000))` (`:64-96`) |
| `StatsViewModel` | `StatsUiState` | `StateFlow` | same shape (`:97-169`) |
| `EntryViewModel` | `EntryUiState` | `asStateFlow()` (`:77-78`) | direct updates |
| `SettingsViewModel` | `SettingsUiState` | `asStateFlow()` (`:67-68`) | DataStore mirror in `init` (`:75-89`) |
| `CategoryViewModel` | `List<Category>` and `CategoryUiState` | two `StateFlow`s (`:64-74`) | `stateIn(WhileSubscribed(5000))` plus a `MutableStateFlow` |
| `UpdateViewModel` | `status`, `download`, `betaOffer` | three `asStateFlow()` (`:125-131`) | `MutableStateFlow` |

`UpdateViewModel` also exposes plain values computed once at construction: `channel`,
`currentVersion`, `history`, `currentRelease`, `alphas`, `historyWithAlphas` (`:83-112`). They
depend only on `BuildConfig.VERSION_NAME` and the shipped `RELEASES` list, so they cannot change
while the process lives, and `SettingsScreen` reads them directly rather than as state.

Coroutines launch from `viewModelScope` in every view model, from
`CoroutineScope(SupervisorJob() + Dispatchers.IO)` in `BudgeApplication` (`:44`), and from
`rememberCoroutineScope()` for the pager animation (`NavGraph.kt:124`) and the future-time
snackbar (`EntryScreen.kt:94`). `StatsViewModel` launches nothing and is flows only.

No repository switches dispatchers. A DAO's suspend function runs on Room's own executor, and
`map`/`catch` stay where the flow emits. Explicit `Dispatchers.IO` appears six times, all for work
that is not a database query: the whole HTTP read (`GithubReleaseSource.kt:190`), the streamed
download (`ApkDownloader.kt:50`), hashing the downloaded file (`UpdateViewModel.kt:180`),
clear-all (`SettingsViewModel.kt:123`), the export encode and write (`:146`, `:148`), and the
import read and restore (`:174`, `:196`).

The rules the code follows:

- **Re-entrancy is refused before a coroutine starts.** `EntryViewModel.save` returns while
  `isSaving` (`:305-307`), so a double tap cannot insert twice. `CategoryViewModel.save` does the
  same (`:109-111`). `UpdateViewModel.checkForUpdate` returns while the status is `Checking`
  (`:243`), and `downloadUpdate` while a download is `Downloading` or `Verifying` (`:155`).
- **A one-shot read is used where a collector would miss the first emission.** A Room flow emits
  when collected and then only on writes, so a form that resets its own state cannot wait for a
  change. `EntryViewModel.init` reads `getAllOnce()` and `safeData().first()` once per open
  (`:116-117`) and keeps collectors beside them (`:124-128`).
- **One-shot reads are guarded.** `launchGuarded` runs a suspend block in `viewModelScope` and
  swallows every non-cancellation exception, leaving the form as it was (`:200-210`). A flow read
  has `fallingBackTo`. A bare suspend read has nothing, and a throw there is an unhandled
  exception in `viewModelScope`.
- **Cancellation is always rethrown**, with the `CancellationException` check first in every catch
  on a suspend path: `ReadFallback.kt:23`, `GithubReleaseSource.kt:218-219`,
  `BudgeApplication.kt:107-108`, `HomeViewModel.kt:126-127`, `EntryViewModel.kt:204-205` and
  `:343-344`, `CategoryViewModel.kt:146-147` and `:179-180`, `SettingsViewModel.kt:126-127`,
  `:155-156`, `:199-200`, `:257-258`, `UpdateViewModel.kt:282-283`.
- **A late callback cannot revive a finished state.** The progress lambda writes only while the
  state is still `Downloading` (`UpdateViewModel.kt:169-171`), because it runs on the download's
  own thread and can arrive after a cancel.
- **Cancellation is observed inside the download loop.** `read` blocks on a socket and `cancel()`
  cannot interrupt it, so the loop calls `ensureActive()` every iteration (`ApkDownloader.kt:84`).
- **Startup work cannot kill the process.** The application-scope launch catches everything except
  cancellation, so an unopenable database or a full disk is left for the next launch instead of
  crashing every launch while the condition lasts (`BudgeApplication.kt:107-111`).

---

## 7. Persistence and preferences

The Room file is `budge.db`, created by name in `DatabaseModule.kt:35` and therefore in the app's
standard `databases/` directory. The schema is exported to `app/schemas/` by the Room plugin
(`app/build.gradle.kts:17-19`). The first open happens on the application scope during startup,
not on the main thread. A damaged database file is not repaired: Room throws while opening it,
the flows answer through `fallingBackTo`, and the screens show nothing. Deleting the file would
delete a ledger the user may not have backed up, so the recovery route is importing a backup
(`ReadFallback.kt:7-16`, `DESIGN.md` §12.9 and §12.16).

One preferences store, named `settings`, built with `PreferenceDataStoreFactory` and a corruption
handler rather than the `preferencesDataStore` delegate, because the delegate cannot take one
(`di/AppModule.kt:38-45`).

| Constant | Key | Type | Values |
| --- | --- | --- | --- |
| `Prefs.LANGUAGE` | `language` | string | `"system"`, or one of `zh en fr de es ru ja it pt` |
| `Prefs.THEME` | `theme` | string | `"system"`, `"light"`, `"dark"` |
| `Prefs.CURRENCY_SYMBOL` | `currency_symbol` | string | one of `Currencies.choices` |
| `Prefs.CATEGORIES_SEEDED` | `categories_seeded` | boolean | presence is what matters |

`Currencies.choices` is nine signs in display order: `$ € ¥ £ ₽ ₹ ₩ ₺ R$` (`Prefs.kt:119-120`).
`defaultCurrencyFor(locale)` maps a language to the first-launch sign: `¥` for Chinese and
Japanese, `₽` for Russian, `€` for the listed European languages, `$` for everything else
(`Prefs.kt:142-147`).

`safeData()` (`data/prefs/SafePreferences.kt:26-29`) catches `IOException` from `data` and emits
`emptyPreferences()`. Anything that is not an `IOException` is rethrown, so a programming error is
not disguised as an empty store (`SafePreferencesTest`). Every consumer uses it: `MainActivity`,
`BudgeApplication`, `HomeViewModel`, `EntryViewModel`, `StatsViewModel`, `SettingsViewModel`.

A *corrupt* file is replaced by the handler with empty preferences (`AppModule.kt:42`). The user
loses theme, language and currency choice, which beats an app that dies on every launch. A
*failed read* on the startup path is treated differently on purpose: `BudgeApplication` returns
from its launch on an `IOException` instead of continuing with empty preferences (`:53-58`),
because the code below writes defaults for everything it cannot find and would overwrite a
currency the user had chosen. Writes are wrapped where they happen
(`SettingsViewModel.persist`, `:253-263`) and leave the stored value alone, so the control follows
the store rather than the tap.

---

## 8. Backup format

A single JSON object (`data/backup/BackupCodec.kt:29-34`):

```json
{
  "format": 1,
  "transactions": [ { "id": 5, "type": 0, "amount": 1234, "categoryId": 1,
                      "note": "lunch", "timestamp": 1700000000000,
                      "createdAt": 1, "updatedAt": 2 } ],
  "categories":  [ { "id": 1, "name": "Dining", "icon": "restaurant",
                     "color": 4292133167, "type": 0, "isDefault": true, "sortOrder": 1 } ],
  "budgets":     [ { "id": 1, "month": 202608, "amount": 200000 } ]
}
```

The records are the Room entities serialized directly, so the keys are the column names. Every
field carries `@SerializedName` and `data/backup/**` is kept whole by R8 (`proguard-rules.pro:22`).
Both exist because Gson maps JSON by field name and R8 renames fields: without them a release
build wrote `{"a":…,"b":…}` and read every backup back as empty. `format` is 1 (`:58`). A file
declaring a higher number is refused. A file with no `format` is read as 1, because files written
before the marker existed are format 1.

`decodeBackup(json)` returns `BackupData?` and returns null when the text does not parse as JSON
or is not an object (`:73-80`), when the object carries none of
`transactions`/`categories`/`budgets` (`:80`, `BACKUP_KEYS` at `:47`), when Gson cannot map it
(`:86-91`), when `format` is present and above 1 (`:93`), or when all three collections are empty
after mapping (`:102`). The last rule protects the ledger: an import deletes every table before it
writes, so a document that merely names the keys would erase everything and report success.
Clearing the records is a separate action in Settings.

`BackupData.sanitized()` (`:126-147`) brings three things into range instead of dropping rows.
`type` goes through `TransactionType.fromValue`, so an unknown direction becomes an expense, the
same rule the list already uses. `amount` is clamped into `0..Amount.MAX_CENTS`. `timestamp`,
`createdAt` and `updatedAt` are clamped into `0..MAX_PICKER_MILLIS` (4,102,444,800,000, i.e.
2100-01-01T00:00:00Z, `:155`), because Material's date picker throws on a year outside its window
while it is being composed and the app cannot produce such a row itself.

`BackupRepository.export()` runs the three `getAllOnce` reads inside one `database.withTransaction`
(`:45-52`), so a write cannot interleave and produce a file whose transactions point at a category
missing from its own category list.

`import(data, locale)` runs inside one `database.withTransaction` (`:65-123`), in this order:
delete transactions, budgets and categories — explicit deletes rather than `clearAllTables()`,
which commits a transaction of its own, and in this order because of the RESTRICT key (`:70-77`);
insert the file's categories then budgets, keeping their ids; per transaction, keep `categoryId`
when it is non-zero and present in the file, otherwise call `ensureCategoryOfType(type, locale)`
once per direction, cache the id and copy the row onto it (`:86-104`); insert the restored
transactions in one batch; `seedDefaultsIfEmpty(locale)`, because a file carrying budgets and
nothing else is legitimate and without this the app would have no category to record into
(`:110-115`).

The returned `ImportReport` becomes one of three messages in `SettingsViewModel.confirmImport`:
failed, imported with a plural count of reassigned rows, or imported (`:204-209`).

The import is two-phase in the UI. `onImportPicked` reads the picked URI through
`readAtMost(MAX_BACKUP_CHARS)` where the cap is 20 MiB (`SettingsViewModel.kt:57`, `:231-242`),
decodes it, and holds the parsed `BackupData`. `confirmImport` restores that held object, so what
the user confirmed is what is written and the bytes cannot change underneath. `clearAll(locale)` is
also one transaction: delete transactions, budgets, categories, then insert the locale's
built-ins. Preferences are untouched.

---

## 9. The update system

### 9.1 Configuration

`UpdateConfig` (`data/update/UpdateCheck.kt:11-43`): `GITHUB_REPOSITORY` is `"stephen2754/budge"`,
`PLACEHOLDER` is `"OWNER/REPO"`, `isConfigured` is true when the repository is not the placeholder
and matches `^[^/\s]+/[^/\s]+$`, `releasesUrl()` is
`https://api.github.com/repos/<repo>/releases?per_page=30`, and `releasesFeedUrl()` is
`https://github.com/<repo>/releases.atom`. While `isConfigured` is false, `checkForUpdate` sets
`NotConfigured` and makes **no request at all** (`UpdateViewModel.kt:244-247`).

### 9.2 The check

`UpdateViewModel.checkForUpdate` (`:242-290`) returns if the status is already `Checking`
(`:243`), sets `NotConfigured` when no repository is configured, then sets `Checking` and clears
`betaOffer` (`:250-251`). If the download state is not `Ready` it calls `cancelDownload()`: an
offer may change under a download in flight, but a verified file is kept (`:256`). The fetch is
mapped as `Failure(failure, statusCode)` → `Unreachable`, `Success` with no releases →
`NoReleases`, and otherwise `_betaOffer` from `selectBetaOffer`, then `selectUpdate`, cancelling a
`Ready` download whose version is no longer the offered one, then `UpToDate(current)` or
`Available(current, release)`. Any non-cancellation exception → `Unreachable(UpdateFailure.PARSE)`
(`:284-288`). A failed check never reads as up to date.

`UpdateStatus` has seven cases: `Idle`, `Checking`, `NotConfigured`, `NoReleases`,
`UpToDate(current)`, `Available(current, release)`, `Unreachable(failure, statusCode?)`
(`UpdateCheck.kt:97-139`). `UpdateFailure` distinguishes `NETWORK`, `TIMEOUT`, `RATE_LIMITED`,
`NOT_FOUND`, `HTTP`, `PARSE`, each with its own sentence in `updateStatusLabel`
(`SettingsScreen.kt:214-237`).

### 9.3 The read: API first, Atom feed second

`GithubReleaseSource.releases` runs on `Dispatchers.IO` (`:189-203`). It reads the API and returns
at once if that succeeds, so the feed is never requested in the normal case. On failure it reads
the feed. A feed with releases wins. An empty feed does not override the API's failure, because
"nothing was published" is not the same as "the API was refused". A failed feed leaves the API's
failure as the answer, because it is the more specific one.

`read()` (`:205-231`) maps `HttpResult.Failed` to its failure, a non-2xx code through
`failureForStatus`, and parses a 2xx body in `try/catch`. The catch is `Throwable`, not
`Exception`, because a parser can raise an `Error` on a body it cannot handle and an unhandled
throwable in a coroutine is a crash. `CancellationException` is rethrown first.
`failureForStatus` (`:159-169`): 2xx → null, 404 → `NOT_FOUND`, 429 → `RATE_LIMITED`, 403 with the
quota header → `RATE_LIMITED`, anything else → `HTTP`. `rateLimitExhausted(code, remaining)` is
`code == 403 && remaining == "0"` (`:148-149`), because GitHub answers 403 both for the anonymous
quota and for a repository it will not serve.

`UrlConnectionGet` (`:69-129`) is the only HTTP call: `HttpURLConnection`, `GET`, connect and read
timeouts of 20,000 ms (`:113`), `Accept: application/vnd.github+json, application/atom+xml` and
`User-Agent: Budge-Android` (`:115-118`), body read through `readAtMost(2 * 1024 * 1024)` (`:95`,
`:127`) so an oversized body becomes a parse failure rather than half a document,
`SocketTimeoutException` → `TIMEOUT` and anything else → `NETWORK` (`:137-138`), `disconnect()` in
a `finally`.

### 9.4 Parsing

`parseGithubReleases` (`:273-300`) skips drafts, tags that do not parse as a version, and releases
without `html_url`. A suffix-less tag that GitHub flags `prerelease: true` is read as `BETA`,
because it is certainly not stable (`:279-283`). The APK is the first asset whose name ends `.apk`
(case-insensitive) and that has both a non-blank `browser_download_url` and a non-blank `digest`
(`:287-290`). Without both, `assetUrl` and `digest` stay null and the release is offered as a page
instead of an in-app download.

`parseGithubFeed` (`:314-359`) refuses a body containing `<!DOCTYPE` before any parser sees it
(`:320`). The factory sets `isExpandEntityReferences = false`, tries `disallow-doctype-decl`
through `runCatching` because a parser that does not know the feature throws, and installs an
entity resolver that returns an empty document (`:322-331`). An entry's `link` is kept only if its
`href` contains `/releases/tag/`, and the tag is what follows that path (`:355-358`). Notes are
null, because the feed carries rendered HTML and the dialog is written for Markdown. The feed
cannot mention assets, so a release read from it is page-only.

### 9.5 The channel ladder

`ReleaseChannel.accepts` (`AppVersion.kt:30-36`):

| Installed build | May be offered |
| --- | --- |
| `ALPHA` | alpha, beta, rc, stable |
| `BETA` | beta, rc, stable |
| `RC` | rc, stable |
| `STABLE` | stable |

`rank` orders the rungs: alpha 0, beta 1, rc 2, stable 3 (`:42-49`). With the numeric comparison
that gives `1.0.0-alpha.2 < 1.0.0-beta.1 < 1.0.0-rc.1 < 1.0.0 < 1.0.1`, so a beta build is moved
onto the release it leads to by ordinary comparison and no special case.

A build's channel comes from its own `versionName` and cannot be changed at runtime:
`AppVersion.channelOf(BuildConfig.VERSION_NAME)` is read once at construction
(`UpdateViewModel.kt:83`). An unparseable name counts as `STABLE`, the cautious direction
(`AppVersion.kt:144`).

`AppVersion.parse` accepts `v1.2.3`, `1.2.3`, and any word suffix with an optional number
(`PATTERN`, `:96`). `alpha`, `beta` and `rc` keep their own rungs. **Any other word suffix is read
as `BETA`** (`:110-120`). A tag this build cannot parse is dropped from the list entirely, so a
build older than a new suffix would see no release using it and report itself up to date forever.
Reading an unknown suffix as a pre-release keeps it away from stable builds and visible to
everyone on a test channel. Numbers that do not fit an `Int` return null rather than throwing
(`:125-127`).

`selectUpdate` (`UpdateCheck.kt:149-155`) keeps releases the installed channel accepts **and**
strictly newer than the running build, and returns the maximum. `selectBetaOffer` (`:169-180`)
returns null unless the running build is `STABLE`, and otherwise the newest `BETA` strictly newer
than it. That is the one place a stable build is pointed at something its own channel would not
hand it, and it is an offer to open a page, never an install. It is recomputed on every check.

The in-app history uses the same filter, so the two cannot disagree: `releaseHistoryFor(channel)`
is `filterHistory(RELEASES, channel)` (`Changelog.kt:58`, `:75-81`), and `alphasFor(channel)` is
non-empty only for `BETA` (`:67-72`).

### 9.6 The download

`UpdateViewModel.downloadUpdate` (`:152-199`) requires an `Available` status and a non-null
`assetUrl`, and returns if a download is already `Downloading` or `Verifying`. It calls
`clearReady()` to delete any previous verified file, takes the scratch file for that version, and
sets `Downloading(0, 0)`. It launches on `viewModelScope` and calls
`apkDownloader.download(url, scratch, onProgress)`. An incomplete download deletes the scratch file
and sets `DownloadFailed`. Otherwise it sets `Verifying`, hashes on `Dispatchers.IO`, and compares
with `matchesSha256`. A mismatch deletes the scratch file, sets `VerificationFailed`, and installs
nothing. A successful check renames scratch to the verified name: a failed rename deletes the
scratch file and sets `DownloadFailed`, because the file could not be put where the installer can
read it. Finally it records `readyFile` and `readyVersion` and sets `Ready`.

`UrlConnectionApkDownloader.download` (`:45-99`) refuses any URL not starting with
`https://github.com/` (`:51`, `:102`), opens an `HttpsURLConnection` with
`instanceFollowRedirects = true` and 30,000 ms timeouts, refuses a non-2xx response, and checks the
host the connection **actually reached** against `github.com` and `githubusercontent.com`, matching
the bare domain or a subdomain (`isGithubHost`, `:126-129`). The original URL being `github.com`
says nothing about where the bytes came from. It refuses an announced `contentLengthLong` above
64 MiB (`MAX_APK_BYTES`, `:109`), streams through a 64 KiB buffer straight to the file — the bytes
never become a `String` or a `ByteArray` — and refuses a running total above 64 MiB, which covers a
server that announced nothing. It calls `ensureActive()` every iteration, reports
`(written, total)` after each chunk and `(0, total)` before the first, and disconnects in a
`finally`. The whole body is inside `runCatching { … }.getOrDefault(false)`.

`sha256(file)` returns lowercase hex or null when the file cannot be read
(`VerifiedDownload.kt:9-21`). `matchesSha256(published, actual)` strips a `sha256:` prefix, trims,
lowercases, and returns false for every way of not knowing: a null or blank actual hash, a null or
blank published one, or a digest in another algorithm (`:31-42`). A check that cannot be performed
is a refusal.

### 9.7 The installer handoff

`installDownloaded()` (`:232-237`) takes `readyFile` and calls `apkInstaller.install(file)`. It
does **not** clear the ready state. `install()` returning true means the system installer was
launched, nothing more: the user may cancel, or the platform may refuse.

`AndroidApkInstaller` (`ApkInstaller.kt:38-72`): `canInstallPackages()` is
`packageManager.canRequestPackageInstalls()` (`:43`). `install(file)` returns false when the file is
missing, builds a `FileProvider` URI with authority `"${context.packageName}.updates"` (`:49`), and
starts `ACTION_VIEW` with `application/vnd.android.package-archive`,
`FLAG_GRANT_READ_URI_PERMISSION` and `FLAG_ACTIVITY_NEW_TASK` (`:51-56`).
`requestInstallPermission()` opens `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` for this package
(`:59-65`). The provider is `${applicationId}.updates`, `exported="false"`,
`grantUriPermissions="true"`, with `res/xml/update_paths.xml` allowing `files-path` at `updates/`
(`AndroidManifest.xml:42-50`). The grant is per intent, for one file.

`SettingsScreen` asks `canInstallPackages()` again at the moment the install button is pressed, not
only when the dialog was drawn (`:850-857`), because the user may have just granted it in the
screen this dialog sent them to.

### 9.8 The file store and the cleanup rule

`InternalUpdateFileStore` (`UpdateFileStore.kt:43-77`) keeps files in `filesDir/updates`, two names
per version: `budge-<version>.apk.part` while a download is arriving, and `budge-<version>.apk`
once it has passed its hash. Only the second is ever given to the installer. Two writers can
therefore never share one file, which is what stops a retry after a cancel from producing a
mixture that fails its own checksum.

`clearStale(running)` (`:52-65`) removes a file when the name ends `.apk.part`, or the version
cannot be parsed out of the name, or the parsed version is `<= running`. Anything strictly newer is
kept. That is what makes it safe at startup: while the system installer is reading a verified
download, the running app is still the old build, so the file belongs to a newer version and
survives. After a successful install the app runs as that version, so the next start removes it.
It is called once, from `BudgeApplication.onCreate` (`:72`), with
`AppVersion.parse(BuildConfig.VERSION_NAME) ?: AppVersion(0, 0, 0)`.

`cancelDownload()` (`:202-211`) cancels the job, deletes `scratchFile`, clears the ready file and
sets `Idle`. The scratch and verified names differ, which is what keeps a cancel from deleting
something installable. A progress callback that arrives after the cancel is dropped by the
`Downloading` guard.

### 9.9 What the chain does not claim

The SHA-256 arrives in the same response as the asset URL, so it defends against truncation,
corruption, a substituted CDN copy and redirect abuse. It does not defend against a compromised
release response. The platform installer's signature check is the backstop, and the app relies on
it rather than duplicating it. `REQUEST_INSTALL_PACKAGES` used for self-update is outside Google
Play's permitted uses for that permission: fine for GitHub distribution, a blocker if the app ever
ships on Play.

---

## 10. Application startup

`BudgeApplication` is `@HiltAndroidApp` and injects `CategoryRepository`, the
`DataStore<Preferences>` and `UpdateFileStore` (`:33-42`). `onCreate` calls `super.onCreate()` and
launches one coroutine on `CoroutineScope(SupervisorJob() + Dispatchers.IO)` (`:44-48`). Nothing in
`onCreate` blocks. Inside the launch:

1. Read `dataStore.data.first()`. An `IOException` returns from the launch without doing anything
   else (`:53-58`), because everything below writes defaults for what it cannot find.
2. `deviceLocale()` — the device locale, or English if it cannot be read (`Prefs.kt:51-56`).
3. Inside one `try`: `fileStore.clearStale(AppVersion.parse(BuildConfig.VERSION_NAME) ?:
   AppVersion(0, 0, 0))` (`:72`); if `preferences` lacks `categoriesSeededKey`,
   `seedDefaultsIfEmpty(deviceLocale)` then write the flag (`:77-80`), because the flag and not
   "is the table empty" decides whether to seed; then currency.
4. Currency: if no symbol is stored, `dataStore.edit` writes `defaultCurrencyFor(deviceLocale)`
   after re-checking inside the edit that the key is still null. If a stored symbol is not in
   `Currencies.choices`, another `edit` replaces it only if the stored value is still the one that
   was read (`:89-106`). The re-checks exist because the snapshot was taken before a slow first
   database open, and Settings can be used while it runs.
5. `catch (e: CancellationException) { throw e }`, then `catch (_: Exception) {}`, leaving the rest
   for the next launch (`:107-111`).

The first database open happens inside step 3, on the IO dispatcher. Screens collect Room flows, so
they fill in by themselves when the rows land.

`MainActivity` is `@AndroidEntryPoint` and injects the DataStore (`:39-40`). `attachBaseContext`
(`:46-48`) wraps the base context with `updateBaseContextLocale(newBase)`, which (`:109-131`) runs
`runBlocking { settingsDataStore(context.applicationContext).safeData().first() }` inside a `try`,
falls back to `emptyPreferences()` on any exception, reads `language` and `theme` (both defaulting
to `"system"`), stores them in the `@Volatile` companion fields `appliedLanguage` and
`appliedTheme`, resolves the locale with `appLocaleFor(language)`, copies
`context.resources.configuration`, calls `setLocale`, and returns
`context.createConfigurationContext(config)`. Any exception returns the context unchanged.

The `runBlocking` is deliberate (`:97-108`). The language has to be applied before any resource is
resolved, which is earlier than any coroutine can run, and it is one small file read. Caching the
language in memory instead would apply a stale language after a language change, which is the one
path that triggers `recreate()`. The API 33+ per-app language API would remove the need for it.

`onCreate` (`:50-95`) calls `enableEdgeToEdge()` on every supported release, not only where
targetSdk 35 forces it: without it the decor insets the Compose root while
`WindowInsets.systemBars` still reports the bars, and `NavigationBar` pads itself twice. Then
`setContent`, where the theme is collected from
`dataStore.safeData().map { it[themeKey] ?: "system" }` with `collectAsState(initial =
appliedTheme)`, the language is collected the same way with `initial = appliedLanguage` so the
initial value can never disagree with what `attachBaseContext` applied,
`appLocale = remember(language) { appLocaleFor(language) }`, `LaunchedEffect(language)` calls
`activity.recreate()` when the stored language differs from the applied one and records the new one
first, and the tree is `CompositionLocalProvider(LocalAppLocale provides appLocale) {
BudgeTheme(themeMode = theme) { BudgeNavGraph() } }`.

`LocalAppLocale` is a `staticCompositionLocalOf` defaulting to the device locale
(`ui/AppLocale.kt:12`). It drives the date and time formatters in `Formats.kt` and the CLDR pattern
lookups in `LocalizedPatterns.kt`.

---

## 11. Build and release

| Item | Value | Where |
| --- | --- | --- |
| Modules | `:app` only | `settings.gradle.kts:28` |
| AGP / Kotlin / KSP | 8.7.3 / 2.0.21 / 2.0.21-1.0.27 | `gradle/libs.versions.toml` |
| Gradle | 8.9 (wrapper) | `gradle/wrapper/gradle-wrapper.properties` |
| JDK | 17, source/target/jvmTarget | `app/build.gradle.kts:106-113` |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 | `:38-43` |
| `applicationId`, `namespace` | `com.example.budge` | `:37`, `:41` |
| `versionCode` / `versionName` | 18 / `0.1.1` | `:44`, `:48` |
| Annotation processing | KSP for Room and Hilt | `:159`, `:163` |
| Room schemas | `schemaDirectory("$projectDir/schemas")` | `:17-19` |
| `buildConfig` | on, for `BuildConfig.VERSION_NAME` | `:115-120` |

There is one build type, `release` (`:79-94`). There is no `debug` block, so a debug build takes
the Android defaults. `release` sets `isMinifyEnabled = true`, `isShrinkResources = true`, and the
proguard files `proguard-android-optimize.txt` and `proguard-rules.pro`.

`keystore.properties` at the project root supplies `storeFile`, `storePassword`, `keyAlias` and
`keyPassword` (`:28-34`, `:62-77`). Both it and the keystore are git-ignored (`.gitignore`:
`*.jks`, `*.keystore`, `*.p12`, `keystore.properties`). When the file exists a `release` signing
config is created and `enableV3Signing = true` is set (`:74`). When it does not, no `signingConfig`
is assigned, the build still succeeds, and the APK comes out unsigned and therefore not
installable (`:88-93`). `enableV2Signing = true` is written alongside it (`:78`), so the
release APK verifies with both v2 and v3; v1 is left off, which is correct at minSdk 26. The comment on v3 (`:69-73`) records the reason: v3 carries a key-rotation proof, so a
compromised release key could be rotated instead of forcing every user to uninstall, which for this
app would mean losing the ledger.

`lint { checkReleaseBuilds = false }` (`:96-104`). `assembleRelease` otherwise runs
`lintVitalRelease`, and AGP 8.7.3 cannot parse a `platform-tools` revision of `37.0`: it fails with
`NumberFormatException: For input string: "37.0"` before reading a source file. The block should go
when the toolchain is upgraded.

`LOCALES = listOf("en", "zh", "fr", "de", "es", "ru", "ja", "it", "pt")` (`:23`) is added to
`resourceConfigurations` (`:56`), which keeps only those nine in `resources.arsc`. A `values-xx/`
folder not in that list is silently dropped from the APK. `values/` is English and is the default,
and holds 151 string keys against 132 in each translation, the difference being the 19
`release_notes_*` summaries.

`versionName` carries both the number and the channel, and the channel is read from the installed
string at runtime, so a build cannot promote itself.

| `versionName` | Channel | Offered |
| --- | --- | --- |
| `1.0.0` | stable | stable only |
| `1.0.0-rc.1` | release candidate | rc and stable |
| `1.1.0-beta.2` | beta | beta, rc, stable |
| `2.0.0-alpha.1` | alpha | everything |

The release routine, from `README.md:100-141` and `DESIGN.md:503-511`, checked against the code:

1. Bump `versionCode` and `versionName`.
2. Add the release to `RELEASES` (`data/update/Changelog.kt:28-49`) and its one-line
   `release_notes_*` string to `values/strings.xml` **and** `values-zh/strings.xml`. The other seven
   locales fall back to English. `UpdateCheckTest:247-257` fails if the installed `versionName` has
   no entry in its own history.
3. Tag `v<versionName>` and publish a GitHub release. The tag suffix assigns the channel, and a tag
   that is not a version is skipped entirely.
4. Do not tick "Set as a pre-release" for a stable release. A stable tag GitHub flags as a
   pre-release is read by the app as a beta, and neither a beta-and-above build nor a candidate
   accepts a beta, so the release becomes invisible and must be deleted and recreated.
5. Attach the APK and confirm GitHub shows its SHA-256. Without a digest the app offers the release
   page instead of an in-app install.
6. Rebuild from a clean tree and check the provenance stamp:
   `unzip -p app/build/outputs/apk/release/app-release.apk META-INF/version-control-info.textproto`.
   A build from a dirty tree stamps the previous commit, so the artifact matches no commit at all.
7. A stable release keeps the candidate's `versionCode`. The only intended differences between
   `0.1.0-rc.2` and `0.1.0` are the version name, the release's own history entry and its notes
   string. Do not add the stable entry to the candidate: a candidate accepts the stable release, so
   it would advertise a version that does not exist yet.

---

## 12. Tests

`app/src/test/java/com/example/budge/`, 19 files, 165 `@Test` methods, JUnit 4, all JVM. There is
no `app/src/androidTest/` directory, although the instrumentation dependencies are declared
(`app/build.gradle.kts:178-182`). The command is `./gradlew :app:testDebugUnitTest`, and
`.github/workflows/android.yml` runs exactly that on every push to `main` and every pull request.

| Area | Files | Covers |
| --- | --- | --- |
| Money and formatting | `model/AmountTest`, `ui/FormatsTest` | the 32-bit ceiling, `isValid`, saturation, the signed balance, both decimal separators, the two-decimal field, swatch contrast, release-note Markdown stripping, date-picker conversion in several zones |
| Statistics | `ui/stats/StatsPeriodTest` | the single date anchor, period navigation, month-end clamping |
| Update state machine | `ui/settings/UpdateViewModelTest` | download, verify, cancel, late progress, install failure, keeping a verified file |
| Locales | `LocaleResourcesTest` | key parity across nine locales, specifier parity, plurals, the release-note exception |
| Backup | `data/backup/BackupCodecTest`, `data/BoundedReadTest` | the wire format and its keys, refusal of non-backups, `format` handling, sanitisation, the read cap |
| Preferences | `data/prefs/PrefsTest`, `SafePreferencesTest` | first-launch currency, the language map, the sign list, `IOException` as empty preferences |
| Repositories | `CategoryRepositoryTest`, `DefaultCategoriesTest`, `TransactionRepositoryTest` | seeding once, re-homing, a seeded category being ordinary, twelve built-ins per locale in the same slots, the half-open month range |
| Update rules | `AppVersionTest`, `UpdateCheckTest`, `PublishedReleaseTest`, `GithubReleaseSourceTest`, `GithubReleaseFeedTest`, `ReleaseFetchFallbackTest`, `VerifiedDownloadTest` | parsing and ordering, unknown suffixes, the channel ladder, `selectUpdate`/`selectBetaOffer`, a captured live payload, status codes, the asset-with-hash rule, a DOCTYPE that must not expand, which failure is reported, SHA-256 against known vectors |

Fixtures: `app/src/test/resources/github-releases-0.1.0-alpha.1.json` and
`releases-feed-0.1.0-alpha.2.atom`, both kept verbatim from the live endpoints.

Nothing below the repository layer is tested. There is no in-memory Room test, no Robolectric test
and no Compose UI test, so these are verified by reading and by device testing only: every SQL
statement in the three DAOs including the statistics grouping, the six migrations, anything Compose
draws or measures (including the swipe-to-delete threshold and its haptic),
`ui/LocalizedPatterns.kt`, `BudgeApplication`'s startup order, and `MainActivity`'s blocking read.
`DESIGN.md` §11 and §11.1 state the same gap and call it the largest single risk in the build.

---

## 13. Invariants a redesign must not break

1. **Money is integer cents everywhere.** A `Long` of cents from the input field to the `INTEGER`
   column to the formatter. No `Float`, no `Double`, no `BigDecimal` in state. `formatMoney` uses
   `BigDecimal.valueOf(cents, 2)` (`ui/Formats.kt:56`), `parseAmountToCents` uses `BigDecimal` and
   `longValueExact` (`:67-79`), and `AmountTest` pins a value a `Double` would round wrongly.
2. **No data leaves the device except the two user-initiated requests.** No analytics, no crash
   reporting, no telemetry, no fonts or images fetched at runtime, no new endpoint. The manifest
   declares `INTERNET` and `REQUEST_INSTALL_PACKAGES` and nothing else (`AndroidManifest.xml:18`,
   `:25`).
3. **No new permissions.** Both existing ones belong to the update chain, and the manifest comment
   records why each is there.
4. **The hash is checked before anything is installed.** A file leaves its `.part` name only after
   `matchesSha256` returns true, and only the verified name reaches the installer
   (`UpdateViewModel.kt:179-197`). Every way of not knowing counts as a mismatch
   (`VerifiedDownload.kt:31-42`).
5. **The channel ladder stays as it is.** `accepts` and `rank` (`AppVersion.kt:30-49`) decide what
   a build may see, and the same filter drives the in-app history. The one beta a stable build can
   reach is an explicit "join the test line" action that opens a page (`selectBetaOffer`,
   `UpdateCheck.kt:169-180`).
6. **Nine locales keep identical key sets.** `values/`, `values-zh`, `values-fr`, `values-de`,
   `values-es`, `values-ru`, `values-ja`, `values-it`, `values-pt`. Each must also be in `LOCALES`
   (`app/build.gradle.kts:23`) or it is dropped from the APK. `LocaleResourcesTest` holds the
   parity.
7. **No unescaped ASCII apostrophe in a string resource.** The resource compiler rejects it and the
   build fails (`CONTRIBUTING.md:52-53`). Rephrase rather than escape. No apostrophe appears in a
   string value in any of the nine files today. The only one in each file is inside an XML comment.
8. **Counted text is a `<plurals>`.** Two exist, `stats_donut_description` and
   `settings_import_reassigned` (`values/strings.xml:37-44`). Every locale needs at least an
   `other` form, and Russian needs `one`, `few` and `many` (`LocaleResourcesTest:103-130`). A
   number interpolated into a `<string>` is the bug this prevents.
9. **Release-note summaries exist in English and Chinese only.** The 19 `release_notes_*` keys live
   in `values/` and `values-zh/` and in no other locale, and `LocaleResourcesTest:79-90` fails if a
   third locale grows one. A new release adds the pair.
10. **Portrait only.** `android:screenOrientation="portrait"` (`AndroidManifest.xml:55`), and
    `pageWindowInsets` is `WindowInsets(0, 0, 0, 0)` because there is nothing to dodge sideways
    (`ui/theme/Theme.kt:190`).
11. **The bottom system inset belongs to the `NavigationBar`.** Pages use `pageWindowInsets` and
    must not reserve it again (`ui/theme/Theme.kt:182-190`, and the comment on each screen's
    `Scaffold`). The entry form is the exception that takes `imePadding()` for itself, because
    edge-to-edge drawing no longer resizes the window for the keyboard (`EntryScreen.kt:159`).
12. **Every screen state stays representable.** `HomeUiState.transactions` is nullable, and null
    means "not read yet", which is a different thing from an empty month (`HomeViewModel.kt:38-44`,
    `HomeScreen.kt:230-253`). A redesign may change what those states look like. It may not
    collapse them into one.
13. **Strings live in resources.** No user-visible text in Kotlin. The only literals a screen draws
    are the `"--"` placeholder (`HomeScreen.kt:328`) and the `-`/`+` in front of an amount.
14. **A read that fails must not crash the app.** Flow reads keep ending in `fallingBackTo`, and any
    suspend read a screen adds gets a `try` or a `launchGuarded`.
15. **A backup that is refused must stay refused.** The validation rules in §8 and the sanitiser are
    the difference between "import failed" and "the ledger was erased and the app said it worked".

---

## Where this document could be wrong

- `budgets.month` is documented in `BudgetEntity.kt` as `yyyyMM` and used that way by
  `StatsViewModel.yearMonthToMonthCode` (`:198`), but `model/Budget.kt:3-5` describes the same
  field as "the first millisecond of the month". That comment is stale. The code is `yyyyMM`.
- The DataStore path is DataStore's own convention for `preferencesDataStoreFile("settings")`. The
  code passes only the name, so the path was not read out of a built artifact.
- Signing: `enableV2Signing` and `enableV3Signing` are both written explicitly. The built release
  APK was verified with `apksigner verify -v` (v2 true, v3 true, v1 false), so this one is
  checked rather than inferred.
- The provenance stamp is described as `README.md` describes it. The Gradle feature that writes it
  is not configured anywhere in this repository, so its presence was not verified from the build
  files.
- The downgrade migrations are described as the code and its comments intend them. That Room
  actually walks a `3 → 2` path through `addMigrations` was not executed here.
- `BudgeTheme` passes no `typography` and no `shapes` to `MaterialTheme`
  (`ui/theme/Theme.kt:164-167`), so the app renders with the Material 3 baseline type scale and
  default shapes. There is no `Type.kt` and no `Shape.kt`.
- The launcher icon is five raster WebPs, one per density. There is no `mipmap-anydpi-v26` and no
  `res/drawable/` directory.
