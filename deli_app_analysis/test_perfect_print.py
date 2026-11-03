import asyncio
import os
import sys
from PIL import Image, ImageDraw, ImageFont
from bleak import BleakClient

MAC = "5E:55:09:F0:A2:1E"
FF01 = "0000ff01-0000-1000-8000-00805f9b34fb"
FF02 = "0000ff02-0000-1000-8000-00805f9b34fb"
FF03 = "0000ff03-0000-1000-8000-00805f9b34fb"

# Paper: 15mm x 30mm
# Printable area: 96 dots wide x 216 dots high (~12mm x 27mm)
W_PX = 96
H_PX = 216
W_BYTES = W_PX // 8

def find_font(size):
    candidates = [
        r"C:\Windows\Fonts\msyh.ttc",     # Microsoft YaHei
        r"C:\Windows\Fonts\simsun.ttc",   # SimSun
        r"C:\Windows\Fonts\simhei.ttf",   # SimHei
        r"C:\Windows\Fonts\arial.ttf",
    ]
    for p in candidates:
        if os.path.exists(p):
            try:
                return ImageFont.truetype(p, size)
            except Exception:
                continue
    return ImageFont.load_default()

def render_sample_label():
    # Landscape orientation: 216 wide x 96 high, then rotate 90 degrees
    # This prints text horizontally along the 30mm length!
    canvas_w = H_PX # 216
    canvas_h = W_PX # 96

    img = Image.new('1', (canvas_w, canvas_h), color=1)
    draw = ImageDraw.Draw(img)

    # Clean outer border
    draw.rectangle([1, 1, canvas_w - 2, canvas_h - 2], outline=0, width=1)

    font_title = find_font(18)
    font_sub = find_font(14)
    font_info = find_font(13)

    # Line 1: 得力 Q1A 标签机
    draw.text((canvas_w // 2, 8), "得力 Q1A 标签机", fill=0, font=font_title, anchor="mt")
    # Horizontal divider line
    draw.line([(8, 32), (canvas_w - 8, 32)], fill=0, width=1)
    # Line 2: RFID 芯片破解成功!
    draw.text((canvas_w // 2, 40), "免芯片打印成功!", fill=0, font=font_sub, anchor="mt")
    # Line 3: 15x30mm 间隙纸
    draw.text((canvas_w // 2, 66), "15x30mm 间隙纸", fill=0, font=font_info, anchor="mt")

    # Rotate 90 degrees clockwise to match printhead feed direction (96x216)
    img = img.rotate(90, expand=True)
    return img

def bitmap_from_img(img):
    w, h = img.size
    bytes_per_row = (w + 7) // 8
    buf = bytearray(bytes_per_row * h)
    for y in range(h):
        for xb in range(bytes_per_row):
            b = 0
            for bit in range(8):
                px = xb * 8 + bit
                if px < w and img.getpixel((px, y)) == 0:
                    b |= (1 << (7 - bit))
            buf[y * bytes_per_row + xb] = b
    return bytes(buf)

async def write_chunked(client, data, chunk_size=200, delay=0.015):
    for off in range(0, len(data), chunk_size):
        chunk = data[off : off + chunk_size]
        await client.write_gatt_char(FF02, chunk, response=False)
        await asyncio.sleep(delay)

async def main():
    img = render_sample_label()
    img.save(r"d:\myProject\deli\deli_app_analysis\sample_preview.png")
    print(f"Sample label image generated ({img.width}x{img.height})")

    bitmap = bitmap_from_img(img)
    w_bytes = (img.width + 7) // 8
    h_px = img.height

    raster_header = bytes([
        0x1D, 0x76, 0x30, 0x00,
        w_bytes & 0xFF, (w_bytes >> 8) & 0xFF,
        h_px & 0xFF, (h_px >> 8) & 0xFF,
    ])
    full_payload = raster_header + bitmap

    print(f"Connecting to Deli Q1A ({MAC})...")
    async with BleakClient(MAC, timeout=8.0) as client:
        print("Connected successfully!")
        
        def on_notify(sender, data):
            print(f"  [NOTIFY] {data.hex(' ')} | {bytes(data)}")
            
        await client.start_notify(FF01, on_notify)
        await client.start_notify(FF03, on_notify)
        await asyncio.sleep(0.3)

        # 1. Enable printer
        print("[1/7] Enabling printer: 10 FF F1 03...")
        await client.write_gatt_char(FF02, bytes([0x10, 0xFF, 0xF1, 0x03]), response=False)
        await asyncio.sleep(0.3)

        # 2. Wakeup 12 zeros
        print("[2/7] Waking up print engine...")
        await client.write_gatt_char(FF02, b'\x00' * 12, response=False)
        await asyncio.sleep(0.2)

        # 3. Set Density = 1 (Normal density, smoother current, no motor stall)
        print("[3/7] Setting density to Normal (1)...")
        await client.write_gatt_char(FF02, bytes([0x10, 0xFF, 0x10, 0x00, 0x01]), response=False)
        await asyncio.sleep(0.2)

        # 4. Label mode alignment
        print("[4/7] Label Mode Gap Alignment: 1F 11 51...")
        await client.write_gatt_char(FF02, bytes([0x1F, 0x11, 0x51]), response=False)
        await asyncio.sleep(0.5)

        # 5. Stream raster bitmap
        print(f"[5/7] Streaming bitmap ({len(full_payload)} bytes in 200B chunks)...")
        await write_chunked(client, full_payload, chunk_size=200, delay=0.015)
        
        # Give the thermal head time to physically print without interruption!
        # 216 dots / ~150 dots per sec ≈ 1.5 seconds
        print("      Waiting 2.0s for physical thermal printing to complete smoothly...")
        await asyncio.sleep(2.0)

        # 6. Position to next gap
        print("[6/7] Positioning to next label gap: 1D 0C...")
        await client.write_gatt_char(FF02, bytes([0x1D, 0x0C]), response=False)
        await asyncio.sleep(0.8)

        # Exit label mode
        print("      Exiting label mode: 1F 11 50...")
        await client.write_gatt_char(FF02, bytes([0x1F, 0x11, 0x50]), response=False)
        await asyncio.sleep(0.3)

        # 7. Finish job
        print("[7/7] Completing print job: 10 FF F1 45...")
        await client.write_gatt_char(FF02, bytes([0x10, 0xFF, 0xF1, 0x45]), response=False)
        await asyncio.sleep(1.5)

        print("\n>>> Print sequence completed! Check your printer! <<<")

if __name__ == '__main__':
    asyncio.run(main())
