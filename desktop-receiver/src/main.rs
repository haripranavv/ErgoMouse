mod hid;
mod net;
mod platform;

use crate::net::packet::{self, Packet};
use std::sync::Arc;
use std::time::Duration;
use tokio::net::UdpSocket;

const LISTEN_PORT: u16 = 51234;
const BLUETOOTH_PORT: &str = "COM10"; // Fixed the string type here!

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let addr = format!("0.0.0.0:{}", LISTEN_PORT);
    let socket = UdpSocket::bind(&addr).await?;

    // Core OS hardware injector shared across threads safely
    let injector = Arc::new(hid::injector::platform_injector());
    let bt_injector = Arc::clone(&injector);

    println!("🖱️ Ergomouse Dual-Mode Receiver Activated");
    println!("📡 Wi-Fi: Listening for UDP on port {}", LISTEN_PORT);

    // --- BLUETOOTH SERIAL THREAD ---
    std::thread::spawn(move || {
        loop {
            let port_builder =
                serialport::new(BLUETOOTH_PORT, 115_200).timeout(Duration::from_millis(100));

            match port_builder.open() {
                Ok(mut port) => {
                    println!(
                        "🚀 Bluetooth: Successfully connected to {}! Waiting for phone...",
                        BLUETOOTH_PORT
                    );
                    let mut serial_buf = [0; 1024];

                    loop {
                        match port.read(&mut serial_buf) {
                            Ok(bytes_read) if bytes_read > 0 => {
                                // Process the packet using your unified decoder logic
                                if let Ok((_, pkt)) = packet::decode(&serial_buf[..bytes_read]) {
                                    handle_packet(pkt, &bt_injector);
                                }
                            }
                            Ok(_) => {}
                            Err(e) if e.kind() == std::io::ErrorKind::TimedOut => {}
                            Err(_) => {
                                println!("⚠️ Bluetooth disconnected. Resetting port listener...");
                                break;
                            }
                        }
                    }
                }
                Err(_) => {
                    // Port might be busy, retry in 2 seconds
                    std::thread::sleep(Duration::from_secs(2));
                }
            }
        }
    });

    // --- WI-FI UDP EVENT LOOP ---
    let mut buf = [0; 1024];
    loop {
        let (len, _src_addr) = socket.recv_from(&mut buf).await?;
        match packet::decode(&buf[..len]) {
            Ok((_, pkt)) => handle_packet(pkt, &injector),
            Err(e) => show_error(e),
        }
    }
}

// Unified routing logic regardless of network carrier framework
fn handle_packet(pkt: Packet, injector: &Box<dyn hid::injector::MouseInjector>) {
    match pkt {
        Packet::Move {
            dx_q8_8, dy_q8_8, ..
        } => {
            let dx = packet::q8_8_to_f32(dx_q8_8).round() as i32;
            let dy = packet::q8_8_to_f32(dy_q8_8).round() as i32;
            injector.move_relative(dx, dy);
        }
        Packet::Click { button, down } => {
            let btn = match button {
                0 => hid::injector::Button::Left,
                1 => hid::injector::Button::Right,
                2 => hid::injector::Button::Middle,
                _ => return,
            };
            injector.click(btn, down);
        }
        Packet::Scroll { dx, dy } => {
            println!("✅ SCROLL PACKET RECEIVED: dx={}, dy={}", dx, dy);
            injector.scroll(dx as i32, dy as i32);
        }
        _ => {}
    }
}

fn show_error(e: packet::DecodeError) {
    println!("❌ Packet Error: {:?}", e);
}
