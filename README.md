# Deli Q1A 标签打印机协议逆向与免芯片驱动 (Deli Label Printer BLE Driver)

> 彻底告别耗材绑架与 RFID 芯片锁！通过逆向工程得力官方 App 蓝牙通信协议，实现无需官方 App、无需 RFID 芯片、直接通过 PC 蓝牙打印第三方通用热敏标签纸。

![成功打印实物图](assets/success_print.jpg)

---

## 📌 项目背景与痛点

很多微型热敏标签机（如**得力 Q1A**、Q1、Q2 系列）采用了“耗材绑定”套路：
1. **强制绑定 RFID/NFC 芯片**：官方标签卷轴内嵌加密 RFID 芯片，内部烧录了打印次数计数器。
2. **锁死第三方耗材**：换用便宜的第三方通用耗材后，App 弹窗拦截：`使用非官方耗材，禁止打印`。
3. **次数耗尽即作废**：即使正版耗材还有纸，只要芯片计数归零，App 同样提示：`超过打印次数，耗材用尽`。

本项目通过完整逆向官方 Android App 与原生底层通信库，彻底揭秘了这一机制，并开发了跨平台 Python 命令行驱动，实现**任意第三方耗材自由打印**！

---

## 🔬 逆向工程揭秘 (Reverse Engineering Journey)

### 第一步：硬件层验证 —— 机器真的“锁死”了吗？

在对通信协议动刀之前，我们先验证了一个关键物理事实：
- **双击电源键自检**：在**不放任何 RFID 芯片**的情况下，连续双击得力 Q1A 电源键，机器**成功吐出了自检测试页**！
- **结论**：硬件主控电路、微型步进电机、热敏打印头和光学间隙传感器在物理层面**完全不依赖芯片**，固件底层并未在硬件级硬编码锁死非官方纸张。

### 第二步：解包官方 App —— 锁究竟下在哪里？

我们获取了官方 App **“得力趣贴贴” (`com.delicloud.app.label`)** 进行深度解包分析：
1. **加固壳识别**：APK 采用梆梆企业加固（`com.secneo.apkwrapper`），但核心底层通信库是以未加密 ELF 动态库（`.so`）形式直接暴露的。
2. **RFID 验签逻辑暴露 (`libdeli-rfid-verify.so`)**：
   - 提取出 JNI 接口：`Java_com_delicloud_library_printsdk_ext_NativeLib_getReleaseSecretKey`
   - 导出公钥：`MDIwEAYHKoZIzj0CAQYFK4EEAAYDHgAEss/PBPlpXxt586pS4ei7ShbbK6EN0f7AVEusbg==`
   - ASN.1 DER 解码证明：这是一个标准的 **`secp160r1` ECDSA 非对称加密公钥**。
   - **真相大白**：打印机把 RFID 标签数据通过蓝牙透传给手机，**验签与打印计数校验全部发生在手机 App 端**！一旦芯片缺失或验签失败，App 直接弹窗 `res/PL.xml`（“使用非官方耗材”），**根本不会往蓝牙发送任何打印数据指令**。硬件本身完全是无辜的。

### 第三步：协议破译 —— 为什么早期尝试完全静音？

在我们早期尝试直接用通用指令（如微赢 Wewin 系列指令）与打印机通信时，打印机蓝牙回复了 `OK`、`FD 01`、`FA 01`，但**机械电机完全不转，整机静音**。

通过对官方底层动态库 `libPrinterNative.so` 进行 Capstone ARM 反汇编，我们发现了关键秘密：
- **得力私有协议安全壳 (`PrintNative_a` / `PrintNative_b`)**：
  官方发送的每一笔打印数据，最外层必须包裹一个 **8 字节固定帧头**：
  $$\text{Frame Header} = \texttt{FF F1 F2 F3 03 02 01 01}$$
  打印机返回的确认帧头为：
  $$\text{Response Header} = \texttt{FF F1 F2 F3 03 02 01 00}$$
- **专属字节映射混淆算法**：
  $$\begin{cases}
  0\text{x}10 \longleftrightarrow 0\text{xFF} \\
  0\text{x}01 \longleftrightarrow 0\text{xF1} \\
  0\text{x}02 \longleftrightarrow 0\text{xF2}
  \end{cases}$$
- **静音原因**：打印机内的 BLE 蓝牙芯片负责处理基础 AT 查询，但主控电机的供电 MOSFET 驱动管受得力私有协议解析器保护。未包裹得力加密帧的数据，电机驱动芯片直接静默丢弃。

### 第四步：协议家族溯源与电机时序调优

