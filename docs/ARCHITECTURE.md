# Ergonomic Phone Mouse — Full System Architecture

A phone-as-mouse system built for **hours of comfortable use**, not just remote
control. Two apps, one wire protocol:

- **Android app** (Kotlin + Compose) — the input surface.
- **Desktop receiver** (Rust) — Windows / macOS / Linux, turns packets into a real OS cursor via HID.

---

## 1. System Overview

```
 ┌─────────────────────────┐        USB-C (HID-over-serial)       ┌──────────────────────────┐
 │        ANDROID APP       │ ────────────────────────────────────▶│                          │
 │                          │                                       │                          │
 │  Touch Engine            │        BLE HID (fallback)             │   DESKTOP RECEIVER       │
 │  Palm Rejection          │ ────────────────────────────────────▶│   (Rust, background svc) │
 │  Gesture Recognizer      │                                       │                          │
 │  Sensitivity/Accel Curve │      Wi-Fi Direct / LAN UDP (main)    │  Transport Manager        │
 │  Connection Manager      │ ────────────────────────────────────▶│  Input Fuser              │
 │  Minimal Compose UI      │                                       │  OS HID Injector          │
 └─────────────────────────┘        QR pairing (one-time, TLS+PSK) │  Discovery/Pairing Server │
                                                                     │  Tray UI / Auto-reconnect │
                                                                     └──────────────────────────┘
```

**Design principle:** the phone is a *dumb, fast, opinionated sensor*. All the
"what does this touch mean" logic (gestures, palm rejection, curves) happens
on-device so latency-critical decisions never wait on a round trip. The
desktop side is a *dumb, fast injector* — it trusts the phone and just turns
packets into OS mouse events as fast as the platform allows.

### Transport priority (auto-selected, user can override in Gaming Mode)

| Priority | Transport | Typical latency | Use case |
|---|---|---|---|
| 1 | USB-C (AOA / HID-over-serial) | ~1–3 ms | Competitive gaming, zero jitter |
| 2 | Wi-Fi Direct / local UDP | ~5–15 ms | Default wireless, no router needed |
| 3 | Bluetooth LE (HID profile) | ~10–30 ms | Fallback, best battery life |

The Connection Manager measures round-trip latency continuously and can
hot-switch transports without the user noticing (e.g., BLE while walking to
the desk → auto-upgrades to Wi-Fi Direct once both devices are stationary and
in range, or USB the instant a cable is plugged in).

---

## 2. Repository / Folder Structure

```
ergomouse/
├── protocol/                      # Shared wire-format contract (source of truth)
│   ├── schema/
│   │   └── packet.proto           # Protobuf-lite / FlatBuffers schema (see §5)
│   ├── kotlin/                    # Generated Kotlin bindings
│   └── rust/                      # Generated Rust bindings
│
├── android-app/
│   ├── app/src/main/java/com/ergomouse/
│   │   ├── ui/                    # Jetpack Compose screens (Trackpad, Settings, Pairing)
│   │   │   ├── trackpad/          # TrackpadScreen, ButtonBar, GamingOverlay
│   │   │   ├── pairing/           # QR scan/display, device list
│   │   │   └── theme/
│   │   ├── input/                 # Core input pipeline (the heart of the app)
│   │   │   ├── TouchEngine.kt          # Raw MotionEvent → normalized touch stream
│   │   │   ├── PalmRejectionFilter.kt  # Thumb vs. palm/finger classification
│   │   │   ├── GestureRecognizer.kt    # Tap/drag/scroll/swipe/pinch → intents
│   │   │   ├── SensitivityCurve.kt     # Adaptive accel/precision math
│   │   │   └── InputIntent.kt          # Sealed class of output events
│   │   ├── connection/
│   │   │   ├── TransportManager.kt     # Picks/switches USB|WiFiDirect|BLE
│   │   │   ├── UsbHidTransport.kt
│   │   │   ├── WifiDirectTransport.kt
│   │   │   ├── BleHidTransport.kt
│   │   │   ├── PairingManager.kt       # QR generation/scan, PSK exchange
│   │   │   └── PacketCodec.kt          # Serialize InputIntent → wire packet
│   │   ├── sensor/                # Battery, latency probe
│   │   ├── haptics/               # HapticFeedbackController.kt
│   │   ├── di/                    # Hilt modules
│   │   └── MainActivity.kt
│   └── build.gradle.kts
│
├── desktop-receiver/              # Rust workspace
│   ├── Cargo.toml
│   ├── src/
│   │   ├── main.rs                # Background service entry point + tray
│   │   ├── hid/
│   │   │   ├── mod.rs
│   │   │   ├── injector.rs        # Cross-platform trait: MouseInjector
│   │   ├── platform/
│   │   │   ├── windows/mod.rs     # SendInput / raw HID
│   │   │   ├── macos/mod.rs       # CGEvent / IOKit HID
│   │   │   └── linux/mod.rs       # uinput / evdev
│   │   ├── net/
│   │   │   ├── usb_serial.rs
│   │   │   ├── wifi_direct.rs
│   │   │   ├── ble.rs
│   │   │   └── packet.rs          # Decode wire packets
│   │   ├── discovery/
│   │   │   ├── mdns.rs            # LAN auto-discovery
│   │   │   └── pairing.rs         # QR payload + PSK handshake
│   │   ├── input_fuser.rs         # Merges curve output, applies OS cursor step
│   │   └── tray_ui.rs             # System tray: status, latency, reconnect
│   └── assets/icons/
│
├── docs/
│   ├── ARCHITECTURE.md            # this file
│   ├── PROTOCOL.md
│   └── ROADMAP.md
└── README.md
```

