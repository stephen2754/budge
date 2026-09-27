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

## [0.1.0-alpha.3] — four ways the app could be quietly wrong

An alpha with no new features: the results of reading the shipped code looking for places
it could give a confident wrong answer, plus what that search turned up in the update row.

### Fixed

- **A comma decimal separator was thrown away.** The amount field kept digits and `.` and
  dropped everything else, and six of the nine shipped locales — German, French, Spanish,
  Italian, Portuguese, Russian — put `,` on the decimal key of their keyboard, so `12,50`
  was saved as `1250.00`. Amounts now take whichever separator was typed and read the
  **last** one as the decimal point, which is what makes `1,234.56` and `1.234,56` the
  same number. A leading minus survives the field so the parser can still refuse a
  negative amount instead of flipping its sign.
- **A backup carrying no records erased the ledger and reported success.** `isEmpty`
  existed on the parsed document but nothing consulted it, and a restore deletes every
  table before it writes — so any file that merely named the three keys, including one
  written by a later format, wiped the data and then said "Import successful". Such a
  file is refused now. A restore also re-seeds the built-in categories when the file
  carries none, so importing a budgets-only file can no longer leave the app unable to
  record anything.
- **The update dialog showed release notes as Markdown.** The API sends the body as
  Markdown and the dialog is a `Text`: the first published notes carried four `##`
  headings, eight `**` pairs and ten backticks, all of which the user would have read as
  markup instead of as a message.
- **A 403 was called a rate limit whether or not it was one.** GitHub answers 403 both
  for the anonymous hourly quota and for a repository it will not serve. The app now
  needs the response's own `x-ratelimit-remaining: 0` before it says "too many requests";
  a plain refusal is reported as an HTTP error with its code.
- **One unreadable tag failed the whole check.** `AppVersion.parse` threw on
  `v9999999999.0.0` rather than returning null, so a single odd tag made the entire
  response unreadable instead of being skipped.

## [0.1.0-alpha.2] — the update check, fixed

An alpha, and a small one: the first fix to the update check, which could not be completed
at all on a network where the release page opens in a browser without trouble.

### Fixed

- **A failed check no longer means one sentence.** It now says what failed: nothing
  reachable, a timeout, a refusal that will pass, a repository that is not there, a status
  code, or an answer that arrived but could not be read. Every one of those used to be
  reported as "could not connect", including a payload that was read and then failed to
  parse — a state the code could not even reach.
- **The check no longer depends on one endpoint.** GitHub's API allows sixty anonymous
  requests an hour to everyone sharing an address, and it is the host most often
  interfered with on the networks this app runs on. When the API refuses the request or
  answers with something unreadable, the app now reads the repository's release feed,
  served by `github.com` itself and not metered at all.
- **The request waits 20 seconds instead of 10**, which a slow international route can
  need before it reports the device as offline.

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

[0.1.0-alpha.3]: https://github.com/stephen2754/budge/releases/tag/v0.1.0-alpha.3
[0.1.0-alpha.2]: https://github.com/stephen2754/budge/releases/tag/v0.1.0-alpha.2
[0.1.0-alpha.1]: https://github.com/stephen2754/budge/releases/tag/v0.1.0-alpha.1
