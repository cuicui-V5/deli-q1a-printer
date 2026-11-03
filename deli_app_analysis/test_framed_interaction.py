import asyncio
import sys
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

def deli_decode(packet: bytes) -> bytes:
    data = packet
    has_header = False
    if data.startswith(b'\xFF\xF1\xF2\xF3\x03\x02\x01\x00') or data.startswith(b'\xFF\xF1\xF2\xF3\x03\x02\x01\x01'):
        data = data[8:]
        has_header = True
    sub = bytearray()
    for b in data:
        if b == 0x01: sub.append(0xF1)
        elif b == 0xF1: sub.append(0x01)
        elif b == 0x02: sub.append(0xF2)
        elif b == 0xF2: sub.append(0x02)
        elif b == 0x10: sub.append(0xFF)
        elif b == 0xFF: sub.append(0x10)
        else: sub.append(b)
    prefix = "[DELI-FRAME] " if has_header else "[RAW-PAYLOAD] "
    return prefix + bytes(sub).hex(' ') + " | " + repr(bytes(sub))

async def main():
    print(f"Connecting to Deli Q1A [{MAC}]...")
    async with BleakClient(MAC, timeout=10.0) as client:
        print("Connected!")
        
        def on_ff01(sender, data):
            print(f"  <- [FF01] {data.hex(' ')} => {deli_decode(data)}")
            
        def on_ff03(sender, data):
            print(f"  <- [FF03] {data.hex(' ')} => {deli_decode(data)}")
            
        await client.start_notify(FF01, on_ff01)
        await client.start_notify(FF03, on_ff03)
        await asyncio.sleep(0.5)

        async def send_cmd(name, raw_bytes, framed=True):
            if framed:
                packet = deli_encode(raw_bytes)
                print(f"\n>> Sending {name} (FRAMED: {packet.hex(' ')})")
            else:
                packet = raw_bytes
                print(f"\n>> Sending {name} (UNFRAMED: {packet.hex(' ')})")
            await client.write_gatt_char(FF02, packet, response=False)
            await asyncio.sleep(0.8)

        # 1. Unframed model query
        await send_cmd("Model Query (Unframed)", bytes([0x10, 0xFF, 0x20, 0xF0]), framed=False)

        # 2. Framed model query
        await send_cmd("Model Query (Framed)", bytes([0x10, 0xFF, 0x20, 0xF0]), framed=True)

        # 3. Framed status query
        await send_cmd("Status Query (Framed)", bytes([0x10, 0xFF, 0x40, 0x00]), framed=True)

        # 4. Framed battery query
        await send_cmd("Battery Query (Framed)", bytes([0x10, 0xFF, 0x50, 0xF1]), framed=True)

        # 5. Framed session start
        await send_cmd("Session Start: 10 FF FE 01", bytes([0x10, 0xFF, 0xFE, 0x01]), framed=True)

        # 6. Framed Mode 2: 10 FF 10 00 02
        await send_cmd("Mode 2: 10 FF 10 00 02", bytes([0x10, 0xFF, 0x10, 0x00, 0x02]), framed=True)

        # 7. Framed Paper Feed / Backoff: 10 FF FE 40
        await send_cmd("Feed/Backoff: 10 FF FE 40", bytes([0x10, 0xFF, 0xFE, 0x40]), framed=True)

        # 8. Framed Gap Positioning: 1D 0C
        await send_cmd("Gap Position: 1D 0C", bytes([0x1D, 0x0C]), framed=True)

        # 9. Framed Session End: 10 FF FE 45
        await send_cmd("Session End: 10 FF FE 45", bytes([0x10, 0xFF, 0xFE, 0x45]), framed=True)

        print("\nWaiting 2 seconds for final notifications...")
        await asyncio.sleep(2.0)
        print("Done!")

if __name__ == '__main__':
    asyncio.run(main())