---

## 3. UI/UX Design

### 3.1 Layout (portrait, one-handed grip)

```
┌───────────────────────────────┐
│  ● Connected   🔋87%   ⏱ 6ms   │  ← 24dp status bar, auto-hides after 2s idle
├───────────────────────────────┤
│                                │
│                                │
│         TRACKPAD ZONE          │  70% of height
│      (full-bleed touch area)   │  no visible chrome — max area = max comfort
│                                │
│                                │
├───────────────────────────────┤
│  LEFT CLICK │ SCROLL │ RIGHT   │  30% of height, 3 large zones
│   (48%)     │ (18%)  │ (34%)   │  scroll zone doubles as middle-click (tap)
└───────────────────────────────┘
```

- Button split is **asymmetric by design**: left-click gets the most thumb
  reach, right-click is angled toward the outer edge where the thumb naturally
  lands, scroll sits dead-center as a thin "spine" (matches old ThinkPad
  trackpoint muscle memory).
- **Left-handed mode** mirrors the entire layout horizontally in one toggle —
  not just the buttons, but the asymmetric sizing too.
- No hamburger menu, no bottom nav. Settings are a single edge-swipe-down
  sheet from the status bar — kept out of thumb's way entirely.
- Dynamic button positioning: on first launch, a 3-second calibration asks the
  user to rest their grip naturally and tap where their thumb rests at rest,
  idle, and full-stretch. This defines per-device thumb-reach zones used to
  bias hit-targets (see §4.3).

### 3.2 Gaming Mode overlay

Toggled from the status bar. Replaces status text with a live latency graph
(sparkline, last 3s), disables all animations/haptics that add frame delay,
and pins transport to USB if a cable is present.

### 3.3 Visual language

- Dark, near-black background (`#0B0B0D`) — no glow/burn-in risk, low GPU cost.
- One accent color for state (green = connected/low latency, amber = degraded,
  red = disconnected).
- Zero decorative animation on the trackpad surface itself — any compositing
  there adds input-to-photon lag for no benefit, since the user isn't looking
  at their phone while mousing.

---

## 4. Gesture & Input Pipeline (Android)

### 4.1 Pipeline stages

```
MotionEvent (raw, all pointers)
      │
      ▼
TouchEngine            — normalizes coordinates, tracks per-pointer history
      │
      ▼
PalmRejectionFilter    — classifies each active pointer: THUMB | PALM | UNKNOWN
      │  (drops PALM pointers before they ever reach gesture logic)
      ▼
GestureRecognizer      — stateful FSM: tap / double-tap / long-press-drag /
      │                   two-finger-scroll / three-finger-swipe / pinch
      ▼
SensitivityCurve       — velocity-aware transform (see 4.2)
      │
      ▼
InputIntent (sealed class: Move, Click, Scroll, Drag, Modifier, Zoom)
      │
      ▼
PacketCodec → TransportManager → wire
```

