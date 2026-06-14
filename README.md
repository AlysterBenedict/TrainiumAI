# Trainium AI — Your On-Device AI Fitness Coach

**Trainium AI** is a premium, high-performance Android fitness application that delivers a hyper-personalized, 100% offline, privacy-first coaching experience. Powered by local deep learning models running via LiteRT and MediaPipe, it tracks your body metrics, creates tailored progressive workout plans, guides your form in real-time, and answers your fitness queries without ever sending your data to the cloud.

For full design details, copywriting, and visual assets, refer to the official presentation website source at [website/index.html](file:///c:/Users/bened/AndroidStudioProjects/AIFitnessCoach/website/index.html) or visit the live deployment at [https://trainium-ai.web.app](https://trainium-ai.web.app).

---

## 🌟 Key Application Features

As outlined in the official product page, the app delivers a complete offline fitness ecosystem:

### 1. AI Body Scan & Biometrics
* **Silhouette Metric Estimation**: Processes frontal and side silhouette views to estimate **15+ body metrics** in seconds (including chest, waist, hips, thighs, arms, calves, wrist, and waist-to-hip ratios).
* **Automatic BMI Calculation**: Synced globally across the application.
* **On-Device Regression**: Powered by a dual-branch EfficientNet neural network.

### 2. Real-Time Pose Tracking & Auditing
* **33 Body Keypoints**: Powered by **MediaPipe BlazePose GHUM 3D** to track skeletal movement in real-time through the front camera.
* **50+ Exercises Tracked**: Audits joints angles, postures, and counts reps/holds.
* **Live Form Correction**: Real-time HUD overlays color-code your stance (green for correct, red for warnings) and speak corrective voice cues (e.g. *"Go Deeper"*, *"Keep your back straight"*).

### 3. Autoregressive Workout Generation
* **Causal Transformer**: Uses a decoder-only Transformer network (trained on 100k synthetic profiles) to generate custom 30-day training programs.
* **Smart Progressions**: Integrates progressive overload schedules and an active deload cycle (Week 3) based on user goal, experience level, and body metrics.

### 4. Interactive AI Chat Coach (Gemma 4)
* **Gemma 4 via LiteRT**: Runs Google's Gemma model locally on the phone's CPU/GPU.
* **Context-Aware Conversations**: Accesses your biometrics, daily calories, consistency streaks, and workout logs to give personalized macro plans, motivation, and recovery advice.
* **Multilingual Assistant**: Supports English, Hindi, Kannada, Tamil, Telugu, Malayalam, and more.

### 5. Advanced Analytics Dashboard
* **Weight Trend Charts**: Interactive weight tracker with goal line comparison and delta computations.
* **Weekly Streak Grid**: Visual consistency checker to track completed daily routines.
* **Daily Goal Rings**: Custom targets for active minutes and calorie burns.

---

## 🛠️ App Architecture & Setup

### Dynamic Model Downloader
To comply with app footprint best practices, the 2.58 GB `gemma-4-E2B-it.litertlm` weights are not pre-packaged in the APK. Instead, the application features an on-demand downloader:
* **Background Scope**: Managed by a singleton coroutine context in `ModelDownloader.kt` that persists if the user navigates away or switches tabs.
* **Resume-Capable**: Uses HTTP Range headers to pause, resume, or cancel the download at any time.
* **User Control**: Deleting the offline model in Profile settings removes the weights file but preserves the user's SQLite progress data.

### Three Workout Modes
1. **AI Trainer Mode**: Full camera tracking with pose estimation and HUD corrective cues.
2. **Self Mode**: No-camera, timer-based tracking with manual rep logging.
3. **Tutorial Mode**: High-definition video demos detailing proper form for all 50+ exercises.

---

## 🚀 Building the App

1. Clone this repository:
   ```bash
   git clone https://github.com/AlysterBenedict/AI_GYM.git
   cd AIFitnessCoach
   ```
2. Build the Android Package (APK):
   ```bash
   ./gradlew assembleDebug
   ```
3. Locate the compiled build at `app/build/outputs/apk/debug/app-debug.apk`.
