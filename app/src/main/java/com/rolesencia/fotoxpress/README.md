# 📸 FotoXPress

A native Android application designed for high-efficiency batch image processing. Built with **Jetpack Compose** and **Clean Architecture** principles, this tool solves the complexity of mass photo editing through intelligent state management and native hardware integration.

---

## 🚀 Technical Highlights & Architecture

**Algorithmic Auto-Crop Logic**
Implemented a custom trigonometric algorithm that dynamically recalculates the zoom scale based on rotation angle. This ensures the image always fills the viewport without leaving empty edges ("voids"), eliminating the need for manual re-cropping after straightening.

**Secure Scoped Storage Strategy**
Adopted a modern "Safe Copy" architecture compliant with Android 10+ strict privacy rules. Instead of requesting legacy file permissions, the app leverages the **MediaStore API** and `ContentResolver` to securely read source files and publish processed assets to the DCIM directory without compromising system security.

**Reactive Material 3 Theming**
Engineered a fully dynamic UI system using **Semantic Color Tokens** (Surface, OnPrimary, Tertiary). Includes a custom State Hoisting pattern to persist the user's Theme Preference (Dark/Light Mode) across configuration changes (screen rotations) using `rememberSaveable` serialization.

**Deterministic State Management**
Replaced indeterminate loading indicators with a deterministic feedback loop. The ViewModel exposes real-time progress (`processed/total`) via `StateFlow`, coupled with a transactional cleanup logic that ensures database sessions are only purged after successful file operations.

**Optimized Data Projection**
Utilizes **Room Database** with specialized DTOs (Data Transfer Objects) and SQL sub-queries to handle dashboard statistics. This avoids loading full entity lists into memory, ensuring O(1) performance impact even with large datasets (500+ images).

**Non-Destructive Visual History**
Features a "Ghosting" visualization pattern in the selection gallery. The app compares the current directory against the local database in real-time (`O(N)` hash set lookup) to visually distinct previously processed images without removing them from the selection pool.

---

## 🛠 Tech Stack

* **Language:** Kotlin
* **UI Toolkit:** Jetpack Compose (Material Design 3)
* **Architecture:** MVVM (Model-View-ViewModel)
* **Persistence:** Room Database (SQLite)
* **Image Loading:** Coil (Memory caching & async decoding)
* **Concurrency:** Kotlin Coroutines & Flow
* **Compatibility:** Android 12+ Splash Screen API & Adaptive Icons

---

## 📄 License

This project is open-source and available under the MIT License.