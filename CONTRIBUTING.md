# Contributing to Budge

Bug reports, translations and pull requests are all welcome. Issues written in **Chinese or
English** are equally fine — answer in whichever you are comfortable with.

## Reporting a bug

Use the [bug report form](https://github.com/stephen2754/budge/issues/new?template=bug_report.yml).
It asks for the things that make a report actionable:

- **the app version**, exactly as it reads in Settings → the version row (`0.1.1`, `0.1.0-beta.6`, …)
- **what you did and what happened**, and what you expected instead
- **the shortest steps that reproduce it** — or a note that it only happens sometimes, which is
  useful in itself
- **your Android version and device**
- **how you installed the build**, because the update path behaves differently for an APK you
  opened by hand and one installed from inside the app

The app writes no logs of its own and sends nothing anywhere, so there is no log file to
attach. A screenshot, or the exact wording of an error message, is the most useful thing you
can add.

Search the existing issues first. If yours is already reported, a comment with your version and
device is more valuable than a duplicate.

## Suggesting a change

Use the [feature request form](https://github.com/stephen2754/budge/issues/new?template=feature_request.yml).
Describe the problem you are in before the solution you have in mind — the problem is the part
that decides whether a change is worth making. Budge is deliberately small: a suggestion that
keeps it small is much easier to accept than one that turns it into a platform. The README's
"Not implemented yet" list says what is already planned.

## Building it

You need JDK 17 and an Android SDK with API 35.

```bash
export JAVA_HOME=/path/to/jdk-17
./gradlew :app:assembleRelease     # the APK lands in app/build/outputs/apk/release/
./gradlew :app:testDebugUnitTest   # the whole test suite; this is what CI runs
```

The project has a single `release` build type. Without a `keystore.properties` the build still
succeeds and produces an **unsigned** APK — that is expected, not a broken checkout. Signing
material is never committed, and a pull request must not add any.

## The rules that keep the build green

These are not style preferences; each one breaks the build or the app if ignored.

1. **No unescaped ASCII apostrophe (`'`) in a string resource.** Android's resource compiler
   rejects it and the build fails. Rephrase instead of escaping where you can.
2. **Counted text is a `<plurals>`, not a `<string>` with a number in it.** Languages need
   different plural forms; English and Russian need different numbers of them.
3. **All nine locales keep the same key set.** `values/`, `values-zh`, `values-fr`, `values-de`,
   `values-es`, `values-ru`, `values-ja`, `values-it`, `values-pt`. The one deliberate exception
   is `release_notes_*`, which exists in English and Chinese only; the other seven fall back to
   English.
4. **Format specifiers must match across locales.** `%1$s` in English is `%1$s` everywhere, in
   the same order.
5. **A tag must stay a version.** The update check reads the version from the release tag; a tag
   it cannot parse is dropped from the list entirely, which hides the release from users.

## The voice for user-facing copy

Copy is part of the product, and it is read by people who are looking at their money. Write the
way a careful developer writes release notes for their own app:

- one idea per sentence; concrete verbs ("fixed", "no longer", "keeps")
- no semicolon chains carrying five clauses in one breath
- no "Daily use fixes: A, B, C" colon-and-list framing
- no "Neither…", "not X but Y", or three-item lists used for rhythm
- no sentences that narrate the app's own honesty; if a limit matters, state the limit
- Chinese: no 分号串, no 四字并列堆叠, no 书面语 padding such as 此外/从而/以便

Say exactly what is true. A privacy statement or a limitation is not a place for a nicer
sentence — if the wording and the behaviour disagree, the wording is the bug.

## Translations

A new locale means a new `values-<code>/strings.xml` with the same keys, the same specifiers
and no apostrophes, plus adding the locale to `resourceConfigurations` in `app/build.gradle.kts`
and to the README. Partial translations are welcome: a missing key falls back to English, so
translate as much as you can rather than nothing.

## Pull requests

Small and focused. One change per pull request; if it does two things, it is probably two pull
requests. Before opening one:

- run `./gradlew :app:testDebugUnitTest`
- fill in the pull request template — the checklist is short and it is the review
- attach a screenshot for anything visual
- explain any new dependency, permission or network call. The app's promise is that it makes
  two requests, both started by you, and carries no identifier; that promise is the product

Comments in code explain *why* something is the way it is, never *what* the next line does.

## Licence

Budge is licensed under the [Apache License 2.0](LICENSE). By contributing you agree that your
contribution is licensed under the same terms. There is no CLA and no copyright assignment.
