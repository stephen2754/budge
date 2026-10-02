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

## [0.1.0-rc.1] — the release candidate

The release candidate. Apart from its version string it **is** the stable release: the same
source, the same strings, the same behaviour, the same build type and signing configuration.
There is nothing in this build that will not be in `0.1.0`, and nothing missing from it, which
is what makes installing it a test of the release rather than of something else.

### Added

- **A release-candidate channel**, because a version the app cannot parse is a version it
  cannot reason about: before this, `0.1.0-rc.1` failed to parse, `channelOf` fell back to
  stable, and an unparseable `versionName` made every published release look newer than the
  one installed. The rung sits between beta and stable, exactly where SemVer puts it —
  `0.1.0-beta.6 < 0.1.0-rc.1 < 0.1.0` — so a beta build is offered the candidate and the
  candidate is offered the release, both by ordinary comparison. A *stable* build is never
  shown a candidate.

## [0.1.0-beta.6] — an update that installs itself, but not behind your back

### Added

- **Updates are downloaded and installed in the app.** When the answer from GitHub names an
  APK *and* the SHA-256 it records for it, the update window offers "download and install"
  instead of a page:
  - the file is streamed straight to app-private storage — the bytes never become a string or
    a byte array — with a progress bar in the window;
  - it is hashed and compared with the published digest. **Every way of not knowing is a
    refusal, not a pass**: no digest, an empty one, an algorithm other than SHA-256. A
    mismatch deletes the file and installs nothing;
  - only then does the app hand a content URI to the *system* installer, which shows its own
    confirmation;
  - the file is deleted on the next launch, by which time a successful install has restarted
    the app.
- **The app asks for `REQUEST_INSTALL_PACKAGES`**, which is what Android requires before an
  app may install anything, and sends you to the per-app "install unknown apps" setting when
  it is missing rather than pretending it can proceed.

### What this changes about the app, stated plainly

This is the largest capability the app has ever asked for, so it is worth being exact about
its limits. Nothing is downloaded unless you press the button. Nothing is installed without
the system installer's own confirmation. The file is installed only if its hash is the one
GitHub published — and the check happens before the installer is involved at all. The
platform, in turn, refuses any update not signed with the same key as the installed app, so a
file signed by anybody else cannot replace this app even if it somehow got that far.
Downloading and installing are the *only* new network and storage behaviour; no ledger data,
no identifier and nothing else leaves the device, and an update replaces the application in
place, so categories, transactions, budgets and preferences are untouched.

### Fixed

- **The "jump to today" line is a tap target the size of its words.** It filled the row, so
  tapping anywhere along an invisible line — well outside the text — moved the date. It now
  shrinks to its label plus a few dp of slack, and is centred rather than stretched.

## [0.1.0-beta.5] — long words, long lists, and a top-heavy page

Three layout problems, all of them found by looking at the German build.

### Fixed

- **A long release history pushed the dialog buttons off the bottom of the screen.** The body
  now takes the height that is left over (`weight(1f, fill = false)`) and scrolls inside
  itself, instead of growing the dialog until the buttons left the window. `fill = false`
  keeps a short body short rather than stretching it to fill the window.
- **"Schliessen" was squeezed onto three lines by the button opposite it.** The dialog was
  sized by its content's *minimum* intrinsic width, and for a paragraph that is its longest
  single word — so the dialog was as narrow as one word and the two buttons fought over what
  was left. It is sized by the *maximum* intrinsic width now: the width the content actually
  wants, still capped by the window. Every dialog button label also goes through
  `DialogButton`, which pins it to a single line with an ellipsis as the last resort, because
  a label broken into "Sche / liess / en" is not a label.
- **The statistics page was top-heavy.** The date sat against the status bar and then a
  reserved line and two lots of padding pushed the period selector far below it. The screen
  now lays its own top area out instead of using Material's fixed-height `TopAppBar`: the
  date is 16dp below the status bar, the "jump to today" line sits directly under it, and the
  period selector follows 8dp under that. Nothing moves when the line appears — it is still
  always laid out — but the gap it used to leave is gone.

