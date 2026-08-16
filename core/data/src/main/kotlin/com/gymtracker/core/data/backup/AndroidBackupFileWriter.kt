package com.gymtracker.core.data.backup

import android.content.Context
import android.net.Uri
import com.gymtracker.core.domain.backup.BackupFileWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/**
 * [BackupFileWriter] over the Storage Access Framework. [BackupFileWriter.write]'s `destination`
 * is a content URI's string form; this is the one place it gets parsed back into a real `Uri`
 * and opened through `ContentResolver` — the domain interface stays platform-free by design.
 */
class AndroidBackupFileWriter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : BackupFileWriter {
        override suspend fun write(
            destination: String,
            content: String,
        ) {
            withContext(Dispatchers.IO) {
                val stream =
                    context.contentResolver.openOutputStream(Uri.parse(destination))
                        ?: throw IOException("no output stream for $destination")
                stream.use { it.write(content.toByteArray()) }
            }
        }
    }
