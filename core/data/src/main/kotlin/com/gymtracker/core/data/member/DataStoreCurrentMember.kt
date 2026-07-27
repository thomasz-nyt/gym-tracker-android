package com.gymtracker.core.data.member

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.model.UserId
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject

/**
 * The local member UUID, in DataStore (ADR-0005, `data-model.md` § "Identity before M2").
 *
 * Generated once and never regenerated: it is stamped on every session and set, so losing it
 * would orphan everything already logged.
 */
class DataStoreCurrentMember
    @Inject
    constructor(
        private val preferences: DataStore<Preferences>,
    ) : CurrentMember {
        override suspend fun id(): UserId {
            preferences.data.first()[MEMBER_ID]?.let { return UserId(it) }

            // edit is atomic, so two racing first-launch callers agree on one id.
            val stored =
                preferences.edit { current ->
                    if (current[MEMBER_ID] == null) {
                        current[MEMBER_ID] = UUID.randomUUID().toString()
                    }
                }
            return UserId(requireNotNull(stored[MEMBER_ID]))
        }

        private companion object {
            val MEMBER_ID = stringPreferencesKey("local_member_id")
        }
    }
