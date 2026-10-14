package org.nicho.arca.data.prefs

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

// One DataStore file per concern, because Auto Backup treats them differently (DD-A16):
// settings are worth restoring, the capture context is not. Each lands at
// files/datastore/<name>.preferences_pb, which app/src/main/res/xml/*backup*.xml refer to.
internal object PreferenceFiles {
    const val CAPTURE_CONTEXT = "capture_context"
    const val SETTINGS = "settings"
}

// A read that fails with an I/O error falls back to defaults: neither file holds anything
// worth refusing to start over. Anything else is a bug and still throws.
internal fun Flow<Preferences>.orEmptyOnIoError(): Flow<Preferences> = catch { e ->
    if (e is IOException) emit(emptyPreferences()) else throw e
}
