#!/usr/bin/env python3
"""
Deli Q1A WebApp Server (得力标签机可视化设计与打印系统)
FastAPI backend server providing REST API for web-based canvas editing and BLE direct printing.
"""

import asyncio
import base64
import io
import os
import sys
import webbrowser
from typing import Optional

from PIL import Image
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel
import uvicorn

# Import core printer driver routines
from core import (
    DEFAULT_MAC,
    WIDTH_PX,
    HEIGHT_PX,
    DeliPrinter,
    print_job,
    render_text_label
)

app = FastAPI(title="Deli Q1A Label Studio", description="Web-based Label Designer and BLE Driver for Deli Q1A")

# Enable CORS for local development flexibility
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
WEB_DIR = os.path.join(BASE_DIR, "web")
STATIC_DIR = os.path.join(WEB_DIR, "static")

# Mount static files
app.mount("/static", StaticFiles(directory=STATIC_DIR), name="static")

class PrintRequest(BaseModel):
    image_base64: str          # PNG data URL or pure base64
    mac: Optional[str] = DEFAULT_MAC
    density: Optional[int] = 1 # 0: Light, 1: Normal, 2: Dark
    copies: Optional[int] = 1
    width: Optional[int] = WIDTH_PX
    height: Optional[int] = HEIGHT_PX

class ScanResponse(BaseModel):
    devices: list

@app.get("/")
async def serve_index():
    index_file = os.path.join(WEB_DIR, "index.html")
    if os.path.exists(index_file):
        return FileResponse(index_file)
    return {"message": "Deli Q1A WebApp API is running."}

@app.get("/api/config")
async def get_config():
    return {
        "default_mac": DEFAULT_MAC,
        "width_px": WIDTH_PX,
        "height_px": HEIGHT_PX,
        "dpi": 203,
        "default_size_mm": "15x30",
        "version": "1.0.0"
    }

@app.get("/api/scan")
async def scan_ble_devices():
    """Scans for nearby BLE devices, identifying Deli printers."""
    try:
        devices = await DeliPrinter.scan(timeout=5.0)
        return {"devices": devices}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"BLE scan failed: {str(e)}")

@app.post("/api/print")
async def handle_print(req: PrintRequest):
    """
    Receives rendered canvas image as base64, converts to 1-bit monochrome bitmap,
    and sends the verified print packet sequence directly to the physical printer.
    """
    try:
        # Decode base64
        data_str = req.image_base64
        if "base64," in data_str:
            data_str = data_str.split("base64,")[1]
        
        img_bytes = base64.b64decode(data_str)
        img = Image.open(io.BytesIO(img_bytes)).convert('RGBA')

        # Create white background and composite
        bg = Image.new('RGB', img.size, (255, 255, 255))
        bg.paste(img, mask=img.split()[3]) # 3 is alpha
        gray = bg.convert('L')

        # Deli Q1A printhead is 96 dots wide.
        # If user edited in Landscape mode (e.g. 216x96), rotate 90 degrees to print along 30mm length
        if gray.width > gray.height:
            gray = gray.rotate(90, expand=True)

        # Ensure width is exactly 96 dots
        if gray.width != WIDTH_PX:
            target_h = int(gray.height * (WIDTH_PX / gray.width))
            gray = gray.resize((WIDTH_PX, target_h), Image.Resampling.LANCZOS)

        # Convert to 1-bit monochrome (0=black dot, 1=white background)
        bw_img = gray.point(lambda p: 0 if p < 128 else 1, mode='1')

        # Execute print job over BLE
        mac = req.mac.strip() if req.mac else DEFAULT_MAC
        copies = max(1, min(20, req.copies or 1))
        density = max(0, min(2, req.density or 1))

        await print_job(
            mac=mac,
            img=bw_img,
            density=density,
            copies=copies
        )

        return {
            "success": True,
            "message": f"成功打印 {copies} 份标签！",
            "mac": mac,
            "density": density,
            "dimensions": f"{bw_img.width}x{bw_img.height}"
        }
    except Exception as e:
        print(f"[Error in /api/print]: {e}", file=sys.stderr)
        raise HTTPException(status_code=500, detail=f"打印失败: {str(e)}")

@app.post("/api/test_print")
async def handle_test_print():
    """Generates and prints the verified test calibration label."""
    try:
        img = render_text_label(
            "得力 Q1A 标签机\\n免芯片打印成功!\\n15x30mm 间隙纸",
            orientation='landscape',
            border=True
        )
        await print_job(mac=DEFAULT_MAC, img=img, density=1, copies=1)
        return {"success": True, "message": "测试页打印指令已成功发送！"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"测试页打印失败: {str(e)}")

def main():
    port = 8000
    host = "127.0.0.1"
    url = f"http://{host}:{port}"
    print("=" * 60)
    print(f"🌟 得力 Q1A 标签工坊 WebApp 已就绪！")
    print(f"🌐 服务地址: {url}")
    print(f"📱 默认打印机 MAC: {DEFAULT_MAC}")
    print("=" * 60)

    # Open browser automatically after a short delay
    async def open_browser():
        await asyncio.sleep(1.0)
        webbrowser.open(url)

    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    loop.create_task(open_browser())

    config = uvicorn.Config(app=app, host=host, port=port, log_level="info")
    server = uvicorn.Server(config)
    loop.run_until_complete(server.serve())

if __name__ == '__main__':
    main()
