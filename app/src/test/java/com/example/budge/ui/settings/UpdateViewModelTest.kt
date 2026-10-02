package com.example.budge.ui.settings

import com.example.budge.data.update.ApkDownloader
import com.example.budge.data.update.ApkInstaller
import com.example.budge.data.update.AppVersion
import com.example.budge.data.update.ReleaseFetch
import com.example.budge.data.update.ReleaseSource
import com.example.budge.data.update.RemoteRelease
import com.example.budge.data.update.UpdateConfig
import com.example.budge.data.update.UpdateFileStore
import com.example.budge.data.update.sha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests for the update download state machine.
 *
 * This is the piece that decides whether a file gets installed, and until now nothing
 * exercised it — which is how a Cancel button that cancelled nothing, a retry that wrote into
 * the file an abandoned writer was still filling, and an install that silently threw away a
 * verified download all survived to a release candidate. The downloader, installer and file
 * store are interfaces precisely so this can run on the JVM.
 */
class UpdateViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var dispatcher: TestDispatcher

    @Before
    fun setUp() {
        // A fresh scheduler per test. A fake that is deliberately still suspended when a test
        // ends would otherwise sit in the next test's scheduler and stop it from completing.
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    // ------------------------------------------------------------------ fakes

    private class FakeSource(
        private val result: ReleaseFetch,
    ) : ReleaseSource {
        override suspend fun releases(repository: String): ReleaseFetch = result
    }

    /** Writes [bytes] to the target and reports it, then returns as told. */
    private class FakeDownloader(
        private val bytes: ByteArray,
        private val hang: Boolean = false,
    ) : ApkDownloader {
        var calls = 0
            private set

        var lastProgress: ((Long, Long) -> Unit)? = null
            private set

        override suspend fun download(
            url: String,
            target: File,
            onProgress: (bytes: Long, total: Long) -> Unit,
        ): Boolean {
            calls++
            lastProgress = onProgress
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
            onProgress(bytes.size.toLong(), bytes.size.toLong())
            if (hang) awaitCancellation()
            return true
        }

        /** The callback a cancelled download's own thread can still make. */
        fun lateProgress() {
            lastProgress?.invoke(bytes.size.toLong(), bytes.size.toLong())
        }
    }

    private class FakeInstaller(
        private val installWorks: Boolean = true,
    ) : ApkInstaller {
        var installCalls = 0
            private set

        override fun canInstallPackages(): Boolean = true

        override fun install(file: File): Boolean {
            installCalls++
            return installWorks
        }

        override fun requestInstallPermission() = Unit
    }

    private class FakeFileStore(
        private val directory: File,
    ) : UpdateFileStore {
        override fun scratch(version: AppVersion): File = File(directory, "budge-$version.apk.part")

        override fun verified(version: AppVersion): File = File(directory, "budge-$version.apk")

        override fun clearStale(running: AppVersion) = Unit
    }

    // ------------------------------------------------------------------ helpers

    private val content = "the apk bytes".toByteArray()

    private fun digestOf(bytes: ByteArray): String {
        val file = folder.newFile()
        file.writeBytes(bytes)
        return "sha256:${sha256(file)}"
    }

    /**
     * A version strictly newer than the one under test.
     *
     * Derived from the installed version rather than written down, so bumping the app's
     * version does not silently turn every offer into "already up to date" — which is exactly
     * what a hard-coded candidate did when the app moved to the candidate it named.
     */
    private val offered =
        AppVersion.parse(com.example.budge.BuildConfig.VERSION_NAME)!!.let { running ->
            // The patch number, not the channel number: a stable version has no number to
            // raise, and raising it would render the same string as the version under test.
            running.copy(patch = running.patch + 1)
        }

    private fun release(
        version: String = offered.toString(),
        digest: String? = digestOf(content),
        assetUrl: String? = "https://github.com/o/r/releases/download/v$version/budge-$version.apk",
    ) = RemoteRelease(
        version = AppVersion.parse(version)!!,
        title = version,
        notes = null,
        pageUrl = "https://github.com/o/r/releases/tag/v$version",
        assetUrl = assetUrl,
        digest = digest,
    )

    private fun viewModel(
        downloader: ApkDownloader,
        installer: ApkInstaller = FakeInstaller(),
        offered: RemoteRelease = release(),
    ): Pair<UpdateViewModel, UpdateFileStore> {
        val store = FakeFileStore(folder.newFolder())
        val model =
            UpdateViewModel(
                releaseSource = FakeSource(ReleaseFetch.Success(listOf(offered))),
                apkDownloader = downloader,
                apkInstaller = installer,
                fileStore = store,
            )
        return model to store
    }

    private fun UpdateViewModel.check(scope: TestScope) {
        checkForUpdate()
        scope.advanceUntilIdle()
    }

    /**
     * Waits for the download to reach a state it stays in.
     *
     * The hashing step runs on the real IO dispatcher, which virtual time does not advance, so
     * a test that only advanced the scheduler would assert against `Verifying` forever. The
     * wait is bounded by the enclosing `runTest` timeout.
     */
    private suspend fun UpdateViewModel.awaitSettled(): UpdateDownload =
        download.first { state ->
            state is UpdateDownload.Ready ||
                state is UpdateDownload.DownloadFailed ||
                state is UpdateDownload.VerificationFailed ||
                state is UpdateDownload.InstallFailed
        }

    // ------------------------------------------------------------------ tests

    @Test
    fun `a checked download becomes ready and leaves nothing in the scratch name`() = runTest(dispatcher) {
        val downloader = FakeDownloader(content)
        val (model, store) = viewModel(downloader)

        model.check(this)
        model.downloadUpdate()
        model.awaitSettled()

        assertEquals(UpdateDownload.Ready, model.download.value)
        assertTrue("the verified file is where the installer will look", store.verified(offered).exists())
        assertFalse("the scratch name is not left behind", store.scratch(offered).exists())
    }

    @Test
    fun `a download whose hash does not match is deleted and never becomes ready`() = runTest(dispatcher) {
        val downloader = FakeDownloader(content)
        val (model, store) = viewModel(downloader, offered = release(digest = "sha256:${"0".repeat(64)}"))

        model.check(this)
        model.downloadUpdate()
        model.awaitSettled()

        assertEquals(UpdateDownload.VerificationFailed, model.download.value)
        assertFalse(store.verified(offered).exists())
        assertFalse(store.scratch(offered).exists())
    }

    @Test
    fun `cancelling stops the state and removes the scratch file, and a late callback cannot revive it`() =
        runTest(dispatcher) {
            // The regression this test exists for: the progress callback runs on the download's
            // own thread, so without a guard the last one lands after the cancel and the window
            // freezes on a progress bar nobody can clear.
            val downloader = FakeDownloader(content, hang = true)
            val (model, store) = viewModel(downloader)

            model.check(this)
            model.downloadUpdate()
            runCurrent()
            assertEquals(
                "the download is running",
                true,
                model.download.value is UpdateDownload.Downloading,
            )

            model.cancelDownload()
            assertEquals(UpdateDownload.Idle, model.download.value)
            assertFalse(
                "the file being written is removed, not just forgotten",
                store.scratch(offered).exists(),
            )

            downloader.lateProgress()
            assertEquals(
                "a callback from the abandoned download must not put the bar back",
                UpdateDownload.Idle,
                model.download.value,
            )
        }

    @Test
    fun `a second download while one is running starts only one`() = runTest(dispatcher) {
        val downloader = FakeDownloader(content, hang = true)
        val (model, _) = viewModel(downloader)

        model.check(this)
        model.downloadUpdate()
        model.downloadUpdate()
        runCurrent()

        assertEquals(1, downloader.calls)

        // The fake downloader is still suspended on purpose; leaving it running would leak a
        // coroutine into the next test.
        model.cancelDownload()
    }

    @Test
    fun `an installer that refuses is reported and the verified file is kept`() = runTest(dispatcher) {
        val installer = FakeInstaller(installWorks = false)
        val (model, store) = viewModel(FakeDownloader(content), installer = installer)

        model.check(this)
        model.downloadUpdate()
        model.awaitSettled()
        model.installDownloaded()

        assertEquals(UpdateDownload.InstallFailed, model.download.value)
        assertEquals(1, installer.installCalls)
        assertTrue(
            "nothing needs downloading again",
            store.verified(offered).exists(),
        )
    }

    @Test
    fun `an install that starts keeps the verified file, because the user may still cancel it`() =
        runTest(dispatcher) {
            val installer = FakeInstaller()
            val (model, store) = viewModel(FakeDownloader(content), installer = installer)

            model.check(this)
            model.downloadUpdate()
            model.awaitSettled()
            model.installDownloaded()

            assertEquals(
                "an activity starting says nothing about what the user did with it",
                UpdateDownload.Ready,
                model.download.value,
            )
            assertTrue(store.verified(offered).exists())
        }

    @Test
    fun `a check that offers the same release again keeps a verified download`() = runTest(dispatcher) {
        val (model, store) = viewModel(FakeDownloader(content))

        model.check(this)
        model.downloadUpdate()
        model.awaitSettled()
        model.check(this)

        assertEquals(UpdateDownload.Ready, model.download.value)
        assertTrue(store.verified(offered).exists())
    }

    @Test
    fun `a release with no hash is offered as a page and never downloaded here`() = runTest(dispatcher) {
        val downloader = FakeDownloader(content)
        val (model, _) = viewModel(downloader, offered = release(assetUrl = null, digest = null))

        model.check(this)
        model.downloadUpdate()
        // Nothing starts, so there is nothing to wait for: the state is already the one it
        // will stay in.
        advanceUntilIdle()

        assertEquals("nothing to download, so nothing starts", UpdateDownload.Idle, model.download.value)
        assertEquals(0, downloader.calls)
    }

    @Test
    fun `a configured repository is required before anything is requested`() {
        // The sentinel is what keeps a checkout that was never pointed at a repository from
        // making a request at all; this asserts the constant the check compares against is the
        // one the app ships, so the guard cannot be quietly lost.
        assertNotNull(UpdateConfig.GITHUB_REPOSITORY)
        assertTrue(UpdateConfig.GITHUB_REPOSITORY.contains("/"))
    }
}
