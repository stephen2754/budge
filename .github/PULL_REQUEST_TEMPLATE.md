## What this changes

<!-- One or two sentences. What was wrong or missing, and what does this do about it? -->

## Related issue

<!-- "Fixes #123" if there is one. Small changes with no issue are fine too. -->

## Checklist

- [ ] `./gradlew :app:testDebugUnitTest` passes.
- [ ] The change does one thing. If it does two, it is probably two pull requests.
- [ ] If a string resource changed: all nine locales still have the same keys, the same format
      specifiers, and no unescaped ASCII apostrophe (the resource compiler rejects one).
- [ ] If counted text changed: it is a `<plurals>`, not a `<string>` with a number in it.
- [ ] If the UI changed: a screenshot is attached.
- [ ] No new dependency, permission, or network call — or, if there is one, it is explained
      above and in the README's privacy section.
- [ ] Comments explain *why*, not *what*. Code that reads clearly does not need a comment
      saying what it does.
