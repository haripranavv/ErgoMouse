//! Fixed-size binary packet decode. No serde/JSON on this path — see
//! docs/ARCHITECTURE.md §5 for the byte layout rationale.

use thiserror::Error;

#[derive(Debug, Error)]
pub enum DecodeError {
    #[error("packet too short: {0} bytes")]
    TooShort(usize),
    #[error("unknown packet type: {0}")]
    UnknownType(u8),
}

#[derive(Debug, Clone, Copy)]
pub enum Packet {
    Move { dx_q8_8: i16, dy_q8_8: i16, timestamp_ms: u16, precision: bool, drag: bool },
    Click { button: u8, down: bool },
    Scroll { dx: i16, dy: i16 },
    Modifier { ctrl: bool, shift: bool, alt: bool },
    Heartbeat { seq: u16 },
    Hello,
    Battery { percent: u8, charging: bool },
}

const HEADER_LEN: usize = 6;

pub fn decode(buf: &[u8]) -> Result<(u16 /* seq */, Packet), DecodeError> {
    if buf.len() < HEADER_LEN {
        return Err(DecodeError::TooShort(buf.len()));
    }
    let _version = buf[0];
    let ptype = buf[1];
    let seq = u16::from_le_bytes([buf[2], buf[3]]);
    let payload = &buf[HEADER_LEN..];

    let packet = match ptype {
        0x02 => Packet::Heartbeat { seq },
        0x10 => {
            if payload.len() < 6 {
                return Err(DecodeError::TooShort(payload.len()));
            }
            Packet::Move {
                dx_q8_8: i16::from_le_bytes([payload[0], payload[1]]),
                dy_q8_8: i16::from_le_bytes([payload[2], payload[3]]),
                timestamp_ms: u16::from_le_bytes([payload[4], payload[5]]),
                precision: payload.get(6).map_or(false, |f| f & 0x01 != 0),
                drag: payload.get(6).map_or(false, |f| f & 0x02 != 0),
            }
        }
        0x11 => Packet::Click {
            button: *payload.first().unwrap_or(&0),
            down: payload.get(1).map_or(false, |v| *v != 0),
        },
        0x12 => Packet::Scroll {
            dx: i16::from_le_bytes([*payload.first().unwrap_or(&0), *payload.get(1).unwrap_or(&0)]),
            dy: i16::from_le_bytes([*payload.get(2).unwrap_or(&0), *payload.get(3).unwrap_or(&0)]),
        },
        0x13 => {
            let flags = *payload.first().unwrap_or(&0);
            Packet::Modifier { ctrl: flags & 1 != 0, shift: flags & 2 != 0, alt: flags & 4 != 0 }
        }
        0x01 => Packet::Hello,
        0x20 => Packet::Battery {
            percent: *payload.first().unwrap_or(&0),
            charging: payload.get(1).map_or(false, |v| *v != 0),
        },
        other => return Err(DecodeError::UnknownType(other)),
    };

    Ok((seq, packet))
}

/// Converts the wire's Q8.8 fixed-point delta into a float pixel delta.
pub fn q8_8_to_f32(v: i16) -> f32 {
    v as f32 / 256.0
}
