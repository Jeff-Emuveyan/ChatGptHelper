package com.bellogate_caliphate.chatgpthelper.data

enum class ExecutionStatus {
    IDLE,
    RUNNING,
    WAITING_TIMER,
    PAUSED,
    COMPLETED,
    ERROR
}

data class AutomationState(
    val status: ExecutionStatus = ExecutionStatus.IDLE,
    val totalUrls: Int = 0,
    val totalBatches: Int = 0,
    val sentBatches: Int = 0,
    val remainingBatches: Int = 0,
    val currentBatchIndex: Int = 0,
    val countdownSeconds: Int = 0,
    val currentBatchText: String = "",
    val statusMessage: String = "Ready to start.",
    val isAccessibilityEnabled: Boolean = false,
    val errorMessage: String? = null
) {
    val progress: Float
        get() = if (totalBatches > 0) sentBatches.toFloat() / totalBatches.toFloat() else 0f

    val isComplete: Boolean
        get() = status == ExecutionStatus.COMPLETED || (totalBatches > 0 && sentBatches >= totalBatches)
}
