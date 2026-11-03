import os
from elftools.elf.elffile import ELFFile
import capstone

so_path = r"d:\myProject\deli\deli_app_analysis\libPrinterNative.so"

with open(so_path, "rb") as f:
    elf = ELFFile(f)
    print("Machine:", elf["e_machine"])
    symbols = []
    for section in elf.iter_sections():
        if section["sh_type"] in ("SHT_SYMTAB", "SHT_DYNSYM"):
            for sym in section.iter_symbols():
                symbols.append((sym.name, sym["st_value"], sym["st_size"]))

for name, val, size in sorted(symbols, key=lambda x: x[1]):
    if name:
        print(f"0x{val:08x} ({size:4d} bytes): {name}")
