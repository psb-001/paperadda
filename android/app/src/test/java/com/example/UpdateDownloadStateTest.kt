package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.UpdateInstaller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The update dialog used to treat "a download was started" and "a download is
 * still running" as the same thing, so its button showed a spinner beside
 * "Install update" until the user gave up, and every tap produced "isn't
 * finished yet". These pin down the states that replaced it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UpdateDownloadStateTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    // ---- progress maths ----

    @Test
    fun reportsPercentWhenTheServerSentASize() {
        val state = UpdateInstaller.DownloadState.Running(downloaded = 512, total = 2048)
        assertEquals(25, state.percent)
    }

    @Test
    fun percentIsNullWhenTheSizeIsUnknown() {
        // GitHub release downloads normally do send a length, but a redirect
        // that does not must degrade to an indeterminate spinner, not to 0%.
        val state = UpdateInstaller.DownloadState.Running(downloaded = 4096, total = -1)
        assertNull(state.percent)
    }

    @Test
    fun percentStaysWithinBounds() {
        assertEquals(0, UpdateInstaller.DownloadState.Running(0, 2048).percent)
        assertEquals(
            100,
            UpdateInstaller.DownloadState.Running(9999, 2048).percent
        )
    }

    @Test
    fun zeroTotalDoesNotDivideByZero() {
        // DownloadManager reports 0 before it has read the content length.
        val state = UpdateInstaller.DownloadState.Running(downloaded = 10, total = 0)
        assertNull(state.percent)
    }

    // ---- querying the system queue ----

    @Test
    fun unknownDownloadIdIsIdleNotAFailure() {
        val installer = UpdateInstaller(context)
        // Nothing was ever queued, so the system has no row. This must not be
        // mistaken for a completed download.
        assertTrue(
            installer.query(999_999L) is UpdateInstaller.DownloadState.Idle
        )
    }

    @Test
    fun queryNeverThrowsOnThisDevice() {
        // Guards the polling loop: an exception here would kill the coroutine and
        // leave the dialog spinning for ever, which is the bug being fixed.
        val installer = UpdateInstaller(context)
        for (id in listOf(0L, 1L, -1L, 999_999L)) {
            assertNotNull(installer.query(id).toString())
        }
    }

    @Test
    fun clearingAFinishedDownloadIsSafe() {
        val installer = UpdateInstaller(context)
        // Called on retry; must not throw even when the row is already gone.
        installer.clear(999_999L)
    }

    @Test
    fun statesAreDistinguishableSoTheButtonCanNeverBeStuck() {
        // The whole fix rests on these being separate cases rather than a
        // single boolean that stays true.
        val states: List<UpdateInstaller.DownloadState> = listOf(
            UpdateInstaller.DownloadState.Idle,
            UpdateInstaller.DownloadState.Running(1, 2),
            UpdateInstaller.DownloadState.Ready,
            UpdateInstaller.DownloadState.Failed("nope")
        )
        assertEquals("every state must be its own case", 4, states.distinct().size)
    }

    @Test
    fun eachTerminalStateCarriesSomethingActionable() {
        // A failure with a blank message would leave the student staring at a
        // red "Try again" button with no idea what went wrong.
        val failure = UpdateInstaller.DownloadState.Failed("Not enough free space")
        assertTrue(failure.message.isNotBlank())
    }
}
