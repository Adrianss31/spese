package it.avanzi.spese.ui.widget

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import it.avanzi.spese.R
import it.avanzi.spese.data.ledgerServices
import it.avanzi.spese.domain.ItalianFormats
import it.avanzi.spese.ui.theme.SpeseColors
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class SpeseWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val cents = ledgerServices(context).repository().totalOn(LocalDate.now())
        val marked = savedUntil > SystemClock.elapsedRealtime()
        provideContent { WidgetContent(cents, marked) }
    }

    companion object {
        @Volatile private var savedUntil: Long = 0

        suspend fun refresh(context: Context, saved: Boolean = false) {
            if (saved) savedUntil = SystemClock.elapsedRealtime() + 2_000
            val widget = SpeseWidget()
            val ids = GlanceAppWidgetManager(context).getGlanceIds(SpeseWidget::class.java)
            ids.forEach { widget.update(context, it) }
            if (saved && ids.isNotEmpty()) {
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "widget-reset", ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                        .setInitialDelay(2_000, TimeUnit.MILLISECONDS).build(),
                )
            }
        }

        fun scheduleDayChange(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
            val now = ZonedDateTime.now()
            val next = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
            WorkManager.getInstance(context).enqueueUniqueWork(
                "widget-midnight", policy, OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                    .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
                    .setInputData(androidx.work.workDataOf("midnight" to true)).build(),
            )
        }
    }
}

@Composable
private fun WidgetContent(cents: Long, marked: Boolean) {
    val context = LocalContext.current
    val popup = Intent(context, WidgetPopupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val total = ItalianFormats.money(cents)
    Column(
        GlanceModifier.fillMaxSize().background(SpeseColors.Accent).cornerRadius(22.dp).padding(9.dp)
            .clickable(actionStartActivity(popup)),
        horizontalAlignment = Alignment.Start,
    ) {
        Image(
            ImageProvider(if (marked) R.drawable.widget_saved else R.drawable.widget_add),
            context.getString(if (marked) R.string.widget_saved else R.string.new_expense),
            GlanceModifier.size(26.dp),
        )
        Spacer(GlanceModifier.defaultWeight())
        Text(
            total, style = TextStyle(
                color = ColorProvider(day = SpeseColors.Bg, night = SpeseColors.Bg),
                fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.SansSerif,
            ), maxLines = 1,
        )
    }
}

class SpeseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = SpeseWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        SpeseWidget.scheduleDayChange(context)
    }
}

class WidgetRefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        SpeseWidget.refresh(applicationContext)
        if (inputData.getBoolean("midnight", false)) {
            SpeseWidget.scheduleDayChange(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        }
        return Result.success()
    }
}
