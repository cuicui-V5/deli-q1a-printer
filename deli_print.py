#!/usr/bin/env python3
"""
Deli Q1A Label Printer BLE Tool (得力 Q1A 标签打印机免芯片工具)
Bypasses proprietary NFC/RFID consumable checks & counter locks.
Communicates directly with the printer over Bluetooth Low Energy (BLE).

Paper specification: 15mm x 30mm 间隙纸 (Gap label paper)
Printhead: 203 DPI (8 dots/mm)
Printable area: 96 dots wide (12mm) x 216 dots long (27mm)
"""

import argparse
import asyncio
import os
import sys
from PIL import Image, ImageDraw, ImageFont, ImageOps
from bleak import BleakClient, BleakScanner

DEFAULT_MAC = "5E:55:09:F0:A2:1E"

SERVICE_UUID = "0000ff00-0000-1000-8000-00805f9b34fb"
WRITE_CHAR   = "0000ff02-0000-1000-8000-00805f9b34fb"
NOTIFY_CHAR  = "0000ff01-0000-1000-8000-00805f9b34fb"
EVENT_CHAR   = "0000ff03-0000-1000-8000-00805f9b34fb"

# Paper geometry (dots at 203 DPI)
WIDTH_PX = 96        # 12mm width (12 bytes/line)
HEIGHT_PX = 216      # 27mm length (leaves 1.5mm margin for 30mm label)
WIDTH_BYTES = WIDTH_PX // 8

def find_font(size):
    candidates = [
        r"C:\Windows\Fonts\msyh.ttc",     # Microsoft YaHei
        r"C:\Windows\Fonts\msyhbd.ttc",   # YaHei Bold
        r"C:\Windows\Fonts\simsun.ttc",   # SimSun
        r"C:\Windows\Fonts\simhei.ttf",   # SimHei
        r"C:\Windows\Fonts\arial.ttf",    # Arial
    ]
    for path in candidates:
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except Exception:
                continue
    return ImageFont.load_default()

