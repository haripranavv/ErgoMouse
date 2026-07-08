# ErgoMouse

Turn an Android phone into a comfortable, ultra-low-latency mouse/trackpad
for Windows, macOS, and Linux — built for hours of one-handed use, not just
remote control.

- **Full architecture, protocol, UI/UX, and roadmap:** [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- **Wire protocol schema:** [`protocol/schema/packet.proto`](protocol/schema/packet.proto)
- **Android app:** [`android-app/`](android-app/) — Kotlin + Jetpack Compose
- **Desktop receiver:** [`desktop-receiver/`](desktop-receiver/) — Rust, native HID per OS

## Status

Phase 0: repo scaffold, protocol schema, and core skeletons in place
(palm rejection filter, input intent model, packet decoder, HID injector
trait, Gradle/Cargo build files). See §9 of the architecture doc for the
phased build plan.

## Quick orientation

| I want to... | Look at... |
|---|---|
| Understand the whole system | `docs/ARCHITECTURE.md` §1–2 |
| See the exact byte layout on the wire | `docs/ARCHITECTURE.md` §5 |
| See how palm rejection works | `docs/ARCHITECTURE.md` §4.3 + `android-app/.../input/PalmRejectionFilter.kt` |
| Add a new gesture | `android-app/.../input/InputIntent.kt` then wire into `GestureRecognizer.kt` |
| Add a new OS backend | implement `MouseInjector` in `desktop-receiver/src/platform/<os>/` |
| Check the build/dev roadmap | `docs/ARCHITECTURE.md` §9 |
