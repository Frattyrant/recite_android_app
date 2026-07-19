package com.miearn.app.importing

import java.io.File
import java.io.InputStream

internal class ImportFileStore(
    private val directory: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) {
    fun copy(jobId: String, input: InputStream): File {
        check(directory.mkdirs() || directory.isDirectory) {
            "无法创建导入临时目录"
        }
        val partial = File(directory, "$jobId.partial")
        val target = File(directory, "$jobId.source")
        partial.delete()
        target.delete()
        try {
            partial.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var copied = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    copied += read
                    if (copied > maxBytes) {
                        throw VocabularyImportException("文件不能超过 20 MB")
                    }
                    output.write(buffer, 0, read)
                }
            }
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
                partial.delete()
            }
            return target
        } catch (error: Exception) {
            partial.delete()
            target.delete()
            throw error
        }
    }

    private companion object {
        const val DEFAULT_MAX_BYTES = 20L * 1024 * 1024
    }
}
