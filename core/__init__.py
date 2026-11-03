"""
Deli Q1A Printer Core SDK (得力 Q1A 打印机核心驱动套件)
Pure Python, portable, and cleanly decoupled from Web/UI layers.
"""

from .protocol import (
    SERVICE_UUID,
    WRITE_CHAR,
    NOTIFY_CHAR,
    EVENT_CHAR,
    DEFAULT_MAC,
    DPI,
    WIDTH_PX,
    HEIGHT_PX,
    WIDTH_BYTES,
    CMD_ENABLE,
    CMD_WAKEUP,
    CMD_ALIGN_TOP,
    CMD_FEED_GAP,
    CMD_EXIT_LABEL,
    CMD_FINISH_JOB,
    cmd_density,
    build_raster_header,
    build_continuous_print_stream
)

from .image import (
    bitmap_to_raw_bytes,
    render_text_label,
    render_image_label,
    find_system_font
)

from .driver import (
    DeliPrinter,
    print_job
)

__all__ = [
    "SERVICE_UUID",
    "WRITE_CHAR",
    "NOTIFY_CHAR",
    "EVENT_CHAR",
    "DEFAULT_MAC",
    "DPI",
    "WIDTH_PX",
    "HEIGHT_PX",
    "WIDTH_BYTES",
    "CMD_ENABLE",
    "CMD_WAKEUP",
    "CMD_ALIGN_TOP",
    "CMD_FEED_GAP",
    "CMD_EXIT_LABEL",
    "CMD_FINISH_JOB",
    "cmd_density",
    "build_raster_header",
    "build_continuous_print_stream",
    "bitmap_to_raw_bytes",
    "render_text_label",
    "render_image_label",
    "find_system_font",
    "DeliPrinter",
    "print_job"
]
