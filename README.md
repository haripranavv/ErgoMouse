# Ergomouse 🖱️📱

Ergomouse is a custom-built, dual-mode hardware-to-software pipeline that turns an Android smartphone into a high-performance trackpad for Windows. 

It bypasses standard, laggy third-party apps by using a custom Android UI to capture raw touch data, packaging it into UDP network packets, and firing it to a blazing-fast Rust receiver injected directly into the Windows OS.

## 🚀 Features
* **Zero-Latency Wi-Fi Tunnel:** Uses UDP Datagram Sockets on Port 51234 for instant cursor response.
* **Two-Finger Scrolling:** Custom panning math cleanly maps Android touch offsets to Windows scroll wheel increments.
* **Invisible Background Server:** The Rust receiver is compiled in "Ghost Mode" (`windows_subsystem`) to run completely silently in the background without cluttering the taskbar.
* **Modern Android UI:** Built entirely in Kotlin and Jetpack Compose.

## 🛠️ Tech Stack
* **Mobile Client:** Kotlin, Jetpack Compose, Android `DatagramSocket`.
* **PC Server:** Rust, Tokio (Async Networking), Windows API mouse injection.

## ⚙️ How to Run It

### 1. The Windows Server
1. Download or compile `desktop-receiver.exe`.
2. Double-click to run. (Note: The app runs invisibly in the background).
3. *To close the server:* Open Task Manager (Ctrl + Shift + Esc) and end `desktop-receiver.exe`.

### 2. The Android Client
1. Install the Ergomouse APK on your phone.
2. Ensure your phone and PC are on the same Wi-Fi network.
3. Enter your PC's local IP address into the app and connect.
4. Swipe away! 

## 🧠 What I Learned
* Handling raw pointer/touch event math across two different coordinate systems.
* Designing binary network packets byte-by-byte to minimize payload size.
* Compiling Rust into a standalone, optimized Windows executable.