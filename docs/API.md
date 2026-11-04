# Budge — API reference for a visual redesign

Source of truth: `app/src/main/java/com/example/budge/`, 57 files, 8,506 lines, read in full. Package root
`com.example.budge`, single Gradle module `app`. Every signature below is copied from the file named above it.

How to read this:

- Tables are `Member | Signature | Notes`. The **Notes** column carries only what the signature cannot: what a
  value means, what is nullable and why, what throws, which dispatcher, what is a one-shot, what is idempotent.
- `@Composable` is shown in the signature. A composable may only be called from composition; everything else may be
  called from anywhere, subject to the dispatcher noted for it.
- Paths in headings are relative to `app/src/main/java/com/example/budge/`.
- "One-shot" = `suspend` function that returns once. "Stream" = returns `Flow`/`StateFlow` and keeps emitting.

| Layer | Files | Touches |
| --- | --- | --- |
| `model/` | 6 | nothing Android; pure Kotlin |
| `data/` | 27 | Room (`local/` 8), repositories (6), update+network (9), DataStore (`prefs/` 2), backup codec (1), one IO helper (1) |
| `di/` | 3 | Hilt modules only |
| `ui/` | 19 | Compose, plus `Context` for pickers, intents and `DateFormat` |
| root | 2 | `Application`, `Activity` |

