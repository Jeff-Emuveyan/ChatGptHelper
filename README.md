# ChatGptHelper 🚀

An Android application that automates sending large lists of website URLs to **ChatGPT in Google Chrome** (`https://chatgpt.com`) in timed batches.

---

## 📌 The Problem It Solves

When reviewing long lists of company websites (e.g., 500–1000+ career pages for job vacancies), manually pasting 25 URLs every 4–5 minutes into ChatGPT on Chrome can take hours of continuous manual effort. 

**ChatGptHelper** automates this entire repetitive workflow using Android Accessibility Services and Foreground Services.

---

## ⚙️ How It Works

1. **URL Batch Parsing**: The app reads your URL list from `app/src/main/assets/urls.txt`. Groups of URLs separated by **blank lines** are automatically converted into distinct batches.
2. **Chrome Browser Launch**: The app opens Google Chrome directly to `https://chatgpt.com`.
3. **Accessibility Automation (`GptAutomationService`)**: Uses Android's `AccessibilityService` API to inspect Chrome's active window, locate the web text input box, insert the current batch prompt, and click the web **Send** button automatically.
4. **Timed Foreground Execution (`AutomationForegroundService`)**: Runs as a Foreground Service with a wake lock to ensure the timer runs reliably in the background every 4 minutes (configurable) without Android putting the app to sleep.
5. **Jetpack Compose UI**: Provides real-time metrics, including:
   - Total URLs loaded
   - Total Batches
   - Batches Sent
   - Batches Remaining
   - Overall Progress bar
   - Start and Pause controls

---

## 🚀 How to Use the App

### Step 1: Add Your URLs
Open `app/src/main/assets/urls.txt` in Android Studio and paste your list of URLs. Separate each batch using a **blank line**:

```text
https://company1.com/careers
https://company2.com/jobs
https://company3.com/careers

https://company4.com/jobs
https://company5.com/careers
https://company6.com/jobs
```

### Step 2: Grant Accessibility Permission
1. Build and install the app on your Android device or emulator.
2. Open **ChatGptHelper**.
3. Tap the **"Open Accessibility Settings"** banner and enable **ChatGptHelper** under Installed Apps / Accessibility.

### Step 3: Give ChatGPT Initial Instructions in Chrome
1. Open Google Chrome on your Android device and go to `https://chatgpt.com`.
2. Send an initial instruction prompt, such as:
   > *"I will provide batches of company website URLs. Please check each batch for active job vacancies and summarize the results."*

### Step 4: Start Automation
1. Return to **ChatGptHelper**.
2. Tap **Start**.
3. The app will automatically launch Chrome to `https://chatgpt.com`, paste the first batch, click **Send**, wait 4 minutes, and repeat until all batches are sent!

---

## ⏱️ How to Change the Waiting Time

To adjust the waiting interval between batches (default is 4 minutes / 240 seconds), open `AutomationForegroundService.kt` and edit `TIMER_INTERVAL_SECONDS`:

```kotlin
// app/src/main/java/com/bellogate_caliphate/chatgpthelper/service/AutomationForegroundService.kt
private const val TIMER_INTERVAL_SECONDS = 240 // 4 minutes (e.g. 180 for 3 mins, 300 for 5 mins)
```

---

## 🛠️ Tech Stack & Requirements

* **Language**: Kotlin
* **UI Framework**: Jetpack Compose (Material 3)
* **Minimum SDK**: Android 10 (API 29+) / Target SDK 37
* **Android System APIs**:
  * `AccessibilityService`
  * `ForegroundService` (Special Use)
  * Package Visibility (`<queries>`) for `com.android.chrome`
* **Prerequisites**: Google Chrome installed on device.
