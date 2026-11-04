# Budge — documentation

Budge is an offline expense tracker for Android. These documents describe the app as it is, for
someone taking it over — in particular for a visual redesign, which is the work they were
written for.

| Document | What it covers | Read it when |
| --- | --- | --- |
| [`../README.md`](../README.md) | What the app promises, how to build it, how a release is made. | First. Its promises are product constraints. |
| [`../DESIGN.md`](../DESIGN.md) | Engineering decisions and the reasons for them, including §12's deliberate trade-offs. | Before changing anything that looks odd. The oddness usually has a reason. |
| [`../HANDOFF-PROMPT.md`](../HANDOFF-PROMPT.md) | The brief for a visual redesign: task, phases, decisions to make, non-negotiables, how the work is checked. | If you are the one doing the redesign. |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Layers and dependency direction, the data model, repositories, dependency injection, threading, storage, the backup format, the update trust chain, startup, the build and release routine, tests. | To understand how the app works before touching it. |
| [`UI-INVENTORY.md`](UI-INVENTORY.md) | Every screen's composition tree, every dimension written in the code, typography and colour usage, states, interaction, accessibility observations, cross-screen patterns. | To know exactly what exists today. |
| [`DESIGN-SYSTEM.md`](DESIGN-SYSTEM.md) | The theme as it is: colour roles, the baseline type scale, default shapes, the observed spacing distribution, the four kinds of motion, the icon set, dark theme and dynamic colour. Then the gaps, the constraints, and which surfaces are open to a redesign. | To know what is missing and what must not move. |
| [`API.md`](API.md) | Every type, signature and contract in the codebase, grouped by package, with the extension points a redesign flows through. | To call or change something without reading 8,000 lines of Kotlin. |

## The short version

- **One module**, Kotlin + Jetpack Compose + Material 3, minSdk 26, targetSdk 35, JDK 17.
- **Data**: Room (three entities plus two summary projections), DataStore for preferences, one
  local database. Money is integer cents end to end, with saturation at the aggregate edges.
- **No account, no cloud, no analytics.** Exactly two network requests exist, both started by
  the user: the update check and the download of an update. Neither carries ledger data or an
  identifier.
- **Updates** are verified against the SHA-256 published with the release before the system
  installer is handed the file; the platform enforces the signing key.
- **Nine locales**, identical key sets, counted text as `<plurals>`, no unescaped ASCII
  apostrophe in a string resource.
- **Visual layer**: the Material 3 baseline palette and baseline type scale, default shapes,
  ad-hoc spacing literals, no illustrations, almost no motion. That is what a redesign is for.

## Conventions used in these documents

- Paths are relative to the repository root (`app/src/main/java/.../HomeScreen.kt:120`), except
  in `DESIGN-SYSTEM.md`, which writes Kotlin paths package-relative (`ui/theme/Theme.kt` means
  `app/src/main/java/com/example/budge/ui/theme/Theme.kt`). Each document says which it uses.
- "Verified" means read in the source. Anything inferred says so.
- Where a document states a number (a size, a colour, a count), it was measured at the commit it
  names, not remembered.
