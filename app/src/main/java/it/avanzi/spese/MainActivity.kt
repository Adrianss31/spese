package it.avanzi.spese

import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SpeseTheme { SpeseScreen(model) } }
    }
}
