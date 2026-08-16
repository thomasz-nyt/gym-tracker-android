package com.gymtracker.core.domain.backup

import com.gymtracker.core.domain.model.UserId

/**
 * Reads everything a member has logged, for US-40's export.
 *
 * A thin read through [BackupStore] — the interesting work is what [BackupStore.read] itself
 * gathers, and what the caller in `:core:data` does with the result (encoding it and writing it
 * through the Storage Access Framework, per ADR-0034).
 */
class ExportBackup(
    private val store: BackupStore,
) {
    suspend operator fun invoke(memberId: UserId): BackupContents = store.read(memberId)
}