## [0.1.0-beta.4] — corners, and a line that stays put

### Fixed

- **An extra action in a dialog now sits in the dialog's own bottom-left corner.** It was in
  Material's `dismissButton` slot, which is laid out immediately beside "Close", near the
  right — a second action next to the one that closes the window rather than a different
  place to go. Material's dialog markup is internal to the library, so the shell is rebuilt
  from `BasicAlertDialog` with Material 3's own container, shape, elevation, spacing and
  typography; only the button row differs, and the corner is reserved whether or not
  anything occupies it.
- **The "jump to today" line no longer moves the statistics screen.** It was added to the
  layout only when the anchor was not today, so the period selector and everything under it
  shifted down the moment it appeared — on every period change. It is now always laid out,
  with only its visibility, clickability and accessibility changing, so it reserves exactly
  its own height at any font scale and nothing under it ever moves.

### Added

- **A beta build can add the alpha releases to its update history.** An "include alpha
  versions" button in the history window's bottom-left corner merges them into the list, and
  the list is merged and sorted once so the order does not change when they appear. The rule
  lives in `alphasFor`: betas only — an alpha build already sees them, and a stable build is
  not on the test programme, so the button has nothing to show there and does not exist.
- **The open-source components have a page of their own**, behind a bottom-left button in
  the licence window. Every entry now links to its project's repository, underlined and
  tappable, and each address was checked to resolve before it was written down — Room and
  DataStore have no repository of their own because they are part of AndroidX, which is what
  they link to.

### Changed

- **The licence window says only what a reader needs to know**: where the records are, and
  the one thing the app does over the network. The dependency tree is a page away rather than
  in the middle of it.

## [0.1.0-beta.3] — what this build is, and how to leave it

### Added

- **An "update history" button in the version window**, bottom left and level with the close
  button. The window now says what the *installed* build is — what its channel promises and
  what it changed — and the earlier releases are one press away instead of a list that grows
  without limit in the place a reader goes to ask a single question. The second window holds
  everything this channel may see, filtered by exactly the rule the update check uses: an
  alpha build sees alpha, beta and stable; a beta build sees beta and stable; a stable build
  sees stable only. Nothing about the numbers is guessed, so the history and the check can
  never disagree about what exists.
- **A "join the beta" button for stable builds**, bottom left of the update window and level
  with the buttons opposite. It is offered only by a *stable* build, only while a **beta** is
  strictly newer than the running release, and never an alpha — the promise is the newest
  beta, not the development channel. The condition is re-decided on every check rather than
  remembered, so the button disappears as soon as the stable release catches up with the
  betas, and a failed check offers nothing at all, because a failed check found out nothing.
  Pressing it opens that beta's release page, like every other download here.

### Fixed

- **The compiled release list was missing two releases** (0.1.0-beta.1 and 0.1.0-alpha.9),
  which is why a beta build opening the version window saw a single entry. A unit test
  already held the shipped list against the installed version; the gap was in the entries
  themselves.

## [0.1.0-beta.2] — the ways it could still fall over

Two audits — one on the crash surface, one on where the work goes — turned up more than the
first beta had covered. Everything below is either a way the app could die, a way it could
lose something the user did, or a way it told an untruth; the rest of the findings are
recorded as decisions in `DESIGN.md` §12.21 rather than quietly dropped.

### Fixed

- **A database that cannot be opened no longer locks the user out.** Room refuses to open a
  file whose version it has no path back from, and the app's own update screen opens the
  releases page where every older APK is downloadable — so installing an older build over a
  newer one crashed on every launch, with clearing app data (and so deleting the ledger) as
  the only way back. Versions 1, 2 and 3 are one schema, so the reverse migrations are
  no-ops and the version can now go down as well as up.
