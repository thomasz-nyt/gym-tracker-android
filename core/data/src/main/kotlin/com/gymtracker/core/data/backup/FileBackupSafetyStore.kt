package com.gymtracker.core.data.backup

import android.content.Context
import android.util.AtomicFile
import com.gymtracker.core.domain.backup.AppVersion
import com.gymtracker.core.domain.backup.BackupContents
import com.gymtracker.core.domain.backup.BackupSafetyStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Private rolling recovery file, committed only after a decode/readback check (US-66). */
@Singleton
class FileBackupSafetyStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val codec: BackupCodec,
        private val clock: Clock,
        private val version: AppVersion,
    ) : BackupSafetyStore {
        private val file = AtomicFile(File(context.filesDir, "before-restore.json"))

        override suspend fun write(contents: BackupContents) =
            withContext(Dispatchers.IO) {
                val raw = codec.encode(contents, clock.instant(), version.name())
                check(codec.decode(raw) == contents) { "Safety copy encoding did not round trip" }
                val stream = file.startWrite()
                try {
                    stream.write(raw.toByteArray(Charsets.UTF_8))
                    stream.flush()
                    stream.fd.sync()
                    file.finishWrite(stream)
                } catch (error: IOException) {
                    file.failWrite(stream)
                    throw error
                }
                check(read() == contents) { "Safety copy readback failed" }
            }

        override suspend fun read(): BackupContents? =
            withContext(Dispatchers.IO) {
                if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) {
                    null
                } else {
                    file.openRead().bufferedReader().use { codec.decode(it.readText()) }
                }
            }
    }
