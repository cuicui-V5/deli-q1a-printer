"""
Deli Q1A Label Printer - BLE Driver (得力 Q1A 蓝牙底层驱动引擎)
Lightweight, pure Python BLE communication engine using Bleak.
Designed for clean porting to Android (BluetoothGatt), iOS (CoreBluetooth), or embedded C.
"""

import asyncio
from typing import Optional
from PIL import Image
from bleak import BleakClient, BleakScanner

from .protocol import (
    DEFAULT_MAC,
    WRITE_CHAR,
    NOTIFY_CHAR,
    EVENT_CHAR,
    WIDTH_PX,
    MAX_WIDTH_PX,
    CMD_ENABLE,
    CMD_WAKEUP,
    CMD_ALIGN_TOP,
    CMD_EXIT_LABEL,
    CMD_FINISH_JOB,
    cmd_density,
    build_continuous_print_stream
)
from .image import bitmap_to_raw_bytes

class DeliPrinter:
    """
    Direct BLE Driver for Deli Q1A Thermal Label Printer.
    Communicates directly with the printer hardware, bypassing official app and RFID locks.
    """

    def __init__(self, mac: str = DEFAULT_MAC):
        self.mac = mac
        self.client: Optional[BleakClient] = None

    @staticmethod
    async def scan(timeout: float = 5.0) -> list:
        """Scans for nearby BLE devices, identifying Deli printers."""
        devices = await BleakScanner.discover(timeout=timeout)
        result = []
        for d in devices:
            name = d.name or "Unknown Device"
            is_deli = bool(
                name and (
                    'Q1' in name.upper() or 
                    'DELI' in name.upper() or 
                    'A21E' in name.upper() or
                    'SS_' in name.upper()
                ) or (d.address.upper() == DEFAULT_MAC.upper())
            )
            result.append({
                "name": name,
                "address": d.address,
                "is_deli": is_deli
            })
        result.sort(key=lambda x: not x["is_deli"])
        return result

    async def connect(self, timeout: float = 8.0):
        """Establishes BLE connection and validates the print service characteristic."""
        print(f"Connecting to Deli Q1A [{self.mac}]...")
        self.client = BleakClient(self.mac, timeout=timeout)
        await self.client.connect()

        if not self.client.is_connected:
            raise ConnectionError(f"Failed to connect to printer at {self.mac}")

        # Validate write characteristic existence
        write_char = self.client.services.get_characteristic(WRITE_CHAR)
        if not write_char:
            await self.client.disconnect()
            raise ConnectionError(
                f"设备已连接，但该设备 (MAC: {self.mac}) 并非得力 Q1A 打印机（缺少 FF02 特征值）！"
                f"请确认打印机已开机（指示灯常亮），并确认连接正确的设备 MAC。"
            )

        # Optional telemetry notification subscriptions
        def on_notify(sender, data):
            pass

        try:
            if self.client.services.get_characteristic(NOTIFY_CHAR):
                await self.client.start_notify(NOTIFY_CHAR, on_notify)
            if self.client.services.get_characteristic(EVENT_CHAR):
                await self.client.start_notify(EVENT_CHAR, on_notify)
        except Exception:
            pass

        print("Connected and verified Deli Q1A print service successfully!")

    async def disconnect(self):
        """Disconnects the BLE connection gracefully."""
        if self.client and self.client.is_connected:
            await self.client.disconnect()
            self.client = None

    async def write_chunks(self, data: bytes, chunk_size: int = 200, delay: float = 0.012):
        """
        Splits data into MTU-safe chunks and sends them sequentially.
        Default chunk_size is 200 bytes (within negotiated 240B ATT MTU).
        """
        if not self.client or not self.client.is_connected:
            raise ConnectionError("Printer is not connected.")

        for off in range(0, len(data), chunk_size):
            chunk = data[off : off + chunk_size]
            await self.client.write_gatt_char(WRITE_CHAR, chunk, response=False)
            await asyncio.sleep(delay)

    async def print_image(self, img: Image.Image, density: int = 1, copies: int = 1):
        """
        Executes a continuous, seamless print job from a 1-bit monochrome PIL Image.
        """
        # Validate printhead width limit
        if img.width > MAX_WIDTH_PX:
            raise ValueError(f"Image width ({img.width} dots) exceeds max printhead width ({MAX_WIDTH_PX} dots)")

        bitmap_bytes = bitmap_to_raw_bytes(img)
        w_bytes = (img.width + 7) // 8
        h_px = img.height

        # Assemble the continuous pipeline stream (Raster GS v 0 + CMD_FEED_GAP)
        continuous_stream = build_continuous_print_stream(bitmap_bytes, w_bytes, h_px)

        # Set print density (0=Light, 1=Normal, 2=Dark)
        await self.client.write_gatt_char(WRITE_CHAR, cmd_density(density), response=False)
        await asyncio.sleep(0.15)

        for copy in range(copies):
            if copies > 1:
                print(f"--- Printing Copy {copy + 1}/{copies} ---")

            # 1. Enable printer session
            await self.client.write_gatt_char(WRITE_CHAR, CMD_ENABLE, response=False)
            await asyncio.sleep(0.2)

            # 2. Wakeup null bytes
            await self.client.write_gatt_char(WRITE_CHAR, CMD_WAKEUP, response=False)
            await asyncio.sleep(0.15)

            # 3. Label mode alignment (backfeed to top of label on first copy)
            if copy == 0:
                print("Aligning paper to top of label (1F 11 51)...")
                await self.client.write_gatt_char(WRITE_CHAR, CMD_ALIGN_TOP, response=False)
                await asyncio.sleep(0.4)

            # 4. Stream raster bitmap + gap feed in ONE continuous pipeline
            print(f"Streaming continuous print & feed pipeline ({len(continuous_stream)} bytes)...")
            await self.write_chunks(continuous_stream, chunk_size=200, delay=0.012)

            # Wait for physical motion and gap tracking to complete
            feed_time = max(1.2, (h_px / 180.0) + 0.4)
            await asyncio.sleep(feed_time)

            # 5. Exit label mode on last copy
            if copy == copies - 1:
                await self.client.write_gatt_char(WRITE_CHAR, CMD_EXIT_LABEL, response=False)
                await asyncio.sleep(0.2)

            # 6. Finish job
            await self.client.write_gatt_char(WRITE_CHAR, CMD_FINISH_JOB, response=False)
            await asyncio.sleep(0.4)

        print("All labels printed smoothly and successfully!")

async def print_job(mac: str, img: Image.Image, density: int = 1, copies: int = 1):
    """Convenience helper function to connect, print, and disconnect in one call."""
    printer = DeliPrinter(mac=mac)
    try:
        await printer.connect()
        await printer.print_image(img=img, density=density, copies=copies)
    finally:
        await printer.disconnect()
