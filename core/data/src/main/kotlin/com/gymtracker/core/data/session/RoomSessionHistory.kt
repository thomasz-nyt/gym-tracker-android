package com.gymtracker.core.data.session

import com.gymtracker.core.domain.history.SessionHistory
import com.gymtracker.core.domain.history.SessionSummary
import com.gymtracker.core.domain.model.SessionId
import com.gymtracker.core.domain.model.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

/** [SessionHistory] over Room. */
class RoomSessionHistory
    @Inject
    constructor(
        private val dao: SessionDao,
    ) : SessionHistory {
        override fun observeHistory(userId: UserId): Flow<List<SessionSummary>> =
            dao.observeHistory(userId.value).map { rows ->
                rows.map { row ->
                    SessionSummary(
                        id = SessionId(row.id),
                        startedAt = Instant.ofEpochMilli(row.startedAt),
                        endedAt = Instant.ofEpochMilli(row.endedAt),
                        exerciseCount = row.exerciseCount,
                        setCount = row.setCount,
                        volumeKg = row.volumeKg,
                    )
                }
            }
    }
