# 得力 Q1A 标签工坊 - Android 原生工程 (Deli Label Studio for Android)

本项目为 **得力 (Deli) Q1A 蓝牙便携标签打印机** 的 100% 原生 Android 客户端实现，已完全解耦并移除官方应用中的 RFID 耗材芯片强校验。

---

## 🌟 核心特性

1. **原生低功耗蓝牙 (BLE) 驱动引擎**：
   - 适配 Android 12+ (API 31+) 最新的 `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` 运行时权限体系，兼顾 Android 6.0~11 的位置权限兼容。
   - 自动协商 **MTU 247** 高速数据通道，消除蓝牙丢包。
   - 支持蓝牙信号强度 (RSSI) 实时排序，智能识别 `Q1A_SS_A21E` / `DELI` 打印机，提供 **默认 MAC (5E:55:09:F0:A2:1E) 一键直连**。
2. **一气呵成连续流打印管道 (Continuous Streaming Pipeline)**：
   - 实现无顿挫无卡顿的平滑走纸：光栅位图与 `1D 0C` (间隙定位走纸) 紧随其后以单管道发送至打印机内部 FIFO，彻底消除电机在中途停滞 0.5s 的问题。
3. **120 点阵满幅无白边居中映射**：
   - 精准按照 203 DPI (8 dots/mm) 点阵设计，覆盖 15mm 耗材全部 120 个发热点（15 字节/行），消除右侧 4.5mm 白边。
   - 支持 15×30mm、15×40mm、15×50mm、12×30mm、12×40mm 等多规格一键切换。
4. **所见即所得 (WYSIWYG) 交互式触摸画板**：
   - 支持单指拖拽、层级选中、右上角一键删除。
   - 自由添加与编辑：
     - **富文本**：字号滑块实时调节、粗体开关、居中/靠左对齐。
     - **条形码 / 二维码**：基于 ZXing 引擎生成高对比度 Code-128 与 QR Code。
     - **几何图形**：自适应标签模切角的安全圆角边框、分割线、矩形块。
     - **相册/图片导入**：自动等比缩放、二值化与 Floyd-Steinberg 误差扩散抖动。
   - 完整的撤销 (Undo) / 重做 (Redo) 历史记录栈。
5. **四大经典场景预置模版**：
   - 🍎 **食品赏味保鲜标**（自动插入当前日期、保质提示、安全圆角边框）
   - 🔌 **数码线缆标**（Type-C、PD 充电瓦数、设备位置）
   - 📦 **居家整理收纳标**（分类、物品明细、整理日期）
   - 🏷️ **商超特惠价签标**（品名、大字价格、划线原价、Code-128 条码）

---

## 🏗️ 工程结构

```
android/
├── app/
│   ├── build.gradle.kts           # 模块构建配置 (compileSdk 34, minSdk 24)
│   ├── proguard-rules.pro         # 混淆保护规则
│   └── src/main/
│       ├── AndroidManifest.xml    # 蓝牙权限与活动声明
│       ├── java/com/deli/printer/
│       │   ├── DeliApp.kt         # 全局 Application 单例
│       │   ├── ble/
│       │   │   ├── BlePermissionHelper.kt  # 动态权限请求与检查
│       │   │   ├── BleDeviceItem.kt        # 扫描设备模型
│       │   │   └── DeliBleManager.kt       # 核心 BLE 控制引擎 (连接/MTU/分块/管道打印)
│       │   ├── protocol/
│       │   │   ├── DeliProtocol.kt         # 底层指令集与光栅流打包
│       │   │   └── BitmapRasterizer.kt     # Bitmap 转 1-Bit 单色点阵 (逆时针90度自动旋转)
│       │   ├── model/
│       │   │   ├── PaperSpec.kt            # 物理耗材规格定义 (15x30, 15x40...)
│       │   │   └── CanvasElement.kt        # 画板图元模型 (Text, Barcode, Shape, Image)
│       │   ├── barcode/
│       │   │   └── BarcodeEncoder.kt       # ZXing 条码与二维码渲染器
│       │   ├── template/
│       │   │   └── LabelTemplates.kt       # 常用场景预置模版
│       │   └── ui/
│       │       ├── MainActivity.kt         # 主活动界面与交互调度
│       │       ├── canvas/
│       │       │   └── LabelCanvasView.kt  # 核心触摸交互画板自定义 View
│       │       ├── adapter/
│       │       │   └── DeviceAdapter.kt    # 蓝牙设备列表适配器
│       │       └── dialog/
│       │           ├── DeviceScanDialog.kt # 蓝牙搜索弹窗 (带默认MAC一键直连)
│       │           ├── PrintDialog.kt      # 打印设置弹窗 (份数/浓度/进度条)
│       │           ├── TextEditDialog.kt   # 文本属性编辑弹窗
│       │           ├── BarcodeDialog.kt    # 条码生成弹窗
│       │           └── PaperSpecDialog.kt  # 耗材尺寸选择弹窗
│       └── res/                            # 布局与主题资源
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## 🚀 编译与运行指南

### 方法一：使用 Android Studio 直接打开
1. 启动 **Android Studio** (推荐 2023.1 Hedgehog 或更新版本)。
2. 选择 **Open...**，选中 `d:\myProject\deli\android` 目录。
3. 等待 Gradle 自动完成依赖同步（Sync）。
4. 连接 Android 手机或启动模拟器，点击顶部绿色的 **Run (运行)** 按钮，即可将 App 安装至手机。

### 方法二：命令行一键编译打包 APK
在终端进入 `android` 目录执行：

```bash
cd android
./gradlew assembleDebug
```

编译成功后，生成的 Debug APK 位于：
`android/app/build/outputs/apk/debug/app-debug.apk`

通过 ADB 直接安装至手机：
```bash
adb install android/app/build/outputs/apk/debug/app-debug.apk
```
