package com.bellogate_caliphate.chatgpthelper.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

object AutomationManager {

    private val _state = MutableStateFlow(AutomationState())
    val state: StateFlow<AutomationState> = _state.asStateFlow()

    private var batches: List<Batch> = emptyList()

    fun loadBatches(batchInfo: UrlBatchInfo) {
        batches = batchInfo.batches
        _state.update {
            it.copy(
                totalUrls = batchInfo.totalUrls,
                totalBatches = batchInfo.totalBatches,
                sentBatches = 0,
                remainingBatches = batchInfo.totalBatches,
                currentBatchIndex = 0,
                status = if (batchInfo.totalBatches > 0) ExecutionStatus.IDLE else ExecutionStatus.ERROR,
                statusMessage = if (batchInfo.totalBatches > 0) "Loaded ${batchInfo.totalUrls} URLs in ${batchInfo.totalBatches} batches." else "No URLs found in list.",
                errorMessage = null
            )
        }
    }

    fun setAccessibilityEnabled(enabled: Boolean) {
        _state.update { it.copy(isAccessibilityEnabled = enabled) }
    }

    fun getCurrentBatch(): Batch? {
        val currentState = _state.value
        if (currentState.currentBatchIndex in batches.indices) {
            return batches[currentState.currentBatchIndex]
        }
        return null
    }

    fun start() {
        val currentState = _state.value
        if (batches.isEmpty()) {
            _state.update { it.copy(statusMessage = "Cannot start: No batches loaded.") }
            return
        }
        if (currentState.sentBatches >= currentState.totalBatches) {
            _state.update {
                it.copy(
                    status = ExecutionStatus.COMPLETED,
                    statusMessage = "All batches have been sent! Work is complete."
                )
            }
            return
        }
        _state.update {
            it.copy(
                status = ExecutionStatus.RUNNING,
                statusMessage = "Preparing to send batch ${it.sentBatches + 1} of ${it.totalBatches}..."
            )
        }
    }

    fun onBatchSending() {
        _state.update {
            it.copy(
                status = ExecutionStatus.RUNNING,
                statusMessage = "Sending batch ${it.sentBatches + 1} of ${it.totalBatches} into ChatGPT..."
            )
        }
    }

    fun onBatchSentSuccess() {
        val current = _state.value
        val newSent = current.sentBatches + 1
        val newRemaining = (current.totalBatches - newSent).coerceAtLeast(0)
        val nextIndex = current.currentBatchIndex + 1

        if (newSent >= current.totalBatches) {
            _state.update {
                it.copy(
                    status = ExecutionStatus.COMPLETED,
                    sentBatches = newSent,
                    remainingBatches = 0,
                    currentBatchIndex = nextIndex,
                    statusMessage = "All batches have been sent! Work is complete.",
                    countdownSeconds = 0
                )
            }
        } else {
            _state.update {
                it.copy(
                    status = ExecutionStatus.WAITING_TIMER,
                    sentBatches = newSent,
                    remainingBatches = newRemaining,
                    currentBatchIndex = nextIndex,
                    statusMessage = "Batch $newSent sent successfully! Waiting 4 minutes for next batch...",
                    countdownSeconds = 240 // 4 minutes
                )
            }
        }
    }

    fun updateCountdown(secondsLeft: Int) {
        _state.update {
            if (it.status == ExecutionStatus.WAITING_TIMER) {
                val mins = secondsLeft / 60
                val secs = secondsLeft % 60
                val timeFormatted = String.format(Locale.US, "%02d:%02d", mins, secs)
                it.copy(
                    countdownSeconds = secondsLeft,
                    statusMessage = "Batch ${it.sentBatches} sent. Next batch in $timeFormatted..."
                )
            } else {
                it
            }
        }
    }

    fun pause() {
        _state.update {
            if (it.status != ExecutionStatus.COMPLETED) {
                it.copy(
                    status = ExecutionStatus.PAUSED,
                    statusMessage = "Automation paused."
                )
            } else {
                it
            }
        }
    }

    fun reset() {
        _state.update {
            it.copy(
                status = ExecutionStatus.IDLE,
                sentBatches = 0,
                remainingBatches = it.totalBatches,
                currentBatchIndex = 0,
                countdownSeconds = 0,
                statusMessage = "Reset complete. Ready to start."
            )
        }
    }

    fun setError(message: String) {
        _state.update {
            it.copy(
                status = ExecutionStatus.ERROR,
                statusMessage = message,
                errorMessage = message
            )
        }
    }
}
