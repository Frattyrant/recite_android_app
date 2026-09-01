package com.miearn.app.ui.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportPickerPolicyTest {
    @Test
    fun contentPickerFallsBackToDocumentPickerWhenLaunchFails() {
        assertEquals(ImportPickerMode.OPEN_DOCUMENT, nextImportPicker(ImportPickerMode.GET_CONTENT))
        assertNull(nextImportPicker(ImportPickerMode.OPEN_DOCUMENT))
    }

    @Test
    fun cancelledPickerProducesActionableFeedback() {
        assertEquals(
            "未选择文件，可尝试系统文件选择器或直接粘贴文本。",
            importPickerResultMessage(hasUri = false),
        )
        assertNull(importPickerResultMessage(hasUri = true))
    }
}
