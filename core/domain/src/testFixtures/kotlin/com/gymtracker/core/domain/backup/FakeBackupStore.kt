package com.gymtracker.core.domain.backup

import com.gymtracker.core.domain.model.UserId

/**
 * An in-memory [BackupStore], keyed by member id like the real Room-backed one is scoped by
 * `user_id`. `testFixtures` rather than `test` so `:feature:settings` can reuse it (the same
 * reasoning `FakeSessionRepository` and friends already follow).
 */
class FakeBackupStore(
    seed: Map<UserId, BackupContents> = emptyMap(),
) : BackupStore {
    private val contentsByMember = seed.toMutableMap()

    /** What [replaceAll] last wrote, or null if it has never been called. Test-only surface. */
    var lastReplaced: BackupContents? = null
        private set

    override suspend fun read(memberId: UserId): BackupContents =
        contentsByMember[memberId]
            ?: error("no fixture data seeded for $memberId — pass it to the constructor")

    override suspend fun replaceAll(contents: BackupContents) {
        lastReplaced = contents
        contentsByMember[contents.memberId] = contents
    }
}