Contents: [model](#model) · [data/local](#datalocal) · [data](#data) · [data/repository](#datarepository) ·
[data/prefs](#dataprefs) · [data/backup](#databackup) · [data/update](#dataupdate) · [di](#di) ·
[ui root](#ui) · [ui/theme](#uitheme) · [ui/navigation](#uinavigation) · [ui/home](#uihome) ·
[ui/stats](#uistats) · [ui/entry](#uientry) · [ui/category](#uicategory) · [ui/settings](#uisettings) ·
[root](#root-package) · [extension points](#extension-points-a-redesign-will-touch) ·
[not covered](#what-this-document-does-not-cover)

---

## `model/`

Pure Kotlin, no Android imports. Money is integer cents (`Long`), moments are epoch millis (`Long`). Every other
layer converts to and from these types; nothing in `model/` reads a database, a preference or a resource.

### `model/Amount.kt`

`Amount` — the one place the acceptable width of a money value is defined.

| Member | Signature | Notes |
| --- | --- | --- |
| `MAX_CENTS` | `const val MAX_CENTS: Long = 0xFFFF_FFFFL` | 4,294,967,295 cents ≈ 42.9M units. A u32 convention over a Room `INTEGER`/`Long` column — no schema enforces it. |
| `isValid` | `fun isValid(cents: Long): Boolean` | `cents in 1..MAX_CENTS`. Zero is **not** valid: the entry form refuses a zero-amount transaction. |
| `coerceToAmount` | `fun coerceToAmount(cents: Long): Long` | Saturates into `0..MAX_CENTS`. Never throws; used on every SQL sum and every per-day subtotal. A pinned total is obviously wrong, a wrapped one looks plausible. |

Note the asymmetry: totals may be 0 (`coerceToAmount`), an entered amount may not (`isValid`).

### `model/Transaction.kt`

`Transaction` — one income or expense entry, with its category display data denormalized in.

```kotlin
data class Transaction(
    val id: Long = 0,
    val type: TransactionType = TransactionType.EXPENSE,
    val amount: Long = 0L,
    val categoryId: Long = 0L,
    val categoryName: String = "",
    val categoryIcon: String = "",
    val categoryColor: Long = 0L,
    val note: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
```

- `id = 0` means "not inserted yet"; Room assigns the id.
- `categoryName`, `categoryIcon`, `categoryColor` are a join snapshot, not columns on the transaction row. They are
  filled from a `LEFT JOIN` + `COALESCE`, so a transaction whose category row is gone still renders — with an empty
  name and colour `0`. Writing a `Transaction` back does not persist them.
- `note` is `null` when there is no note; the form maps a blank field to `null` on save.
- `timestamp` is the user-facing date. `createdAt`/`updatedAt` are audit only; an edit keeps `createdAt` and moves
  `updatedAt` (`EntryViewModel.save`).
- All three timestamp defaults are evaluated **when the object is constructed**, not at insert.
- `amount` is cents. Format it in the UI with `formatMoney`; never format a raw `Long` inline.

### `model/TransactionType.kt`

`TransactionType` — money in or out, and the persisted `Int` it maps to.

```kotlin
enum class TransactionType(
    val value: Int,
) {
    EXPENSE(0),
    INCOME(1),
    ;

    companion object {
        fun fromValue(value: Int): TransactionType
    }
}
```

`fromValue` returns `EXPENSE` for any `Int` it does not recognise. That fallback is the
defined behaviour for corrupt or foreign data (see `sanitized()`), so it must not be "fixed" into a throw.

### `model/Category.kt`

`Category` — a spending/income bucket. `isDefault` is provenance, not a permission.

```kotlin
data class Category(
    val id: Long = 0,
    val name: String = "",
    val icon: String = "",
    val color: Long = 0L,
    val type: TransactionType = TransactionType.EXPENSE,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
)
```

- `color` is an ARGB `Long`; the UI wraps it in `Color(category.color)`.
- `icon` is a Material icon **name** string (`"restaurant"`, `"directions_car"`). Nothing in the app resolves it:
  every row draws `name.initialChar()` on `color` instead. It is persisted and carried, never rendered.
- `isDefault = true` marks a seeded row. Seeded rows are edited, re-typed, re-coloured and deleted exactly like
  user rows; nothing gates on the flag.
- `sortOrder` orders within a type (`CategoryDao.getByType`), then `name`.

### `model/Budget.kt`

`Budget` — a monthly spending limit.

```kotlin
data class Budget(
    val id: Long = 0,
    val month: Long = 0L,
    val amount: Long = 0L,
)
```

`month` here is the **yyyyMM code** (`202608`), matching `BudgetEntity` and `StatsViewModel.yearMonthToMonthCode`
(private). It is not an epoch timestamp, despite being a `Long`.

### `model/Summary.kt`

Two independent summaries, both fully required (no default values):

```kotlin
data class CategorySummary(
    val categoryId: Long,
    val categoryName: String,
    val categoryIcon: String,
    val categoryColor: Long,
    val total: Long,
    val type: TransactionType,
)

data class MonthlySummary(
    val totalExpense: Long,
    val totalIncome: Long,
)
```

- `CategorySummary.type` is the direction of the **transactions** in the group, not the category's current type:
  the SQL groups by `c.id, t.type`. A category re-typed after use appears once per direction.
- `MonthlySummary` carries no period. The screen knows which period it asked for; a label stored beside the numbers
  could only disagree with it.

---

## `data/local/`

Room. Three tables, one DAO each, plus two query projections that are not tables.

### `data/local/BudgeDatabase.kt`

`BudgeDatabase` — the single Room database (`budge.db`, version 3, `exportSchema = true`).

| Member | Signature | Notes |
| --- | --- | --- |
| `transactionDao` | `abstract fun transactionDao(): TransactionDao` | |
| `categoryDao` | `abstract fun categoryDao(): CategoryDao` | |
| `budgetDao` | `abstract fun budgetDao(): BudgetDao` | |

Entities: `TransactionEntity`, `CategoryEntity`, `BudgetEntity`. Versions 1, 2 and 3 are byte-identical (one
identity hash), so all registered migrations are no-ops — including the downgrades, which exist only so an older
build can open a file written by a newer one. The first real schema change must ship real migration SQL.

### `data/local/dao/TransactionDao.kt`

`TransactionDao` — CRUD plus every read the home and statistics screens use.

| Member | Signature | Notes |
| --- | --- | --- |
| `insert` | `suspend fun insert(transaction: TransactionEntity): Long` | `@Insert(onConflict = REPLACE)`; returns the new rowid. |
| `insertAll` | `suspend fun insertAll(transactions: List<TransactionEntity>)` | REPLACE, one statement per list. Backup restore. |
| `update` | `suspend fun update(transaction: TransactionEntity)` | `@Update` by primary key. A missing row affects 0 rows and does not throw. |
| `deleteById` | `suspend fun deleteById(id: Long)` | `DELETE FROM transactions WHERE id = :id`. |
| `deleteAll` | `suspend fun deleteAll()` | `DELETE FROM transactions`. |
| `getById` | `suspend fun getById(id: Long): TransactionEntity?` | `null` when absent. **No join**, so category fields are not populated. |
| `getByDateRange` | `fun getByDateRange(startTime: Long, endTime: Long): Flow<List<TransactionWithCategory>>` | Stream. Half-open range `[startTime, endTime)`, `ORDER BY timestamp DESC`. `LEFT JOIN` + `COALESCE`, so an orphaned transaction stays visible with `""`/`0`. |
| `getAllOnce` | `suspend fun getAllOnce(): List<TransactionEntity>` | One-shot, newest first. Backup export. |
| `getMonthlySummary` | `fun getMonthlySummary(startTime: Long, endTime: Long): Flow<MonthlyTotal?>` | Stream. One pass, two `SUM(CASE WHEN type = 0/1 …)`. An empty range yields one row of nulls (see `MonthlyTotal`). |
| `getCategorySummaries` | `fun getCategorySummaries(startTime: Long, endTime: Long): Flow<List<CategorySummaryEntity>>` | Stream. `INNER JOIN categories`, `GROUP BY c.id, t.type`, `ORDER BY total DESC`. Unlike the list query, rows whose category is gone are **excluded**. |
| `getCountByCategoryId` | `suspend fun getCountByCategoryId(categoryId: Long): Int` | One-shot. Guards a category delete. |

`MonthlyTotal` — also declared in this file:

```kotlin
data class MonthlyTotal(
    val totalExpense: Long?,
    val totalIncome: Long?,
)
```

Both fields are nullable because `SUM` over no rows is `NULL`. Callers must read `null` as zero;
`TransactionRepository.getSummary` does exactly that and then clamps.

### `data/local/dao/CategoryDao.kt`

`CategoryDao` — CRUD, ordering, and the two transactional seeding helpers.

| Member | Signature | Notes |
| --- | --- | --- |
| `insert` | `suspend fun insert(category: CategoryEntity): Long` | REPLACE. |
| `insertAll` | `suspend fun insertAll(categories: List<CategoryEntity>)` | REPLACE, batch. Restore and re-seed. |
| `update` | `suspend fun update(category: CategoryEntity)` | By primary key. Allowed to change `type` of a category that already has transactions. |
| `delete` | `suspend fun delete(category: CategoryEntity)` | `@Delete` by entity id. |
| `deleteById` | `suspend fun deleteById(id: Long): Int` | Returns rows removed, so a caller can tell "deleted" from "no such row". Seeded rows are deletable. |
| `getById` | `suspend fun getById(id: Long): CategoryEntity?` | |
| `getByType` | `fun getByType(type: Int): Flow<List<CategoryEntity>>` | Stream. `WHERE type = :type ORDER BY sortOrder ASC, name ASC`. |
| `getFirstByType` | `suspend fun getFirstByType(type: Int): CategoryEntity?` | Same order, `LIMIT 1`. |
| `getAll` | `fun getAll(): Flow<List<CategoryEntity>>` | Stream. `ORDER BY type ASC, sortOrder ASC, name ASC`. |
| `getAllOnce` | `suspend fun getAllOnce(): List<CategoryEntity>` | One-shot. Backup export, and the entry form's per-open read. |
| `deleteAll` | `suspend fun deleteAll()` | |
| `getCount` | `suspend fun getCount(): Int` | |
| `getFirstByTypeOrInsert` | `suspend fun getFirstByTypeOrInsert(type: Int, template: CategoryEntity): Long` | Default method, `@Transaction`. Returns the first id of that type or inserts `template`. Atomic on purpose: two reads could both see an empty table and seed duplicates the seeded-once flag never repairs. |
| `insertAllIfEmpty` | `suspend fun insertAllIfEmpty(categories: List<CategoryEntity>)` | Default method, `@Transaction`. Inserts only when `getCount() == 0`. Count and insert must not be split — "clear all" can empty the table between them. |

### `data/local/dao/BudgetDao.kt`

`BudgetDao` — one row per month by convention only.

| Member | Signature | Notes |
| --- | --- | --- |
| `insert` | `suspend fun insert(budget: BudgetEntity): Long` | REPLACE. |
| `insertAll` | `suspend fun insertAll(budgets: List<BudgetEntity>)` | REPLACE, batch. Restore. |
| `update` | `suspend fun update(budget: BudgetEntity)` | |
| `getByMonth` | `fun getByMonth(month: Long): Flow<BudgetEntity?>` | Stream, keyed by yyyyMM. Emits `null` when no row. There is **no unique index** on `month`; if two rows shared one, only the first would be read. |
| `getAllOnce` | `suspend fun getAllOnce(): List<BudgetEntity>` | One-shot, `ORDER BY month ASC`. Export. |
| `deleteAll` | `suspend fun deleteAll()` | |

### `data/local/entity/TransactionEntity.kt`

`TransactionEntity` — the `transactions` table.

```kotlin
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["categoryId"]), Index(value = ["timestamp"])],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: Int = 0,
    val amount: Long = 0L,
    val categoryId: Long = 0L,
    val note: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
```

`RESTRICT` is why a category in use cannot be deleted: the delete fails at the constraint, so the app checks the
count first and says so in a sentence instead of surfacing a driver error.

### `data/local/entity/CategoryEntity.kt`

`CategoryEntity` — the `categories` table.

```kotlin
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val icon: String = "",
    val color: Long = 0L,
    val type: Int = 0,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
)
```

### `data/local/entity/BudgetEntity.kt`

`BudgetEntity` — the `budgets` table.

```kotlin
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val month: Long = 0L,
    val amount: Long = 0L,
)
```

`month` is yyyyMM (`202608`), not epoch millis. No unique index: "one budget per month" is a writer's convention.

### `data/local/entity/SummaryEntities.kt`

Two projections, neither is a table:

```kotlin
data class TransactionWithCategory(
    @Embedded val transaction: TransactionEntity,
    val categoryName: String,
    val categoryIcon: String,
    val categoryColor: Long,
)

data class CategorySummaryEntity(
    val categoryId: Long,
    val categoryName: String,
    val categoryIcon: String,
    val categoryColor: Long,
    val total: Long,
    val type: Int,
)
```

The category fields are query aliases, not foreign keys. They are non-null in Kotlin, which is why the list query
must `COALESCE` them rather than select `c.name` directly.

---

## `data/`

### `data/BoundedRead.kt`

`readAtMost` — the bounded reader shared by the only two places that take text from outside the app.

| Member | Signature | Notes |
| --- | --- | --- |
| `readAtMost` | `internal fun Reader.readAtMost(limit: Int): String?` | Reads in 8 KB chunks and returns `null` — not a truncated string — as soon as the content would exceed `limit`. Half a backup and half a JSON document are wrong answers, not partial ones, and both callers report unreadable input as a failure. Bounding matters because an `OutOfMemoryError` is an `Error` and escapes every `catch (Exception)` on these paths. Blocking IO: call off the main thread. |

The two limits in force: 2 MB for a release response (`UrlConnectionGet`, where overflow becomes `""`), 20 MB for a
picked backup file (`SettingsViewModel`, where `null` becomes `settings_import_invalid`).

---

## `data/repository/`

The only layer the view models talk to for data. Every `Flow` exiting a repository ends in `fallingBackTo`, so a
read failure renders as an empty screen; every `suspend` function can throw and the caller decides.

### `data/repository/ReadFallback.kt`

`fallingBackTo` — the read-failure policy for every repository flow.

| Member | Signature | Notes |
| --- | --- | --- |
| `fallingBackTo` | `fun <T> Flow<T>.fallingBackTo(fallback: T): Flow<T>` | Emits `fallback` once when the upstream flow throws, then completes. `CancellationException` is rethrown — a cancelled collector is not a read failure. |

### `data/repository/TransactionRepository.kt`

`TransactionRepository` — `@Singleton`, `@Inject constructor(transactionDao: TransactionDao)`. Maps Room entities to
`Transaction` and owns the month→range conversion.

| Member | Signature | Notes |
| --- | --- | --- |
| `getByDateRange` | `fun getByDateRange(startTime: Long, endTime: Long): Flow<List<Transaction>>` | Stream, `[start, end)`, newest first. Empty list on failure. |
| `getMonthlySummary` | `fun getMonthlySummary(yearMonth: YearMonth): Flow<MonthlySummary>` | Stream. Month → range in `ZoneId.systemDefault()`. |
| `getSummary` | `fun getSummary(startTime: Long, endTime: Long): Flow<MonthlySummary>` | Stream. One SQL aggregate for any range. `null` sums read as 0, totals clamped by `Amount.coerceToAmount`. Zeros on failure. |
| `getCategorySummaries` | `fun getCategorySummaries(yearMonth: YearMonth): Flow<List<CategorySummary>>` | Stream, month-scoped. |
| `getCategorySummariesByDateRange` | `fun getCategorySummariesByDateRange(startTime: Long, endTime: Long): Flow<List<CategorySummary>>` | Stream; per-group totals clamped. Empty list on failure. |
| `getById` | `suspend fun getById(id: Long): Transaction?` | One-shot. `null` when absent. **Throws** on a read failure — no fallback on one-shots. |
| `insert` | `suspend fun insert(transaction: Transaction): Long` | One-shot; returns the new id. **Throws** (e.g. FK violation). |
| `update` | `suspend fun update(transaction: Transaction)` | One-shot. **Throws**. |
| `deleteById` | `suspend fun deleteById(id: Long)` | One-shot. **Throws**; `HomeViewModel.deleteTransaction` catches and reports. |
| `getTransactionCountForCategory` | `suspend fun getTransactionCountForCategory(categoryId: Long): Int` | One-shot. Used to refuse a category delete. |
| `yearMonthToRange` | `fun yearMonthToRange(yearMonth: YearMonth): Pair<Long, Long>` (companion) | `[first instant of the month, first instant of the next)`, system zone. Pure, no IO. |

### `data/repository/CategoryRepository.kt`

`CategoryRepository` — `@Singleton`, `@Inject constructor(categoryDao: CategoryDao)`. Owns seeding and never
re-localizes an existing row.

| Member | Signature | Notes |
| --- | --- | --- |
| `getAll` | `fun getAll(): Flow<List<Category>>` | Stream, `type, sortOrder, name` order. Empty list on failure. |
| `getAllOnce` | `suspend fun getAllOnce(): List<Category>` | One-shot. Exists because a Room flow emits on collection and then only on writes; a screen that resets its own state re-reads. **Throws**. |
| `getByType` | `fun getByType(type: TransactionType): Flow<List<Category>>` | Stream. |
| `getById` | `suspend fun getById(id: Long): Category?` | One-shot. **Throws**. |
| `insert` | `suspend fun insert(category: Category): Long` | One-shot. **Throws**. |
| `update` | `suspend fun update(category: Category)` | One-shot. May change `type` of a category that already has transactions. |
| `deleteById` | `suspend fun deleteById(id: Long): Int` | One-shot; 0 = no such row. FK `RESTRICT` makes this throw if transactions reference it. |
| `getCount` | `suspend fun getCount(): Int` | One-shot. |
| `seedDefaultsIfEmpty` | `suspend fun seedDefaultsIfEmpty(locale: Locale = Locale.getDefault())` | One transaction: inserts the locale's 12 categories only when the table is empty. Used at first launch and after a restore. |
| `insertDefaultCategories` | `suspend fun insertDefaultCategories(locale: Locale)` | Unconditional insert. "Clear all records" only, which has already emptied the table. |
| `ensureCategoryOfType` | `suspend fun ensureCategoryOfType(type: TransactionType, locale: Locale): Long` | Transactional "first of this type, or insert the locale's built-in one". Backup restore re-homes missing categories through it. The template is selected with `first { it.type == type.value }` over the locale's 12 slots, so it requires that type to exist among them. |

### `data/repository/BudgetRepository.kt`

`BudgetRepository` — `@Singleton`, `@Inject constructor(budgetDao: BudgetDao)`. Read-only today.

| Member | Signature | Notes |
| --- | --- | --- |
| `getByMonth` | `fun getByMonth(month: Long): Flow<Budget?>` | Stream, keyed by yyyyMM. Emits `null` both for "no row" and for a read failure. |

No write path exists. `Budget.toEntity()` is private and currently unused — the writer belongs here when a budget
screen arrives, and it has to look the month up first because the table has no unique index.

### `data/repository/BackupRepository.kt`

`ImportReport` (declared here) and `BackupRepository` — `@Singleton`,
`@Inject constructor(database: BudgeDatabase, categoryRepository: CategoryRepository)`. Owns the destructive paths
so the view model never sequences database work.

```kotlin
data class ImportReport(
    val transactions: Int,
    val categories: Int,
    val budgets: Int,
    val reassigned: Int,
)
```

| Member | Signature | Notes |
| --- | --- | --- |
| `export` | `suspend fun export(): BackupData` | One transaction over the three `getAllOnce` reads, so the file cannot contain a transaction pointing at a category missing from its own category list. **Throws**. |
| `import` | `suspend fun import(data: BackupData, locale: Locale): ImportReport` | One transaction. Deletes transactions → budgets → categories (that order respects `RESTRICT`), reinserts categories and budgets **with their original ids**, then transactions. A transaction whose `categoryId` is `0` or absent from the file is re-homed to `ensureCategoryOfType(type, locale)`, counted in `reassigned`, never dropped. Re-seeds defaults afterwards if the file left no categories. **Throws**; nothing is committed. |
| `clearAll` | `suspend fun clearAll(locale: Locale)` | One transaction: empties all three tables and re-seeds `locale`. Preferences are untouched. **Throws**. |

### `data/repository/DefaultCategories.kt`

`DefaultCategory` (`internal`) and `defaultCategoriesFor` — the seeded set.

```kotlin
internal data class DefaultCategory(
    val name: String,
    val icon: String,
    val color: Long,
    val type: Int,
    val sortOrder: Int,
)
```

| Member | Signature | Notes |
| --- | --- | --- |
| `defaultCategoriesFor` | `internal fun defaultCategoriesFor(locale: Locale): List<DefaultCategory>` | 12 entries: 8 expense then 4 income. Names come from a private `NAMES: Map<String, List<String>>` covering en, zh, fr, de, es, ru, ja, it, pt; an unknown language falls back to English. Order is index-aligned with the private `SLOTS` — a short name list throws rather than silently shortening the seed. |

The slots (a private `Slot(icon, color, type, sortOrder)`, one per seeded category) are:

```kotlin
// icon name, ARGB colour, type, sortOrder — eight expense slots, then four income slots
Slot("restaurant",     0xFFD32F2F, 0, 1)
Slot("directions_car", 0xFFC2185B, 0, 2)
Slot("shopping_bag",   0xFF7B1FA2, 0, 3)
Slot("movie",          0xFF303F9F, 0, 4)
Slot("favorite",       0xFF1976D2, 0, 5)
Slot("school",         0xFF0097A7, 0, 6)
Slot("home",           0xFF00796B, 0, 7)
Slot("bolt",           0xFF388E3C, 0, 8)
Slot("payments",       0xFFAFB42B, 1, 1)
Slot("savings",        0xFFFBC02D, 1, 2)
Slot("savings",        0xFFF57C00, 1, 3)
Slot("attach_money",   0xFF5D4037, 1, 4)
```

The English names, in that order: Dining, Transport, Shopping, Entertainment, Health, Education, Housing, Utilities,
Salary, Bonus, Income, Deposit. Two slots share the `savings` icon and two differ only in colour.

---

## `data/prefs/`

DataStore Preferences. One keys object, one safe-read wrapper, plus the locale and currency defaulting rules.

### `data/prefs/Prefs.kt`

`Prefs` — every key and every accepted value, so a typo is a compile error rather than a preference that reads unset.

| Member | Signature | Notes |
| --- | --- | --- |
| keys | `const val LANGUAGE = "language"`, `THEME = "theme"`, `CURRENCY_SYMBOL = "currency_symbol"`, `CATEGORIES_SEEDED = "categories_seeded"` | `CATEGORIES_SEEDED` is the once-only seed flag. It, not "is the table empty", decides whether to seed — otherwise deleting every category would bring them back. |
| language values | `FOLLOW_SYSTEM = "system"`, `CHINESE = "zh"`, `ENGLISH = "en"`, `FRENCH = "fr"`, `GERMAN = "de"`, `SPANISH = "es"`, `RUSSIAN = "ru"`, `JAPANESE = "ja"`, `ITALIAN = "it"`, `PORTUGUESE = "pt"` | |
| theme values | `LIGHT = "light"`, `DARK = "dark"` | `BudgeTheme` compares against the string literals `"dark"`/`"light"`, not these constants; they agree today. Any other value (including `"system"`) means follow the system. |
| `SUPPORTED_LANGUAGES` | `val SUPPORTED_LANGUAGES = setOf(CHINESE, ENGLISH, FRENCH, GERMAN, SPANISH, RUSSIAN, JAPANESE, ITALIAN, PORTUGUESE)` | The 9 languages the app ships; keep in step with `values-*`. |
| typed keys | `val languageKey = stringPreferencesKey(LANGUAGE)`, `themeKey = stringPreferencesKey(THEME)`, `currencySymbolKey = stringPreferencesKey(CURRENCY_SYMBOL)`, `categoriesSeededKey = booleanPreferencesKey(CATEGORIES_SEEDED)` | |

`Currencies` — the currency **signs** the picker offers. A sign is not a currency; the app never claims to know which.

| Member | Signature | Notes |
| --- | --- | --- |
| tokens | `DOLLAR = "$"`, `EURO = "€"`, `YEN_SIGN = "¥"`, `POUND = "£"`, `RUBLE = "₽"`, `RUPEE = "₹"`, `WON = "₩"`, `LIRA = "₺"`, `REAL = "R$"` | `YEN_SIGN` is shared by yuan and yen on purpose. `REAL` is a letter plus a sign and never appears alone. |
| `choices` | `val choices = listOf(DOLLAR, EURO, YEN_SIGN, POUND, RUBLE, RUPEE, WON, LIRA, REAL)` | Display order of the picker. The picker shows the token and nothing beside it. |

Top-level functions in the same file:

| Member | Signature | Notes |
| --- | --- | --- |
| `deviceLocale` | `fun deviceLocale(): Locale` | Device locale, `Locale.ENGLISH` if it cannot be read. Catches `Throwable`; never throws. |
| `supportedLocaleFor` | `fun supportedLocaleFor(locale: Locale): Locale` | `locale` if its language is in `SUPPORTED_LANGUAGES`, else `Locale.ENGLISH`. The formatting locale must fall back with the string resources or English labels sit beside Korean month names. |
| `appLocaleFor` | `fun appLocaleFor(language: String?): Locale` | Maps a stored code to a `Locale` (`"zh"`→`SIMPLIFIED_CHINESE`, `"ja"`→`JAPANESE`, `"es"`/`"ru"`/`"pt"`→`forLanguageTag`). `"system"`, `null` and unknown codes yield `supportedLocaleFor(deviceLocale())`. |
| `defaultCurrencyFor` | `fun defaultCurrencyFor(locale: Locale): String` | First-launch sign only: zh/ja → ¥, ru → ₽, a fixed set of other European languages → €, everything else (English included) → $. A stored sign is never rewritten. |

### `data/prefs/SafePreferences.kt`

`safeData` — the read-failure policy for every preference read.

| Member | Signature | Notes |
| --- | --- | --- |
| `safeData` | `fun DataStore<Preferences>.safeData(): Flow<Preferences>` | Emits `emptyPreferences()` when the read throws `IOException`; any other throwable propagates. Prevents a damaged settings file from crashing every launch on the startup path and in composition. A *corrupt* file is handled where the store is built (`AppModule`), by replacement. |

---

## `data/backup/`

### `data/backup/BackupCodec.kt`

`decodeBackup`, `BackupData`, `sanitized`, `encodeBackup` — the JSON file the user exports and imports.

```kotlin
data class BackupData(
    val transactions: List<TransactionEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
) {
    val isEmpty: Boolean
        get() = transactions.isEmpty() && categories.isEmpty() && budgets.isEmpty()
}
```

| Member | Signature | Notes |
| --- | --- | --- |
| `BACKUP_KEYS` | `val BACKUP_KEYS = setOf("transactions", "categories", "budgets")` | A file must name at least one of these keys or it is not a backup. |
| `decodeBackup` | `fun decodeBackup(json: String): BackupData?` | Never throws. `null` for: not JSON, not a JSON object, none of `BACKUP_KEYS`, `format` greater than 1, or a document that parses to zero records. Returns `data.sanitized()` otherwise. `null` is the answer the caller acts on — it wipes the database on a non-null verdict. |
| `sanitized` | `fun BackupData.sanitized(): BackupData` | Clamps each transaction: `type` through `TransactionType.fromValue` (unknown → expense), `amount` into `0..MAX_CENTS`, `timestamp`/`createdAt`/`updatedAt` into `0..4_102_444_800_000` (2100-01-01Z, the date picker's own ceiling; a value past it makes the picker throw while composing). Values are brought into range, never dropped. |
| `encodeBackup` | `fun encodeBackup(data: BackupData): String` | Writes `{"format":1,"transactions":[…],"categories":[…],"budgets":[…]}`. Entities are serialized by field name. |

Document shape: a flat JSON object, three optional arrays of entity objects, plus `format` (absent in files written
before the marker existed, and read as format 1). Transaction objects carry `id`, `type`, `amount`, `categoryId`,
`note`, `timestamp`, `createdAt`, `updatedAt`; category objects `id`, `name`, `icon`, `color`, `type`, `isDefault`,
`sortOrder`; budget objects `id`, `month`, `amount`. Every field has an explicit `@SerializedName`, because R8
renames fields in release builds and Gson maps by field name — without them a release build wrote
`{"a":…,"b":…}` and read every backup back as empty. The private `BackupDocument` is the only type that touches the
file: four nullable fields (`format`, `transactions`, `categories`, `budgets`), all `@SerializedName`.

`format` exists so a build refuses a backup written by a later one instead of reading the keys it knows, finding
them empty and wiping the ledger on that reading.

---

## `data/update/`

The update path: version rules, release reading, download, verification, install. Nothing here is a UI concern
except `UpdateStatus`, which the settings screen renders as one line plus a dialog.

### `data/update/AppVersion.kt`

`ReleaseChannel` and `AppVersion` — what a build may see, and how versions order.

```kotlin
enum class ReleaseChannel {
    ALPHA,
    BETA,
    RC,
    STABLE,
    ;

    fun accepts(candidate: ReleaseChannel): Boolean
    internal val rank: Int
}

data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val channel: ReleaseChannel = ReleaseChannel.STABLE,
    val channelNumber: Int = 0,
) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int
    val number: String
    override fun toString(): String
    companion object {
        fun parse(raw: String?): AppVersion?
        fun channelOf(versionName: String?): ReleaseChannel
    }
}
```

| Member | Signature | Notes |
| --- | --- | --- |
| `accepts` | `fun accepts(candidate: ReleaseChannel): Boolean` | ALPHA accepts everything; BETA accepts everything except ALPHA; RC accepts RC and STABLE; STABLE accepts STABLE only. The release history uses the same rule, so it cannot show a version the check would refuse. |
| `rank` | `internal val rank: Int` | alpha 0 < beta 1 < rc 2 < stable 3. Tie-break when major.minor.patch are equal. |
| `compareTo` | `override fun compareTo(other: AppVersion): Int` | Numbers first, then channel rank, then `channelNumber`. SemVer-ordering: `1.0.0-alpha.2 < 1.0.0-beta.1 < 1.0.0-rc.1 < 1.0.0 < 1.0.1`. |
| `number` | `val number: String` | `"$major.$minor.$patch"`, no suffix. |
| `toString` | `override fun toString(): String` | `1.0.0`, `1.0.0-alpha.2`, `1.0.0-beta.2`, `1.0.0-rc.1`. |
| `parse` | `fun parse(raw: String?): AppVersion?` | Accepts a leading `v`, and a missing channel number (`1.0.0-beta` == beta.0). A suffix it does not know is read as **BETA** (a pre-release, visible to beta-and-above, never to stable). Returns `null` only for text that is not a version — including digit runs too large for `Int`, which must not throw. |
| `channelOf` | `fun channelOf(versionName: String?): ReleaseChannel` | The installed build's channel from `versionName`; unparseable counts as STABLE (the direction that offers the fewest updates). |

### `data/update/UpdateCheck.kt`

`UpdateConfig`, `RemoteRelease`, `UpdateFailure`, `UpdateStatus`, and the two selection rules.

`UpdateConfig` — where releases are read from, and whether anything is read at all.

| Member | Signature | Notes |
| --- | --- | --- |
| `GITHUB_REPOSITORY` | `const val GITHUB_REPOSITORY = "stephen2754/budge"` | `owner/name`. A build that does not fill this in reports "not configured" and makes no request. |
| `isConfigured` | `val isConfigured: Boolean` | True when the value is not the internal placeholder and matches `^[^/\s]+/[^/\s]+$`. True for this checkout. |
| `releasesUrl` | `fun releasesUrl(repository: String = GITHUB_REPOSITORY): String` | `https://api.github.com/repos/$repository/releases?per_page=30`. |
| `releasesFeedUrl` | `fun releasesFeedUrl(repository: String = GITHUB_REPOSITORY): String` | `https://github.com/$repository/releases.atom`. Unmetered fallback; cannot express "pre-release". |

```kotlin
data class RemoteRelease(
    val version: AppVersion,
    val title: String?,
    val notes: String?,
    val pageUrl: String,
    val assetUrl: String? = null,
    val digest: String? = null,
)
```

`assetUrl` and `digest` are null together when the answer says nothing about assets (the Atom feed never does).
A download is offered only when both exist: an APK is only ever installed with a hash to check it against.

`UpdateFailure` — why a check could not finish. Mapped to a sentence by `SettingsScreen.updateStatusLabel`.

```kotlin
enum class UpdateFailure {
    NETWORK,        // host unreachable
    TIMEOUT,        // reached, no answer in time
    RATE_LIMITED,   // 429, or 403 with x-ratelimit-remaining: 0
    NOT_FOUND,      // 404: no such repository, or private
    HTTP,           // any other non-2xx
    PARSE,          // body was not JSON/XML this app reads
}
```

`UpdateStatus` — the outcome of the most recent check.

```kotlin
sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object NotConfigured : UpdateStatus
    data object NoReleases : UpdateStatus
    data class UpToDate(val current: AppVersion) : UpdateStatus
    data class Available(val current: AppVersion, val release: RemoteRelease) : UpdateStatus
    data class Unreachable(val failure: UpdateFailure, val statusCode: Int? = null) : UpdateStatus
}
```

`NoReleases` is deliberately not `UpToDate`: an empty repository says nothing about whether this build is current.
There is no state meaning "could not check, therefore up to date", and there should not be. `Unreachable.statusCode`
is set when a status code is what went wrong.

| Member | Signature | Notes |
| --- | --- | --- |
| `selectUpdate` | `fun selectUpdate(current: AppVersion, releases: List<RemoteRelease>): RemoteRelease?` | The newest release `current.channel.accepts` **and** `> current`; `null` if none. Pure. This is what a beta build being moved onto the stable release falls out of. |
| `selectBetaOffer` | `fun selectBetaOffer(current: AppVersion, releases: List<RemoteRelease>): RemoteRelease?` | The newest BETA newer than a **stable** build; `null` for any other channel. The one place a stable build is pointed at something its channel would not hand it (joining the test programme). |

### `data/update/Changelog.kt`

`ReleaseNotes` plus three functions over a compiled-in history.

```kotlin
data class ReleaseNotes(
    val version: AppVersion,
    @StringRes val notesRes: Int,
)
```

| Member | Signature | Notes |
| --- | --- | --- |
| `releaseHistoryFor` | `fun releaseHistoryFor(channel: ReleaseChannel): List<ReleaseNotes>` | Releases the channel accepts, newest first. |
| `alphasFor` | `fun alphasFor(channel: ReleaseChannel): List<ReleaseNotes>` | The ALPHA entries, newest first — **only** for a BETA build; empty otherwise. Keeps "the button that reveals alphas must not exist elsewhere" out of the call site. |
| `filterHistory` | `internal fun filterHistory(releases: List<ReleaseNotes>, channel: ReleaseChannel): List<ReleaseNotes>` | The filtering rule itself, split out so it is testable. |

The private `RELEASES` list holds 19 entries: `0.1.1`, `0.1.0` (stable), `0.1.0-rc.2`, `-rc.1`, six betas
(`0.1.0-beta.6` … `-beta.1`), nine alphas (`0.1.0-alpha.9` … `-alpha.1`), each mapped to
`R.string.release_notes_0_1_*`. It is compiled in, not fetched: offline-safe and unchangeable under the user.
`notesRes` is a one-line summary, and the only string in the app translated into English and Chinese only.

### `data/update/GithubReleaseSource.kt`

The release reader: one interface, one HTTP call, two parsers. `GithubReleaseSource` is
`@Singleton @Inject constructor(http: HttpGet)`; `UrlConnectionGet` is `@Singleton @Inject constructor()`.

```kotlin
interface ReleaseSource {
    suspend fun releases(repository: String): ReleaseFetch
}

sealed interface ReleaseFetch {
    data class Success(val releases: List<RemoteRelease>) : ReleaseFetch
    data class Failure(val failure: UpdateFailure, val statusCode: Int? = null) : ReleaseFetch
}

fun interface HttpGet {
    fun get(url: String): HttpResult
}

sealed interface HttpResult {
    data class Answered(
        val code: Int,
        val body: String,
        val rateLimitExhausted: Boolean = false,
    ) : HttpResult

    data class Failed(val failure: UpdateFailure) : HttpResult
}
```

| Member | Signature | Notes |
| --- | --- | --- |
| `releases` (impl) | `override suspend fun releases(repository: String): ReleaseFetch` | Switches to `Dispatchers.IO` internally, so it is safe to call from `viewModelScope`. Asks the API first; on a failed or unreadable API answer it falls back to the Atom feed. If the feed yields nothing and the API failed, the **API's** failure is returned (the more specific of the two). An empty API success is returned as-is. `CancellationException` is rethrown. |
| `UrlConnectionGet.get` | `override fun get(url: String): HttpResult` | **Blocking** (`HttpURLConnection`, 20 s connect and read). Must be called off the main thread; only `GithubReleaseSource` calls it, inside `withContext(IO)`. Sets `Accept: application/vnd.github+json, application/atom+xml` and `User-Agent: Budge-Android`. Never throws — a failure is `HttpResult.Failed`. |
| `failureFor` | `internal fun failureFor(e: Exception): UpdateFailure` | `SocketTimeoutException` → TIMEOUT, anything else → NETWORK. |
| `rateLimitExhausted` | `internal fun rateLimitExhausted(code: Int, remaining: String?): Boolean` | `code == 403 && remaining == "0"`. GitHub answers 403 both for an exhausted quota and for a repository it will not serve; only the header distinguishes them. |
| `failureForStatus` | `internal fun failureForStatus(code: Int, rateLimitExhausted: Boolean = false): UpdateFailure?` | `null` for 2xx; 404 → NOT_FOUND; 429 → RATE_LIMITED; 403 + header → RATE_LIMITED; else HTTP. |
| `parseGithubReleases` | `internal fun parseGithubReleases(json: String): List<RemoteRelease>` | Skips drafts, releases whose tag does not parse, and releases with no `html_url`. A release GitHub flags `prerelease` whose tag has no suffix becomes BETA. Picks the first asset whose name ends `.apk` (case-insensitive) **and** has both a download URL and a `digest`; otherwise leaves both null. Empty list for an empty body. **Throws** if the body is not JSON (caller maps to PARSE). |
| `parseGithubFeed` | `internal fun parseGithubFeed(xml: String): List<RemoteRelease>` | Returns an empty list immediately if the body contains `<!DOCTYPE`. Parses with entity expansion off, a DOCTYPE-refusing feature set through `runCatching`, and an entity resolver that returns empty input. Reads each `<entry>`'s `title` and its `link` whose `href` contains `/releases/tag/`; version comes from the tag suffix; `notes`, `assetUrl` and `digest` are always null. **Throws** if the body is not this XML. |

A response body larger than 2 MB is replaced with `""` rather than reported as an error, so an oversized API answer
parses as "no releases" and the feed is then tried.

Private DTOs are the only place the wire format is named: `GithubReleaseDto` (`tag_name`, `name`, `body`, `html_url`,
`draft`, `prerelease`, `assets`) and `GithubAssetDto` (`name`, `browser_download_url`, `digest`), every field
`@SerializedName` for the R8 reason above.

### `data/update/ApkDownloader.kt`

```kotlin
interface ApkDownloader {
    suspend fun download(
        url: String,
        target: File,
        onProgress: (bytes: Long, total: Long) -> Unit,
    ): Boolean
}
```

`UrlConnectionApkDownloader` — `@Inject constructor()`. One HTTPS GET streamed straight to disk.

- Returns `true` only when the whole file arrived. Any failure — including a refusal — is `false`; it never throws
  (`runCatching{…}.getOrDefault(false)`).
- `onProgress(bytes, total)` reports bytes written and the server's announced length, which is **-1** when the server
  did not say. Callers treat anything not positive as unknown. Called once with `(0, total)` before writing, then per
  buffer.
- Cancellable: `currentCoroutineContext().ensureActive()` runs each loop iteration, because a blocked socket read
  cannot be interrupted. Cancelling the coroutine is how a download is stopped.
- Refuses a URL not starting `https://github.com/`, a non-2xx response, a host after redirects that is not
  `github.com`/`githubusercontent.com` (suffix match on a dot boundary), an announced size over 64 MiB, and a
  delivered size over 64 MiB. Creates `target.parentFile` if needed.

### `data/update/ApkInstaller.kt`

```kotlin
interface ApkInstaller {
    fun canInstallPackages(): Boolean
    fun install(file: File): Boolean
    fun requestInstallPermission()
}
```

`AndroidApkInstaller` — `@Inject constructor(@ApplicationContext context: Context)`.

- `canInstallPackages()` = `packageManager.canRequestPackageInstalls()`.
- `install(file)` returns `false` when the file is missing, when `FileProvider.getUriForFile` fails, or when
  `startActivity` throws. It builds `ACTION_VIEW` with the content URI, `application/vnd.android.package-archive`,
  `FLAG_GRANT_READ_URI_PERMISSION` and `FLAG_ACTIVITY_NEW_TASK`. Authority is `"${context.packageName}.updates"`,
  matching the manifest provider. The system installer still asks the user to confirm; nothing here bypasses that.
- `requestInstallPermission()` opens `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` for this package; a failure to
  start it is swallowed.

### `data/update/UpdateFileStore.kt`

```kotlin
interface UpdateFileStore {
    fun scratch(version: AppVersion): File
    fun verified(version: AppVersion): File
    fun clearStale(running: AppVersion)
}
```

`InternalUpdateFileStore` — `@Inject constructor(@ApplicationContext context: Context)`. Files live in
`filesDir/updates/` as `budge-<version>.apk.part` (scratch, never installed) and `budge-<version>.apk` (verified).
Two names per version on purpose: a retry after a cancelled download can never interleave with the abandoned writer.

- `clearStale(running)` deletes every `.part` unconditionally, and every ready file whose parsed version is `null`
  or `<= running`. Safe at startup: while the installer is reading a download the app is still the old build, so
  that file is newer than the running version and is kept; after the install the next start removes it. Best-effort
  (`runCatching`), never throws. Idempotent.

### `data/update/VerifiedDownload.kt`

| Member | Signature | Notes |
| --- | --- | --- |
| `sha256` | `fun sha256(file: File): String?` | Lowercase hex, or `null` when the file cannot be read. Blocking file IO — `UpdateViewModel` calls it inside `withContext(Dispatchers.IO)`. |
| `matchesSha256` | `fun matchesSha256(published: String?, actual: String?): Boolean` | Accepts `published` as `sha256:<hex>` or bare hex, case-insensitive. Every way of **not knowing** is `false`: blank `actual`, blank `published`, another algorithm. "The check could not be performed" is not a pass. |

### `data/update/OpenSourceLicenses.kt`

```kotlin
data class OpenSourceComponent(
    val name: String,
    val license: String,
    val url: String,
)

val openSourceComponents: List<OpenSourceComponent>
```

Seven entries, all `Apache-2.0`, all `https://` project pages: AndroidX / Jetpack Compose, Kotlin standard library,
kotlinx.coroutines, Dagger / Hilt, Room, DataStore, Gson. Build-time-only tooling (KSP, Compose compiler) is not
listed because it is not shipped. The licence text is not bundled; each row links to the project page.

---

## `di/`

Three Hilt modules, all `@InstallIn(SingletonComponent::class)`. There are no qualifiers and no scoped components
other than the singleton.

### `di/AppModule.kt`

`settingsDataStore` (top-level) and `AppModule` — the one DataStore instance.

| Member | Signature | Notes |
| --- | --- | --- |
| `settingsDataStore` | `fun settingsDataStore(context: Context): DataStore<Preferences>` | Process-wide singleton built with a double-checked lock. Produces `files/datastore/settings.preferences_pb`. Installs `ReplaceFileCorruptionHandler { emptyPreferences() }` — a corrupt file is replaced, losing theme/language/currency, because the alternative is a crash on every launch with no way back in. Reachable without injection because `MainActivity` reads it before injection exists. |
| `provideDataStore` | `@Provides @Singleton fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences>` | Returns `settingsDataStore(context)`. |

### `di/DatabaseModule.kt`

`DatabaseModule` — the Room instance and the DAOs.

| Member | Signature | Notes |
| --- | --- | --- |
| `provideDatabase` | `@Provides @Singleton fun provideDatabase(@ApplicationContext context: Context): BudgeDatabase` | `Room.databaseBuilder(context, BudgeDatabase::class.java, "budge.db")` with six migrations: 1→2, 2→3, 1→3, and the downgrades 3→2, 2→1, 3→1. All are no-ops (versions 1–3 share one schema identity hash); the downgrades exist so installing an older build over a newer one does not crash on every launch. |
| `provideTransactionDao` | `@Provides fun provideTransactionDao(database: BudgeDatabase): TransactionDao` | |
| `provideCategoryDao` | `@Provides fun provideCategoryDao(database: BudgeDatabase): CategoryDao` | |
| `provideBudgetDao` | `@Provides fun provideBudgetDao(database: BudgeDatabase): BudgetDao` | |

The DAOs are unscoped: short-lived, cheap, tied to the database instance.

### `di/UpdateModule.kt`

`UpdateModule` — abstract class; five interface bindings, each `@Binds @Singleton`.

| Member | Signature | Notes |
| --- | --- | --- |
| `bindReleaseSource` | `abstract fun bindReleaseSource(impl: GithubReleaseSource): ReleaseSource` | |
| `bindHttpGet` | `abstract fun bindHttpGet(impl: UrlConnectionGet): HttpGet` | Lets the API→feed fallback be tested without a network. |
| `bindApkDownloader` | `abstract fun bindApkDownloader(impl: UrlConnectionApkDownloader): ApkDownloader` | |
| `bindApkInstaller` | `abstract fun bindApkInstaller(impl: AndroidApkInstaller): ApkInstaller` | |
| `bindUpdateFileStore` | `abstract fun bindUpdateFileStore(impl: InternalUpdateFileStore): UpdateFileStore` | |

---

## `ui/`

Compose. No screen constructs a repository: view models are `hiltViewModel()` defaults, and every screen takes its
callbacks from `NavGraph`.

### `ui/AppLocale.kt`

| Member | Signature | Notes |
| --- | --- | --- |
| `LocalAppLocale` | `val LocalAppLocale = staticCompositionLocalOf { Locale.getDefault() }` | The locale the app is rendering in, which may differ from the device's. Provided once in `MainActivity`; read by every formatter in `Formats`/`LocalizedPatterns`. Static: changing it recomposes everything below, which is correct because it is changed by recreating the Activity. |

### `ui/Formats.kt`

No type named `Formats` — the file is a set of top-level functions shared by every screen. Everything here is plain
Kotlin except the `@Composable` formatters, and the file deliberately has no Android framework dependency (that is
why `localizedPattern` lives next door).

| Member | Signature | Notes |
| --- | --- | --- |
| `formatMoney` | `fun formatMoney(amountCents: Long, symbol: String = Currencies.DOLLAR): String` | `SYMBOL1,234.56`. Grouping and decimal separator pinned to `Locale.US` so a figure reads identically on every device; the symbol is prefixed with no space. Formats via `BigDecimal.valueOf(cents, 2)`, never through `Double`. Thread-local `DecimalFormat`. |
| `parseAmountToCents` | `fun parseAmountToCents(text: String): Long?` | `null` for empty, malformed, ≤ 0, longer than a `Long`, or above `Amount.MAX_CENTS`. Accepts `,` or `.` as the decimal mark (last one wins); rounds HALF_UP at the cent. |
| `sanitizeAmountInput` | `fun sanitizeAmountInput(input: String): String` | Keeps digits, a leading `-` and one `.`, truncating the fraction to 2 digits. This is what the amount field stores on each keystroke, so the user cannot type what the parser rejects. |
| `amountTextStyle` | `@Composable fun amountTextStyle(text: String): TextStyle` | Length-driven: ≤ 12 chars `headlineSmall`, ≤ 18 `titleMedium`, else `bodyMedium`. Counts characters of the *formatted* string. |
| `centsToEditableAmount` | `fun centsToEditableAmount(amountCents: Long): String` | `"12.34"` — plain, no grouping, no symbol. Used to fill the edit field. |
| `initialChar` | `fun String.initialChar(): String` | First code point as a `String`; `""` for empty. Never splits a surrogate pair — this is the category avatar glyph. |
| `relativeLuminance` | `internal fun relativeLuminance(argb: Long): Double` | WCAG relative luminance, 0.0–1.0. Hand-written so it is JVM-testable. |
| `contrastRatio` | `internal fun contrastRatio(first: Long, second: Long): Double` | 1.0 identical, 21.0 black on white. |
| `categoryInitialColor` | `fun categoryInitialColor(background: Long): Long` | White or black, whichever contrasts more with the swatch. |
| `WHITE`, `BLACK` | `internal const val WHITE = 0xFFFFFFFFL`, `BLACK = 0xFF000000L` | ARGB longs for the two candidates above. |
| `releaseNotesText` | `fun releaseNotesText(markdown: String): String` | Strips the Markdown a GitHub release body actually uses: headings, bullets → `• `, horizontal rules, links → text, code spans, bold, italic; collapses 3+ newlines and trims. Unrecognised constructs are left alone. |
| `datePickerMillisFor` | `fun datePickerMillisFor(date: LocalDate): Long` | That day's **UTC** midnight — the only value `DatePickerState` understands. |
| `localDateFromPickerMillis` | `fun localDateFromPickerMillis(pickerMillis: Long): LocalDate` | Inverse of the above; reads in UTC. |
| `applyDateToTimestamp` | `fun applyDateToTimestamp(pickerMillis: Long, timestamp: Long, zone: ZoneId): Long` | Changes the date, keeps the wall-clock time already chosen. |
| `applyTimeToTimestamp` | `fun applyTimeToTimestamp(hour: Int, minute: Int, timestamp: Long, zone: ZoneId): Long` | Changes the time, keeps the date. |
| `formatMonthYear` | `@Composable fun formatMonthYear(yearMonth: YearMonth): String` | `"2026年8月"` for zh/ja, otherwise the locale's `yMMMM`. |
| `formatDayHeader` | `@Composable fun formatDayHeader(date: LocalDate): String` | `"8月11日 周二"` for zh/ja, otherwise the locale's `EEEMMMd`. |
| `formatShortDate` | `@Composable fun formatShortDate(timestamp: Long): String` | Locale `FormatStyle.MEDIUM` in the system zone. Used on the entry form's date button. |
| `formatClockTime` | `@Composable fun formatClockTime(timestamp: Long): String` | Locale 12/24-hour pattern in the system zone. |
| `formatYear` | `@Composable fun formatYear(date: LocalDate): String` | `"2026年"` for zh/ja, otherwise `yyyy`. |
| `formatDayMonthYear` | `@Composable fun formatDayMonthYear(date: LocalDate): String` | `"2026年8月11日"` for zh/ja, otherwise the locale's `yMMMMd`. |

Private helpers in the same file: `normalizeDecimalSeparator`, `cjkStyle`, `pattern`, `localizedDate`, and the
Markdown regexes. All formatters cache their `DateTimeFormatter` per locale with `remember`.

### `ui/LocalizedPatterns.kt`

The one formatting helper that needs the Android framework, kept apart so `Formats.kt` stays plain Kotlin.

| Member | Signature | Notes |
| --- | --- | --- |
| `localizedPattern` | `@Composable fun localizedPattern(skeleton: String, fallback: String): String` | `DateFormat.getBestDateTimePattern(locale, skeleton)`, or `fallback` when the platform returns nothing usable or throws. Asked of the platform rather than hard-coded so French gets `d MMMM yyyy` and 12-hour locales get a 12-hour clock. |
| `uses24HourClock` | `@Composable fun uses24HourClock(): Boolean` | `DateFormat.is24HourFormat(context)`, remembered per context. Drives the entry screen's time picker. |

### `ui/SummaryFigure.kt`

| Member | Signature | Notes |
| --- | --- | --- |
| `SummaryFigure` | `@Composable fun SummaryFigure(label: String, amount: String, color: Color, horizontalAlignment: Alignment.Horizontal, modifier: Modifier = Modifier)` | A label over a money figure, both single-line. `amount` is already formatted; the style shrinks with its length via `amountTextStyle`. `Modifier` is last and defaulted — callers pass `Modifier.weight(1f)` so two figures share a `Row`. `color` applies to the amount only; the label is always `onSurfaceVariant`. |

---

## `ui/theme/`

### `ui/theme/Color.kt`

No type. 35 colour roles, each declared twice as `val md_theme_light_<role> = Color(0xFF…)` and
`val md_theme_dark_<role> = Color(0xFF…)`, holding the Material 3 baseline (purple seed) palette, plus two semantic
income colours:

| Roles | |
| --- | --- |
| `primary`, `onPrimary`, `primaryContainer`, `onPrimaryContainer` | `secondary`, `onSecondary`, `secondaryContainer`, `onSecondaryContainer` |
| `tertiary`, `onTertiary`, `tertiaryContainer`, `onTertiaryContainer` | `error`, `onError`, `errorContainer`, `onErrorContainer` |
| `background`, `onBackground`, `surface`, `onSurface` | `surfaceVariant`, `onSurfaceVariant`, `outline`, `outlineVariant` |
| `inverseSurface`, `inverseOnSurface`, `inversePrimary`, `surfaceTint` | `surfaceDim`, `surfaceBright` |
| `surfaceContainerLowest`, `surfaceContainerLow`, `surfaceContainer` | `surfaceContainerHigh`, `surfaceContainerHighest` |
| `md_theme_light_income = Color(0xFF2E7D32)` | `md_theme_dark_income = Color(0xFF81C784)` |

The income green has no Material role. It exists because the literal had been copied six times, one of them a light
green that measured ≈1.9:1 on a light surface; the light-theme value is now dark enough to read, the dark-theme one
a lighter tint.

### `ui/theme/Theme.kt`

| Member | Signature | Notes |
| --- | --- | --- |
| `BudgeTheme` | `@Composable fun BudgeTheme(themeMode: String = "system", dynamicColor: Boolean = true, content: @Composable () -> Unit)` | `themeMode` is compared against the literals `"dark"` and `"light"`; anything else follows `isSystemInDarkTheme()`. With `dynamicColor` and API ≥ 31 it uses the wallpaper scheme, else `DarkColorScheme`/`LightColorScheme` built from `Color.kt`. A `SideEffect` paints `statusBarColor` on API < 35 and always sets light/dark status- and navigation-bar icon appearance (the app can force a theme that disagrees with the system). The cast to `Activity` is checked, so previews are safe. Typography is Material's default scale. |
| `LocalIsDarkTheme` | `val LocalIsDarkTheme = staticCompositionLocalOf { false }` | Provided by `BudgeTheme` around `MaterialTheme`; lets semantic colours follow the active scheme. |
| `incomeColor` | `@Composable fun incomeColor(): Color` | `md_theme_dark_income` when `LocalIsDarkTheme` is true, else `md_theme_light_income`. Call inside composition only. |
| `pageWindowInsets` | `val pageWindowInsets: WindowInsets = WindowInsets(0, 0, 0, 0)` | The value every screen passes as `contentWindowInsets`. Empty on purpose: `BudgeNavGraph`'s `NavigationBar` owns the bottom inset, each page's top bar owns the status bar, and the app is portrait-only. A page that reserved the bottom again would show a band above the nav bar and lift its FAB by the same amount. |

---

## `ui/navigation/`

No `NavHost`. Three tabs live in a `HorizontalPager`; the entry form and category management are overlays stacked
above it and animated in from the bottom.

### `ui/navigation/Screen.kt`

```kotlin
sealed class Screen {
    data object Home : Screen()
    data object Stats : Screen()
    data object Settings : Screen()
}
```

Identity for a pager page, not a route: there are no route strings and no arguments.

### `ui/navigation/NavGraph.kt`

| Member | Signature | Notes |
| --- | --- | --- |
| `BottomNavItem` | `data class BottomNavItem(val screen: Screen, val icon: ImageVector, val label: String)` | One tab: page identity, icon, already-localized label. |
| `BudgeNavGraph` | `@Composable fun BudgeNavGraph()` | The app's only entry composable, called from `MainActivity`. No parameters. |

`BudgeNavGraph` owns:

- **Tab order** — one list drives both the pager and the bar: `Stats` (`Icons.AutoMirrored.Filled.List`,
  `nav_stats`), `Home` (`Icons.Default.Home`, `nav_home`), `Settings` (`Icons.Default.Settings`, `nav_settings`).
  Home is index 1 and is the initial page.
- **Tab taps** — `goTo(index)` sets the highlight immediately, then animates the pager (200 ms when skipping more
  than one page, else 300 ms). A `navTarget` guard stops intermediate pages from moving the highlight; a manual swipe
  updates it at the 50 % threshold.
- **Overlays** — a private `enum class Overlay { ENTRY, CATEGORIES }` held in `rememberSaveable` with a custom
  `Saver` that stores the enum name (`""` = none). `editTransactionId` is also saveable, with `-1L` as "new entry"
  (`NEW_ENTRY`). While an overlay is set the pager's `userScrollEnabled` is false, the nav highlight is `selected`
  only when `overlay == null`, and system back closes the overlay instead of leaving the app.
- **Overlay calls** — `EntryScreen(transactionId, onNavigateBack = ::dismissOverlay)` and
  `CategoryScreen(onNavigateBack = ::dismissOverlay)`, each inside a private `OverlayHost` (300 ms slide+fade in,
  250 ms out) sized `Modifier.matchParentSize()`.
- **Screens** — `StatsScreen()`, `HomeScreen(onAddTransaction = …, onEditTransaction = …)`,
  `SettingsScreen(onNavigateToCategories = …)`.

---

## `ui/home/`

### `ui/home/HomeViewModel.kt`

```kotlin
data class HomeUiState(
    val currentMonth: YearMonth = YearMonth.now(),
    val transactions: List<Transaction>? = null,
    val totalExpense: Long = 0L,
    val totalIncome: Long = 0L,
    val currencySymbol: String = "$",
)
```

`transactions` is nullable **for a reason the screen depends on**: `null` means "nothing has been read yet", which
is not the same as an empty month. The summary card shows `--` placeholders while it is null rather than `$0.00` in
a currency the reader may not have chosen.

| Member | Signature | Notes |
| --- | --- | --- |
| `uiState` | `val uiState: StateFlow<HomeUiState>` | `stateIn(viewModelScope, WhileSubscribed(5000), HomeUiState())`. Combines the selected month with the currency symbol (`distinctUntilChanged`, so an unrelated preference write does not restart the queries), then `flatMapLatest` over the month's summary and transaction flows. |
| `refreshMonth` | `fun refreshMonth()` | Re-reads `YearMonth.now()` and only writes when the month actually changed. Idempotent. The screen calls it on every `ON_RESUME`, which is what stops a session left open across the 1st from showing the previous month. |
| `deleteError` | `val deleteError: StateFlow<Boolean>` | Set once per failed delete; stays `true` until cleared. |
| `clearDeleteError` | `fun clearDeleteError()` | Called by the screen after it has shown the snackbar. |
| `deleteTransaction` | `fun deleteTransaction(id: Long)` | Launches in `viewModelScope`; a failure sets `deleteError`, cancellation is rethrown. No return value and no refresh: the Room flow re-emits the new list. |

The constructor is `@Inject constructor(transactionRepository: TransactionRepository, dataStore: DataStore<Preferences>)`,
`@HiltViewModel`.

### `ui/home/HomeScreen.kt`

```kotlin
@Composable
fun HomeScreen(
    onAddTransaction: () -> Unit,
    onEditTransaction: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
)
```

Behaviour a redesign must preserve:

- `contentWindowInsets = pageWindowInsets`; the top bar is a centred `TopAppBar` showing `formatMonthYear`.
- Both summary figures come from `SummaryFigure` inside a `Row` with `Modifier.weight(1f)`; expense is
  `colorScheme.error`, income is `incomeColor()`, and the balance line takes `incomeColor()`, `error` or
  `Color.Unspecified` at exactly zero.
- Rows are grouped by local calendar date (`remember(transactions)`), with each day's expense/income summed and
  `Amount.coerceToAmount`-clamped before the header is drawn.
- The list is a `LazyColumn` with `contentPadding = PaddingValues(bottom = 96.dp)` so the last row clears the FAB.
- Deleting is a swipe (`deleteSwipeDistance = 96.dp`) that never dismisses the row: it snaps back and opens an
  `AlertDialog`. The haptic fires when the travelled distance crosses 96 dp, and the same action is exposed as a
  `CustomAccessibilityAction` because the gesture is not in the accessibility tree.
- Before the first emission the list area is an empty `Box` — no "no transactions this month" text, which would be
  a claim the app cannot yet make.

| Private member | Signature | Notes |
| --- | --- | --- |
| `DayHeader` | `@Composable private fun DayHeader(date: LocalDate, dayExpense: Long, dayIncome: Long, currencySymbol: String)` | `surfaceContainer` band; `-`-prefixed expense in `error`, `+`-prefixed income in `incomeColor()`; each side hidden at 0. |
| `TransactionItem` | `@Composable private fun TransactionItem(transaction: Transaction, onClick: () -> Unit, onDelete: () -> Unit, currencySymbol: String)` | 40 dp circle in `categoryColor` with `initialChar()` tinted by `categoryInitialColor`; category name, optional note, signed amount. |
| `DayTransactions` | `private data class DayTransactions(val date: LocalDate, val rows: List<Transaction>, val expense: Long, val income: Long)` | Grouping bucket, not persisted. |
| `UNKNOWN_AMOUNT` | `private const val UNKNOWN_AMOUNT = "--"` | The pre-load placeholder. |
| `deleteSwipeDistance` | `private val deleteSwipeDistance = 96.dp` | Fixed distance rather than a proportion of the row. |

---

## `ui/stats/`

### `ui/stats/StatsViewModel.kt`

```kotlin
enum class StatsPeriod {
    YEARLY,
    MONTHLY,
    DAILY,
}

data class StatsUiState(
    val period: StatsPeriod = StatsPeriod.MONTHLY,
    val currentDate: LocalDate = LocalDate.now(),
    val monthlySummary: MonthlySummary? = null,
    val categorySummaries: List<CategorySummary> = emptyList(),
    val budget: Budget? = null,
    val currencySymbol: String = "$",
) {
    val currentMonth: YearMonth
        get() = YearMonth.from(currentDate)
}
```

One anchor, `currentDate`, is what every period is a view onto; `currentMonth` is derived, never stored, so Day,
Month and Year cannot drift apart. `budget` is only ever populated in `MONTHLY` — the year and day paths build
state without it, so a redesign must not show a budget bar there.

| Member | Signature | Notes |
| --- | --- | --- |
| `uiState` | `val uiState: StateFlow<StatsUiState>` | `stateIn(viewModelScope, WhileSubscribed(5000), StatsUiState())`. `flatMapLatest` on (period, date, symbol). Yearly and daily go through one range query pair; monthly uses the month-keyed queries plus `budgetRepository.getByMonth(yyyyMM)`. |
| `jumpToToday` | `fun jumpToToday()` | Sets the anchor to `LocalDate.now()`, keeping the period. |
| `setPeriod` | `fun setPeriod(period: StatsPeriod)` | Pure state write; nothing is re-synced because every period reads the same anchor. |
| `previousPeriod` | `fun previousPeriod()` | Shifts the anchor back one unit of the **active** period. Month arithmetic clamps at month end (31 Mar − 1 month = 28 Feb). |
| `nextPeriod` | `fun nextPeriod()` | Shifts forward one unit. |

| Free function | Signature | Notes |
| --- | --- | --- |
| `shiftedBy` | `internal fun LocalDate.shiftedBy(period: StatsPeriod, steps: Int): LocalDate` | `plusMonths`/`plusYears`/`plusDays`. Extracted so the rule is testable without a dispatcher, database or DataStore. |

Constructor: `@Inject constructor(transactionRepository, budgetRepository, dataStore)`, `@HiltViewModel`.

### `ui/stats/StatsScreen.kt`

```kotlin
@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel())
```

No parameters beyond the view model — navigation is entirely internal. Structure:

- The top bar is a plain `Column`, not a `TopAppBar`, padded by `WindowInsets.statusBars` + 16 dp, so the date, the
  arrows and the "jump to today" line can be placed exactly and the page below is not pushed around.
- The jump-to-today line is always laid out; only its `alpha`, `clickable(enabled=)` and semantics change when the
  anchor already is today, so showing it never shifts the chips.
- Period chips iterate `StatsPeriod.entries` (Year, Month, Day), each `Modifier.weight(1f)`.
- Summary card renders only when `monthlySummary != null`; the budget card only when **both** `budget` and
  `monthlySummary` exist, with `LinearProgressIndicator` clamped to 0–1 and turning `error` above 0.9. The balance
  line is `incomeColor()` for `balance >= 0` (break-even counts as money in, unlike Home) and `error` below.
- Two separate breakdown cards, expenses and income, filtered by `CategorySummary.type`; each has a donut and a row
  per category.
- The whole page is a `verticalScroll` `Column`; `contentWindowInsets = pageWindowInsets`.

| Private member | Signature | Notes |
| --- | --- | --- |
| `CategoryDonutChart` | `@Composable private fun CategoryDonutChart(categories: List<CategorySummary>, modifier: Modifier = Modifier)` | Tokenises the arcs with a 40 dp `Stroke` from −90°, sweep proportional to `total`; returns without drawing when the total is ≤ 0. Carries a `contentDescription` from the `stats_donut_description` plural and no other text. |
| `CategorySummaryItem` | `@Composable private fun CategorySummaryItem(category: CategorySummary, currencySymbol: String)` | 12 dp dot, name, unsigned total coloured by `type`. |

---

## `ui/entry/`

### `ui/entry/EntryViewModel.kt`

```kotlin
data class EntryUiState(
    val type: TransactionType = TransactionType.EXPENSE,
    val amount: String = "",
    val selectedCategoryId: Long = 0L,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val currencySymbol: String = "$",
    val expenseCategories: List<Category> = emptyList(),
    val incomeCategories: List<Category> = emptyList(),
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null,
) {
    val categories: List<Category>
        get() = if (type == TransactionType.EXPENSE) expenseCategories else incomeCategories
}
```

`saveSuccess` and `error` are **one-shot** fields: the screen consumes each one (`consumeSaveSuccess`, `clearError`)
so a recomposition or configuration change cannot navigate twice or swallow a repeated error.

| Member | Signature | Notes |
| --- | --- | --- |
| `uiState` | `val uiState: StateFlow<EntryUiState>` | Plain `MutableStateFlow`; the form is input-driven, not database-driven. |
| `init` | `fun init(transactionId: Long)` | **Idempotent per id**: a repeat call with the same id returns immediately, which is what stops the exit animation and configuration changes from blanking a half-typed form. A new id resets the form, loads the stored transaction when `> 0`, re-reads categories and currency once, and starts the two collectors **once per instance** (guarded by `collectorsStarted`). Called from a `LaunchedEffect(transactionId)` in the screen. |
| `updateType` | `fun updateType(type: TransactionType)` | Keeps the selected category when it exists in the newly shown list, else selects that list's first, else `0`. If the new direction has no categories at all it repairs the table with `ensureCategoryOfType` in the background. |
| `updateAmount` | `fun updateAmount(amount: String)` | Runs `sanitizeAmountInput`; the state never holds text the parser would reject. |
| `updateCategoryId` | `fun updateCategoryId(categoryId: Long)` | |
| `updateNote` | `fun updateNote(note: String)` | |
| `updateTimestamp` | `fun updateTimestamp(timestamp: Long)` | Millis; the screen passes date-picker and time-picker results through `Formats`. |
| `consumeSaveSuccess` | `fun consumeSaveSuccess()` | Clears the one-shot success flag. |
| `clearError` | `fun clearError()` | Clears the one-shot error. |
| `save` | `fun save()` | Ignores re-entrant calls while `isSaving`. Validates amount (`entry_invalid_amount`) and category (`entry_select_category`), setting `error` and returning. Otherwise inserts (`id = 0`) or updates (`id = _transactionId`), keeping `createdAt` and moving `updatedAt`; success sets `saveSuccess`, any exception sets `save_failed`. Cancellation is rethrown. |

Constructor: `@Inject constructor(application: Application, transactionRepository, categoryRepository, dataStore)`,
`@HiltViewModel`, extends `AndroidViewModel` (it needs `getString` for localized errors).

### `ui/entry/EntryScreen.kt`

```kotlin
@Composable
fun EntryScreen(
    transactionId: Long = -1L,
    onNavigateBack: () -> Unit,
    viewModel: EntryViewModel = hiltViewModel(),
)
```

Field order, top to bottom: type chips (expense/income) → amount `OutlinedTextField` with
`prefix = { Text(currencySymbol) }` and `KeyboardType.Decimal` → category `FlowRow` of `FilterChip`s with a 16 dp
colour dot → date and time `OutlinedButton`s side by side → note field (2–4 lines) → full-width save button. The
column is `imePadding()` + `verticalScroll` + 16 dp, because the page reserves no bottom inset and the save action
must be reachable with the keyboard up.

`transactionId > 0` edits; otherwise it creates. The screen consumes `saveSuccess` before calling `onNavigateBack`,
and shows `error` in a snackbar then clears it.

The date picker allows today and earlier only (`pastDatesOnly`); the time picker clamps a future time to "now" and
says so with `entry_future_time`.

| Private member | Signature | Notes |
| --- | --- | --- |
| `pastDatesOnly` | `private val pastDatesOnly: SelectableDates` | `isSelectableDate` = the UTC-normalized cell is not after `LocalDate.now()`; `isSelectableYear` = `year <= LocalDate.now().year`. |

---

## `ui/category/`

### `ui/category/CategoryViewModel.kt`

```kotlin
data class CategoryUiState(
    val showDialog: Boolean = false,
    val editingCategory: Category? = null,
    val name: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val color: Long = DEFAULT_CATEGORY_COLOR,
    val isSaving: Boolean = false,
    val error: String? = null,
) {
    val isEditing: Boolean
        get() = editingCategory != null
}
```

`showDialog` and `editingCategory` answer two different questions; the insert-vs-update decision reads
`editingCategory`, never "is the dialog open". Answering both with one flag is what once made "add" run an update
against `id = 0`.

`DEFAULT_CATEGORY_COLOR` — `const val DEFAULT_CATEGORY_COLOR = 0xFFE57373` (top-level in this file; equals the
first entry of `CategoryScreen.categoryColors`).

| Member | Signature | Notes |
| --- | --- | --- |
| `categories` | `val categories: StateFlow<List<Category>>` | `categoryRepository.getAll().stateIn(viewModelScope, WhileSubscribed(5000), emptyList())`. |
| `uiState` | `val uiState: StateFlow<CategoryUiState>` | `MutableStateFlow` behind `asStateFlow()`. |
| `startEditing` | `fun startEditing(category: Category?)` | `null` opens a blank "new" dialog; a category pre-fills name, type and colour. |
| `updateName` | `fun updateName(name: String)` | |
| `updateType` | `fun updateType(type: TransactionType)` | |
| `updateColor` | `fun updateColor(color: Long)` | ARGB long from the fixed palette. |
| `save` | `fun save()` | Ignores re-entrant calls while `isSaving`. Blank name → `category_name_empty` and returns. An existing category keeps its `icon`, `isDefault` and `sortOrder` and only the user-facing fields change; a new one gets `icon = "circle"`, `isDefault = false`, `sortOrder = 0`. Success closes the dialog and clears the draft; failure → `category_save_failed`. |
| `delete` | `fun delete(category: Category)` | Refuses when `getTransactionCountForCategory(id) > 0` (`category_cannot_delete`) — the FK is `RESTRICT` and a sentence beats a constraint error. The check is advisory, so the write is also guarded by a catch. Seeded categories are deletable. |
| `clearError` | `fun clearError()` | |
| `dismissDialog` | `fun dismissDialog()` | Closes and clears the draft, keeping `categories` untouched. |

Constructor: `@Inject constructor(application: Application, categoryRepository, transactionRepository)`,
`@HiltViewModel`, `AndroidViewModel` for `getString`.

### `ui/category/CategoryScreen.kt`

```kotlin
@Composable
fun CategoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: CategoryViewModel = hiltViewModel(),
)
```

FAB adds a category (`startEditing(null)`), each row opens the shared dialog for edit or delete. A `LazyColumn`
with `contentPadding` bottom 96 dp keeps the last row clear of the FAB. Errors go to a snackbar and are cleared.

| Member | Signature | Notes |
| --- | --- | --- |
| `categoryColors` | `internal val categoryColors: List<Long>` | The 10 swatches the picker offers, in order: `0xFFE57373`, `0xFF81C784`, `0xFF64B5F6`, `0xFFFFB74D`, `0xFFBA68C8`, `0xFF4DB6AC`, `0xFF90A4AE`, `0xFFFFF176`, `0xFF7986CB`, `0xFFA1887F`. Users cannot enter a custom colour. |
| `CategoryItem` | `@Composable private fun CategoryItem(category: Category, onEdit: () -> Unit, onDelete: () -> Unit)` | Circle + initial + type label + edit/delete icon buttons; delete asks first. |
| `CategoryDialog` | `@Composable private fun CategoryDialog(uiState: CategoryUiState, onNameChange: (String) -> Unit, onTypeChange: (TransactionType) -> Unit, onColorChange: (Long) -> Unit, onSave: () -> Unit, onDismiss: () -> Unit)` | Stateless: every field edit and save is delegated. Colour swatches are 48 dp targets around 32 dp circles, `Role.RadioButton`, each with a `category_color_option` content description. |

---

## `ui/settings/`

### `ui/settings/SettingsViewModel.kt`

```kotlin
data class SettingsUiState(
    val currencySymbol: String = "$",
    val theme: String = Prefs.FOLLOW_SYSTEM,
    val language: String = Prefs.FOLLOW_SYSTEM,
    val message: String? = null,
    val showImportConfirm: Boolean = false,
)
```

`message` is one-shot: the screen shows it in a snackbar and calls `clearMessage`. `showImportConfirm` drives the
overwrite-confirmation dialog.

| Member | Signature | Notes |
| --- | --- | --- |
| `uiState` | `val uiState: StateFlow<SettingsUiState>` | A collector started in `init` mirrors all three preferences into the state, so the screen always reflects the store rather than the last tap. |
| `updateCurrencySymbol` | `fun updateCurrencySymbol(symbol: String)` | Fire-and-forget write. |
| `cycleTheme` | `fun cycleTheme()` | system → light → dark → system, one step per call. |
| `updateLanguage` | `fun updateLanguage(language: String)` | Persists the code only. Category names are deliberately **not** rewritten: seeded names belong to the user once created. The Activity is recreated by `MainActivity`, not here. |
| `clearAllRecords` | `fun clearAllRecords()` | Wipes the database and re-seeds in the current app language, on `Dispatchers.IO`. Preferences are untouched. Failure → `settings_clear_all_failed`. |
| `exportRecords` | `fun exportRecords(context: Context, uri: Uri)` | Writes `encodeBackup(export())` to the picked URI via `contentResolver.openOutputStream`, both steps on `Dispatchers.IO`. Reports success only when a stream was opened and written. Accepts `Context` rather than an injected one because it is the caller's `ContentResolver` scope. |
| `onImportPicked` | `fun onImportPicked(context: Context, uri: Uri)` | **Phase 1**: reads at most 20 MB, `decodeBackup`; `null` → `settings_import_invalid` and nothing else happens. Otherwise the parsed document is held in a private field and `showImportConfirm` is raised. Holding the parsed data, not the URI, means the bytes cannot change between "validated" and "confirmed". |
| `confirmImport` | `fun confirmImport()` | **Phase 2**: clears `pendingImport` first, then restores in `Dispatchers.IO`. Message is `settings_import_failed` on null report, the `settings_import_reassigned` plural when anything was re-homed, else `settings_import_success`. |
| `cancelImport` | `fun cancelImport()` | Drops the pending document and closes the dialog. |
| `clearMessage` | `fun clearMessage()` | Consumes the one-shot message. |

Private: `persist(write)` writes DataStore and swallows `IOException` (the value simply stays; every control reads
the store, so the UI follows it), `string(...)`/`plural(...)` resolve resources from the application,
`readBackup(...)` returns `null` for an unreadable or invalid file. File-level
`private const val MAX_BACKUP_CHARS = 20 * 1024 * 1024`.

Constructor: `@Inject constructor(application: Application, dataStore, backupRepository)`, `@HiltViewModel`,
`AndroidViewModel`.

### `ui/settings/UpdateViewModel.kt`

```kotlin
sealed interface UpdateDownload {
    data object Idle : UpdateDownload
    data class Downloading(val bytes: Long, val total: Long) : UpdateDownload
    data object Verifying : UpdateDownload
    data object Ready : UpdateDownload
    data object DownloadFailed : UpdateDownload
    data object VerificationFailed : UpdateDownload
    data object InstallFailed : UpdateDownload
}
```

Kept apart from `UpdateStatus` (what the check found) because a download outlives the dialog it started from:
closing that dialog must not throw the progress away. `total` is `0` when the server did not announce a size. A file
that has not passed its hash check is never `Ready`.

| Member | Signature | Notes |
| --- | --- | --- |
| `channel` | `val channel: ReleaseChannel` | From `BuildConfig.VERSION_NAME`; not configurable at runtime. `"0.1.1"` → STABLE. |
| `currentVersion` | `val currentVersion: AppVersion` | Falls back to `0.0.0` when `versionName` does not parse, which makes every release look newer — the safe direction. |
| `history` | `val history: List<ReleaseNotes>` | `releaseHistoryFor(channel)`, newest first. |
| `currentRelease` | `val currentRelease: ReleaseNotes?` | This build's own entry, or `null` if it was never added. |
| `alphas` | `val alphas: List<ReleaseNotes>` | `alphasFor(channel)`; empty except on BETA. Its emptiness is what hides the alpha button. |
| `historyWithAlphas` | `val historyWithAlphas: List<ReleaseNotes>` | `history + alphas`, sorted newest first. |
| `betaOffer` | `val betaOffer: StateFlow<RemoteRelease?>` | Re-decided on every check, cleared at the start of one. Empty after a failed check — a button promising "the newest beta" would be guessing. |
| `status` | `val status: StateFlow<UpdateStatus>` | Starts `Idle`. |
| `download` | `val download: StateFlow<UpdateDownload>` | Starts `Idle`. |
| `downloadUpdate` | `fun downloadUpdate()` | Only when `status` is `Available` **and** `release.assetUrl != null`; no-op while already `Downloading` or `Verifying`. Downloads to `fileStore.scratch(version)`, hashes it, and only renames to `fileStore.verified(version)` when `matchesSha256` passes. Failure paths delete the scratch file and land on `DownloadFailed`/`VerificationFailed`. Progress callbacks only write while the state is still `Downloading`. |
| `cancelDownload` | `fun cancelDownload()` | Cancels the job, deletes the scratch file and any ready file, sets `Idle`. Safe to call when nothing is running. |
| `canInstallPackages` | `fun canInstallPackages(): Boolean` | Delegates to the platform; asked again on `ON_RESUME`, since the user may have just granted it. |
| `installDownloaded` | `fun installDownloaded()` | No-op when there is no ready file. On failure sets `InstallFailed`. The verified file and `Ready` state are **kept** — a cancelled install would otherwise cost another whole download; the next launch of a build at least that version clears it. |
| `requestInstallPermission` | `fun requestInstallPermission()` | Opens the per-app "install unknown apps" screen. |
| `checkForUpdate` | `fun checkForUpdate()` | No-op while `Checking`. `!UpdateConfig.isConfigured` → `NotConfigured` with **no request**. Otherwise sets `Checking`, clears `betaOffer`, abandons an in-flight download unless it is `Ready`, then maps `ReleaseFetch`: `Failure` → `Unreachable(failure, statusCode)`; `Success` with no releases → `NoReleases`; else `selectBetaOffer` + `selectUpdate` → `UpToDate` or `Available`. A `Ready` file is kept only while its version is still the offered one. An unexpected exception becomes `Unreachable(PARSE)` — never `UpToDate`. |

Constructor: `@Inject constructor(releaseSource, apkDownloader, apkInstaller, fileStore)`, `@HiltViewModel`.

### `ui/settings/SettingsScreen.kt`

```kotlin
@Composable
fun SettingsScreen(
    onNavigateToCategories: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
)
```

Two view models on purpose: preferences and the update/about section. One scrolling `Column` of `SettingsSection`s
separated by `HorizontalDivider`s, in this order: **Currency** (symbol row → dialog) · **Appearance** (theme row
cycles on tap, language row → dialog) · **Categories** (the only route to `CategoryScreen`) · **Data** (export /
import / clear all) · **About** (version + channel → changelog, licences, check for updates). Export launches
`CreateDocument("application/json")` with the name `budge_records.json`; import launches `OpenDocument()` for
`application/json`. `contentWindowInsets = pageWindowInsets`.

Dialog state is local `remember` booleans, not view-model state: currency, language, clear-all, changelog, update
history, alpha history, components, licences, update result. The tenth dialog, the import overwrite confirmation,
comes from `uiState.showImportConfirm`.

| Private member | Signature | Notes |
| --- | --- | --- |
| `CorneredAlertDialog` | `@Composable private fun CorneredAlertDialog(onDismissRequest: () -> Unit, title: @Composable () -> Unit, text: @Composable () -> Unit, startButton: (@Composable () -> Unit)? = null, endButton: @Composable () -> Unit)` | A `BasicAlertDialog` + `Surface` rebuilt so the two actions sit in opposite corners instead of Material's right-aligned row. Width is `IntrinsicSize.Max` (Min made a long German word squeeze "Close" onto three lines). The text takes `weight(1f, fill = false)`. |
| `DialogButton` | `@Composable private fun DialogButton(text: String, onClick: () -> Unit)` | A `TextButton` whose label is always one line, ellipsised. |
| `ReleaseEntry` | `@Composable private fun ReleaseEntry(release: ReleaseNotes, currentVersion: AppVersion?)` | Version + channel, a `changelog_current` marker when it is the installed build, then the one-line notes. `currentVersion = null` inside the version dialog. |
| `updateStatusLabel` | `@Composable private fun updateStatusLabel(status: UpdateStatus): String` | The single line under "Check for updates"; `Idle` renders `""`. Each `UpdateFailure` gets its own sentence. |
| `SettingsSection` | `@Composable private fun SettingsSection(title: String, content: @Composable () -> Unit)` | Primary-coloured section title over its rows. |
| `SettingsItem` | `@Composable private fun SettingsItem(title: String, subtitle: String, onClick: (() -> Unit)?)` | `subtitle` empty → not drawn. `onClick` null → read-only row with no click action; `Role.Button` otherwise. Every current call passes a lambda. |
| `ComponentLicence` | `@Composable private fun ComponentLicence(component: OpenSourceComponent, onOpen: (String) -> Unit)` | Name, licence, underlined URL; tapping the URL calls `onOpen`. |
| `channelLabel`, `channelNote` | `@StringRes private fun channelLabel(channel: ReleaseChannel): Int`, `channelNote(channel: ReleaseChannel): Int` | String ids for the channel name and its one-line promise. |
| `languageLabel` | `@Composable private fun languageLabel(code: String): String` | Endonym from `languageEndonyms`, else the translated "follow system" label. |
| `languageEndonyms` | `private val languageEndonyms: List<Pair<String, String>>` | de, en, es, fr, it, ja, pt, ru, zh — each language named in itself, ordered by code. |
| `openReleasePage` | `private fun openReleasePage(context: Context, url: String)` | Only `https://github.com/…` is opened, because the URL comes from a response. A failed start is swallowed. |
| `openProjectPage` | `private fun openProjectPage(context: Context, url: String)` | Requires `https://` (any host) for the shipped licence links. |

---

## root package

`com.example.budge` — two classes. Both are wiring, and a redesign usually touches them only to change the theme
call.

### `BudgeApplication.kt`

`BudgeApplication` — `@HiltAndroidApp`; first-launch work, all of it off the main thread.

| Member | Signature | Notes |
| --- | --- | --- |
| `categoryRepository` | `@Inject lateinit var categoryRepository: CategoryRepository` | |
| `dataStore` | `@Inject lateinit var dataStore: DataStore<Preferences>` | |
| `fileStore` | `@Inject lateinit var fileStore: UpdateFileStore` | |
| `onCreate` | `override fun onCreate()` | Launches on `CoroutineScope(SupervisorJob() + Dispatchers.IO)`: (1) `fileStore.clearStale(AppVersion.parse(BuildConfig.VERSION_NAME) ?: AppVersion(0, 0, 0))`; (2) when `categoriesSeededKey` is absent, `seedDefaultsIfEmpty(deviceLocale())` then set the flag; (3) when no currency symbol is stored, write `defaultCurrencyFor(deviceLocale())`; (4) when a stored symbol is not in `Currencies.choices` (an older build's token), replace it. |

Failure policy: a `dataStore.data.first()` `IOException` aborts the whole block (treating it as empty would overwrite
the user's currency with the locale default); everything after that is best-effort — `CancellationException` is
rethrown and any other exception is left for the next launch, so a full disk or a damaged database never crashes
the first frame. Both decisions are re-checked **inside** the write they belong to, because Settings can run while
a slow first database open is in flight.

### `MainActivity.kt`

`MainActivity` — `@AndroidEntryPoint`, `ComponentActivity`; the single activity, and the only place the theme and
locale are applied.

| Member | Signature | Notes |
| --- | --- | --- |
| `dataStore` | `@Inject lateinit var dataStore: DataStore<Preferences>` | The same instance `AppModule` provides (unscoped injection would be a second store on the same file). |
| `attachBaseContext` | `override fun attachBaseContext(newBase: Context)` | Wraps the base context with a `Configuration` carrying the stored locale. Must happen here, before any resource is created; going through a wrapped context keeps `LocalContext.current` the Activity, which `hiltViewModel()` requires. |
| `onCreate` | `override fun onCreate(savedInstanceState: Bundle?)` | Calls `enableEdgeToEdge()` unconditionally (otherwise the decor insets the Compose root while `WindowInsets.systemBars` still reports the bars, and `NavigationBar` pads itself twice). Then `setContent`: collects theme and language from `safeData()`, seeded from the values `attachBaseContext` already read, provides `LocalAppLocale`, and calls `BudgeTheme(themeMode = theme) { BudgeNavGraph() }`. A `LaunchedEffect(language)` calls `recreate()` when the stored language diverges from the applied one. |

| Private member | Signature | Notes |
| --- | --- | --- |
| `updateBaseContextLocale` | `private fun updateBaseContextLocale(context: Context): Context` | One `runBlocking` DataStore read — the only synchronous read left in the app, and unavoidable: it must finish before any resource resolves. Records `appliedLanguage` and `appliedTheme` in the companion, falls back to `emptyPreferences()` on failure and to the original context if `createConfigurationContext` throws. |
| `appliedLanguage` | `@Volatile private var appliedLanguage: String` (companion) | Process-wide so it survives recreation; seeded to `Prefs.FOLLOW_SYSTEM`. Prevents a `recreate()` loop at startup. |
| `appliedTheme` | `@Volatile private var appliedTheme: String` (companion) | The first composition's initial theme, so a user who chose dark on a light device never sees a light frame. |

---

## Extension points a redesign will touch

| Entry point | Where | Why a visual change flows through it |
| --- | --- | --- |
| `BudgeTheme(themeMode, dynamicColor, content)` | `ui/theme/Theme.kt:109`, called once at `MainActivity.kt:90` | The single place a colour scheme reaches every screen. It passes only `colorScheme` to `MaterialTheme` — no `typography`, no `shapes` — so both are Material defaults; a redesign that wants its own type scale or shapes must add them here. `dynamicColor = true` means the wallpaper wins on API 31+; set it `false` to make a custom palette actually visible. |
| `md_theme_*` palette | `ui/theme/Color.kt` (35 roles × 2, plus the two income greens) | Consumed only by `LightColorScheme`/`DarkColorScheme` in `Theme.kt:22` and `:61`. No screen hardcodes a colour except the category swatch palette; the only other literals in `ui/` are `DEFAULT_CATEGORY_COLOR` and `WHITE`/`BLACK` in `Formats.kt`. |
| `incomeColor()` / `LocalIsDarkTheme` | `ui/theme/Theme.kt:180` / `:176` | The only "money in" colour in the app: every income figure and positive balance on Home and Stats. The entry form does **not** use it — its amount is plain text and its chips carry category colours. Any new semantic colour belongs beside it, not in a screen. |
| `pageWindowInsets` | `ui/theme/Theme.kt:190` | Passed as `contentWindowInsets` by all five screens (e.g. `HomeScreen.kt:150`). The bottom inset belongs to `NavigationBar` in `NavGraph.kt:265`; changing this value is how page padding around the bar is changed. |
| Screen parameter lists | `HomeScreen.kt:105`, `StatsScreen.kt:82`, `EntryScreen.kt:87`, `CategoryScreen.kt:90`, `SettingsScreen.kt:294` | These five are the whole surface a redesign replaces. Only `HomeScreen` (two callbacks) and `SettingsScreen` (one callback) take navigation; everything else is internal. Their sole wiring is `NavGraph.kt:229–262`. |
| `BudgeNavGraph()` | `ui/navigation/NavGraph.kt:123` | Owns tab order, the `NavigationBar`, and overlay animation timing (300 ms in / 250 ms out, `OverlayHost`). Replacing the bottom bar means editing this function, not the screens. |
| `SummaryFigure(label, amount, color, horizontalAlignment, modifier)` | `ui/SummaryFigure.kt:21` | The shared "expense / income" pair on Home and Stats. A new summary card layout changes this one composable, or both screens drift. |
| `formatMoney`, `amountTextStyle`, `categoryInitialColor`, `initialChar` | `ui/Formats.kt:47`, `:134`, `:186`, `:145` | Every amount, every figure size step and every category avatar glyph goes through these. `amountTextStyle` is what keeps two large totals from wrapping. |
| `categoryColors` + `DEFAULT_CATEGORY_COLOR` | `ui/category/CategoryScreen.kt:69`, `ui/category/CategoryViewModel.kt:47` | The 10-swatch picker palette and its default. `DEFAULT_CATEGORY_COLOR` must remain one of `categoryColors`, or a new dialog opens with no swatch selected. |
| `releaseNotesText` | `ui/Formats.kt:212` | Renders remote Markdown into the update dialog's plain `Text`. If the dialog gains rich text, this is the function that becomes unnecessary. |
| String resources | `app/src/main/res/values/strings.xml` (151 strings, 2 plurals) and `values-{de,es,fr,it,ja,pt,ru,zh}/strings.xml` (132 strings each) | Every label on screen. The 19 `release_notes_*` strings exist only in `values/` and `values-zh/`; the other seven locales intentionally fall back to English. The 8 `cd_*` strings are content descriptions (`cd_back`, `cd_save`, `cd_delete`, `cd_edit`, `cd_add_transaction`, `cd_add_category`, `cd_previous_period`, `cd_next_period`) — rename or restructure controls and these must follow. Locale filtering is declared in `app/build.gradle.kts` (`resourceConfigurations += LOCALES`, 9 codes); a new translation added to `res/` but not to that list is silently dropped from the APK. |
| Dates and clock format | `ui/LocalizedPatterns.kt:21`, `:44` | The skeleton→pattern lookup and the 12/24-hour decision. Hard-coding a pattern in a screen re-introduces the English-order bug the file exists to prevent. |
| Icons | `ui/navigation/NavGraph.kt` (`ImageVector` only) | Nav icons are the only vector icons rendered. Category `icon` values are strings in the database and are **never** resolved — every category is drawn as its initial on its colour (`HomeScreen.kt:533`, `CategoryScreen.kt:216`). A design that wants real category icons needs a new name→`ImageVector` mapping and an `icon` fallback. |
| Accessibility hooks | `strings.xml` `cd_*`; `HomeScreen.kt` `semantics { customActions = … }` | Swipe-to-delete is invisible to a screen reader, so the same delete is offered as a custom action. Any gesture-only affordance needs an equivalent here. |

---

## What this document does not cover

- Test sources (`app/src/test/java/…`, 19 files) — behaviour they pin, and any regression they would catch.
- Gradle configuration beyond the facts cited above: dependency versions, signing (`keystore.properties`), R8
  (`app/proguard-rules.pro`), Room schema export (`app/schemas/…`).
- Resources other than strings: launcher mipmaps, `drawable/`, `values/themes.xml` (`Theme.Budge`, the window theme
  in force before Compose draws), `values-night/themes.xml`, `xml/update_paths.xml` and the backup rules.
- Exact `dp`/`sp` values that are purely visual. Only the ones that are behaviour are recorded here: the 96 dp
  delete distance, the 96 dp FAB clearance under lists, the 40 dp donut stroke, the 48/32/24 dp swatch targets.
- The full text of `values-*/strings.xml`, and the rendered layout of any screen (measured bounds, scroll extents).
- Platform behaviour: the system installer, the "install unknown apps" permission flow, edge-to-edge insets as the
  framework computes them.

