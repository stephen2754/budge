package com.example.budge.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import com.example.budge.BuildConfig
import com.example.budge.R
import com.example.budge.data.prefs.Currencies
import com.example.budge.data.prefs.Prefs
import com.example.budge.data.update.OpenSourceComponent
import com.example.budge.data.update.AppVersion
import com.example.budge.data.update.ReleaseNotes
import com.example.budge.data.update.ReleaseChannel
import com.example.budge.data.update.UpdateFailure
import com.example.budge.data.update.UpdateStatus
import com.example.budge.data.update.openSourceComponents
import com.example.budge.ui.releaseNotesText
import com.example.budge.ui.theme.pageWindowInsets

/** Localized name of a release channel. */
@StringRes
private fun channelLabel(channel: ReleaseChannel): Int =
    when (channel) {
        ReleaseChannel.ALPHA -> R.string.channel_alpha
        ReleaseChannel.BETA -> R.string.channel_beta
        ReleaseChannel.RC -> R.string.channel_rc
        ReleaseChannel.STABLE -> R.string.channel_stable
    }

/** What the channel promises, in one line, for the build the user is running. */
@StringRes
private fun channelNote(channel: ReleaseChannel): Int =
    when (channel) {
        ReleaseChannel.ALPHA -> R.string.channel_alpha_note
        ReleaseChannel.BETA -> R.string.channel_beta_note
        ReleaseChannel.RC -> R.string.channel_rc_note
        ReleaseChannel.STABLE -> R.string.channel_stable_note
    }

/**
 * An alert dialog whose two actions sit at opposite ends of the bottom row.
 *
 * Material's own `AlertDialog` lays its buttons out right-aligned, which puts a second action
 * immediately beside "Close" rather than in the dialog's own bottom-left corner — and a
 * second action is not the same kind of thing as closing. Only the button row is replaced
 * here; everything else comes from `AlertDialogContent`, so the shape, colours, padding and
 * typography stay Material's.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CorneredAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    startButton: (@Composable () -> Unit)? = null,
    endButton: @Composable () -> Unit,
) {
    // Material's own dialog markup is internal to the library, so this is the same shell
    // built from the public pieces: the container, shape and elevations are the Material 3
    // ones, and only the button row differs.
    BasicAlertDialog(onDismissRequest = onDismissRequest) {
        Surface(
            // Max, not Min. The minimum intrinsic width of a paragraph is its longest
            // *word*, so sizing to that made the dialog as narrow as one word and left the
            // buttons fighting over the space: in German "Open-Source-Komponenten" squeezed
            // "Schliessen" onto three lines. The maximum intrinsic width is the width the
            // content actually wants, so a short dialog stays compact and a long one gets the
            // room it needs — still capped by the window, which is what keeps it on screen.
            modifier = Modifier.width(IntrinsicSize.Max),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                    LocalTextStyle provides MaterialTheme.typography.headlineSmall,
                ) {
                    title()
                }
                Spacer(modifier = Modifier.height(16.dp))
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                    LocalTextStyle provides MaterialTheme.typography.bodyMedium,
                ) {
                    // The text takes the height that is left and no more. Without this a long
                    // release history grew the dialog past the bottom of the screen and took
                    // the buttons with it; `fill = false` lets a short text stay short rather
                    // than stretching the dialog to fill the window.
                    Box(modifier = Modifier.weight(1f, fill = false)) { text() }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // An empty box reserves the corner, so the closing button stays where it
                    // is whether or not there is anything opposite it.
                    Box { startButton?.invoke() }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box { endButton() }
                }
            }
        }
    }
}

/**
 * A dialog button whose label stays on one line.
 *
 * A label squeezed into "Sche / liess / en" by whatever sits opposite it is not a label any
 * more. The dialog is now wide enough for its buttons, and this is the guarantee: the
 * address of the component list may shorten with an ellipsis in a language that spells it
 * longer still, but the button that closes the window never breaks in half.
 */
