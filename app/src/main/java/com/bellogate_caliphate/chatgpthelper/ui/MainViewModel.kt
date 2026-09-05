package com.bellogate_caliphate.chatgpthelper.ui

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bellogate_caliphate.chatgpthelper.data.AutomationManager
import com.bellogate_caliphate.chatgpthelper.data.AutomationState
import com.bellogate_caliphate.chatgpthelper.data.UrlBatchParser
import com.bellogate_caliphate.chatgpthelper.service.AutomationForegroundService
import com.bellogate_caliphate.chatgpthelper.service.GptAutomationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.InputStreamReader

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val state: StateFlow<AutomationState> = AutomationManager.state

    init {
        loadUrlsFromAssets()
    }

    private fun loadUrlsFromAssets() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = getApplication<Application>().assets.open("urls.txt")
                val content = InputStreamReader(inputStream).use { it.readText() }
                val batchInfo = UrlBatchParser.parseBatches(content)
                AutomationManager.loadBatches(batchInfo)
            } catch (e: Exception) {
                AutomationManager.setError("Failed to read urls.txt from assets: ${e.localizedMessage}")
            }
        }
    }

    fun checkAccessibilityStatus() {
        val isEnabled = GptAutomationService.instance != null
        AutomationManager.setAccessibilityEnabled(isEnabled)
    }

    fun startAutomation() {
        checkAccessibilityStatus()
        val context = getApplication<Application>()
        val intent = Intent(context, AutomationForegroundService::class.java).apply {
            action = AutomationForegroundService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun pauseAutomation() {
        val context = getApplication<Application>()
        val intent = Intent(context, AutomationForegroundService::class.java).apply {
            action = AutomationForegroundService.ACTION_PAUSE
        }
        context.startService(intent)
    }
}
