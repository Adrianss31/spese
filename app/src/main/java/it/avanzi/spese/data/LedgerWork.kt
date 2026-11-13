package it.avanzi.spese.data

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import it.avanzi.spese.MainActivity
import it.avanzi.spese.R
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

const val EXTRA_EXPENSE_ID = "it.avanzi.spese.expense"
const val EXTRA_RECURRING_ID = "it.avanzi.spese.recurring"
const val EXTRA_OCCURRENCE = "it.avanzi.spese.occurrence"
private const val CHANNEL = "ledger_alerts"

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LedgerServices {
    fun engine(): RecurringEngine
    fun repository(): SpeseRepository
    fun preferences(): PreferencesRepository
    fun database(): SpeseDatabase
}

fun ledgerServices(context: Context): LedgerServices =
    EntryPointAccessors.fromApplication(context.applicationContext, LedgerServices::class.java)

class LedgerWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        return try {
            val services = ledgerServices(applicationContext)
            services.repository().initializeCategories()
            services.engine().generate(LocalDate.now(), Instant.now()).forEach { event ->
                LedgerNotifications(applicationContext).occurrence(event)
            }
            checkBudget(applicationContext)
            it.avanzi.spese.ui.widget.SpeseWidget.refresh(applicationContext)
            if (inputData.getBoolean("daily", false)) scheduleNext(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun onOpen(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "ledger-open", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<LedgerWorker>().build(),
            )
            scheduleNext(context, ExistingWorkPolicy.KEEP)
        }

        private fun scheduleNext(context: Context, policy: ExistingWorkPolicy) {
            val current = ZonedDateTime.now()
            val next = current.toLocalDate().plusDays(1).atStartOfDay(current.zone).plusMinutes(5)
            val request = OneTimeWorkRequestBuilder<LedgerWorker>()
                .setInitialDelay(Duration.between(current, next).toMillis(), TimeUnit.MILLISECONDS)
                .setInputData(androidx.work.workDataOf("daily" to true))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("ledger-daily", policy, request)
        }
    }
}

suspend fun checkBudget(context: Context) {
    val manager = LedgerNotifications(context)
    if (!manager.allowed()) return
    val services = ledgerServices(context)
    val month = YearMonth.now()
    val spent = services.database().dao().confirmedBetween(month.atDay(1), month.atEndOfMonth())
    val totals = spent.groupBy { it.categoryId }.mapValues { (_, rows) -> rows.sumOf { it.amountCents } }
    val prefs = services.preferences().values.first()
    val limits = buildList {
        services.repository().categories.first().forEach { category ->
            category.monthlyLimitCents?.let { add(Triple(category.id, category.name, it)) }
        }
        if (prefs.overallBudgetEnabled && prefs.overallBudgetCents > 0) {
            add(Triple(0L, context.getString(R.string.budget_overall), prefs.overallBudgetCents))
        }
    }
    for ((id, label, limit) in limits) {
        val total = if (id == 0L) spent.sumOf { it.amountCents } else totals[id] ?: 0L
        for (threshold in listOf(85, 100)) {
            val reached = BigDecimal.valueOf(total).movePointRight(2) >= BigDecimal.valueOf(limit) * BigDecimal.valueOf(threshold.toLong())
            if (reached && services.preferences().recordBudgetAlert(month.toString(), "$id:$threshold")) {
                manager.budget(id, label, threshold)
            }
        }
    }
}

private class LedgerNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun allowed(): Boolean = manager.areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    private fun builder(): Notification.Builder {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.spese_alerts), NotificationManager.IMPORTANCE_DEFAULT))
        return Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification).setAutoCancel(true)
    }

    fun occurrence(event: GeneratedOccurrence) {
        if (!allowed()) return
        val pendingId = event.pendingExpenseId
        val tag = "${event.recurring.id}:${event.date}"
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = Uri.parse("spese://occurrence/$tag")
            if (pendingId == null) {
                putExtra(EXTRA_RECURRING_ID, event.recurring.id)
                putExtra(EXTRA_OCCURRENCE, event.date.toEpochDay())
            } else putExtra(EXTRA_EXPENSE_ID, pendingId)
        }
        val content = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val note = builder().setContentIntent(content)
            .setContentTitle(context.getString(if (pendingId == null) R.string.reminder else R.string.confirm_amount_title, event.recurring.name))
            .setContentText(it.avanzi.spese.domain.ItalianFormats.money(event.recurring.amountCents))
        if (pendingId != null) {
            val confirm = Intent(context, ConfirmExpenseReceiver::class.java)
                .setData(Uri.parse("spese://confirm/$pendingId"))
                .putExtra(EXTRA_EXPENSE_ID, pendingId)
                .putExtra("notification_tag", tag)
            val action = PendingIntent.getBroadcast(context, 0, confirm, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            note.addAction(Notification.Action.Builder(null, context.getString(R.string.confirm), action).build())
            note.addAction(Notification.Action.Builder(null, context.getString(R.string.edit), content).build())
        }
        manager.notify(tag, 1, note.build())
    }

    fun budget(id: Long, label: String, threshold: Int) {
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        manager.notify("budget:$id:$threshold", 2, builder().setContentIntent(open)
            .setContentTitle(context.getString(R.string.budget))
            .setContentText(context.getString(R.string.budget_alert, label, threshold)).build())
    }
}

class ConfirmExpenseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_EXPENSE_ID, 0)
        if (id <= 0) return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val services = ledgerServices(context)
                val expense = services.database().dao().expense(id)
                if (expense != null) services.repository().confirmExpense(id, expense.amountCents)
                context.getSystemService(NotificationManager::class.java).cancel(intent.getStringExtra("notification_tag"), 1)
                checkBudget(context)
                it.avanzi.spese.ui.widget.SpeseWidget.refresh(context, saved = true)
            } finally {
                result.finish()
            }
        }
    }
}
