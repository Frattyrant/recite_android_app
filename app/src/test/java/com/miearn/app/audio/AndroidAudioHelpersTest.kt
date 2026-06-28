package com.miearn.app.audio

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AndroidAudioHelpersTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun assetProbeUsesStreamOpenForCompressedAssets() {
        assertTrue(
            AssetAvailability.exists(
                context.assets,
                "content/words_v1.json",
            ),
        )
        assertFalse(
            AssetAvailability.exists(
                context.assets,
                "audio/does_not_exist.ogg",
            ),
        )
    }

    @Test
    fun ttsInstallIntentIsSafeForAnApplicationContext() {
        val intent = TtsInstallIntent.create()

        assertEquals(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA, intent.action)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
}
