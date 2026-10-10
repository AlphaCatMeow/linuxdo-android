package org.linuxdo.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import org.linuxdo.android.ui.AppShell
import org.linuxdo.android.ui.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.toString()?.let { uri ->
            viewModel.handleWebAuthCallback(uri)
        }
        val browser = (application as App).container.browser
        setContent {
            AppShell(viewModel, browser)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.toString()?.let { uri ->
            viewModel.handleWebAuthCallback(uri)
        }
    }
}