### 4.2 Adaptive sensitivity / acceleration

Not a single multiplier — a velocity-bucketed curve, re-evaluated every touch
frame (~120–240 Hz on modern panels):

- `|v| < precisionThreshold` → sub-pixel mode: cursor delta scaled down
  (e.g., 0.4×) for pixel-perfect placement — text cursor placement, design tools.
- `precisionThreshold ≤ |v| < cruiseThreshold` → linear 1:1-ish mapping,
  user-tunable base sensitivity slider.
- `|v| ≥ cruiseThreshold` → progressive acceleration curve (configurable
  exponent) so a fast flick crosses a 4K monitor in one thumb stroke.
- Curve parameters are saved per-profile: **Productivity**, **Gaming**,
  **Design/Precision** — user picks or app can prompt based on active
  desktop app (future: desktop side reports focused app via optional
  companion, purely additive, never required).

### 4.3 Palm rejection (thumb detection)

Signals fused per pointer (weighted heuristic, tuned per phone via a small
on-device calibration step, not a heavy ML model — must run every frame with
near-zero cost):

1. **Touch major/minor axis (contact ellipse size)** — thumbs pressing from
   below the phone produce a larger, more elongated ellipse than fingertip
   contact from the back/sides bleeding onto the edge.
2. **Entry position relative to calibrated thumb-reach zone** (§3.1) — touches
   originating far outside the learned thumb arc are down-weighted.
3. **Pressure** (where `MotionEvent.getPressure()` is reliable) — light,
   incidental contact from supporting fingers registers lower than deliberate
   thumb presses.
4. **Motion coherence** — a supporting finger tends to be static or drift
   with hand micro-tremor; the active thumb shows purposeful, continuous
   trajectories. Static touches lasting > N ms with < ε movement and small
   contact area are reclassified as rest/PALM and ignored entirely.
5. **Edge-of-screen bias** — touches within a few mm of the physical bezel
   edges opposite the thumb's natural arc are rejected by default (that's
   where the four supporting fingers wrap around).

Only one pointer is ever promoted to "active thumb" at a time for
click/move; extra simultaneous touches are evaluated purely for multi-finger
gestures (2-finger scroll, 3-finger swipe) using the same classifier so a palm
resting on the case doesn't get counted as one of those fingers either.

### 4.4 Gesture table

| Gesture | Action |
|---|---|
| Single tap (thumb) | Left click |
| Double tap | Double click |
| Long press + hold + move | Click-and-drag |
| Two-finger vertical drag | Smooth scroll (with inertia) |
| Two-finger tap | Middle click |
| Three-finger swipe left/right | Browser Back / Forward |
| Pinch (two-finger) | Zoom (Ctrl+Scroll emulated, or native pinch on supported apps) |
| Edge-hold (left/right/top bezel) | Momentary Ctrl / Shift / Alt modifier |

---

## 5. Wire Protocol

Binary, fixed-size where possible, designed to fit in a single UDP datagram
or USB HID report — **no JSON on the hot path**.

### 5.1 Packet types

```
enum PacketType : u8 {
  HELLO         = 0x01,  // pairing handshake
  HEARTBEAT     = 0x02,  // keepalive + latency probe (RTT ping)
  MOVE          = 0x10,  // dx, dy (delta, not absolute)
  CLICK         = 0x11,  // button, down/up
  SCROLL        = 0x12,  // dx, dy scroll delta
  MODIFIER      = 0x13,  // ctrl/shift/alt bitmask
  DRAG_BEGIN    = 0x14,
  DRAG_END      = 0x15,
  BATTERY       = 0x20,  // status/telemetry, low frequency
  MODE          = 0x21,  // gaming/productivity profile switch
  ACK           = 0xFE,
  BYE           = 0xFF,
}
```

