with open(r"d:\myProject\deli\deli_app_analysis\libapp.so", "rb") as f:
    app_data = f.read()

keywords = [b"Q1A", b"Q1", b"codeTSPL", b"codeESC", b"codeCPCL", b"codeLihu", b"Compress", b"PrintNative", b"rfid", b"10 FF", b"PrintSDK", b"printsdk"]
for kw in keywords:
    idx = 0
    cnt = 0
    while True:
        idx = app_data.find(kw, idx)
        if idx == -1:
            break
        cnt += 1
        start = max(0, idx - 20)
        end = min(len(app_data), idx + len(kw) + 30)
        sample = app_data[start:end]
        if cnt <= 3:
            print(f"{kw}: at 0x{idx:x}: {repr(sample)}")
        idx += len(kw)
    print(f"Total {kw}: {cnt}")