- **A read that fails no longer kills the process.** Room reports a failure by throwing from
  the flow, and these flows are collected in `viewModelScope` and in composition, where the
  exception has nowhere to go. Reads now end in `fallingBackTo(...)`: an unreadable database
  shows an empty screen instead of crashing, and the writes that fail say so where they
  happen. A *corrupt* file is still not repaired — that would mean deleting a ledger the
  user may not have backed up.
- **First-launch work cannot crash before the first screen.** It opens the database and
  writes preferences on a scope with no handler, and none of it has a reader waiting on it.
- **The entry form keeps what was typed.** It lived in plain `remember`, so a configuration
  change — the system switching to dark mode at sunset, a font-scale change — closed the
  form and threw the input away. The overlay is saved now, and `EntryViewModel.init` no
  longer resets a form it has already prepared for the same target.
- **An import cannot be made to eat the app's memory.** The file was read whole and parsed
  twice; the read is capped and the parse happens once. Both the import and the update check
  go through one bounded-read helper, because an `OutOfMemoryError` is an `Error` and so
  escapes every `catch (Exception)` on those paths.
- **An imported file cannot leave the ledger disagreeing with itself.** A direction that is
  neither expense nor income used to render as one while the SQL totals counted it as
  neither, and an out-of-range amount could wrap a day's subtotal into a small, plausible
  number. Both are brought into range on the way in, through the app's own mapping rather
  than a second rule that could drift from it.
- **Two read-then-write races are single transactions**: seeding built-in categories, and
  `ensureCategoryOfType` — which the entry form can lose against itself, leaving duplicate
  categories that the once-only seed flag never repairs. The first-launch currency default is
  decided inside the write it belongs to, so it can no longer overwrite a sign chosen in
  Settings while the database was still opening.

### Changed

- **Dark mode no longer opens with a white flash.** The window theme had no night variant,
  so the system splash and the pre-Compose window were light on a dark device.
- **The first frame tells the truth.** Before the first query answered, the home screen drew
  a full summary of zeros — in the dollar sign, whatever currency was chosen — and "no
  transactions this month". The state now distinguishes "not read yet" from "empty", and the
  first frames show a placeholder the reader cannot mistake for a balance.
- **The stored theme is applied on the first frame**, from the read the locale already makes,
  instead of one dispatch later.

## [0.1.0-beta.1] — beta, and what that now asserts

This is the first **beta**. The channel rules in `DESIGN.md` §12.15 spell out what that
changes: alpha was an unstable test build that could have security problems, and beta is a
stable test build with no *known* security problems — crashes and other major problems are
still possible. Earning that meant going looking for the ways this app can fall over rather
than declaring it finished, so most of this release is things that were wrong in ways nobody
would have seen until they were.

### Fixed

- **A failing preference read no longer crashes the app, and no longer costs the settings.**
  DataStore reports an unreadable settings file by throwing from its flow, and every reader
  here collects that flow from a view model or from composition, where the exception is a
  crash on every launch. Reads now go through `safeData()`, which reports a failed read as
  "nothing is stored". Writes are wrapped where they happen and leave the stored value
  alone, so the UI follows the store instead of the tap. The first-launch path is the one
  deliberate exception: it *skips* on a read failure rather than treating it as an empty
  store, because that path writes defaults for whatever it cannot find and would have
  overwritten the user's currency choice.
- **A delete that fails says so instead of taking the app down.** `HomeViewModel`'s delete
  had no handler at all, so a storage failure during a swipe-to-delete was an unhandled
  exception in a coroutine: a crash, from a gesture whose whole point is that it is
  reversible. It now reports the failure and the row stays where it is, which is the truth
  of it.

### Changed

- **An export is a single transaction.** The three reads that make up a backup could be
  interleaved by a write, producing a file whose transactions point at a category that is
  missing from its own categories — restoring that quietly re-homes rows onto other
  categories.