### 5.2 Core packet layout (little-endian, 1 datagram = 1+ events)

```
Header (6 bytes)
  u8   version
  u8   type
  u16  seq            // monotonic, for reorder/dedupe + latency calc
  u16  payload_len

Payload (MOVE, 8 bytes)
  i16  dx             // sub-pixel fixed-point (Q8.8) for smooth low-speed motion
  i16  dy
  u16  timestamp_ms    // capture time on phone (mod 65536), for jitter smoothing
  u8   flags           // bit0=precision_mode, bit1=drag_active
  u8   reserved
```

- **MOVE/SCROLL are coalesced**: if the transport queue backs up (e.g., BLE
  under load), the phone merges pending deltas into one packet rather than
  flooding the link — bounded queue depth of 2 frames max, older frames are
  summed, never dropped silently, so cursor motion stays proportionally
  correct instead of jittery.
- **CLICK/DRAG events are never coalesced** — sent immediately, out-of-band
  priority over MOVE if the transport supports it (USB does; UDP uses a
  separate high-priority send call to avoid queuing behind bulk MOVE traffic).
- Sequence numbers let the receiver compute one-way jitter and detect
  drops without needing a full ack/retransmit scheme, which would add
  latency — **this protocol is lossy-tolerant by design**: a dropped MOVE
  packet is just a slightly bigger next delta, not a stall.

### 5.3 Pairing (QR flow)

1. Desktop receiver, on first run, generates an ephemeral keypair and shows a
   QR code encoding `{ device_id, ip/mDNS-name, pubkey, one_time_token }`.
2. Phone scans QR → derives a shared secret via ECDH → stores it keyed by
   `device_id` in Android EncryptedSharedPreferences.
3. All subsequent sessions: phone auto-discovers the desktop via mDNS
   (`_ergomouse._udp.local`) or USB VID/PID match, and authenticates with a
   HMAC over the HELLO packet using the stored shared secret — **no password
   prompt, no manual IP entry, ever again**.
4. One-tap reconnect UI simply re-sends HELLO to the last-known transport;
   falls back to discovery scan if that fails.

Full schema lives in `protocol/schema/packet.proto` (see scaffold).

---

## 6. Desktop Receiver (Rust)

### 6.1 Responsibilities

- Listen on all enabled transports concurrently (tokio tasks), decode
  packets, push into a single lock-free ring buffer consumed by one
  dedicated **input-fuser thread** pinned away from GC/async scheduling
  jitter.
- Input-fuser thread converts the fused delta stream into OS calls at the
  highest safe rate the platform allows (matching display refresh, typically
  driven by a 1000 Hz-capable timer on Windows via `SendInput`, `CGEventPost`
  on macOS, `uinput` device writes on Linux).
- Runs as a background service/tray app (no dock/taskbar window), auto-starts
  on login, auto-reconnects to last-paired phone on wake/launch.

### 6.2 Platform HID abstraction

```rust
trait MouseInjector: Send + Sync {
    fn move_relative(&self, dx: i32, dy: i32);
    fn scroll(&self, dx: i32, dy: i32);
    fn click(&self, button: Button, down: bool);
    fn set_modifiers(&self, mods: ModifierMask);
}
```
Each platform module (`platform/windows`, `platform/macos`, `platform/linux`)
implements this trait against the native lowest-level API available
(bypassing any Electron/webview-style intermediaries entirely — this is a
native binary, not a wrapped web app, precisely to protect the latency
budget).

### 6.3 Auto-discovery & reconnect

- `discovery/mdns.rs` advertises/browses `_ergomouse._udp.local` for LAN
  Wi-Fi Direct pairing without router configuration.
- USB path uses Android Open Accessory (AOA) protocol; receiver watches for
  the known VID/PID and re-attaches automatically on cable insert.
- BLE path re-scans for the bonded device's advertised service UUID on
  service restart.

---

## 7. Latency Optimization Strategy

1. **USB HID-over-serial first**: kernel-level, no radio scheduling — this is
   the sub-3ms path for Gaming Mode, always preferred when cable present.
