package com.miearn.app.ui.importing

internal enum class ImportPickerMode {
    GET_CONTENT,
    OPEN_DOCUMENT,
}

internal fun nextImportPicker(mode: ImportPickerMode): ImportPickerMode? = when (mode) {
    ImportPickerMode.GET_CONTENT -> ImportPickerMode.OPEN_DOCUMENT
    ImportPickerMode.OPEN_DOCUMENT -> null
}

internal fun importPickerResultMessage(hasUri: Boolean): String? =
    if (hasUri) null else "未选择文件，可尝试系统文件选择器或直接粘贴文本。"
