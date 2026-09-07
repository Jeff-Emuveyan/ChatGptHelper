# ChatGptHelper 🤖 (UiAutomator Test Suite)

> [!IMPORTANT]
> **📊 Observe Real-Time Execution in Logcat:**
> To watch live batch execution steps, delays, and progress logs while the automation runs, open Android Studio's **Logcat** tool window and filter for:
> ```text
> tag:JEFF_UIAUTOMATOR
> ```

An Android system-wide automation test suite built with **UiAutomator** (`androidx.test.uiautomator:uiautomator`) that automates sending large lists of website URLs to **ChatGPT on Google Chrome** (`https://chatgpt.com`) in timed batches.

> [!NOTE]
> **This is an Android Test Automation Project, NOT a standalone app that needs to be manually operated.** All automation logic runs inside the Android Instrumentation Test Runner via `GptUiAutomatorTest.kt`.

---

## ⚙️ How the System Works

The test script [`GptUiAutomatorTest.kt`](file:///C:/Users/jemuveyan/AndroidStudioProjects/ChatGptHelper/app/src/androidTest/java/com/bellogate_caliphate/chatgpthelper/GptUiAutomatorTest.kt) uses `UiDevice` to control Google Chrome directly at the system level:

```
[ Read URL Batches from assets/urls.txt ]
                   │
                   ▼
[ Foreground Google Chrome via UiDevice ]
                   │
                   ▼
[ Locate "Ask ChatGPT" box (prompt-textarea) or tap (360, 1055) ]
                   │
                   ▼
[ Set/Paste Batch Prompt via UiObject2.setText() / Clipboard ]
                   │
                   ▼
[ Wait 3 Seconds for React State Update on chatgpt.com ]
                   │
                   ▼
[ device.pressBack() -> Press Back Button ONCE to Close Keyboard ]
                   │
                   ▼
[ Click Send Button (composer-submit-button) or tap (640, 1130) ]
                   │
                   ▼
[ Thread.sleep(150_000) -> Wait 150s (2.5 mins) ] -> Repeat
```

---

## 📱 Hardware & Emulator Specifications

This UiAutomator test is configured and calibrated for the following emulator profile:

* **Device Profile**: Small Phone
* **Resolution (Pixels)**: `720 x 1280` px
* **Resolution (DP)**: `360 x 640` dp
* **Density**: `320 dpi`
* **API Level**: Android 10 to 16 (API 29–36)
* **Architecture**: x86_64 / arm64-v8a

---

## 🌐 ChatGPT Setup Instructions

Before running the test:

1. Launch **Google Chrome** on your emulator.
2. Navigate to `https://chatgpt.com` and log into your OpenAI account.
3. Start a new chat tab (or open your existing conversation).
4. Send your initial instruction prompt to ChatGPT, for example:
   > *"I will send you batches of company website URLs. Please check each batch for active job vacancies and summarize the results."*
5. Leave Chrome open to that conversation tab on the emulator screen.

---

## 📝 Adding Your URLs

1. Open [`app/src/main/assets/urls.txt`](file:///C:/Users/jemuveyan/AndroidStudioProjects/ChatGptHelper/app/src/main/assets/urls.txt) in Android Studio.
2. Paste your list of website URLs.
3. Separate each batch using **blank lines**:

```text
https://company1.com/careers
https://company2.com/jobs
https://company3.com/careers

https://company4.com/jobs
https://company5.com/careers
https://company6.com/jobs
```

---

## 🚀 How to Run the UiAutomator Test

### Option A: From Android Studio (Recommended)
1. Open [`app/src/androidTest/java/com/bellogate_caliphate/chatgpthelper/GptUiAutomatorTest.kt`](file:///C:/Users/jemuveyan/AndroidStudioProjects/ChatGptHelper/app/src/androidTest/java/com/bellogate_caliphate/chatgpthelper/GptUiAutomatorTest.kt).
2. Click the green **Run** play icon next to `class GptUiAutomatorTest` or `@Test fun runChatGPTUrlBatchAutomation()`.

### Option B: Via Terminal / ADB
Run this command in terminal/PowerShell:
```bash
adb shell am instrument -w -e class com.bellogate_caliphate.chatgpthelper.GptUiAutomatorTest com.bellogate_caliphate.chatgpthelper.test/androidx.test.runner.AndroidJUnitRunner
```

---

## 📊 Monitoring Real-Time Logs

In Android Studio's **Logcat** tool window, filter for:
```text
tag:JEFF_UIAUTOMATOR
```

You will see real-time progress logs:
* `JEFF_UIAUTOMATOR: Loaded 15 URLs in 3 batches from assets/urls.txt`
* `JEFF_UIAUTOMATOR: Processing Batch 1 of 3 (5 URLs)...`
* `JEFF_UIAUTOMATOR: ✅ Batch 1/3 sent successfully!`
* `JEFF_UIAUTOMATOR: Waiting 150 seconds before sending next batch...`

---

## ⏱️ Modifying Waiting Interval

To change the waiting interval between batches (default is 240 seconds / 4 minutes), open [`GptUiAutomatorTest.kt`](file:///C:/Users/jemuveyan/AndroidStudioProjects/ChatGptHelper/app/src/androidTest/java/com/bellogate_caliphate/chatgpthelper/GptUiAutomatorTest.kt) and edit `BATCH_DELAY_MS`:

```kotlin
private const val BATCH_DELAY_MS = 240_000L // 240 seconds (4 minutes)
```
