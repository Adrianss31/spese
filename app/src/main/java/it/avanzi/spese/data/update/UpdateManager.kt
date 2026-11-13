package it.avanzi.spese.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import it.avanzi.spese.data.PreferencesRepository
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class UpdateError { CREDENTIALS, NETWORK, WIFI, DOWNLOAD, INSTALL }
data class UpdateState(
    val available: AvailableUpdate? = null,
    val checking: Boolean = false,
    val progress: Long? = null,
    val ready: File? = null,
    val error: UpdateError? = null,
)

@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: ReleaseApi,
    private val credentials: UpdateCredentials,
    private val preferences: PreferencesRepository,
) {
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(UpdateState())
    val state = mutable.asStateFlow()

    fun hasCredentials() = credentials.get() != null

    suspend fun setCredential(value: String?) {
        withContext(Dispatchers.IO) { credentials.set(value) }
        check(force = true)
    }

    suspend fun check(force: Boolean = false, daily: Boolean = false) = mutex.withLock {
        val prefs = preferences.values.first()
        val recentlyChecked = prefs.lastCheck?.let { Duration.between(it, Instant.now()) < Duration.ofHours(6) } ?: false
        if (!force && (!prefs.autoCheck || (!daily && recentlyChecked && mutable.value.available == null))) return@withLock
        if (prefs.wifiOnly && !hasWifi()) {
            if (force) mutable.update { it.copy(error = UpdateError.WIFI) }
            return@withLock
        }
        mutable.update { it.copy(checking = true, error = null) }
        try {
            val available = api.latest(prefs.channel, credentials.get())
            mutable.update { current ->
                current.copy(available = available, ready = current.ready.takeIf { available == current.available })
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: CredentialsRequired) {
            mutable.update { it.copy(error = UpdateError.CREDENTIALS) }
        } catch (_: Exception) {
            mutable.update { it.copy(error = UpdateError.NETWORK) }
        } finally {
            mutable.update { it.copy(checking = false) }
        }
        preferences.setLastCheck(Instant.now())
    }

    suspend fun download() = mutex.withLock {
        val available = mutable.value.available ?: return@withLock
        if (preferences.values.first().wifiOnly && !hasWifi()) {
            mutable.update { it.copy(error = UpdateError.WIFI) }
            return@withLock
        }
        mutable.update { it.copy(progress = 0L, error = null, ready = null) }
        try {
            val file = api.download(available, credentials.get()) { bytes ->
                mutable.update { it.copy(progress = bytes) }
            }
            mutable.update { it.copy(ready = file) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutable.update { it.copy(error = UpdateError.DOWNLOAD) }
        } finally {
            mutable.update { it.copy(progress = null) }
        }
    }

    suspend fun install() {
        val file = mutable.value.ready ?: return
        try {
            withContext(Dispatchers.IO) {
                val installer = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                params.setAppPackageName(context.packageName)
                params.setSize(file.length())
                if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                val id = installer.createSession(params)
                try {
                    installer.openSession(id).use { session ->
                        session.openWrite("base.apk", 0, file.length()).use { output ->
                            file.inputStream().use { it.copyTo(output) }
                            session.fsync(output)
                        }
                        val callback = Intent(context, InstallResultReceiver::class.java)
                        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                            (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                        session.commit(PendingIntent.getBroadcast(context, id, callback, flags).intentSender)
                    }
                } catch (error: Exception) {
                    installer.abandonSession(id)
                    throw error
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            reportInstallError()
        }
    }

    fun reportInstallError() { mutable.update { it.copy(error = UpdateError.INSTALL) } }

    suspend fun schedule() {
        val prefs = preferences.values.first()
        val manager = WorkManager.getInstance(context)
        if (!prefs.autoCheck) {
            manager.cancelUniqueWork("spese-update-daily")
            manager.cancelUniqueWork("spese-update-open")
            return
        }
        val connectivity = Constraints.Builder().setRequiredNetworkType(
            if (prefs.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED,
        ).build()
        manager.enqueueUniquePeriodicWork(
            "spese-update-daily", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(1, TimeUnit.DAYS)
                .setConstraints(connectivity)
                .setInputData(androidx.work.workDataOf("daily" to true)).build(),
        )
        manager.enqueueUniqueWork(
            "spese-update-open", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<UpdateWorker>().setConstraints(connectivity).build(),
        )
    }

    private fun hasWifi(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        return manager.getNetworkCapabilities(manager.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UpdateServices {
    fun updater(): UpdateManager
}

private fun updater(context: Context) =
    EntryPointAccessors.fromApplication(context.applicationContext, UpdateServices::class.java).updater()

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        updater(applicationContext).check(daily = inputData.getBoolean("daily", false))
        return Result.success()
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirmation == null) updater(context).reportInstallError()
                else context.startActivity(confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS, PackageInstaller.STATUS_FAILURE_ABORTED -> Unit
            else -> updater(context).reportInstallError()
        }
    }
}
