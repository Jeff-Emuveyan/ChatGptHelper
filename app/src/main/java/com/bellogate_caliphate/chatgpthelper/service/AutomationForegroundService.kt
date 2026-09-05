package com.bellogate_caliphate.chatgpthelper.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.bellogate_caliphate.chatgpthelper.R
import com.bellogate_caliphate.chatgpthelper.data.AutomationManager
import com.bellogate_caliphate.chatgpthelper.data.ExecutionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AutomationForegroundService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_STOP = "ACTION_STOP"

        private const val CHANNEL_ID = "chatgpt_automation_channel"
        private const val NOTIFICATION_ID = 1001
        private const val CHROME_PACKAGE_NAME = "com.android.chrome"
        private const val TIMER_INTERVAL_SECONDS = 120
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var automationJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        val notification = buildNotification("ChatGPT Automation Active")
        startForeground(NOTIFICATION_ID, notification)

        when (action) {
            ACTION_START -> startAutomationLoop()
            ACTION_PAUSE -> pauseAutomationLoop()
            ACTION_STOP -> stopSelf()
        }

        return START_STICKY
    }

    private fun startAutomationLoop() {
        if (automationJob?.isActive == true) return

        AutomationManager.start()

        automationJob = serviceScope.launch {
            while (isActive) {
                val state = AutomationManager.state.value

                if (state.status == ExecutionStatus.COMPLETED) {
                    updateNotification("All batches sent! Work complete.")
                    break
                }

                if (state.status == ExecutionStatus.PAUSED) {
                    updateNotification("Automation paused")
                    delay(1000)
                    continue
                }

                val currentBatch = AutomationManager.getCurrentBatch()
                if (currentBatch == null) {
                    AutomationManager.onBatchSentSuccess()
                    continue
                }

                val accessibilityService = GptAutomationService.instance
                if (accessibilityService == null) {
                    AutomationManager.setError("Accessibility Service is disabled. Please enable it in Settings.")
                    updateNotification("Error: Accessibility Service disabled")
                    break
                }

                AutomationManager.onBatchSending()
                updateNotification("Sending batch ${state.sentBatches + 1} of ${state.totalBatches}...")

                launchChromeApp()
                delay(2000) // Allow Chrome browser to come to foreground

                val (sendSuccess, errorDetails) = accessibilityService.sendBatchToChatGPT(currentBatch.formattedPrompt)

                if (sendSuccess) {
                    AutomationManager.onBatchSentSuccess()
                    val updatedState = AutomationManager.state.value

                    if (updatedState.isComplete) {
                        updateNotification("All batches sent successfully!")
                        break
                    }

                    // Countdown Timer
                    var secondsRemaining = TIMER_INTERVAL_SECONDS
                    while (secondsRemaining > 0 && isActive) {
                        val currentState = AutomationManager.state.value
                        if (currentState.status == ExecutionStatus.PAUSED) {
                            break
                        }
                        AutomationManager.updateCountdown(secondsRemaining)
                        updateNotification("Batch ${updatedState.sentBatches}/${updatedState.totalBatches} sent. Next in ${secondsRemaining}s")
                        delay(1000)
                        secondsRemaining--
                    }
                } else {
                    val msg = errorDetails ?: "Failed to paste batch into ChatGPT in Chrome"
                    AutomationManager.setError(msg)
                    updateNotification("Error: $msg")
                    delay(5000)
                }
            }
        }
    }

    private fun pauseAutomationLoop() {
        automationJob?.cancel()
        automationJob = null
        AutomationManager.pause()
        updateNotification("Automation paused")
    }

    private fun launchChromeApp() {
        val launchIntent = packageManager.getLaunchIntentForPackage(CHROME_PACKAGE_NAME)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            try {
                startActivity(launchIntent)
            } catch (e: Exception) {
                AutomationManager.setError("Could not bring Chrome to foreground: ${e.localizedMessage}")
            }
        } else {
            AutomationManager.setError("Google Chrome ($CHROME_PACKAGE_NAME) is not installed on this device.")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ChatGPT Automation Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of ChatGPT URL batch automation"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ChatGptHelper Automation")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ChatGptHelper::AutomationWakeLock").apply {
            acquire(10 * 60 * 1000L /* 10 minutes */)
        }
    }

    override fun onDestroy() {
        automationJob?.cancel()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
