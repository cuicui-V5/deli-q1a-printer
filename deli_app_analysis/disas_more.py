import capstone
from elftools.elf.elffile import ELFFile

so_path = r"d:\myProject\deli\deli_app_analysis\libPrinterNative.so"

with open(so_path, "rb") as f:
    elf = ELFFile(f)
    text_sec = elf.get_section_by_name(".text")
    text_data = text_sec.data()
    text_addr = text_sec["sh_addr"]

md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_THUMB)

def disas_range(addr, size, title=""):
    offset = addr - text_addr
    code = text_data[offset:offset+size]
    print(f"\n{'='*50}\nDisassembly of {title} (0x{addr:x}, {size} bytes):\n{'='*50}")
    for ins in md.disasm(code, addr):
        print(f"0x{ins.address:08x}:  {ins.mnemonic:10s} {ins.op_str}")

disas_range(0x5f08, 0x98, "func_5f08")
disas_range(0x5fa0, 0x50, "func_5fa0")
disas_range(0x4bec, 0x74, "func_4bec")
disas_range(0x4cf8, 0x100, "func_4cf8")
disas_range(0x584c, 0xb4, "PrintNative_analysisData")
disas_range(0xd000, 0xc6, "F2Data")
disas_range(0xcf08, 0xf8, "Code941")
