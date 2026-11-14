package it.avanzi.spese.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import it.avanzi.spese.domain.UpdateChannel
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map

private val Context.spesePreferences by preferencesDataStore("spese_preferences")

data class SpesePreferences(
    val overallBudgetEnabled: Boolean = false,
    val overallBudgetCents: Long = 0,
    val autoCheck: Boolean = true,
    val wifiOnly: Boolean = true,
    val channel: UpdateChannel = UpdateChannel.STABLE,
    val lastCheck: Instant? = null,
    val bannerDismissedVersion: String? = null,
    val spreadRecurring: Boolean = true,
)

@Singleton
class PreferencesRepository @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.spesePreferences
    val values = store.data.map { data ->
        SpesePreferences(
            data[budgetEnabled] ?: false,
            data[budgetCents] ?: 0,
            data[autoCheck] ?: true,
            data[wifiOnly] ?: true,
            data[channel]?.let(UpdateChannel::valueOf) ?: UpdateChannel.STABLE,
            data[lastCheck]?.let(Instant::ofEpochMilli),
            data[bannerDismissed],
            data[spreadRecurring] ?: true,
        )
    }

    suspend fun setBudget(enabled: Boolean, cents: Long) {
        store.edit { it[budgetEnabled] = enabled; it[budgetCents] = cents }
    }
    suspend fun setAutoCheck(value: Boolean) = put(autoCheck, value)
    suspend fun setWifiOnly(value: Boolean) = put(wifiOnly, value)
    suspend fun setChannel(value: UpdateChannel) = put(channel, value.name)
    suspend fun setLastCheck(value: Instant) = put(lastCheck, value.toEpochMilli())
    suspend fun dismissBanner(version: String) = put(bannerDismissed, version)
    suspend fun setSpreadRecurring(value: Boolean) = put(spreadRecurring, value)

    suspend fun recordBudgetAlert(month: String, alert: String): Boolean {
        var added = false
        store.edit { data ->
            val previous = if (data[alertMonth] == month) data[budgetAlerts].orEmpty() else emptySet()
            added = alert !in previous
            if (added) {
                data[alertMonth] = month
                data[budgetAlerts] = previous + alert
            }
        }
        return added
    }

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        store.edit { it[key] = value }
    }

    private companion object {
        val alertMonth = stringPreferencesKey("alertMonth")
        val budgetAlerts = stringSetPreferencesKey("budgetAlerts")
        val budgetEnabled = booleanPreferencesKey("overallBudgetEnabled")
        val budgetCents = longPreferencesKey("overallBudgetCents")
        val autoCheck = booleanPreferencesKey("update.autoCheck")
        val wifiOnly = booleanPreferencesKey("update.wifiOnly")
        val channel = stringPreferencesKey("update.channel")
        val lastCheck = longPreferencesKey("update.lastCheck")
        val bannerDismissed = stringPreferencesKey("update.bannerDismissedVersion")
        val spreadRecurring = booleanPreferencesKey("spreadRecurring")
    }
}
