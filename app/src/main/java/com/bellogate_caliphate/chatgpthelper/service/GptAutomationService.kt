package com.bellogate_caliphate.chatgpthelper.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
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

    private fun showRedClickIndicator(x: Float, y: Float) {
        try {
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return

            val radius = 30 // 30px radius circle
            val size = radius * 2

            val redDotView = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(android.graphics.Color.RED)
                    setStroke(4, android.graphics.Color.WHITE)
                }
            }

            val params = WindowManager.LayoutParams(
                size,
                size,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                this.x = (x - radius).toInt()
                this.y = (y - radius).toInt()
            }

            windowManager.addView(redDotView, params)

            // Remove red dot overlay after 800ms
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    windowManager.removeView(redDotView)
                } catch (e: Exception) {
                    Log.w(TAG, "Error removing red dot overlay: ${e.localizedMessage}")
                }
            }, 800)
        } catch (e: Exception) {
            Log.w(TAG, "Could not draw red click indicator: ${e.localizedMessage}")
        }
    }

    fun clickAtPosition(x: Float, y: Float) {
        Log.d("JEFF", "Initiating gesture click at position ($x, $y)")

        // Show visual red circle overlay at target coordinates
        showRedClickIndicator(x, y)

        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(path, 0, 100)
            )
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                super.onCompleted(gestureDescription)
                Log.d("JEFF", "Gesture click COMPLETED at ($x, $y)")
            }
            override fun onCancelled(gestureDescription: GestureDescription) {
                super.onCancelled(gestureDescription)
                Log.w("JEFF", "Gesture click CANCELLED at ($x, $y)")
            }
        }, null)
    }

    fun longClickAtPosition(x: Float, y: Float) {
        Log.d("JEFF", "Initiating LONG PRESS gesture at position ($x, $y)")

        showRedClickIndicator(x, y)

        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(path, 0, 700) // 700ms long press
            )
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                super.onCompleted(gestureDescription)
                Log.d("JEFF", "LONG PRESS gesture COMPLETED at ($x, $y)")
            }
            override fun onCancelled(gestureDescription: GestureDescription) {
                super.onCancelled(gestureDescription)
                Log.w("JEFF", "LONG PRESS gesture CANCELLED at ($x, $y)")
            }
        }, null)
    }

    /**
     * Pastes current batch into ChatGPT web in Chrome using Long Press + Paste Popup.
     * Suspends until the operation succeeds or times out.
     */
    suspend fun sendBatchToChatGPT(batchText: String): Pair<Boolean, String?> = withContext(Dispatchers.Main) {
        if (rootInActiveWindow == null) {
            return@withContext Pair(false, "Could not access screen. Make sure Chrome is open in foreground.")
        }

        // 1. Copy batch text to System Clipboard
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null) {
                val clip = ClipData.newPlainText("ChatGPT Batch Prompt", batchText)
                clipboard.setPrimaryClip(clip)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not access Clipboard: ${e.localizedMessage}")
        }

        // 2. Wait 10 seconds before long pressing on the search bar
        Log.d("JEFF", "Step 1: Waiting 10 seconds before long pressing on search bar...")
        delay(10_000)

        // 3. Long Press on the ChatGPT search bar at position (360, 1055) to trigger Android's Paste popup
        Log.d("JEFF", "Step 2: Long Pressing ChatGPT search bar at position (360, 1055) to trigger Paste popup")
        longClickAtPosition(360f, 1055f)
        delay(800) // Allow Chrome's Paste popup menu to appear

        // 4. Search all active windows (including floating context toolbar) for "Paste"
        val pasteClicked = findAndClickPasteInAllWindows()
        Log.d("JEFF", "Step 3: Paste popup button click result = $pasteClicked")

        if (!pasteClicked) {
            // Fallback: Tap coordinates (120, 980) on the far-left of popup toolbar where "Paste" is located
            Log.d("JEFF", "Step 3 Fallback: Tapping 'Paste' popup at far left coordinates (120, 980)")
            clickAtPosition(120f, 980f)
        }

        // 5. Wait 5 seconds for React on chatgpt.com to process the inserted batch and enable Send button
        Log.d("JEFF", "Step 4: Batch pasted. Waiting 5 seconds for React state update...")
        delay(5000)

        // 6. Press the Back button ONCE to dismiss the software keyboard
        Log.d("JEFF", "Step 5: Pressing Back button to dismiss software keyboard...")
        performGlobalAction(GLOBAL_ACTION_BACK)
        delay(800)

        // 7. Click the blue Send button at position (640f, 1130f)
        Log.d("JEFF", "Step 6: Clicking blue Send button at position (640, 1130)")
        clickAtPosition(640f, 1130f)

        return@withContext Pair(true, null)
    }

    private fun findAndClickPasteInAllWindows(): Boolean {
        try {
            val activeWindows = windows
            for (window in activeWindows) {
                val root = window.root ?: continue
                if (findAndClickPasteOption(root)) {
                    return true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking active windows for Paste button: ${e.localizedMessage}")
        }
        return findAndClickPasteOption(rootInActiveWindow)
    }

    private fun findAndClickPasteOption(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val resId = node.viewIdResourceName?.lowercase() ?: ""

        val isPasteButton = text == "paste" ||
                desc == "paste" ||
                resId.contains("paste")

        if (isPasteButton) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            Log.d("JEFF", "Found Paste popup button at bounds: $rect (${node.className}, viewId: $resId, text: $text)")

            // Perform direct click
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)

            // Tap exact left portion of the Paste node
            val targetX = if (rect.left > 0) (rect.left + 30f) else 120f
            val targetY = if (rect.centerY() > 0) rect.centerY().toFloat() else 980f

            Log.d("JEFF", "Tapping 'Paste' node left bounds at ($targetX, $targetY)")
            clickAtPosition(targetX, targetY)

            return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndClickPasteOption(child)) return true
        }

        return false
    }
}
