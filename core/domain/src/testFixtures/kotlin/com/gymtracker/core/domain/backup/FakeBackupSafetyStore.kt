package com.gymtracker.core.domain.backup

/** Test safety storage with the same explicit write/read boundary as the persistent adapter. */
class FakeBackupSafetyStore : BackupSafetyStore {
    private var contents: BackupContents? = null

    override suspend fun write(contents: BackupContents) {
        this.contents = contents
    }

    override suspend fun read(): BackupContents? = contents
}
