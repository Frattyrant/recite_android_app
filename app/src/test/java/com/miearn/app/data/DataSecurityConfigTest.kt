package com.miearn.app.data

import com.miearn.app.data.local.AppDatabase
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataSecurityConfigTest {
    @Test
    fun appDisablesBackupAndReferencesExplicitExtractionRules() {
        val manifest = parse(File("src/main/AndroidManifest.xml"))
        val application = manifest.getElementsByTagName("application").item(0)
        val attributes = application.attributes

        assertEquals("false", attributes.getNamedItem("android:allowBackup")?.nodeValue)
        assertEquals(
            "false",
            attributes.getNamedItem("android:usesCleartextTraffic")?.nodeValue,
        )
        assertEquals(
            "@xml/backup_rules",
            attributes.getNamedItem("android:fullBackupContent")?.nodeValue,
        )
        assertEquals(
            "@xml/data_extraction_rules",
            attributes.getNamedItem("android:dataExtractionRules")?.nodeValue,
        )
    }

    @Test
    fun legacyBackupRulesExcludeEveryAppStorageDomain() {
        val rules = parse(File("src/main/res/xml/backup_rules.xml"))
        assertEquals(
            setOf("root", "file", "database", "sharedpref", "external"),
            excludedDomains(rules),
        )
    }

    @Test
    fun androidTwelveExtractionRulesExcludeCloudAndDeviceTransferData() {
        val rules = parse(File("src/main/res/xml/data_extraction_rules.xml"))
        val required = setOf(
            "root",
            "file",
            "database",
            "sharedpref",
            "external",
            "device_root",
            "device_file",
            "device_database",
            "device_sharedpref",
        )
        val cloud = rules.getElementsByTagName("cloud-backup").item(0)
        val transfer = rules.getElementsByTagName("device-transfer").item(0)

        assertEquals(required, excludedDomains(cloud))
        assertEquals(required, excludedDomains(transfer))
    }

    @Test
    fun databaseUsesOnlyExplicitMigrationsAndNeverDestructiveFallback() {
        assertEquals(
            listOf(1 to 2, 2 to 3),
            AppDatabase.MIGRATIONS.map { it.startVersion to it.endVersion },
        )
        val source = File(
            "src/main/java/com/miearn/app/data/local/AppDatabase.kt",
        ).readText()
        assertFalse(source.contains("fallbackToDestructiveMigration"))
        assertTrue(source.contains("addMigrations(*MIGRATIONS)"))
    }

    private fun parse(file: File) = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(file)

    private fun excludedDomains(node: org.w3c.dom.Node): Set<String> {
        val excludes = node.childNodes
        return buildSet {
            for (index in 0 until excludes.length) {
                val child = excludes.item(index)
                if (child.nodeName == "exclude") {
                    child.attributes?.getNamedItem("domain")?.nodeValue?.let(::add)
                    assertEquals(".", child.attributes?.getNamedItem("path")?.nodeValue)
                }
            }
        }
    }

    private fun excludedDomains(document: org.w3c.dom.Document): Set<String> =
        excludedDomains(document.documentElement)
}