2. **Skip the app-layer stack where possible**: Wi-Fi Direct/UDP path uses raw
   sockets, no TLS on the hot data channel (auth/integrity via lightweight
   HMAC per packet instead — full TLS handshake overhead isn't worth it for a
   trusted, pre-paired local link).
3. **No frame coalescing delay**: packets are flushed immediately, not
   batched on a timer — batching trades latency for throughput, which is the
   wrong trade for a mouse.
4. **Dedicated real-time-priority thread on desktop** for the final
   packet-to-HID-call step, isolated from network I/O and UI thread so a
   GC pause or UI repaint never delays a cursor move.
5. **Predictive smoothing, not delayed smoothing**: jitter reduction on the
   phone uses a lightweight one-euro filter (cheap, low-lag) rather than a
   moving average window, which would add latency proportional to window size.
6. **Render decoupling**: the phone's Compose UI never redraws in response to
   trackpad touch (no ripple/visual feedback on the trackpad surface itself)
   — only haptic feedback, which is off the render thread and near-zero-cost.
7. **Continuous RTT measurement** via HEARTBEAT packets feeds the on-screen
   latency readout and drives automatic transport fallback/upgrade.

Target latency budget (touch-to-cursor-motion, USB): **< 4 ms**.
Wi-Fi Direct target: **< 12 ms**. BLE fallback target: **< 25 ms**.

---

## 8. Battery & Efficiency

- Touch sampling throttles to display refresh rate (no oversampling beyond
  what the panel reports).
- BLE mode uses connection-interval negotiation tuned for low latency only
  when Gaming Mode is off; Productivity mode relaxes interval for battery.
- Screen can dim/blank after N seconds of idle *without* disconnecting —
  trackpad remains touch-active under a locked/dimmed screen for one-handed
  blind operation (a genuinely differentiating comfort feature: users
  shouldn't need to look at the phone at all once positioned in the hand).

---

## 9. Development Roadmap

**Phase 0 — Protocol & Scaffolding (this deliverable)**
Repo structure, wire protocol schema, shared type generation.

**Phase 1 — MVP (single transport, one platform)**
- Android: trackpad + 3 buttons, basic tap/drag/scroll, no palm rejection yet.
- Desktop: Linux OR Windows receiver, Wi-Fi Direct/UDP only, manual pairing.
- Goal: prove the input pipeline and protocol end-to-end.

**Phase 2 — Ergonomics & Palm Rejection**
- Full gesture table, calibration flow, palm rejection heuristic, adaptive
  sensitivity curves, haptics.

**Phase 3 — Multi-transport**
- Add BLE HID and USB AOA transports + automatic transport selection/switch.
- QR pairing flow, EncryptedSharedPreferences key storage, mDNS discovery.

**Phase 4 — Cross-platform receiver**
- Port HID injector to macOS and Windows; unify behind the `MouseInjector`
  trait; tray UI on all three OSes; auto-start/auto-reconnect.

**Phase 5 — Gaming Mode & Performance Hardening**
- Real-time thread priority tuning, latency HUD, one-euro filter tuning,
  packet coalescing edge cases, jitter testing across transports.

**Phase 6 — Polish & Ship**
- Left-handed mode, per-app sensitivity profiles, settings sheet, onboarding
  calibration UX, crash/telemetry opt-in, store listing, signed installers
  (MSI/pkg/AppImage) + Play Store release.

---

## 10. Tech Stack Summary

| Layer | Choice | Why |
|---|---|---|
| Android UI | Kotlin + Jetpack Compose | Native, low overhead, good touch APIs |
| Android input | Native `MotionEvent` handling, no gesture library | Full control needed for palm rejection |
| DI | Hilt | Standard, testable |
| Desktop | Rust + tokio | Memory-safe, no GC pause, native perf |
| Desktop HID | Platform-native (SendInput / CGEvent / uinput) | Lowest latency path per OS |
| Wire format | Custom fixed-size binary | No JSON/serde overhead on hot path |
| Discovery | mDNS (`_ergomouse._udp.local`) | Zero-config LAN discovery |
| Security | ECDH pairing + per-packet HMAC | Fast, no per-packet TLS overhead |
