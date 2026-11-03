"""
Deli Q1A Label Printer - Protocol Definition (得力 Q1A 打印机底层协议规范)
Core BLE service UUIDs, control command frames, and raster packet builders.
"""

# BLE GATT Service and Characteristic UUIDs
SERVICE_UUID = "0000ff00-0000-1000-8000-00805f9b34fb"
WRITE_CHAR   = "0000ff02-0000-1000-8000-00805f9b34fb"  # Write without response (Data & commands)
NOTIFY_CHAR  = "0000ff01-0000-1000-8000-00805f9b34fb"  # Notification (Status telemetry)
EVENT_CHAR   = "0000ff03-0000-1000-8000-00805f9b34fb"  # Notification (Printer events)

# Physical Device & Paper Parameters
DEFAULT_MAC = "5E:55:09:F0:A2:1E"
DPI = 203                         # 8 dots per mm (203 / 25.4 ≈ 7.992 dots/mm)
WIDTH_PX = 120                    # 15mm paper width @ 8 dots/mm = 120 dots (15 bytes per line)
HEIGHT_PX = 240                   # 30mm label length @ 8 dots/mm = 240 dots
WIDTH_BYTES = WIDTH_PX // 8       # 15 bytes (120 // 8)
MAX_WIDTH_PX = 120                # Physical thermal head maximum printable width (15mm)

# Control Commands (LuckPrinter / Lujiang protocol family)
CMD_ENABLE      = bytes([0x10, 0xFF, 0xF1, 0x03])  # Enable printer / open session
CMD_WAKEUP      = b'\x00' * 12                     # 12 null bytes to wake print engine MCU
CMD_ALIGN_TOP   = bytes([0x1F, 0x11, 0x51])        # Label Mode: Retract paper to align top of label
CMD_FEED_GAP    = bytes([0x1D, 0x0C])              # Label Mode: Feed paper to tear blade at label gap
CMD_EXIT_LABEL  = bytes([0x1F, 0x11, 0x50])        # Exit label mode
CMD_FINISH_JOB  = bytes([0x10, 0xFF, 0xF1, 0x45])  # Terminate print job & power down motor

def cmd_density(level: int = 1) -> bytes:
    """
    Sets printhead burn darkness/density.
    0: Light (浅度)
    1: Normal (标准 - 推荐，功耗与电机最平稳)
    2: Dark (深度 - 耗电大，易造成电机瞬时电压抖动)
    """
    val = max(0, min(2, level))
    return bytes([0x10, 0xFF, 0x10, 0x00, val])

def build_raster_header(width_bytes: int, height_px: int) -> bytes:
    """
    Constructs the ESC/POS 'GS v 0' raster image header.
    Format: 1D 76 30 00 [xL] [xH] [yL] [yH]
    where width = (xL + xH*256) bytes, height = (yL + yH*256) dots.
    """
    return bytes([
        0x1D, 0x76, 0x30, 0x00,
        width_bytes & 0xFF, (width_bytes >> 8) & 0xFF,
        height_px & 0xFF, (height_px >> 8) & 0xFF,
    ])

def build_continuous_print_stream(bitmap_bytes: bytes, width_bytes: int, height_px: int) -> bytes:
    """
    Packs raster graphics and the gap feed command into ONE continuous byte pipeline.
    By queuing CMD_FEED_GAP (1D 0C) immediately behind the raster data in the printer's
    internal FIFO buffer, the stepper motor transitions seamlessly from burning dots
    to feeding to the tear notch without decelerating or pausing mid-print.
    """
    raster_header = build_raster_header(width_bytes, height_px)
    return raster_header + bitmap_bytes + CMD_FEED_GAP