- **Category seeding is a single transaction.** The count and the insert were two calls with
  a suspension between them, which is long enough for "clear all records" to empty the table
  in between and leave a second set of built-ins on top of the twelve that path had just
  written. The once-only seed flag never repairs that.
- **Only a GitHub https address is opened.** The release page comes from a remote response;
  a release list is not a place to accept an address from and hand to a browser.
- **Each day's totals are summed once, with the grouping.** The header used to add up its own
  rows on every recomposition, and its parameter was a list — which is the difference between
  a row the list can skip and one it cannot.
- The unused `compose.ui.tooling.preview` dependency is gone (nothing annotates a preview;
  the debug tooling brings it transitively). R8 had already removed its classes, so this is
  hygiene rather than size.

### Checked, and left alone

Every DAO query was replayed against the exported schema under `EXPLAIN QUERY PLAN`: the
month list, the month totals and the category summaries all search on
`index_transactions_timestamp`, the per-category count uses the covering
`index_transactions_categoryId`, and the only temporary sorts are over one period's rows or
over the twelve-row category table. No index was added because none was needed.

## [0.1.0-alpha.9] — three things that did not line up

### Fixed

- **A quick flick could delete without a buzz.** The confirmation dialog was shown when the
  gesture *settled* towards the delete position, and a fast flick is settled there by its
  speed even when the row has travelled a few millimetres — so the dialog appeared although
  the row had never gone far enough for the haptic to fire. The dialog is now decided the
  same way the haptic is, by the distance the row actually travelled, and a flick too short
  to cross it settles back like any other short swipe. Both are measured from the same
  offset, in magnitude rather than sign, so they cannot disagree in the other direction
  either — buzzing where nothing happens.
- **The statistics date moved when the jump-to-today line appeared.** The line was drawn
  inside the app bar, which is a fixed height: a second line in it pushed the title up.
  It is drawn below the bar now, directly under the date, and the date stays where it is.
- **The language list had no order.** It began with Chinese because the app is developed in
  Chinese, and the rest followed no rule. The nine are ordered by language code now —
  `de, en, es, fr, it, ja, pt, ru, zh` — which reads alphabetically for the Latin names and
  puts the other two scripts where their code falls.

## [0.1.0-alpha.8] — the swipe that stopped working

### Fixed

- **Swipe-to-delete worked again.** 0.1.0-alpha.7 put a horizontal drag detector on each
  row so that a rightward swipe could be told apart from a leftward one. A drag detector
  consumes the touch slop as soon as it is exceeded — whichever direction the finger went —
  and the dismiss box underneath the row therefore never saw the gesture at all: the row
  would not move, and nothing could be deleted. The detector is gone and the box owns the
  drag again, exactly as it did in 0.1.0-alpha.6, with the 96dp threshold and the
  confirmation dialog unchanged. The buzz that says the swipe has gone far enough is now
  read from the dismiss state's own settle target, which changes at the threshold and
  changes back if the finger returns — a value to observe rather than a gesture to
  intercept.

### Removed

- **A rightward swipe on a transaction row no longer turns the page.** It never did, and
  making it work the way the empty space around the rows works — the page following the
  finger — means dropping the dismiss box and writing the row's own drag, threshold,
  animation and settling. That is a rewrite of the gesture that already works, on the part
  of the screen where a mistake costs the delete function, so it is off rather than half
  done. The page still turns from the space around the rows and from the bottom bar.

## [0.1.0-alpha.7] — a balance you can read, and swipes that say what they mean

### Added

- **A way back to today on the statistics screen.** When the anchor is not today, a line
  under the date says so and takes it there. It moves the anchor to today's whole date and
  leaves the period alone: the year view goes on showing a year, and switching to the month
  or day view afterwards is already on today's month and day — which is the point of
  anchoring all three periods on one date.
- **Swiping a transaction row to the right turns to Statistics.** The row only ever
  dismissed to the left and the dismiss box claims horizontal drags, so the gesture did
  nothing at all. It now asks the pager for the same move the bottom bar makes.

