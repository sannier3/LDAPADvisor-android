package com.jbsan.ldapadvisor.core.logging

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogExport {
    fun writeSnapshot(context: Context, logger: AppLogger): File {
        val dir = File(context.cacheDir, "logs").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "ldapadvisor-logs-$stamp.txt")
        val lines = logger.snapshot().joinToString("\n") { event ->
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                .format(Date(event.timestampEpochMs))
            "$time [${event.level}] ${event.component}: ${event.message}"
        }
        file.writeText(lines.ifBlank { "(no log events)" })
        return file
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }
}
