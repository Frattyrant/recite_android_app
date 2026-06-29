package com.miearn.app.importing

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CompactDictionaryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun cleanInstalledDictionary() {
        File(context.noBackupFilesDir, "dictionaries").deleteRecursively()
    }

    @Test
    fun installsPackagedDatabaseAndFindsCommonWord() = runTest {
        val entry = CompactDictionary(context).lookup("  Fixture ")

        assertNotNull(entry)
        assertTrue(entry!!.translation.isNotBlank())
        assertTrue(entry.phonetic.isNotBlank())
    }
}
