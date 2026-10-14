package org.nicho.arca.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.nicho.arca.data.di.CaptureContextPreferences
import org.nicho.arca.data.mapper.key
import org.nicho.arca.domain.model.AccountId
import org.nicho.arca.domain.repository.CaptureContext
import org.nicho.arca.domain.repository.CaptureContextStore

// SDD-ARC-002 §8.6 · FR-104. Plain DataStore is enough: CaptureActivity runs in the app's own
// process, so nothing is shared across processes.
//
// The remembered account may since have been archived or deleted. This store only reports
// what was saved; capture checks it against the live account list before pre-selecting it.
internal class DataStoreCaptureContextStore @Inject constructor(
    @param:CaptureContextPreferences private val dataStore: DataStore<Preferences>,
) : CaptureContextStore {

    override val context: Flow<CaptureContext> = dataStore.data
        .orEmptyOnIoError()
        .map { prefs ->
            CaptureContext(
                lastAccountId = prefs[LAST_ACCOUNT_ID]?.toAccountIdOrNull(),
                lastDate = prefs[LAST_DATE]?.let(Instant::ofEpochMilli),
            )
        }

    override suspend fun remember(accountId: AccountId, date: Instant) {
        dataStore.edit { prefs ->
            prefs[LAST_ACCOUNT_ID] = accountId.key()
            prefs[LAST_DATE] = date.toEpochMilli()
        }
    }

    private fun String.toAccountIdOrNull(): AccountId? =
        runCatching { AccountId(UUID.fromString(this)) }.getOrNull()

    private companion object {
        val LAST_ACCOUNT_ID = stringPreferencesKey("lastAccountId")
        val LAST_DATE = longPreferencesKey("lastDate")
    }
}