def render_text_label(text, orientation='landscape', border=True, font_size=None):
    """
    Renders text onto a monochrome canvas.
    Landscape: 216x96 canvas rotated 90 degrees to 96x216 (prints along the 30mm length).
    Portrait: 96x216 canvas.
    """
    if orientation == 'landscape':
        cw, ch = HEIGHT_PX, WIDTH_PX  # 216 x 96
    else:
        cw, ch = WIDTH_PX, HEIGHT_PX  # 96 x 216

    img = Image.new('1', (cw, ch), color=1) # 1 = white
    draw = ImageDraw.Draw(img)

    if border:
        draw.rectangle([1, 1, cw - 2, ch - 2], outline=0, width=1)

    lines = [line.strip() for line in text.split('\\n') if line.strip()]
    if not lines:
        lines = [text]

    if font_size is None:
        if orientation == 'landscape':
            font_size = 20 if len(lines) <= 2 else 15
        else:
            font_size = 18 if len(lines) <= 3 else 13

    font = find_font(font_size)

    total_text_h = 0
    line_metrics = []
    line_spacing = 4
    for line in lines:
        bbox = draw.textbbox((0, 0), line, font=font)
        w = bbox[2] - bbox[0]
        h = bbox[3] - bbox[1]
        line_metrics.append((w, h))
        total_text_h += h

    total_text_h += line_spacing * (len(lines) - 1)
    start_y = max(4, (ch - total_text_h) // 2)

    cur_y = start_y
    for i, line in enumerate(lines):
        w, h = line_metrics[i]
        cur_x = max(4, (cw - w) // 2)
        draw.text((cur_x, cur_y), line, fill=0, font=font)
        cur_y += h + line_spacing

    if orientation == 'landscape':
        img = img.rotate(90, expand=True)

    return img

def render_image_label(image_path, orientation='landscape', dither=True, invert=False):
    """
    Converts user image to 96x216 1-bit monochrome.
    """
    if not os.path.exists(image_path):
        raise FileNotFoundError(f"Image not found: {image_path}")

    raw = Image.open(image_path).convert('L')
    if invert:
        raw = ImageOps.invert(raw)

    if orientation == 'landscape':
        cw, ch = HEIGHT_PX, WIDTH_PX
    else:
        cw, ch = WIDTH_PX, HEIGHT_PX

    # Scale preserving aspect ratio
    raw.thumbnail((cw, ch), Image.Resampling.LANCZOS)
    canvas = Image.new('L', (cw, ch), color=255)
    ox = (cw - raw.width) // 2
    oy = (ch - raw.height) // 2
    canvas.paste(raw, (ox, oy))

    if orientation == 'landscape':
        canvas = canvas.rotate(90, expand=True)

    if dither:
        bw = canvas.convert('1', dither=Image.Dither.FLOYDSTEINBERG)
    else:
        bw = canvas.point(lambda p: 0 if p < 128 else 1, mode='1')

    return bw

def bitmap_to_raw_bytes(img):
    w, h = img.size
    bytes_per_row = (w + 7) // 8
    buf = bytearray(bytes_per_row * h)

    for y in range(h):
        for xb in range(bytes_per_row):
            b = 0
            for bit in range(8):
                px_x = xb * 8 + bit
                if px_x < w and img.getpixel((px_x, y)) == 0:
                    b |= (1 << (7 - bit))
            buf[y * bytes_per_row + xb] = b

    return bytes(buf)

async def write_chunks(client, data, chunk_size=200, delay=0.015):
    for off in range(0, len(data), chunk_size):
        chunk = data[off : off + chunk_size]
        await client.write_gatt_char(WRITE_CHAR, chunk, response=False)
        await asyncio.sleep(delay)

async def print_job(mac, img, density=1, copies=1):
    bitmap = bitmap_to_raw_bytes(img)
    w_px, h_px = img.size
    w_bytes = (w_px + 7) // 8

    # ESC/POS GS v 0 raster header
    raster_header = bytes([
        0x1D, 0x76, 0x30, 0x00,
        w_bytes & 0xFF, (w_bytes >> 8) & 0xFF,
        h_px & 0xFF, (h_px >> 8) & 0xFF,
    ])
    full_payload = raster_header + bitmap

    print(f"Connecting to Deli Q1A ({mac})...")
    async with BleakClient(mac, timeout=8.0) as client:
        if not client.is_connected:
            raise ConnectionError(f"Failed to connect to {mac}")
        print("Connected successfully!")

        def on_notify(sender, data):
            pass

        await client.start_notify(NOTIFY_CHAR, on_notify)
        await client.start_notify(EVENT_CHAR, on_notify)
        await asyncio.sleep(0.3)

        # Set density: 0=Light, 1=Normal, 2=Dark
        density_val = max(0, min(2, density))
        print(f"Configuring print density: {density_val} (0=Light, 1=Normal, 2=Dark)...")
        await client.write_gatt_char(WRITE_CHAR, bytes([0x10, 0xFF, 0x10, 0x00, density_val]), response=False)
        await asyncio.sleep(0.2)

        for copy in range(copies):
            if copies > 1:
                print(f"\n--- Printing Copy {copy + 1}/{copies} ---")

            # 1. Enable printer
            await client.write_gatt_char(WRITE_CHAR, bytes([0x10, 0xFF, 0xF1, 0x03]), response=False)
            await asyncio.sleep(0.3)

            # 2. Wakeup null bytes
            await client.write_gatt_char(WRITE_CHAR, b'\x00' * 12, response=False)
            await asyncio.sleep(0.2)

            # 3. Label mode gap detection / alignment
            if copy == 0:
                print("Aligning paper to label gap (1F 11 51)...")
                await client.write_gatt_char(WRITE_CHAR, bytes([0x1F, 0x11, 0x51]), response=False)
                await asyncio.sleep(0.5)

            # 4. Stream raster bitmap
            print(f"Streaming bitmap ({w_px}x{h_px} dots, {len(full_payload)} bytes)...")
            await write_chunks(client, full_payload, chunk_size=200, delay=0.015)

            # Physical burn time delay (allowing dots to be heated without feed interruption)
            burn_delay = max(1.5, (h_px / 120.0))
            await asyncio.sleep(burn_delay)

            # 5. Position to next gap
            print("Advancing to next label gap (1D 0C)...")
            await client.write_gatt_char(WRITE_CHAR, bytes([0x1D, 0x0C]), response=False)
            await asyncio.sleep(0.8)

            # 6. Exit label mode on last copy
            if copy == copies - 1:
                await client.write_gatt_char(WRITE_CHAR, bytes([0x1F, 0x11, 0x50]), response=False)
                await asyncio.sleep(0.3)

            # 7. Finish job
            await client.write_gatt_char(WRITE_CHAR, bytes([0x10, 0xFF, 0xF1, 0x45]), response=False)
            await asyncio.sleep(1.2)

        print("\n>>> SUCCESS: All labels printed successfully! <<<")

def main():
    parser = argparse.ArgumentParser(description="Deli Q1A Label Printer Tool (得力标签机免芯片打印)")
    parser.add_argument("--text", type=str, help="Text to print (use \\n for newline)")
    parser.add_argument("--image", type=str, help="Path to image file to print")
    parser.add_argument("--mac", type=str, default=DEFAULT_MAC, help=f"Printer MAC (default: {DEFAULT_MAC})")
    parser.add_argument("--orientation", choices=['landscape', 'portrait'], default='landscape',
                        help="Orientation: landscape (along 30mm) or portrait (along 15mm)")
    parser.add_argument("--density", type=int, choices=[0, 1, 2], default=1, help="Density: 0=Light, 1=Normal, 2=Dark (default 1)")
    parser.add_argument("--no-border", action="store_true", help="Disable outer border")
    parser.add_argument("--no-dither", action="store_true", help="Disable dithering for images")
    parser.add_argument("--invert", action="store_true", help="Invert image colors")
    parser.add_argument("--copies", type=int, default=1, help="Number of copies (default 1)")
    parser.add_argument("--preview", type=str, help="Save preview PNG instead of printing")
    parser.add_argument("--scan", action="store_true", help="Scan for nearby BLE printers")

    args = parser.parse_args()

    if args.scan:
        async def do_scan():
            print("Scanning for BLE printers...")
            devices = await BleakScanner.discover(timeout=5.0)
            for d in devices:
                if d.name and ('Q1' in d.name.upper() or 'DELI' in d.name.upper()):
                    print(f"  * Matched: {d.name} [{d.address}]")
                elif d.name:
                    print(f"    {d.name:25s} [{d.address}]")
        asyncio.run(do_scan())
        return

    if args.image:
        img = render_image_label(args.image, orientation=args.orientation,
                                 dither=not args.no_dither, invert=args.invert)
    elif args.text:
        img = render_text_label(args.text, orientation=args.orientation, border=not args.no_border)
    else:
        # Default label
        img = render_text_label("得力 Q1A 标签机\\n免芯片打印成功!\\n15x30mm 间隙纸",
                                orientation=args.orientation, border=not args.no_border)

    if args.preview:
        img.save(args.preview)
        print(f"Preview saved to {args.preview} ({img.width}x{img.height})")
        return

    asyncio.run(print_job(mac=args.mac, img=img, density=args.density, copies=args.copies))

if __name__ == '__main__':
    main()
