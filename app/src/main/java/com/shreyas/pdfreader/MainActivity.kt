package com.shreyas.pdfreader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import com.shreyas.pdfreader.navigation.AppNav
import com.shreyas.pdfreader.ui.theme.AppTheme

class MainActivity : ComponentActivity() {

    private val container get() = (application as ReaderApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A recreated activity gets the same intent again. Handle it only once.
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            AppTheme {
                AppNav(container)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        } ?: return
        container.incomingDocuments.trySend(uri)
    }
}
