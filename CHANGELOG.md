# Changelog

Notable changes per release, newest first. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

A `-alpha.N` or `-beta.N` suffix marks a test build, and the suffix is the only thing
that decides which updates a build is offered:

- **alpha** — an unstable test build. Major problems are possible, security issues
  included.
- **beta** — a stable test build. No security issues, but crashes and other major
  problems are still possible.
- **stable** — a release. No known problems.

Neither alpha nor beta is invite-only; both are simply published earlier than a stable
release. The app shows this same history under *Settings → About → Version*, filtered to
the channels the installed build may see, and the strings for it live in
`app/src/main/res/values*/strings.xml`.

## [0.1.0-alpha.1] — first published build

An alpha. Everything below is new; the app is usable, but expect the rough edges listed
under "Known limitations".

### Added

- Manual entry of expenses and income: type, amount, category, note, date and time.
- Home list grouped by day, with daily subtotals, a monthly summary and swipe-to-delete.
- Statistics by year, month or day: totals, balance, and a per-category breakdown drawn
  as a donut chart. One date anchors all three views, so switching period keeps it.
- Category management: create, rename, re-colour, re-type and delete categories,
  including the ones seeded on first launch.
- Nine languages, and a choice of currency sign covering the common currencies that
  share each sign.
- JSON backup export and import, with validation before anything is overwritten.
- Settings: currency, theme (system / light / dark), language, categories, data, and an
  About section with the release history, third-party licences and an update check.

### Known limitations

- No screen sets a monthly budget yet, although the data model and the statistics card
  for one exist.
- Category icons are stored but not drawn.
- The unit tests cover formatting, storage ranges, backup and the update rules; there
  are no instrumentation or UI tests yet.
- Lint cannot run on a machine whose Android SDK ships platform-tools 37.x: the pinned
  AGP 8.7.3 fails to parse that version string, so `lintDebug` aborts and release builds
  skip the vital check (`checkReleaseBuilds = false`). See the note in
  [README.md](README.md).

[0.1.0-alpha.1]: https://github.com/stephen2754/budge/releases/tag/v0.1.0-alpha.1
