//! Platform-agnostic HID injection contract.
//! Each OS backend (platform/windows, platform/macos, platform/linux)
//! implements this against the lowest-level native API available so we
//! never pay the cost of an intermediate abstraction layer (e.g. a webview
//! or scripting bridge) on the hot path.

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Button {
    Left,
    Right,
    Middle,
}

#[derive(Clone, Copy, Debug, Default)]
pub struct ModifierMask {
    pub ctrl: bool,
    pub shift: bool,
    pub alt: bool,
}

pub trait MouseInjector: Send + Sync {
    /// Relative cursor move, sub-pixel deltas already resolved to integer
    /// device pixels by the input fuser before this call.
    fn move_relative(&self, dx: i32, dy: i32);

    /// Scroll wheel delta (vertical dy, horizontal dx).
    fn scroll(&self, dx: i32, dy: i32);

    /// Button down/up edge event. Called immediately, never batched.
    fn click(&self, button: Button, down: bool);

    /// Applies to subsequent click/scroll calls until cleared.
    fn set_modifiers(&self, mods: ModifierMask);
}

/// Returns the platform-appropriate injector for the current OS.
pub fn platform_injector() -> Box<dyn MouseInjector> {
    #[cfg(target_os = "windows")]
    {
        Box::new(crate::platform::windows::WindowsInjector::new())
    }
    #[cfg(target_os = "macos")]
    {
        Box::new(crate::platform::macos::MacInjector::new())
    }
    #[cfg(target_os = "linux")]
    {
        Box::new(crate::platform::linux::LinuxInjector::new())
    }
}
