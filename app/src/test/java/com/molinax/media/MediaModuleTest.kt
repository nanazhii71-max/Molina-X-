package com.molinax.media

import android.content.Context
import android.view.Surface
import android.view.SurfaceHolder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.molinax.di.MediaModule
import com.molinax.media.mpv.MPVLib
import com.molinax.media.mpv.MPVPlaybackState
import com.molinax.media.mpv.MPVPlayerEngine
import com.molinax.media.ui.MpvVideoSurface
import com.molinax.player.AspectRatioMode
import com.molinax.ui.theme.MyApplicationTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaModuleTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testMediaModuleProvidesEngineSingleton() {
        val engine1 = MediaModule.provideMPVPlayerEngine(context)
        val engine2 = MediaModule.provideMPVPlayerEngine(context)

        assertNotNull("MediaModule should provide MPVPlayerEngine", engine1)
        assertNotNull("Second instance should not be null", engine2)
        assertEquals("Initial speed should be 1.0f", 1.0f, engine1.playbackState.value.playbackSpeed, 0.01f)
    }

    @Test
    fun testMPVLibCommandAndPropertyObservation() {
        MPVLib.create(context)
        MPVLib.init()

        var observedSpeed = 0.0
        val observer = object : MPVLib.EventObserver {
            override fun onEvent(eventId: Int) {}
            override fun onPropertyChange(property: String, value: Any?) {
                if (property == "speed" && value is Number) {
                    observedSpeed = value.toDouble()
                }
            }
        }

        MPVLib.addObserver(observer)
        MPVLib.setPropertyDouble("speed", 1.5)

        assertEquals("MPVLib speed property should update", 1.5, observedSpeed, 0.01)
        assertEquals("Property store should return speed", 1.5, MPVLib.getPropertyDouble("speed") ?: 0.0, 0.01)

        MPVLib.removeObserver(observer)
    }

    @Test
    fun testMPVPlayerEnginePlaybackTransitions() {
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)
        val engine = MPVPlayerEngine(context, testScope)

        // Load media
        engine.loadMedia("/storage/emulated/0/Movies/sample.mp4", "Sample Video")

        val stateAfterLoad = engine.playbackState.value
        assertEquals("Sample Video", stateAfterLoad.currentMediaTitle)
        assertEquals("/storage/emulated/0/Movies/sample.mp4", stateAfterLoad.currentMediaSource)

        // Test Speed
        engine.setPlaybackSpeed(1.25f)
        assertEquals(1.25f, engine.playbackState.value.playbackSpeed, 0.01f)

        // Test Volume Boost
        engine.setVolumeBoost(6)
        assertEquals(6, engine.playbackState.value.volumeBoostDb)

        // Test Aspect Ratio
        engine.setAspectRatio(AspectRatioMode.SIXTEEN_NINE)
        assertEquals(AspectRatioMode.SIXTEEN_NINE, engine.playbackState.value.aspectRatio)

        // Test Play/Pause toggle
        engine.play()
        assertTrue("Engine should be playing", engine.playbackState.value.isPlaying)

        engine.pause()
        assertFalse("Engine should be paused", engine.playbackState.value.isPlaying)

        engine.togglePlayPause()
        assertTrue("Engine should resume playing", engine.playbackState.value.isPlaying)

        // Test Stop
        engine.stop()
        assertFalse("Engine should stop playing", engine.playbackState.value.isPlaying)
        assertEquals(0L, engine.playbackState.value.currentPositionMs)

        engine.release()
    }

    @Test
    fun testMpvVideoSurfaceRendersControlsAndState() {
        var isPlaying by mutableStateOf(false)
        var currentPos by mutableStateOf(15000L)
        var speed by mutableStateOf(1.0f)
        var boost by mutableStateOf(0)
        var aspect by mutableStateOf(AspectRatioMode.FIT)

        composeTestRule.setContent {
            MyApplicationTheme {
                MpvVideoSurface(
                    state = MPVPlaybackState(
                        isPlaying = isPlaying,
                        currentPositionMs = currentPos,
                        durationMs = 60000L,
                        playbackSpeed = speed,
                        volumeBoostDb = boost,
                        aspectRatio = aspect,
                        currentMediaTitle = "Test MPV Stream.mkv",
                        currentMediaSource = "https://example.com/stream.mkv"
                    ),
                    onSurfaceCreated = {},
                    onSurfaceDestroyed = {},
                    onTogglePlayPause = { isPlaying = !isPlaying },
                    onSeekTo = { currentPos = it },
                    onSeekRelative = { currentPos += it * 1000L },
                    onSpeedChange = { speed = it },
                    onVolumeBoostChange = { boost = it },
                    onAspectRatioChange = { aspect = it },
                    showControls = true
                )
            }
        }

        // Verify surface container and overlays are present
        composeTestRule.onNodeWithTag("mpv_video_surface_container").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mpv_surface_view").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mpv_top_bar", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("mpv_bottom_deck", useUnmergedTree = true).assertIsDisplayed()

        // Verify media title
        composeTestRule.onNodeWithText("Test MPV Stream.mkv", substring = true, useUnmergedTree = true).assertIsDisplayed()

        // Verify transport controls
        composeTestRule.onNodeWithTag("mpv_play_pause_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mpv_rewind_10s_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mpv_forward_10s_button").assertIsDisplayed()

        // Click play/pause button
        composeTestRule.onNodeWithTag("mpv_play_pause_button").performClick()
        assertTrue("Playback should toggle to true", isPlaying)

        // Click rewind button
        composeTestRule.onNodeWithTag("mpv_rewind_10s_button").performClick()
        assertEquals(5000L, currentPos)

        // Click 1.5x speed chip
        composeTestRule.onNodeWithTag("mpv_speed_chip_1.5x").performClick()
        assertEquals(1.5f, speed, 0.01f)

        // Click volume boost button
        composeTestRule.onNodeWithTag("mpv_volume_boost_button").performClick()
        assertEquals(6, boost)
    }

    @Test
    fun testMpvVideoSurfaceEmptyState() {
        composeTestRule.setContent {
            MyApplicationTheme {
                MpvVideoSurface(
                    state = MPVPlaybackState(currentMediaSource = null),
                    onSurfaceCreated = {},
                    onSurfaceDestroyed = {},
                    onTogglePlayPause = {},
                    onSeekTo = {},
                    onSeekRelative = {},
                    onSpeedChange = {},
                    onVolumeBoostChange = {},
                    onAspectRatioChange = {},
                    showControls = true
                )
            }
        }

        // When source is null, empty state prompt is displayed
        composeTestRule.onNodeWithTag("mpv_empty_state", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("MPV Video Surface Ready", substring = true, useUnmergedTree = true).assertIsDisplayed()
    }
}
