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

disas_range(0x6328, 0x60, "func_6328")
