package com.miearn.app.reminder

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderManifestTest {
    private val manifest = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(File("src/main/AndroidManifest.xml"))

    @Test
    fun manifestKeepsBackgroundReminderWakeAndBootSupport() {
        val permissions = manifest.getElementsByTagName("uses-permission")
        val names = (0 until permissions.length).mapNotNull { index ->
            permissions.item(index).attributes?.getNamedItem("android:name")?.nodeValue
        }

        assertTrue(names.contains("android.permission.WAKE_LOCK"))
        assertTrue(names.contains("android.permission.RECEIVE_BOOT_COMPLETED"))
    }

    @Test
    fun reminderReceiversAreNotExported() {
        val receivers = manifest.getElementsByTagName("receiver")
        val reminderReceivers = (0 until receivers.length).mapNotNull { index ->
            val attributes = receivers.item(index).attributes ?: return@mapNotNull null
            val name = attributes.getNamedItem("android:name")?.nodeValue.orEmpty()
            if (name.contains("Reminder")) attributes.getNamedItem("android:exported")?.nodeValue else null
        }

        assertEquals(listOf("false", "false"), reminderReceivers)
    }
}
