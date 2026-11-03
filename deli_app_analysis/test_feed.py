import asyncio
from bleak import BleakClient

MAC = "5E:55:09:F0:A2:1E"
FF01 = "0000ff01-0000-1000-8000-00805f9b34fb"
FF02 = "0000ff02-0000-1000-8000-00805f9b34fb"
FF03 = "0000ff03-0000-1000-8000-00805f9b34fb"

def deli_encode(payload: bytes) -> bytes:
    sub = bytearray()
    for b in payload:
        if b == 0x01: sub.append(0xF1)
        elif b == 0xF1: sub.append(0x01)
        elif b == 0x02: sub.append(0xF2)
        elif b == 0xF2: sub.append(0x02)
        elif b == 0x10: sub.append(0xFF)
        elif b == 0xFF: sub.append(0x10)
        else: sub.append(b)
    return bytes([0xFF, 0xF1, 0xF2, 0xF3, 0x03, 0x02, 0x01, 0x01]) + bytes(sub)

async def main():
    print(f"Connecting to {MAC}...")
    async with BleakClient(MAC, timeout=8.0) as client:
        print("Connected!")
        
        def on_notify(sender, data):
            print(f"  [NOTIFY] {data.hex(' ')} | {bytes(data)}")
            
        await client.start_notify(FF01, on_notify)
        await client.start_notify(FF03, on_notify)
        await asyncio.sleep(0.3)

        print("\n=== Test 1: LuckPrinter Sequence (Unframed) ===")
        # 1. Enable printer
        await client.write_gatt_char(FF02, bytes([0x10, 0xFF, 0xF1, 0x03]), response=False)
        await asyncio.sleep(0.1)
        # 2. Wakeup 12 zeros
        await client.write_gatt_char(FF02, b'\x00' * 12, response=False)
        await asyncio.sleep(0.1)
        # 3. Density
        await client.write_gatt_char(FF02, bytes([0x10, 0xFF, 0x10, 0x00, 0x02]), response=False)
        await asyncio.sleep(0.1)
        # 4. Feed 80 dots (ESC J 80): 1B 4A 50
        print(">> Sending ESC J 80 (feed 80 dots): 1B 4A 50...")
        await client.write_gatt_char(FF02, bytes([0x1B, 0x4A, 0x50]), response=False)
        await asyncio.sleep(1.0)
        # 5. Gap feed
        print(">> Sending Gap Feed: 1D 0C...")
        await client.write_gatt_char(FF02, bytes([0x1D, 0x0C]), response=False)
        await asyncio.sleep(1.0)
        # 6. Stop job
        await client.write_gatt_char(FF02, bytes([0x10, 0xFF, 0xF1, 0x45]), response=False)
        await asyncio.sleep(1.0)

        print("\n=== Test 2: LuckPrinter Sequence (Deli Framed) ===")
        # 1. Enable printer (Framed)
        await client.write_gatt_char(FF02, deli_encode(bytes([0x10, 0xFF, 0xF1, 0x03])), response=False)
        await asyncio.sleep(0.1)
        # 2. Density (Framed)
        await client.write_gatt_char(FF02, deli_encode(bytes([0x10, 0xFF, 0x10, 0x00, 0x02])), response=False)
        await asyncio.sleep(0.1)
        # 3. Feed 80 dots (Framed): 1B 4A 50
        print(">> Sending FRAMED ESC J 80 (feed 80 dots)...")
        await client.write_gatt_char(FF02, deli_encode(bytes([0x1B, 0x4A, 0x50])), response=False)
        await asyncio.sleep(1.0)
        # 4. Gap feed (Framed)
        print(">> Sending FRAMED Gap Feed: 1D 0C...")
        await client.write_gatt_char(FF02, deli_encode(bytes([0x1D, 0x0C])), response=False)
        await asyncio.sleep(1.0)
        # 5. Stop job (Framed)
        await client.write_gatt_char(FF02, deli_encode(bytes([0x10, 0xFF, 0xF1, 0x45])), response=False)
        await asyncio.sleep(1.0)

        print("Test complete.")

if __name__ == '__main__':
    asyncio.run(main())