### Changed

- **The home balance says which side it is on.** Money in is the green the income figure
  uses, money out is the theme's error red, and break-even keeps the page's own colour.
  Until now a surplus and a debt were the same colour and the only signal was a minus sign
  to be found in the middle of the figure.
- **Deleting a transaction takes a deliberate swipe.** A left swipe now has to pass 96dp —
  further than the row's own half-width default — and the row **buzzes** at the moment it
  does, so the threshold is felt rather than guessed. Releasing past it asks for
  confirmation; releasing before it settles back with nothing happening at all. The
  confirmation dialog remains the way a deletion happens, so the row still never dismisses
  itself.

## [0.1.0-alpha.6] — the categories that went missing, and a shorter picker

An alpha with a regression fix and a change to the currency picker.

### Fixed

- **The entry form could open with no category buttons at all.** 0.1.0-alpha.5 made the
  form stop starting a new pair of background readers every time it opened, which was the
  point — but the form also resets its state on every open, and a Room flow only emits when
  it is first collected and then when the table is *written*. So the open that started the
  readers showed the categories, and later opens showed none: nothing had changed in the
  database to make the flow speak again, and everything appeared normal as soon as
  something did (which is why it looked intermittent). Each open now reads the categories
  and the currency symbol once as well, and the readers keep them current while the form is
  up. The same reset had been hiding the chosen currency symbol behind `$` on every open
  after the first.

### Changed

- **The currency picker offers signs and nothing else.** It used to print the currencies
  sharing each sign beside it ("USD / CAD / AUD / NZD / HKD / SGD / TWD"), which read as
  though the row were choosing a currency rather than a sign — and most of these signs are
  shared by currencies the list could never name completely. The list is now nine signs:
  `$`, `€`, `¥`, `£`, `₽`, `₹`, `₩`, `₺` and `R$`. A stored sign that is no longer on offer
  is repaired to the locale default, exactly as before.

## [0.1.0-alpha.5] — the things a day of use runs into

An alpha of small fixes rather than features: what the audit turned up in the parts of the
app a person actually touches, plus two ways the app could be brought down by a file.

### Fixed

- **The newest entry was hidden behind the add button.** The home list had no bottom
  padding, so at the end of the scroll the last row — and the amount on its right, which is
  where the button sits — was drawn underneath it, and a tap there opened the add form
  instead of editing that transaction.
- **The keyboard covered the save action.** The entry form draws edge to edge and reserved
  no room for the keyboard, so with the note field focused the save action sat behind it
  with nothing to scroll. The form now takes the IME inset (which already contains the
  navigation-bar area, so the bar is not reserved twice).
- **Deleting every category left the form unable to save.** A category is only protected
  while transactions point at it, so the table can be emptied — and the form then had
  nothing to select and refused to save, with no way out but reinstalling. An empty table
  now gets a default of the type being recorded after a grace period, which is also what
  the other direction needs when it is empty on its own.
- **The form collected a new pair of background readers on every open.** `init()` re-runs
  whenever the form is composed, including during its exit animation, and each call started
  two never-ending collectors on an activity-scoped view model: a session of thirty entries
  left sixty of them re-querying on every write.
- **Home kept showing the previous month.** The month was captured when the view model was
  created, so a session left open or backgrounded across the first of a month showed the
  old one until the process restarted. It is re-read whenever the screen comes back to the
  foreground.
- **Deleting a transaction was only possible by swiping.** A swipe is not in the
  accessibility tree, so there was no way to delete a row without performing the gesture.
  The same deletion is now offered as an accessibility action.
- **Category initials were unreadable on most swatches, invisible on the yellow.** White on
  the offered palette ranges from 1.16:1 to 3.56:1 and misses even the 3:1 that large text
  asks for on seven of the ten colours. The letter now takes whichever of black and white
  reads better, which puts every colour between 5.9:1 and 18.1:1; a test holds that.
