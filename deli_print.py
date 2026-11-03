#!/usr/bin/env python3
"""
Deli Q1A Label Printer - Command Line Interface (得力 Q1A 独立命令行工具)
Standalone CLI tool for quick printing and testing via BLE without starting the WebApp.
"""

import argparse
import asyncio
import sys

from core import (
    DEFAULT_MAC,
    DeliPrinter,
    print_job,
    render_text_label,
    render_image_label
)

def main():
    parser = argparse.ArgumentParser(
        description="Deli Q1A Label Printer CLI Tool (得力标签机免芯片命令行驱动)"
    )
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
            print("Scanning for nearby BLE printers...")
            devices = await DeliPrinter.scan(timeout=5.0)
            if not devices:
                print("No BLE devices found. Ensure Bluetooth is enabled on PC.")
                return
            for d in devices:
                prefix = "  * [MATCHED]" if d["is_deli"] else "   "
                print(f"{prefix} {d['name']:25s} [{d['address']}]")
        asyncio.run(do_scan())
        return

    if args.image:
        img = render_image_label(args.image, orientation=args.orientation,
                                 dither=not args.no_dither, invert=args.invert)
    elif args.text:
        img = render_text_label(args.text, orientation=args.orientation, border=not args.no_border)
    else:
        # Default self-test label
        img = render_text_label("得力 Q1A 标签机\\n免芯片打印成功!\\n15x30mm 间隙纸",
                                orientation=args.orientation, border=not args.no_border)

    if args.preview:
        img.save(args.preview)
        print(f"Preview saved to {args.preview} ({img.width}x{img.height})")
        return

    asyncio.run(print_job(mac=args.mac, img=img, density=args.density, copies=args.copies))

if __name__ == '__main__':
    main()
