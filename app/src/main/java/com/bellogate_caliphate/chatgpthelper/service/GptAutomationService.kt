package com.bellogate_caliphate.chatgpthelper.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.bellogate_caliphate.chatgpthelper.data.AutomationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class GptAutomationService : AccessibilityService() {

    companion object {
        private const val TAG = "GptAutomationService"
        var instance: GptAutomationService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        AutomationManager.setAccessibilityEnabled(true)
        Log.d(TAG, "GptAutomationService connected successfully")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Monitored as needed for window changes
    }

    override fun onInterrupt() {
        Log.w(TAG, "GptAutomationService interrupted")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        AutomationManager.setAccessibilityEnabled(false)
        return super.onUnbind(intent)
    }

    fun findAndLogNodes(node: AccessibilityNodeInfo?, depth: Int = 0) {
        node ?: return
        val indent = " ".repeat(depth * 2)
        Log.d("A11Y", """
            ${indent}className: ${node.className}
            ${indent}text: ${node.text}
            ${indent}contentDesc: ${node.contentDescription}
            ${indent}viewId: ${node.viewIdResourceName}
            ${indent}isClickable: ${node.isClickable}
            ${indent}isEnabled: ${node.isEnabled}
            ${indent}childCount: ${node.childCount}
        """.trimIndent())

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findAndLogNodes(child, depth + 1)
        }
    }

    /**
     * Pastes current batch into ChatGPT web in Chrome and clicks Send button.
     * Suspends until the operation succeeds or times out.
     */
    suspend fun sendBatchToChatGPT(batchText: String): Pair<Boolean, String?> = withContext(Dispatchers.Main) {
        val rootNode = rootInActiveWindow
            ?: return@withContext Pair(false, "Could not access screen. Make sure Chrome is open in foreground.")

        // Dump node tree for debugging
        Log.d("A11Y", "--- START NODE TREE DUMP ---")
        findAndLogNodes(rootNode)
        Log.d("A11Y", "--- END NODE TREE DUMP ---")

        val inputNode = findEditableNode(rootNode)
            ?: return@withContext Pair(false, "Could not find ChatGPT input box in Chrome screen.")

        // 1. Copy text to System Clipboard so React detects genuine Paste event
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null) {
                val clip = ClipData.newPlainText("ChatGPT Batch Prompt", batchText)
                clipboard.setPrimaryClip(clip)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not access Clipboard: ${e.localizedMessage}")
        }

        // 2. Focus input field
        inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

        // 3. Perform ACTION_SET_TEXT
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, batchText)
        }
        val textSetSuccess = inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

        // 4. Perform ACTION_PASTE (triggers native Web input/paste events so React updates state)
        inputNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)

        if (!textSetSuccess) {
            return@withContext Pair(false, "Failed to paste batch URLs into ChatGPT input box in Chrome.")
        }

        // 5. Set cursor selection to end of text
        val selectionArgs = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, batchText.length)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, batchText.length)
        }
        inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArgs)

        // Wait 3 seconds for React on chatgpt.com to process the inserted batch and enable the Send button
        Log.d("A11Y", "Batch pasted. Waiting 3 seconds for React state and Send button to update...")
        delay(3000)

        // 6. Poll over a 5-second window to find and click composer-submit-button / screen coordinates
        for (attempt in 1..10) {
            delay(500)
            val currentRoot = rootInActiveWindow ?: rootNode

            val composerButton = findComposerSubmitButtonNode(currentRoot)
                ?: findClickableNodeInInputContainer(inputNode)

            if (composerButton != null) {
                val clicked = clickComposerSubmitButton(composerButton, inputNode)
                if (clicked) {
                    return@withContext Pair(true, null)
                }
            } else {
                tapSendButtonByCoordinates(inputNode)
            }
        }

        return@withContext Pair(false, "Batch pasted, but Send button could not be clicked in Chrome.")
    }

    private fun findComposerSubmitButtonNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null

        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val resId = node.viewIdResourceName?.lowercase() ?: ""

        val isComposerButton = resId == "composer-submit-button" ||
                resId.contains("composer-submit-button") ||
                resId.contains("send-button") ||
                text == "send prompt" ||
                desc == "send prompt"

        if (isComposerButton) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findComposerSubmitButtonNode(child)
            if (found != null) return found
        }

        return null
    }

    private fun clickComposerSubmitButton(buttonNode: AccessibilityNodeInfo, inputNode: AccessibilityNodeInfo): Boolean {
        val rect = Rect()
        buttonNode.getBoundsInScreen(rect)

        Log.d("A11Y", "Found composer-submit-button at bounds: $rect")

        // 1. Accessibility ACTION_CLICK on button directly
        buttonNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        // 2. Dispatch hardware touch gesture at center of button
        if (rect.centerX() > 0 && rect.centerY() > 0) {
            val tapped = dispatchTapGesture(rect.centerX().toFloat(), rect.centerY().toFloat())
            Log.d("A11Y", "Dispatched tap gesture at (${rect.centerX()}, ${rect.centerY()}), result = $tapped")
        }

        // 3. Fallback: Tap screen coordinates relative to prompt box & screen
        tapSendButtonByCoordinates(inputNode)

        // 4. Accessibility ACTION_CLICK on parent container
        buttonNode.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        return true
    }

    private fun tapSendButtonByCoordinates(inputNode: AccessibilityNodeInfo) {
        val inputRect = Rect()
        inputNode.getBoundsInScreen(inputRect)

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels.toFloat()
        val screenHeight = displayMetrics.heightPixels.toFloat()

        Log.d("A11Y", "Input box bounds: $inputRect, Screen size: ${screenWidth}x${screenHeight}")

        // 1. Calculate target coordinates based on input box bounds
        val boxX = if (inputRect.right > 0) (inputRect.right - 80f) else (screenWidth * 0.888f)
        val boxY = if (inputRect.bottom > 0) (inputRect.bottom - 80f) else (screenHeight * 0.883f)

        Log.d("A11Y", "Attempting gesture tap at box coords: ($boxX, $boxY) and screen ratio: (${screenWidth * 0.888f}, ${screenHeight * 0.883f})")

        // 2. Dispatch tap gesture at calculated box coords
        val t1 = dispatchTapGesture(boxX, boxY)

        // 3. Dispatch tap gesture at Small Phone exact blue button position (640, 1130)
        val t2 = dispatchTapGesture(screenWidth * 0.888f, screenHeight * 0.883f)

        Log.d("A11Y", "Gesture tap results: t1=$t1, t2=$t2")
    }

    private fun dispatchTapGesture(x: Float, y: Float): Boolean {
        if (x <= 0 || y <= 0) return false
        val path = Path().apply {
            moveTo(x, y)
        }
        val gestureBuilder = GestureDescription.Builder()
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        gestureBuilder.addStroke(stroke)
        return dispatchGesture(gestureBuilder.build(), null, null)
    }

    private fun findEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null

        val hint = node.hintText?.toString()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        val resourceId = node.viewIdResourceName?.lowercase() ?: ""

        val isEditableMatch = node.isEditable ||
                node.className == "android.widget.EditText" ||
                hint.contains("message") || hint.contains("ask") ||
                contentDesc.contains("message") || contentDesc.contains("ask") ||
                text.contains("message chatgpt") || text.contains("ask anything") ||
                resourceId.contains("prompt-textarea")

        if (isEditableMatch) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditableNode(child)
            if (found != null) return found
        }
        return null
    }

    private fun findClickableNodeInInputContainer(inputNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var parent: AccessibilityNodeInfo? = inputNode.parent
        var depth = 0
        while (parent != null && depth < 4) {
            for (i in 0 until parent.childCount) {
                val child = parent.getChild(i) ?: continue
                if (child != inputNode) {
                    val desc = child.contentDescription?.toString()?.lowercase() ?: ""
                    val text = child.text?.toString()?.lowercase() ?: ""
                    if (child.isClickable || desc.isNotEmpty() || text.isNotEmpty() || child.className == "android.widget.Button") {
                        if (!desc.contains("mic") && !desc.contains("voice") && !desc.contains("attach")) {
                            return child
                        }
                    }
                }
            }
            parent = parent.parent
            depth++
        }
        return null
    }
}
