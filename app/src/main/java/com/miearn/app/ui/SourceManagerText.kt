package com.miearn.app.ui

internal fun sourceNameValidationMessage(input: String): String? =
    if (input.trim().isEmpty()) "请输入词库名称" else null
