import capstone
from elftools.elf.elffile import ELFFile

so_path = r"d:\myProject\deli\deli_app_analysis\libPrinterNative.so"

with open(so_path, "rb") as f:
    elf = ELFFile(f)
    text_sec = elf.get_section_by_name(".text")
    text_data = text_sec.data()
    text_addr = text_sec["sh_addr"]
    
    symbols = {}
    for section in elf.iter_sections():
        if section["sh_type"] in ("SHT_SYMTAB", "SHT_DYNSYM"):
            for sym in section.iter_symbols():
                if sym.name:
                    symbols[sym.name] = (sym["st_value"], sym["st_size"])

md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_THUMB)

def disas_func(name):
    if name not in symbols:
        print(f"Symbol {name} not found")
        return
    val, size = symbols[name]
    addr = val & ~1  # Thumb bit
    offset = addr - text_addr
    code = text_data[offset:offset+size]
    print(f"\n{'='*50}\nDisassembly of {name} (0x{addr:x}, {size} bytes):\n{'='*50}")
    for ins in md.disasm(code, addr):
        print(f"0x{ins.address:08x}:  {ins.mnemonic:10s} {ins.op_str}")

funcs_to_disas = [
    "Java_com_print_libnative_PrintNative_a",
    "Java_com_print_libnative_PrintNative_b",
    "Java_com_print_base_utils_NativeUtil_getEncryp",
    "Java_com_print_libnative_Compress_codeLihu",
    "Java_com_print_libnative_Compress_codeESC",
    "Java_com_print_libnative_Compress_codeCPCL",
    "Java_com_print_libnative_Compress_codeTSPL",
    "Java_com_print_libnative_DFunction_dataprocesser",
    "Java_com_print_libnative_F2Function_F2Data",
    "Java_com_print_libnative_Code941_code"
]

for fn in funcs_to_disas:
    disas_func(fn)
