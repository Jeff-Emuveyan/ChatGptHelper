package com.bellogate_caliphate.chatgpthelper.service

import android.accessibilityservice.AccessibilityService
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

    /**
     * Pastes current batch into ChatGPT web in Chrome and clicks Send button.
     * Suspends until the operation succeeds or times out.
     */
    suspend fun sendBatchToChatGPT(batchText: String): Pair<Boolean, String?> = withContext(Dispatchers.Main) {
        val rootNode = rootInActiveWindow
            ?: return@withContext Pair(false, "Could not access screen. Make sure Chrome is open in foreground.")

        val inputNode = findEditableNode(rootNode)
            ?: return@withContext Pair(false, "Could not find ChatGPT input box in Chrome screen.")

        // Focus input field
        inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, batchText)
        }

        val textSetSuccess = inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        if (!textSetSuccess) {
            return@withContext Pair(false, "Failed to paste batch URLs into ChatGPT input box in Chrome.")
        }

        // Move cursor/selection to end of text to trigger Web/React state update
        val selectionArgs = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, batchText.length)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, batchText.length)
        }
        inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArgs)

        // Poll over a 5-second window for Chrome web page to enable and present the Send button
        for (attempt in 1..10) {
            delay(500)
            val currentRoot = rootInActiveWindow ?: rootNode

            val sendNode = findSendButtonNode(currentRoot)
                ?: findClickableNodeInInputContainer(inputNode)
                ?: findClickableNodeNearInput(currentRoot, inputNode)

            if (sendNode != null) {
                val clicked = performClickOnNodeOrParent(sendNode)
                if (clicked) {
                    return@withContext Pair(true, null)
                }
            }
        }

        // Final fallback: try clicking clickable nodes near bottom/input
        val finalRoot = rootInActiveWindow ?: rootNode
        val containerClick = clickInputContainerSendButton(finalRoot)
        if (containerClick) {
            return@withContext Pair(true, null)
        }

        return@withContext Pair(false, "Batch pasted, but Send button could not be clicked in Chrome.")
    }

    private fun performClickOnNodeOrParent(node: AccessibilityNodeInfo?): Boolean {
        var curr: AccessibilityNodeInfo? = node
        var depth = 0
        while (curr != null && depth < 5) {
            if (curr.isClickable) {
                val success = curr.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (success) return true
            }
            curr = curr.parent
            depth++
        }
        return false
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

    private fun findSendButtonNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null

        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        val resourceId = node.viewIdResourceName?.lowercase() ?: ""

        val isSendTarget = contentDesc.contains("send") ||
                contentDesc.contains("submit") ||
                contentDesc.contains("prompt") ||
                text.contains("send") ||
                text.contains("submit") ||
                resourceId.contains("send") ||
                resourceId.contains("submit")

        if (isSendTarget) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findSendButtonNode(child)
            if (found != null) return found
        }

        return null
    }

    private fun findClickableNodeInInputContainer(inputNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var parent: AccessibilityNodeInfo? = inputNode.parent
        var depth = 0
        while (parent != null && depth < 3) {
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

    private fun findClickableNodeNearInput(root: AccessibilityNodeInfo?, inputNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val parent = inputNode.parent ?: return null
        for (i in 0 until parent.childCount) {
            val child = parent.getChild(i) ?: continue
            if (child != inputNode) {
                val desc = child.contentDescription?.toString()?.lowercase() ?: ""
                if (child.isClickable || desc.isNotEmpty()) {
                    return child
                }
            }
        }
        return null
    }

    private fun clickInputContainerSendButton(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        val allClickables = mutableListOf<AccessibilityNodeInfo>()
        collectClickableNodes(root, allClickables)

        // Click the last clickable view in active window (the Send button at bottom right)
        for (node in allClickables.reversed()) {
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            if (!desc.contains("back") && !desc.contains("menu") && !desc.contains("settings") && !desc.contains("address")) {
                val clicked = performClickOnNodeOrParent(node)
                if (clicked) return true
            }
        }
        return false
    }

    private fun collectClickableNodes(node: AccessibilityNodeInfo?, list: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        if (node.isClickable) list.add(node)
        for (i in 0 until node.childCount) {
            collectClickableNodes(node.getChild(i), list)
        }
    }
}
