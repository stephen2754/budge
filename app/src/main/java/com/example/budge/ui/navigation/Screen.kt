package com.example.budge.ui.navigation

/**
 * Identity of the app's three pager pages.
 *
 * The app has no `NavHost`: the tabs live in a `HorizontalPager` and the entry and
 * category screens are overlays, so these objects exist to name a page rather than
 * to address a route. The unused route strings and `createRoute` helper that used to
 * live here — along with the `Entry`/`Categories` route entries nothing referenced —
 * have been removed.
 */
sealed class Screen {
    data object Home : Screen()

    data object Stats : Screen()

    data object Settings : Screen()
}
