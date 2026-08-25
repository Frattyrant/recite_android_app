package com.miearn.app.importing

import java.io.File
import java.io.InputStream

internal class ImportFileStore(
    private val directory: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) {
    fun target(jobId: String): File = File(directory, "$jobId.source")

    fun cleanup(jobId: String) {
        target(jobId).delete()
        File(directory, "$jobId.partial").delete()
    }

    fun copy(
        jobId: String,
        input: InputStream,
        checkpoint: () -> Unit = {},
    ): File {
        check(directory.mkdirs() || directory.isDirectory) {
            "无法创建导入临时目录"
        }
        val partial = File(directory, "$jobId.partial")
        val target = target(jobId)
        partial.delete()
        target.delete()
        try {
            partial.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var copied = 0L
                while (true) {
                    checkpoint()
                    val read = input.read(buffer)
                    if (read < 0) break
                    checkpoint()
                    copied += read
                    if (copied > maxBytes) {
                        throw FileSizeLimitException(maxBytes)
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
            cleanup(jobId)
            throw error
        }
    }

    private companion object {
        const val DEFAULT_MAX_BYTES = 20L * 1024 * 1024
    }
}