进一步逆向 `libPrinterNative.so`，我们在符号表中发现了 `LuckP_D1S`，成功追溯出其核心架构属于 **厦门爱印/力虎 LuckPrinter 协议族**：
1. **启用打印机**：`10 FF F1 03`
2. **唤醒微控制器**：`b'\x00' * 12`
3. **设置打印浓度**：`10 FF 10 00 [density]`（0=浅，1=正常，2=浓）
4. **间隙纸校准定位**：`1F 11 51`
   - 机器首次打印前会自动进纸探测透明间隙，确保打印头与第一张标签起始位置精准对齐。
5. **光栅图像数据传输**：ESC/POS 标准光栅指令 `1D 76 30 00 [wL] [wH] [hL] [hH]`
6. **物理加热时序等待**：203 DPI 逐行热敏烧录需要物理时间，数据流发送后需等待约 1.5~2.0 秒，待全部点阵加热完毕后再触发走纸，防止电机发生失步卡顿（嗡嗡声）。
7. **进纸至下一标签切纸口**：`1D 0C`
8. **退出标签模式并结束作业**：`1F 11 50` + `10 FF F1 45`

---

## 🛠️ 硬件与耗材规格

| 参数项 | 规格说明 |
| :--- | :--- |
| **设备型号** | 得力 (Deli) Q1A 标签打印机 (`Q1A_SS_A21E`) |
| **通信接口** | 蓝牙低功耗 Bluetooth Low Energy (BLE 4.2+) |
| **GATT 特征** | `FF02` (写指令/数据), `FF01` / `FF03` (状态通知接收) |
| **分辨率** | 203 DPI (8 dots/mm) |
| **适用耗材** | 15mm $\times$ 30mm 间隙标签纸 (Gap Paper) |
| **可打印区域** | 宽 96 dots (12mm, 12 字节/行) $\times$ 高 216 dots (27mm) |

---

## 💻 快速上手

### 1. 环境准备

需要 Python 3.9+ 环境，安装必要依赖：

```bash
git clone https://github.com/cuicui-V5/deli-q1a-printer.git
cd deli-q1a-printer
pip install -r requirements.txt
```

### 2. 搜索附近的打印机

确保打印机开机（指示灯常亮）：

```bash
python deli_print.py --scan
```

控制台会列出所有蓝牙设备，默认 MAC 为 `5E:55:09:F0:A2:1E`。如果是另一台设备，可通过 `--mac <你的MAC>` 指定。

### 3. 打印自定义文字

```powershell
# 打印多行横版文字（自动居中并添加边框）
python deli_print.py --text "收纳盒-数据线\n规格: Type-C\n日期: 2026-11-03"

# 打印竖版文字
python deli_print.py --text "分类标签" --orientation portrait

# 打印多份并去除边框
python deli_print.py --text "样品01" --copies 3 --no-border
```

### 4. 打印图片

支持任意格式图片，自动按比例缩放并应用 Floyd-Steinberg 误差扩散算法抗锯齿：

```powershell
python deli_print.py --image my_logo.png
```

### 5. 生成本地预览图（不耗纸测试）

```powershell
python deli_print.py --text "测试文字" --preview preview.png
```

---

## 📁 命令行参数详解

| 参数 | 类型 | 默认值 | 作用说明 |
| :--- | :--- | :--- | :--- |
| `--text` | `str` | - | 要打印的文本，换行使用 `\n` |
| `--image` | `str` | - | 要打印的图片文件路径 |
| `--mac` | `str` | `5E:55:09:F0:A2:1E` | 打印机蓝牙 MAC 地址 |
| `--orientation` | `choice` | `landscape` | 文字方向：`landscape` (横版沿30mm) 或 `portrait` (竖版沿15mm) |
| `--density` | `int` | `1` | 打印浓度：`0`=淡, `1`=标准, `2`=浓 (推荐 1，保护电池和电机) |
| `--copies` | `int` | `1` | 打印份数 |
| `--no-border` | `flag` | `False` | 禁用默认的外边框 |
| `--no-dither` | `flag` | `False` | 图片打印时禁用 Floyd-Steinberg 抖动算法 |
| `--invert` | `flag` | `False` | 反色打印（黑底白字） |
| `--preview` | `str` | - | 保存渲染后的 1-bit 图片到指定路径，不向打印机发指令 |
| `--scan` | `flag` | - | 扫描周围的 BLE 蓝牙设备 |

---

## ⚖️ 免责声明与开源许可

- 本项目遵循 **MIT License** 开源。
- 本项目逆向工程仅供**个人硬件互操作性研究（Interoperability）、维修权（Right to Repair）及学术交流**使用。
- 项目中所有商标所有权归得力集团及相关厂商所有。
