package com.bellogate_caliphate.chatgpthelper

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.bellogate_caliphate.chatgpthelper.data.UrlBatchParser
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.InputStreamReader

@RunWith(AndroidJUnit4::class)
class GptUiAutomatorTest {

    companion object {
        private const val TAG = "JEFF_UIAUTOMATOR"
        private const val CHROME_PACKAGE = "com.android.chrome"
        private const val BATCH_DELAY_MS = 300_000L // 300 seconds (5 minutes) waiting interval
    }

    private lateinit var device: UiDevice
    private lateinit var targetContext: Context

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instrumentation)
        targetContext = instrumentation.targetContext
    }

    @Test
    fun runChatGPTUrlBatchAutomation() {
        Log.d(TAG, "Starting UiAutomator ChatGPT URL Batch Automation...")

        // 1. Read and parse URLs from target app assets/urls.txt
        val inputStream = targetContext.assets.open("urls.txt")
        val content = InputStreamReader(inputStream).use { it.readText() }
        val batchInfo = UrlBatchParser.parseBatches(content)

        Log.d(TAG, "Loaded ${batchInfo.totalUrls} URLs in ${batchInfo.totalBatches} batches from assets/urls.txt")

        if (batchInfo.totalBatches == 0) {
            Log.e(TAG, "No URL batches found in urls.txt. Aborting UiAutomator test.")
            return
        }

        // 2. Iterate through each batch
        for ((index, batch) in batchInfo.batches.withIndex()) {
            Log.d(TAG, "--------------------------------------------------")
            Log.d(TAG, "Processing Batch ${index + 1} of ${batchInfo.totalBatches} (${batch.urls.size} URLs)...")

            // Copy batch prompt to system Clipboard
            val clipboard = targetContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("ChatGPT Batch Prompt", batch.formattedPrompt)
            clipboard.setPrimaryClip(clip)

            // Step A: Bring Chrome to foreground
            Log.d(TAG, "Step 1: Bringing Google Chrome to foreground...")
            val launchIntent = targetContext.packageManager.getLaunchIntentForPackage(CHROME_PACKAGE)
            if (launchIntent != null) {
                launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                targetContext.startActivity(launchIntent)
            } else {
                device.executeShellCommand("am start -n com.android.chrome/com.google.android.apps.chrome.Main")
            }

            // Wait for Chrome window
            device.wait(Until.hasObject(By.pkg(CHROME_PACKAGE)), 5000)
            Thread.sleep(2000)

            // Step B: Focus and click the "Ask ChatGPT" box
            Log.d(TAG, "Step 2: Locating and tapping 'Ask ChatGPT' search box...")
            var inputObject = device.findObject(By.res("prompt-textarea"))
                ?: device.findObject(By.textContains("Ask ChatGPT"))
                ?: device.findObject(By.descContains("Chat with ChatGPT"))

            if (inputObject != null) {
                inputObject.click()
            } else {
                // Direct coordinate tap fallback for 720x1280 (360, 1055)
                Log.d(TAG, "Fallback: Tapping prompt box at coordinates (360, 1055)...")
                device.click(360, 1055)
            }
            Thread.sleep(600)

            // Step C: Set/Paste text into input box
            Log.d(TAG, "Step 3: Pasting URL batch into prompt box...")
            inputObject = device.findObject(By.res("prompt-textarea")) ?: inputObject
            if (inputObject != null) {
                try {
                    inputObject.text = batch.formattedPrompt
                } catch (e: Exception) {
                    Log.w(TAG, "UiObject2.text failed, trying clipboard paste keyevent: ${e.localizedMessage}")
                    device.executeShellCommand("input keyevent 279") // KEYCODE_PASTE
                }
            } else {
                device.executeShellCommand("input keyevent 279") // KEYCODE_PASTE
            }

            // Step D: Wait 3 seconds for React state on chatgpt.com to process the text
            Log.d(TAG, "Step 4: Text inserted. Waiting 3 seconds for React state update...")
            Thread.sleep(3000)

            // Step E: Press Back button ONCE to dismiss software keyboard
            Log.d(TAG, "Step 5: Pressing Back button to dismiss software keyboard...")
            device.pressBack()
            Thread.sleep(800)

            // Step F: Click the blue Send button
            Log.d(TAG, "Step 6: Locating and clicking blue Send button...")
            val sendButton = device.findObject(By.res("composer-submit-button"))
                ?: device.findObject(By.descContains("Send prompt"))
                ?: device.findObject(By.textContains("Send prompt"))

            if (sendButton != null) {
                sendButton.click()
            } else {
                // Direct coordinate tap fallback for Small Phone blue Send button (640, 1130)
                Log.d(TAG, "Fallback: Tapping Send button at coordinates (640, 1130)...")
                device.click(640, 1130)
            }

            Log.d(TAG, "✅ Batch ${index + 1}/${batchInfo.totalBatches} sent successfully!")

            // Step G: Wait interval between batches (if not the last batch)
            if (index < batchInfo.batches.size - 1) {
                Log.d(TAG, "Waiting ${BATCH_DELAY_MS / 1000} seconds before sending next batch...")
                Thread.sleep(BATCH_DELAY_MS)
            }
        }

        Log.d(TAG, "🎉 All ${batchInfo.totalBatches} URL batches sent successfully via UiAutomator!")
    }
}
