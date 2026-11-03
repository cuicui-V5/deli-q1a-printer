"""
Deli Q1A Label Printer - Image & Bitmap Utilities (图像与单色点阵处理)
Converts images/text to 1-bit monochrome MSB byte arrays for ESC/POS thermal printing.
"""

import os
from PIL import Image, ImageDraw, ImageFont, ImageOps

from .protocol import WIDTH_PX, HEIGHT_PX

def find_system_font(size: int):
    """Finds a usable Chinese/English font, prioritizing Source Han Sans / Noto Sans SC (思源黑体)."""
    candidates = [
        # Priority 1: Local bundled Source Han Sans / Noto Sans SC
        os.path.join(os.path.dirname(os.path.dirname(__file__)), "web", "static", "fonts", "NotoSansSC.ttf"),
        # Priority 2: Windows official Noto Sans SC
        r"C:\Windows\Fonts\NotoSansSC-VF.ttf",
        r"C:\Windows\Fonts\SourceHanSansSC-Regular.otf",
        r"C:\Windows\Fonts\msyh.ttc",     # Microsoft YaHei
        r"C:\Windows\Fonts\simhei.ttf",   # SimHei
        "/System/Library/Fonts/PingFang.ttc",
        "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
        "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
    ]
    for path in candidates:
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except Exception:
                continue
    return ImageFont.load_default()

def bitmap_to_raw_bytes(img: Image.Image) -> bytes:
    """
    Converts a 1-bit monochrome PIL Image to ESC/POS GS v 0 raw byte array.
    
    Format:
    - 1 byte represents 8 horizontal dots.
    - Most Significant Bit (MSB, bit 7) is the leftmost dot.
    - In ESC/POS: 1 = Black dot (heat element ON), 0 = White background (element OFF).
    - In Pillow mode '1': 0 is black, 1 is white.
    """
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

def render_text_label(text: str, orientation: str = 'landscape', border: bool = True, font_size: int = None) -> Image.Image:
    """
    Renders text onto a monochrome canvas sized for 15mm x 30mm labels.
    - landscape: 240 wide x 120 high, rotated 90 degrees to (120, 240).
    - portrait:  120 wide x 240 high.
    """
    if orientation == 'landscape':
        cw, ch = HEIGHT_PX, WIDTH_PX  # 240 x 120
    else:
        cw, ch = WIDTH_PX, HEIGHT_PX  # 120 x 240

    img = Image.new('1', (cw, ch), color=1) # 1 = white
    draw = ImageDraw.Draw(img)

    if border:
        # Full 4-sided symmetrical rounded border for 240x96 native geometry
        mx, my = (10, 4) if orientation == 'landscape' else (4, 10)
        draw.rounded_rectangle([mx, my, cw - mx, ch - my], radius=6, outline=0, width=2)

    lines = [line.strip() for line in text.split('\\n') if line.strip()]
    if not lines:
        lines = [text]

    if font_size is None:
        if orientation == 'landscape':
            font_size = 20 if len(lines) <= 2 else 15
        else:
            font_size = 18 if len(lines) <= 3 else 13

    font = find_system_font(font_size)

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

def render_image_label(image_path: str, orientation: str = 'landscape', dither: bool = True, invert: bool = False) -> Image.Image:
    """
    Loads an image file, fits it inside the label geometry, and converts to 1-bit monochrome.
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
