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
    if packet.startswith(b'\xFF\xF1\xF2\xF3\x03\x02\x01\x00') or packet.startswith(b'\xFF\xF1\xF2\xF3\x03\x02\x01\x01'):
        packet = packet[8:]
    sub = bytearray()
    for b in packet:
        if b == 0x01: sub.append(0xF1)
        elif b == 0xF1: sub.append(0x01)
        elif b == 0x02: sub.append(0xF2)
        elif b == 0xF2: sub.append(0x02)
        elif b == 0x10: sub.append(0xFF)
        elif b == 0xFF: sub.append(0x10)
        else: sub.append(b)
    return bytes(sub)

async def main():
    print(f"Connecting to {MAC}...")
    async with BleakClient(MAC, timeout=10.0) as client:
        print("Connected!")
        
        def on_notify(sender, data):
            decoded = deli_decode(data)
            print(f"  [NOTIFY RAW ] {data.hex(' ')}")
            print(f"  [NOTIFY DELI] {decoded.hex(' ')} | {decoded}")
            
        await client.start_notify(FF01, on_notify)
        await client.start_notify(FF03, on_notify)
        await asyncio.sleep(0.5)
        
        # Test 1: Raw AT query (unframed)
        print("\n--- Test 1: Sending UNFRAMED query: 10 FF 20 F0 ---")
        await client.write_gatt_char(FF02, bytes([0x10, 0xFF, 0x20, 0xF0]), response=False)
        await asyncio.sleep(1.0)
        
        # Test 2: Framed query
        cmd = bytes([0x10, 0xFF, 0x20, 0xF0])
        framed = deli_encode(cmd)
        print(f"\n--- Test 2: Sending FRAMED query: {framed.hex(' ')} ---")
        await client.write_gatt_char(FF02, framed, response=False)
        await asyncio.sleep(1.0)
        
        # Test 3: Framed status query: 10 FF 40 00
        cmd_status = bytes([0x10, 0xFF, 0x40, 0x00])
        framed_status = deli_encode(cmd_status)
        print(f"\n--- Test 3: Sending FRAMED status query: {framed_status.hex(' ')} ---")
        await client.write_gatt_char(FF02, framed_status, response=False)
        await asyncio.sleep(1.0)

        # Test 4: Framed RFID query / read
        # Let's see what RFID commands look like in standard Deli/Wewin
        # Often 10 FF 30 xx or 10 FF 70 xx or similar
        for rfid_cmd in [
            bytes([0x10, 0xFF, 0x70, 0x01]),
            bytes([0x10, 0xFF, 0x70, 0x00]),
            bytes([0x10, 0xFF, 0x30, 0x01]),
        ]:
            framed_rfid = deli_encode(rfid_cmd)
            print(f"\n--- Test: Sending {rfid_cmd.hex(' ')} framed as {framed_rfid.hex(' ')} ---")
            await client.write_gatt_char(FF02, framed_rfid, response=False)
            await asyncio.sleep(1.0)

        print("\nDone testing. Disconnecting.")

if __name__ == '__main__':
    asyncio.run(main())
