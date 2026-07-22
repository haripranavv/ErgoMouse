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
    fn move_relative(&self, dx: i32, dy: i32);
    fn scroll(&self, dx: i32, dy: i32);
    fn click(&self, button: Button, down: bool);
    fn set_modifiers(&self, mods: ModifierMask);
}

// THIS is the function the compiler was looking for!
pub fn platform_injector() -> Box<dyn MouseInjector> {
    #[cfg(target_os = "windows")]
    {
        Box::new(crate::platform::windows::WindowsInjector::new())
    }
    #[cfg(not(target_os = "windows"))]
    {
        unimplemented!("Only Windows is supported in this build!");
    }
}