- **Dates and clock times were in English order everywhere.** Day headers, month titles and
  the statistics date used fixed patterns, so French read "août 11, mar." where French
  writes "mar. 11 août", and the time picker was pinned to a 24-hour clock regardless of the
  device. Both now come from the locale.
- **Counted text had no plural forms.** "1 transactions", "1 catégories" and the wrong
  Russian case for a single match are gone; the two strings that carry a count are plurals
  in all nine languages, Russian with its one/few/many forms.
- **A damaged settings file made the app unopenable.** DataStore reports corruption by
  throwing, the preferences are read on the startup path, and the file stays damaged — so
  every launch died the same way with no way back in short of clearing the app's data. The
  store now replaces a corrupt file with empty preferences.
- **A backup written by a later format could erase the ledger.** The document now carries a
  format marker, and a file declaring a higher one is refused instead of being read as an
  empty backup and restored over everything.

### Tests

- 128 unit tests, including a new one that compares the nine translation files against each
  other: same keys, same format arguments, and plurals with the forms each language needs.

## [0.1.0-alpha.4] — the breakdown adds up now

An alpha with one fix, and it is a fix to a figure rather than to a feature.

### Fixed

- **Re-typing a category moved its transactions to the other side of the statistics
  screen.** The per-category query summed a category's amounts and labelled the sum with
  the category's *current* type, while the totals and the balance above it count each
  transaction by the type it was **recorded** with. A category that has transactions can
  be re-typed at any time — the category dialog offers it — so one re-type was enough to
  make the two disagree. With one 30.00 expense and one 5.00 income on a category
  re-typed to income, the old query returned a single income row of 35.00: the income
  donut claimed 35.00 against an income card of 5.00, and the expense side of the
  breakdown was empty against an expense card of 30.00. The query now groups by
  `c.id, t.type`, so each amount stays on the side it was recorded on and the breakdown
  adds up to the cards. A category used in both directions appears once per breakdown,
  which is what the two charts want.

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

[0.1.0-rc.1]: https://github.com/stephen2754/budge/releases/tag/v0.1.0-rc.1
[0.1.0-beta.6]: https://github.com/stephen2754/budge/releases/tag/v0.1.0-beta.6
[0.1.0-beta.5]: https://github.com/stephen2754/budge/releases/tag/v0.1.0-beta.5
[0.1.0-beta.4]: https://github.com/stephen2754/budge/releases/tag/v0.1.0-beta.4
[0.1.0-beta.3]: https://github.com/stephen2754/budge/releases/tag/v0.1.0-beta.3
[0.1.0-beta.2]: https://github.com/stephen2754/budge/releases/tag/v0.1.0-beta.2
[0.1.0-beta.1]: https://github.com/stephen2754/budge/releases/tag/v0.1.0-beta.1
[0.1.0-alpha.9]: https://github.com/stephen2754/budge/releases/tag/v0.1.0-alpha.9
[0.1.0-alpha.8]: https://github.com/stephen2754/budge/releases/tag/v0.1.0-alpha.8
[0.1.0-alpha.7]: https://github.com/stephen2754/budge/releases/tag/v0.1.0-alpha.7
[0.1.0-alpha.6]: https://github.com/stephen2754/budge/releases/tag/v0.1.0-alpha.6
[0.1.0-alpha.5]: https://github.com/stephen2754/budge/releases/tag/v0.1.0-alpha.5
[0.1.0-alpha.4]: https://github.com/stephen2754/budge/releases/tag/v0.1.0-alpha.4
[0.1.0-alpha.3]: https://github.com/stephen2754/budge/releases/tag/v0.1.0-alpha.3
[0.1.0-alpha.2]: https://github.com/stephen2754/budge/releases/tag/v0.1.0-alpha.2
[0.1.0-alpha.1]: https://github.com/stephen2754/budge/releases/tag/v0.1.0-alpha.1
