package com.gymtracker.core.data.backup

import android.content.Context
import android.net.Uri
import com.gymtracker.core.domain.backup.BackupFileReader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/** [BackupFileReader] over the Storage Access Framework — the read-side counterpart to [AndroidBackupFileWriter]. */
class AndroidBackupFileReader
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : BackupFileReader {
        override suspend fun read(source: String): String =
            withContext(Dispatchers.IO) {
                val stream =
                    context.contentResolver.openInputStream(Uri.parse(source))
                        ?: throw IOException("no input stream for $source")
                stream.use { it.readBytes().decodeToString() }
            }
    }
