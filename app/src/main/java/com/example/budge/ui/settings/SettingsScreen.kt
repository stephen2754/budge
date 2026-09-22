package com.example.budge.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.example.budge.data.update.ReleaseChannel
import com.example.budge.data.update.UpdateFailure
import com.example.budge.data.update.UpdateStatus
import com.example.budge.data.update.openSourceComponents
import com.example.budge.ui.theme.pageWindowInsets

/** Localized name of a release channel. */
@StringRes
private fun channelLabel(channel: ReleaseChannel): Int =
    when (channel) {
        ReleaseChannel.ALPHA -> R.string.channel_alpha
        ReleaseChannel.BETA -> R.string.channel_beta
        ReleaseChannel.STABLE -> R.string.channel_stable
    }

/** What the channel promises, in one line, for the build the user is running. */
@StringRes
private fun channelNote(channel: ReleaseChannel): Int =
    when (channel) {
        ReleaseChannel.ALPHA -> R.string.channel_alpha_note
        ReleaseChannel.BETA -> R.string.channel_beta_note
        ReleaseChannel.STABLE -> R.string.channel_stable_note
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
                UpdateFailure.NOT_FOUND -> stringResource(R.string.update_status_not_found)
                else -> stringResource(R.string.update_status_unreachable)
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
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

/**
 * Language choices, each named in its own language so the list is legible whichever
 * language the app is currently in. Only "follow the system" is translated, because
 * it names no language of its own.
 */
private val languageEndonyms =
    listOf(
        Prefs.CHINESE to "中文",
        Prefs.ENGLISH to "English",
        Prefs.FRENCH to "Français",
        Prefs.GERMAN to "Deutsch",
        Prefs.SPANISH to "Español",
        Prefs.RUSSIAN to "Русский",
        Prefs.JAPANESE to "日本語",
        Prefs.ITALIAN to "Italiano",
        Prefs.PORTUGUESE to "Português",
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
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var showChangelog by remember { mutableStateOf(false) }
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
                Column {
                    Currencies.choices.forEach { token ->
                        // Naming the currency is the picker's job only: an amount
                        // in the app shows the sign alone. See Currencies.labels.
                        val codes = Currencies.labels[token].orEmpty()
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
                                style = MaterialTheme.typography.titleMedium,
                                color =
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                modifier = Modifier.width(40.dp),
                            )
                            // The code list wraps instead of being clipped: "$" covers five
                            // currencies, so this line is the longest thing in the dialog.
                            Text(
                                text = codes,
                                style = MaterialTheme.typography.bodyMedium,
                                color =
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                modifier = Modifier.weight(1f),
                            )
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
        AlertDialog(
            onDismissRequest = { showChangelog = false },
            title = { Text(stringResource(R.string.changelog_title)) },
            text = {
                val history = updateViewModel.history
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    // What the installed channel means, so a test build says so plainly
                    // before the list of changes.
                    Text(
                        text = stringResource(channelNote(updateViewModel.channel)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (history.isEmpty()) {
                        Text(stringResource(R.string.changelog_empty))
                    } else {
                        history.forEachIndexed { index, release ->
                            if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                            Text(
                                text = "${release.version}  ·  ${stringResource(channelLabel(release.version.channel))}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (release.version == updateViewModel.currentVersion) {
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
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChangelog = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }

    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text(stringResource(R.string.settings_licenses)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = stringResource(R.string.licenses_intro),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.licenses_third_party),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    openSourceComponents.forEach { component ->
                        ComponentLicence(component)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.licenses_full_text_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenses = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }

    if (showUpdateResult) {
        val status = updateStatus
        AlertDialog(
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
                        status.release.notes?.let { notes ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = notes, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                if (status is UpdateStatus.Available) {
                    TextButton(
                        onClick = {
                            openReleasePage(context, status.release.pageUrl)
                            showUpdateResult = false
                        },
                    ) {
                        Text(stringResource(R.string.update_download))
                    }
                } else {
                    TextButton(onClick = { showUpdateResult = false }) { Text(stringResource(R.string.close)) }
                }
            },
            dismissButton = {
                if (status is UpdateStatus.Available) {
                    TextButton(onClick = { showUpdateResult = false }) { Text(stringResource(R.string.close)) }
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
private fun ComponentLicence(component: OpenSourceComponent) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(
            text = component.name,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "${component.license} · ${component.url}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
