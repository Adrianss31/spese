package it.avanzi.spese

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import it.avanzi.spese.data.EXTRA_EXPENSE_ID
import it.avanzi.spese.data.EXTRA_RECURRING_ID
import it.avanzi.spese.data.EXTRA_OCCURRENCE
import it.avanzi.spese.data.LedgerWorker
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import it.avanzi.spese.ui.home.SpeseScreen
import it.avanzi.spese.ui.home.SpeseViewModel
import it.avanzi.spese.ui.theme.SpeseTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val model by viewModels<SpeseViewModel>()

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        LedgerWorker.onOpen(this)
    }

    override fun onStart() {
        super.onStart()
        LedgerWorker.onOpen(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openNotification(intent)
    }

    private fun openNotification(intent: Intent) {
        val expense = intent.getLongExtra(EXTRA_EXPENSE_ID, 0)
        val recurring = intent.getLongExtra(EXTRA_RECURRING_ID, 0)
        val date = intent.getLongExtra(EXTRA_OCCURRENCE, 0)
        if (expense > 0 || recurring > 0) {
            model.openOccurrence(expense, recurring, date)
            intent.removeExtra(EXTRA_EXPENSE_ID)
            intent.removeExtra(EXTRA_RECURRING_ID)
            intent.removeExtra(EXTRA_OCCURRENCE)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openNotification(intent)
        val permission = getPreferences(MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !permission.getBoolean("notificationPromptShown", false)
        ) {
            permission.edit().putBoolean("notificationPromptShown", true).apply()
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { SpeseTheme { SpeseScreen(model) } }
    }
}