@Composable
private fun DialogButton(
    text: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * One release in a changelog window: what it is, and what it changed.
 *
 * [currentVersion] marks the installed build, which only makes sense in a list of several —
 * the version window is already about the installed build and passes null.
 */
@Composable
private fun ReleaseEntry(
    release: ReleaseNotes,
    currentVersion: AppVersion?,
) {
    Text(
        text = "${release.version}  ·  ${stringResource(channelLabel(release.version.channel))}",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    if (release.version == currentVersion) {
        Text(
            text = stringResource(R.string.changelog_current),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = stringResource(release.notesRes),
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** The one line under "Check for updates" describing the last check. */
@Composable
private fun updateStatusLabel(status: UpdateStatus): String =
    when (status) {
        // Nothing has been checked in this process yet: say nothing.
        UpdateStatus.Idle -> ""
        UpdateStatus.Checking -> stringResource(R.string.update_status_checking)
        UpdateStatus.NotConfigured -> stringResource(R.string.update_status_unconfigured)
        UpdateStatus.NoReleases -> stringResource(R.string.update_status_no_releases)
        is UpdateStatus.UpToDate -> stringResource(R.string.update_status_up_to_date)
        is UpdateStatus.Available -> stringResource(R.string.update_status_available, status.release.version.toString())
        is UpdateStatus.Unreachable ->
            when (status.failure) {
                // Each failure says what happened, because each calls for something
                // different: patience, a retry, a corrected address, or a bug report.
                UpdateFailure.NETWORK -> stringResource(R.string.update_status_unreachable)
                UpdateFailure.TIMEOUT -> stringResource(R.string.update_status_timeout)
                UpdateFailure.RATE_LIMITED -> stringResource(R.string.update_status_rate_limited)
                UpdateFailure.NOT_FOUND -> stringResource(R.string.update_status_not_found)
                UpdateFailure.PARSE -> stringResource(R.string.update_status_unreadable)
                UpdateFailure.HTTP ->
                    status.statusCode
                        ?.let { stringResource(R.string.update_status_http_error, it) }
                        ?: stringResource(R.string.update_status_unreachable)
            }
    }

/**
 * Hands a release page to the browser — the app's only outward-facing action.
 *
 * A device with no browser cannot download anything anyway, so a failed start is
 * swallowed rather than reported: there is no second way to fetch the file.
 */
private fun openReleasePage(
    context: Context,
    url: String,
) {
    // The address is taken from a remote response, so it is not opened on trust: only an
    // https GitHub page is handed to a browser. A release list is not a place to accept a
    // URL from and pass on, whatever it parses to.
    if (!url.startsWith("https://github.com/")) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

/**
 * Language choices, each named in its own language so the list is legible whichever
 * language the app is currently in. Only "follow the system" is translated, because
 * it names no language of its own.
 *
 * Ordered by language code — de, en, es, fr, it, ja, pt, ru, zh — which is the order the
 * names read in as well: the Latin ones fall alphabetically, and the two written in other
 * scripts land where their code puts them instead of being sorted to one end or, as
 * before, put first because that is where the app happens to be developed. The list used
 * to start with Chinese, which is not an order at all.
 */
private val languageEndonyms =
    listOf(
        Prefs.GERMAN to "Deutsch",
        Prefs.ENGLISH to "English",
        Prefs.SPANISH to "Español",
        Prefs.FRENCH to "Français",
        Prefs.ITALIAN to "Italiano",
        Prefs.JAPANESE to "日本語",
        Prefs.PORTUGUESE to "Português",
        Prefs.RUSSIAN to "Русский",
        Prefs.CHINESE to "中文",
    )

/** The endonym of the selected language, or the translated "follow system" label. */
@Composable
private fun languageLabel(code: String): String =
    languageEndonyms.firstOrNull { it.first == code }?.second
        ?: stringResource(R.string.settings_language_system)

/**
 * Settings screen: lets the user pick a currency symbol, theme mode and app
 * language, and manage their data (export / import / clear all). Reads
 * [SettingsViewModel.uiState] to render the persisted preferences and the
 * dialogs that back them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToCategories: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val updateStatus by updateViewModel.status.collectAsStateWithLifecycle()
    val betaOffer by updateViewModel.betaOffer.collectAsStateWithLifecycle()
    val updateDownload by updateViewModel.download.collectAsStateWithLifecycle()

    // Whether an install would need the platform's permission is a question for the system,
    // so it is asked again whenever the screen comes back — the user may have just granted it
    // in the settings screen this dialog sent them to.
    var permissionCheck by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionCheck++ }
    val canInstallUpdates = remember(permissionCheck) { updateViewModel.canInstallPackages() }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var showChangelog by remember { mutableStateOf(false) }
    var showUpdateHistory by remember { mutableStateOf(false) }
    var showAlphaHistory by remember { mutableStateOf(false) }
    var showComponents by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }
    var showUpdateResult by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Export: system picker lets the user choose where to save the JSON backup.
    val exportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            uri?.let { viewModel.exportRecords(context, it) }
        }

    // Import: the picked URI is only validated here; the actual overwrite
    // happens after the user confirms the "overwrite" dialog.
    val importLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            uri?.let { viewModel.onImportPicked(context, it) }
        }

    // One-shot message channel: show the message in a snackbar, then clear it
    // so it is not re-shown on the next recomposition.
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.settings_title)) })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // The bottom system inset belongs to the navigation bar laid out below this
        // page, not to the page itself.
        contentWindowInsets = pageWindowInsets,
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding())
                    // Without this the column is simply clipped: "About" and everything
                    // under it were unreachable on a normal phone.
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            SettingsSection(title = stringResource(R.string.settings_currency)) {
                SettingsItem(
                    title = stringResource(R.string.settings_currency_symbol),
                    subtitle = uiState.currencySymbol,
                    onClick = { showCurrencyDialog = true },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSection(title = stringResource(R.string.settings_appearance)) {
                SettingsItem(
                    title = stringResource(R.string.settings_theme),
                    subtitle =
                        when (uiState.theme) {
                            Prefs.LIGHT -> stringResource(R.string.settings_theme_light)
                            Prefs.DARK -> stringResource(R.string.settings_theme_dark)
                            else -> stringResource(R.string.settings_theme_system)
                        },
                    onClick = { viewModel.cycleTheme() },
                )
                SettingsItem(
                    title = stringResource(R.string.settings_language),
                    subtitle = languageLabel(uiState.language),
                    onClick = { showLanguageDialog = true },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // The only route into category management. It used to be reachable
            // only through a StatsScreen callback that was passed in and never
            // invoked, so adding a category had no entry point at all.
            SettingsSection(title = stringResource(R.string.settings_categories)) {
                SettingsItem(
                    title = stringResource(R.string.category_title),
                    subtitle = "",
                    onClick = onNavigateToCategories,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSection(title = stringResource(R.string.settings_data)) {
                SettingsItem(
                    title = stringResource(R.string.settings_export),
                    subtitle = "",
                    onClick = { exportLauncher.launch("budge_records.json") },
                )
                SettingsItem(
                    title = stringResource(R.string.settings_import),
                    subtitle = "",
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                )
                SettingsItem(
                    title = stringResource(R.string.settings_clear_all),
                    subtitle = "",
                    onClick = { showClearAllDialog = true },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSection(title = stringResource(R.string.settings_about)) {
                SettingsItem(
                    title = stringResource(R.string.settings_version),
                    subtitle = "${BuildConfig.VERSION_NAME} · ${stringResource(channelLabel(updateViewModel.channel))}",
                    onClick = { showChangelog = true },
                )
                SettingsItem(
                    title = stringResource(R.string.settings_licenses),
                    subtitle = "",
                    onClick = { showLicenses = true },
                )
                SettingsItem(
                    title = stringResource(R.string.settings_check_update),
                    subtitle = updateStatusLabel(updateStatus),
                    onClick = {
                        updateViewModel.checkForUpdate()
                        showUpdateResult = true
                    },
                )
            }
        }
    }

    if (showCurrencyDialog) {
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text(stringResource(R.string.settings_currency_symbol)) },
            text = {
                // Nine rows, so the list scrolls rather than growing past the dialog on a
                // short screen or at a large font scale.
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Currencies.choices.forEach { token ->
                        // The sign alone, deliberately: see Currencies.choices for why the
                        // currency names are not listed here.
                        val selected = uiState.currencySymbol == token
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = selected,
                                        role = Role.RadioButton,
                                        onClick = {
                                            viewModel.updateCurrencySymbol(token)
                                            showCurrencyDialog = false
                                        },
                                    ).padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = token,
                                style = MaterialTheme.typography.titleLarge,
                                color =
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            if (selected) {
                                Text(
                                    text = "✓",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCurrencyDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showLanguageDialog) {
        val options =
            listOf(Prefs.FOLLOW_SYSTEM to stringResource(R.string.settings_language_system)) +
                languageEndonyms
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.settings_language)) },
            text = {
                Column {
                    options.forEach { (language, label) ->
                        val selected = uiState.language == language
                        Text(
                            text = if (selected) "$label  ✓" else label,
                            style = MaterialTheme.typography.bodyLarge,
                            color =
                                if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.updateLanguage(language)
                                        showLanguageDialog = false
                                    }.padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text(stringResource(R.string.settings_clear_all_title)) },
            text = { Text(stringResource(R.string.settings_clear_all_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllRecords()
                    showClearAllDialog = false
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showChangelog) {
        CorneredAlertDialog(
            onDismissRequest = { showChangelog = false },
            title = { Text(stringResource(R.string.changelog_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    // What the installed channel means, so a test build says so plainly,
                    // followed by what *this* build changed. The earlier releases are one
                    // button away rather than listed here, so the window cannot grow into a
                    // scroll of everything ever published when the reader wanted one answer.
                    Text(
                        text = stringResource(channelNote(updateViewModel.channel)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    val current = updateViewModel.currentRelease
                    if (current == null) {
                        // A build whose own record was never added to the shipped list: say
                        // so rather than showing another release's notes as if they were its.
                        Text(stringResource(R.string.changelog_empty))
                    } else {
                        ReleaseEntry(current, currentVersion = null)
                    }
                }
            },
            startButton = {
                DialogButton(stringResource(R.string.update_history)) { showUpdateHistory = true }
            },
            endButton = {
                DialogButton(stringResource(R.string.close)) { showChangelog = false }
            },
        )
    }

    // The releases this channel is allowed to see, newest first — the same rule the update
    // check uses, so the two can never disagree about what exists. One level down from the
    // version window rather than inside it: what a reader wants from "what am I running" and
    // from "what came before" are different, and only the second one grows without limit.
    if (showUpdateHistory) {
        val history = if (showAlphaHistory) updateViewModel.historyWithAlphas else updateViewModel.history
        CorneredAlertDialog(
            onDismissRequest = {
                showUpdateHistory = false
                showAlphaHistory = false
            },
            title = { Text(stringResource(R.string.update_history)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (history.isEmpty()) {
                        Text(stringResource(R.string.changelog_empty))
                    } else {
                        history.forEachIndexed { index, release ->
                            if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                            ReleaseEntry(release, currentVersion = updateViewModel.currentVersion)
                        }
                    }
                }
            },
            startButton =
                // Only a beta build has anything to add: an alpha already sees these, and a
                // stable build is not on the test programme. The rule lives in alphasFor(),
                // so an empty list is what says there is no button to show.
                if (updateViewModel.alphas.isEmpty()) {
                    null
                } else {
                    {
                        // A tap only reveals and hides: nothing else on the screen moves, and
                        // the list stays in the same order because it is merged and sorted
                        // once rather than appended.
                        DialogButton(
                            text =
                                stringResource(
                                    if (showAlphaHistory) {
                                        R.string.update_exclude_alpha
                                    } else {
                                        R.string.update_include_alpha
                                    },
                                ),
                        ) { showAlphaHistory = !showAlphaHistory }
                    }
                },
            endButton = {
                DialogButton(stringResource(R.string.close)) {
                    showUpdateHistory = false
                    showAlphaHistory = false
                }
            },
        )
    }

    if (showLicenses) {
        // Only what the reader has to know: where the records are, and the one thing the app
        // does over the network. The components are a page of their own behind the button
        // below, because a licence window is not where anyone reads a dependency tree.
        CorneredAlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text(stringResource(R.string.settings_licenses)) },
            text = {
                Text(
                    text = stringResource(R.string.licenses_intro),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            startButton = {
                DialogButton(stringResource(R.string.licenses_components)) { showComponents = true }
            },
            endButton = {
                DialogButton(stringResource(R.string.close)) { showLicenses = false }
            },
        )
    }

    if (showComponents) {
        CorneredAlertDialog(
            onDismissRequest = { showComponents = false },
            title = { Text(stringResource(R.string.licenses_components)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    openSourceComponents.forEach { component ->
                        ComponentLicence(component, onOpen = { url -> openProjectPage(context, url) })
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.licenses_full_text_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            endButton = {
                DialogButton(stringResource(R.string.close)) { showComponents = false }
            },
        )
    }

    if (showUpdateResult) {
        val status = updateStatus
        val download = updateDownload
        CorneredAlertDialog(
            onDismissRequest = { showUpdateResult = false },
            title = { Text(stringResource(R.string.settings_check_update)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = updateStatusLabel(status),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (status is UpdateStatus.Available) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "${status.current} → ${status.release.version}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        // What the download is doing replaces the notes while it runs: a
                        // progress bar under a screenful of release notes is a progress bar
                        // nobody sees.
                        when (download) {
                            is UpdateDownload.Downloading -> {
                                Spacer(modifier = Modifier.height(12.dp))
                                val fraction =
                                    if (download.total > 0L) {
                                        (download.bytes.toFloat() / download.total).coerceIn(0f, 1f)
                                    } else {
                                        null
                                    }
                                Text(
                                    text =
                                        stringResource(R.string.update_downloading) +
                                            if (fraction != null) "  ${(fraction * 100).toInt()}%" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                if (fraction != null) {
                                    LinearProgressIndicator(
                                        progress = { fraction },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                            }

                            UpdateDownload.Verifying -> {
                                Spacer(modifier = Modifier.height(12.dp))
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.update_verifying),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }

                            UpdateDownload.Ready -> {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.update_verified),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (!canInstallUpdates) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(R.string.update_need_install_permission),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }

                            UpdateDownload.DownloadFailed -> {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.update_download_failed),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }

                            UpdateDownload.VerificationFailed -> {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.update_hash_mismatch),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }

                            UpdateDownload.Idle -> {
                                status.release.notes?.let { notes ->
                                    Spacer(modifier = Modifier.height(8.dp))
                                    // The API sends the notes as Markdown; the dialog can only
                                    // show text, so the markup comes off before it is shown.
                                    Text(
                                        text = remember(notes) { releaseNotesText(notes) },
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            startButton = {
                val offer = betaOffer
                when {
                    // While a download runs, the corner holds the way out of it.
                    download is UpdateDownload.Downloading ->
                        DialogButton(stringResource(R.string.cancel)) { updateViewModel.cancelDownload() }
                    // The way onto the test programme, in the dialog's own bottom-left corner
                    // and level with the buttons opposite. Offered only by a stable build, only
                    // while a beta is actually ahead of it, and decided by the check that just
                    // ran rather than remembered from an earlier one.
                    offer != null && download is UpdateDownload.Idle ->
                        DialogButton(stringResource(R.string.update_join_beta)) {
                            openReleasePage(context, offer.pageUrl)
                            showUpdateResult = false
                        }
                }
            },
            endButton = {
                // Nothing to press while the file is arriving or being checked: the bar above
                // says what is happening, and both states end on their own.
                if (download is UpdateDownload.Downloading || download is UpdateDownload.Verifying) {
                    Row {}
                } else {
                    Row {
                        DialogButton(stringResource(R.string.close)) { showUpdateResult = false }
                        if (status is UpdateStatus.Available) {
                            when {
                                download is UpdateDownload.Ready ->
                                    DialogButton(stringResource(R.string.update_install)) {
                                        // Asked again at the moment it matters: the answer can
                                        // have changed since this dialog was drawn.
                                        if (updateViewModel.canInstallPackages()) {
                                            updateViewModel.installDownloaded()
                                        } else {
                                            updateViewModel.requestInstallPermission()
                                        }
                                    }

                                download is UpdateDownload.DownloadFailed ||
                                    download is UpdateDownload.VerificationFailed ->
                                    DialogButton(stringResource(R.string.update_retry)) { updateViewModel.downloadUpdate() }

                                // A release whose answer named an APK and its hash can be
                                // fetched here; anything else — the release feed, an answer
                                // with no hash to check — is a page to open, as before.
                                status.release.assetUrl != null ->
                                    DialogButton(stringResource(R.string.update_download_install)) { updateViewModel.downloadUpdate() }

                                else ->
                                    DialogButton(stringResource(R.string.update_download)) {
                                        openReleasePage(context, status.release.pageUrl)
                                        showUpdateResult = false
                                    }
                            }
                        }
                    }
                }
            },
        )
    }

    if (uiState.showImportConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelImport() },
            title = { Text(stringResource(R.string.settings_import_overwrite)) },
            text = { Text(stringResource(R.string.settings_import_overwrite_confirm)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmImport() }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelImport() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/** One attributed library: its name, its licence and where the licence lives. */
@Composable
private fun ComponentLicence(
    component: OpenSourceComponent,
    onOpen: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(
            text = component.name,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = component.license,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // The address is the link: it is what a reader needs in order to check the licence
        // for themselves, and it is a constant of this build rather than anything a response
        // can influence.
        Text(
            text = component.url,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            modifier =
                Modifier
                    .clickable { onOpen(component.url) }
                    .padding(vertical = 4.dp),
        )
    }
}

/**
 * Opens a project page from the component list.
 *
 * Held to https, though not to one host: unlike the release page — which arrives from a
 * response and is restricted to github.com — these addresses ship with the build. The scheme
 * is still checked, because "it is a constant today" is no reason to hand an arbitrary string
 * to a browser.
 */
private fun openProjectPage(
    context: Context,
    url: String,
) {
    if (!url.startsWith("https://")) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

/** Group of [SettingsItem]s rendered under a primary-colored section title. */
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        content()
    }
}

/**
 * A single settings row: an optional subtitle is shown beneath the title only
 * when it is non-empty (e.g. the current theme). A null [onClick] renders a
 * read-only row that is not focusable and carries no click action.
 */
@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    // `role = Role.Button` so a screen reader announces the row as
                    // an actionable control rather than plain text.
                    if (onClick != null) {
                        Modifier.clickable(role = Role.Button, onClick = onClick)
                    } else {
                        Modifier
                    },
                )
                .padding(vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
        )
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
