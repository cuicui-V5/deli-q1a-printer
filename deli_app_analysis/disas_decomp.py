import capstone
from elftools.elf.elffile import ELFFile

so_path = r"d:\myProject\deli\deli_app_analysis\libPrinterNative.so"

with open(so_path, "rb") as f:
    elf = ELFFile(f)
    s = elf.get_section_by_name('.text')
    data = s.data()
    sh_addr = s['sh_addr']

md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_THUMB)

offset = 0x5290 - sh_addr
print("=== decompressionString (0x5290) ===")
for ins in md.disasm(data[offset:offset+150], 0x5290):
    print(f"0x{ins.address:08x}:  {ins.mnemonic:10s} {ins.op_str}")

offset2 = 0x53ec - sh_addr
print("\n=== decompressionString2 (0x53ec) ===")
for ins in md.disasm(data[offset2:offset2+150], 0x53ec):
    print(f"0x{ins.address:08x}:  {ins.mnemonic:10s} {ins.op_str}")
