import asyncio
import sys
from PIL import Image, ImageDraw, ImageFont
from bleak import BleakClient

MAC = "5E:55:09:F0:A2:1E"
FF01 = "0000ff01-0000-1000-8000-00805f9b34fb"
FF02 = "0000ff02-0000-1000-8000-00805f9b34fb"
FF03 = "0000ff03-0000-1000-8000-00805f9b34fb"

# Paper: 15mm x 30mm (96 x 240 dots)
W_PX = 96
H_PX = 240
W_BYTES = W_PX // 8

def create_test_bitmap():
    img = Image.new('1', (W_PX, H_PX), color=1) # 1 = white
    draw = ImageDraw.Draw(img)
    # Draw thick border
    draw.rectangle([2, 2, W_PX - 3, H_PX - 3], outline=0, width=2)
    # Draw some horizontal bars
    for y in range(20, 40, 4):
        draw.line([(10, y), (W_PX - 10, y)], fill=0, width=2)
    # Draw an X
    draw.line([(10, 60), (W_PX - 10, 140)], fill=0, width=2)
    draw.line([(W_PX - 10, 60), (10, 140)], fill=0, width=2)
    # Draw bottom bars
    for y in range(180, 220, 6):
        draw.line([(10, y), (W_PX - 10, y)], fill=0, width=3)
        
    # Convert to MSB-first bytes (0=black=1, 1=white=0)
    buf = bytearray(W_BYTES * H_PX)
    for y in range(H_PX):
        for xb in range(W_BYTES):
            b = 0
            for bit in range(8):
                px = xb * 8 + bit
                if px < W_PX and img.getpixel((px, y)) == 0:
                    b |= (1 << (7 - bit))
            buf[y * W_BYTES + xb] = b
    return bytes(buf)

async def write_chunks(client, data, chunk_size=20, delay=0.01):
    for off in range(0, len(data), chunk_size):
        await client.write_gatt_char(FF02, data[off:off+chunk_size], response=False)
        await asyncio.sleep(delay)

async def main():
    bitmap = create_test_bitmap()
    print(f"Connecting to {MAC}...")
    async with BleakClient(MAC, timeout=8.0) as client:
        print("Connected!")
        
        def on_notify(sender, data):
            print(f"  [NOTIFY] {data.hex(' ')} | {bytes(data)}")
            
        await client.start_notify(FF01, on_notify)
        await client.start_notify(FF03, on_notify)
        await asyncio.sleep(0.3)

        print("[1/7] Enabling printer: 10 FF F1 03...")
        await client.write_gatt_char(FF02, bytes([0x10, 0xFF, 0xF1, 0x03]), response=False)
        await asyncio.sleep(0.1)

        print("[2/7] Wakeup: 12 zeros...")
        await client.write_gatt_char(FF02, b'\x00' * 12, response=False)
        await asyncio.sleep(0.1)

        print("[3/7] Setting density: 10 FF 10 00 02...")
        await client.write_gatt_char(FF02, bytes([0x10, 0xFF, 0x10, 0x00, 0x02]), response=False)
        await asyncio.sleep(0.1)

        print("[4/7] Label Mode Init: 1F 11 51...")
        await client.write_gatt_char(FF02, bytes([0x1F, 0x11, 0x51]), response=False)
        await asyncio.sleep(0.1)

        # 5. ESC/POS GS v 0 raster command
        # 0x1D 0x76 0x30 0x00 xL xH yL yH
        # xL = 12, xH = 0 (12 bytes per line)
        # yL = 240, yH = 0 (240 lines)
        raster_header = bytes([
            0x1D, 0x76, 0x30, 0x00,
            W_BYTES & 0xFF, (W_BYTES >> 8) & 0xFF,
            H_PX & 0xFF, (H_PX >> 8) & 0xFF,
        ])
        print(f"[5/7] Streaming GS v 0 raster data ({len(raster_header) + len(bitmap)} bytes)...")
        await write_chunks(client, raster_header + bitmap, chunk_size=20, delay=0.01)
        await asyncio.sleep(0.5)

        print("[6/7] Label Mode Exit: 1F 11 50 & Gap Feed: 1D 0C...")
        await client.write_gatt_char(FF02, bytes([0x1F, 0x11, 0x50]), response=False)
        await asyncio.sleep(0.1)
        await client.write_gatt_char(FF02, bytes([0x1D, 0x0C]), response=False)
        await asyncio.sleep(0.5)

        print("[7/7] Finishing job: 10 FF F1 45...")
        await client.write_gatt_char(FF02, bytes([0x10, 0xFF, 0xF1, 0x45]), response=False)
        await asyncio.sleep(2.0)
        print("Print sequence finished!")

if __name__ == '__main__':
    asyncio.run(main())
