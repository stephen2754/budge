# Budge

> A manual expense tracker for Android. Everything stays on the device, and it works
> offline.

| | |
| --- | --- |
| **Version** | 0.1.0-alpha.2 (alpha) — see [CHANGELOG.md](CHANGELOG.md) |
| **Platform** | Android 8.0+ (minSdk 26), targetSdk 35 |
| **Stack** | Kotlin 2.0.21, Jetpack Compose (Material 3), Room, Hilt, DataStore |
| **Languages** | English, 中文, Français, Deutsch, Español, Русский, 日本語, Italiano, Português |
| **Design notes** | [DESIGN.md](DESIGN.md) |

## What it does

- **Record** an expense or income in a few taps: type, amount, category, date, time, note.
- **Review** the month as a day-grouped list with daily subtotals and a running balance.
- **Analyse** any year, month or day: totals, balance, and a per-category breakdown with
  donut charts.
- **Organise** categories — rename, re-colour, re-type or delete any of them, including
  the ones seeded on first launch.
- **Back up** to a JSON file you choose, and restore from one.

No account, no sync, no analytics. The one network request the app can make is the
update check you trigger yourself, and it is a single read-only GET that carries no
ledger data. See [Data and privacy](#data-and-privacy).

## Building

Requirements: JDK 17, the Android SDK with platform 35 and build-tools 35.0.0, and a
`local.properties` pointing at it (`sdk.dir=/path/to/Android/sdk`). The Gradle wrapper
fetches its own Gradle.

```bash
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:testDebugUnitTest      # unit tests (JVM, no device needed)
./gradlew :app:assembleRelease        # release APK (R8 + resource shrinking)
./gradlew installDebug                # build and install on a connected device
```

The debug APK lands in `app/build/outputs/apk/debug/`.

### Building a release

`./gradlew :app:assembleRelease` writes a signed APK to
`app/build/outputs/apk/release/app-release.apk`, provided `keystore.properties` exists in
the project root:

```properties
storeFile=budge-release.jks
storePassword=…
keyAlias=budge
keyPassword=…
```

Both that file and the keystore itself (`*.jks`, `*.keystore`) are git-ignored. Without
them the release build still runs but produces an *unsigned* APK, which no device will
install. **Keep the keystore and its password somewhere safe.** Android identifies an app
by its signing key: lose it and no build can ever update an installed copy again.

> **Known issue.** `:app:assembleRelease` also runs `lintVitalRelease`, and AGP 8.7.3
> cannot parse a modern `platform-tools` revision: on 37.x, lint dies with
> `NumberFormatException: For input string: "37.0"` before it reads a single source file.
> `lint { checkReleaseBuilds = false }` in `app/build.gradle.kts` keeps the release build
> working on such a machine; delete that block once the toolchain is upgraded, and run
> `:app:lintDebug` on a machine whose platform-tools are older.

## Project layout

```
app/src/main/java/com/example/budge/
├── BudgeApplication.kt        first-launch seeding and defaults
├── MainActivity.kt            edge-to-edge host, applies the chosen language
├── ui/                        Compose screens, one package per screen
│   ├── Formats.kt             money and date formatting
│   ├── theme/                 M3 colour schemes, typography, page insets
│   └── settings/              Settings, plus the About section
├── data/
│   ├── local/                 Room database, entities, DAOs
│   ├── repository/            one repository per concern, plus the backup reader
│   ├── backup/                the JSON backup format
│   ├── prefs/                 DataStore keys, languages, currency signs
│   └── update/                version/channel rules and the GitHub reader
├── model/                     domain types (Transaction, Category, Amount…)
└── di/                        Hilt modules
app/src/test/                  JVM unit tests
app/schemas/                   exported Room schemas, one per version
```

[DESIGN.md](DESIGN.md) has the full picture, including the data model and the reasons
behind the choices.

## Versioning and releases

`versionName` in `app/build.gradle.kts` carries both the version and the channel:

| `versionName` | channel | offered updates | what it promises |
| --- | --- | --- | --- |
| `1.0.0` | stable (正式版) | stable only | no known problems |
| `1.1.0-beta.2` | beta (Beta 测试版) | beta and stable | no security issues, but crashes and other major problems are still possible |
| `2.0.0-alpha.1` | alpha (Alpha 测试版) | everything | an unstable test build: major problems, security issues included, are possible |

There is no invite-only track here. Alpha and beta are both test builds; the difference
is how much is known to be wrong with them.

The channel is read from the installed build and cannot be changed at runtime, which is
what makes the rule meaningful. Publishing a release is three steps:

1. Bump `versionCode` and `versionName`.
2. Add the release to `RELEASES` in `data/update/Changelog.kt` with a one-line summary
   in `values/strings.xml` and `values-zh/strings.xml`. This is the one string that is
   not translated into all nine languages; the other seven fall back to English. A unit
   test fails if the entry and the installed version disagree.
3. Tag it `v<versionName>` and publish a GitHub release. The update check reads
   `GET /repos/stephen2754/budge/releases`, and the tag suffix (`-beta.2`, `-alpha.1`)
   is what assigns the channel. Drafts are skipped, and a release that is never
   published is simply not offered.

[CHANGELOG.md](CHANGELOG.md) holds the full detail; the in-app history is the one-line
version of it.

## Data and privacy

- Records live in a local Room database; preferences in DataStore. Neither is uploaded.
- Export and import work on a JSON file you pick through the system file picker.
- The update check is the only network use: two unauthenticated reads of the public
  release list, `api.github.com/repos/stephen2754/budge/releases` and — when the API
  refuses the request or answers with something unreadable — the release feed at
  `github.com/stephen2754/budge/releases.atom`. They send no ledger data and no
  identifier, and carry nothing beyond the usual request headers. Because they are
  unauthenticated, that repository has to stay public for the check to work at all.
- `android:allowBackup="true"`, so the **system** may include the app's data in a cloud
  backup or a device transfer. The rules for that are in
  `res/xml/data_extraction_rules.xml` (API 31+) and `res/xml/backup_rules.xml` (older
  releases); add an `<exclude>` there to opt out.
- Third-party licences are listed in the app under *Settings → About → Licences*.

## Conventions worth knowing

- **Money** is integer cents. Amounts fit an unsigned 32-bit range and the balance is
  signed 64-bit; `model/Amount.kt` is the single place that defines this.
- **Currency signs** are grouped by the currencies that share them
  (`Currencies.labels`). Amounts show the sign alone.
- **Categories** are seeded once, on first launch, in the device language. After that
  they are ordinary rows: nothing renames or protects them.
- **Insets.** The app draws edge to edge. The `NavigationBar` owns the bottom system
  inset, so a page uses `pageWindowInsets` and never reserves it again
  (`ui/theme/Theme.kt`).
- **Locales.** `resourceConfigurations` in `app/build.gradle.kts` lists the nine
  languages; a `values-xx/` folder that is not listed there will be dropped from the
  APK. The nine files otherwise hold the same keys, with one deliberate exception: the
  release-notes summary exists only in `values/` (English) and `values-zh/`.
- **R8 keeps.** Anything Gson reflects over needs `@SerializedName` *and* a keep rule;
  `data/backup` and `data/update` are the two such packages. Check
  `app/build/outputs/mapping/release/mapping.txt` after touching either.

## Not implemented yet

- Writing a monthly budget. The table, DAO, repository and statistics card all exist;
  no screen sets one, so the card never appears.
- Per-category icons. The icon name is stored and carried into the domain model, but no
  screen draws it — lists show the name's first letter on the category colour.
- Instrumentation and Compose UI tests. Unit tests exist; the dependencies are declared
  but nothing is written.
- Checking a host other than GitHub. `ReleaseSource` is an interface; a second
  implementation is all it would take.
