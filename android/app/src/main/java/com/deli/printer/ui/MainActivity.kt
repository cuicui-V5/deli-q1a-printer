package com.deli.printer.ui

import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.deli.printer.DeliApp
import com.deli.printer.R
import com.deli.printer.ble.BleConnectionState
import com.deli.printer.ble.BlePermissionHelper
import com.deli.printer.model.*
import com.deli.printer.protocol.DeliProtocol
import com.deli.printer.template.LabelTemplates
import com.deli.printer.ui.canvas.LabelCanvasView
import com.deli.printer.ui.dialog.*
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val bleManager get() = DeliApp.instance.bleManager

    private lateinit var canvasView: LabelCanvasView
    private lateinit var tvPaperSpec: TextView
    private lateinit var tvBleStatus: TextView
    private lateinit var statusIndicator: View
    private lateinit var btnPrint: Button
    private lateinit var btnUndo: ImageButton
    private lateinit var btnRedo: ImageButton

    // Bottom Toolbars
    private lateinit var mainToolBar: View
    private lateinit var textConfigBar: View
    private lateinit var btnConfigEditText: Button
    private lateinit var btnConfigSizeMinus: Button
    private lateinit var tvConfigSize: TextView
    private lateinit var btnConfigSizePlus: Button
    private lateinit var btnConfigBold: Button
    private lateinit var btnConfigAlign: Button
    private lateinit var btnConfigDelete: Button
    private lateinit var btnConfigDone: Button

    private var currentSpec: PaperSpec = PaperSpec.SPEC_15_30
    private var isLandscape: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        observeBleState()

        // Apply default single-line large text template
        applyTemplate { w, h -> LabelTemplates.createDefaultExample(w, h) }

        // Check BLE Permissions & Auto Connect
        if (!BlePermissionHelper.hasPermissions(this)) {
            BlePermissionHelper.requestPermissions(this)
        } else {
            autoConnectPrinter()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (BlePermissionHelper.hasPermissions(this)) {
            autoConnectPrinter()
        }
    }

    private fun autoConnectPrinter() {
        try {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bluetoothManager?.adapter
            if (adapter != null && adapter.isEnabled) {
                val state = bleManager.connectionState.value
                if (state !is BleConnectionState.Connected && state !is BleConnectionState.Connecting) {
                    bleManager.connectMac(DeliProtocol.DEFAULT_MAC)
                }
            }
        } catch (e: Exception) {
            // Silently ignore or log
        }
    }

    private fun initViews() {
        canvasView = findViewById(R.id.labelCanvasView)
        tvPaperSpec = findViewById(R.id.tvPaperSpec)
        tvBleStatus = findViewById(R.id.tvBleStatus)
        statusIndicator = findViewById(R.id.statusIndicator)
        btnPrint = findViewById(R.id.btnPrint)
        btnUndo = findViewById(R.id.btnUndo)
        btnRedo = findViewById(R.id.btnRedo)

        // Bottom Bars
        mainToolBar = findViewById(R.id.mainToolBar)
        textConfigBar = findViewById(R.id.textConfigBar)
        btnConfigEditText = findViewById(R.id.btnConfigEditText)
        btnConfigSizeMinus = findViewById(R.id.btnConfigSizeMinus)
        tvConfigSize = findViewById(R.id.tvConfigSize)
        btnConfigSizePlus = findViewById(R.id.btnConfigSizePlus)
        btnConfigBold = findViewById(R.id.btnConfigBold)
        btnConfigAlign = findViewById(R.id.btnConfigAlign)
        btnConfigDelete = findViewById(R.id.btnConfigDelete)
        btnConfigDone = findViewById(R.id.btnConfigDone)

        updateCanvasGeometry()
    }

    private fun updateCanvasGeometry() {
        val wDots = if (isLandscape) currentSpec.lengthPx else currentSpec.widthPx
        val hDots = if (isLandscape) currentSpec.widthPx else currentSpec.lengthPx

        canvasView.setCanvasDimensions(wDots, hDots)
        tvPaperSpec.text = "${currentSpec.widthMm}×${currentSpec.lengthMm} mm (${if (isLandscape) "横向" else "纵向"})"
    }

    private fun updateTextConfigUi(elem: TextElement) {
        tvConfigSize.text = "${elem.textSize.toInt()}pt"
        btnConfigBold.text = if (elem.isBold) "B 粗体(开)" else "B 粗体"
        btnConfigAlign.text = if (elem.isCentered) "居中" else "靠左"
    }

    private fun setupListeners() {
        // Selection change listener: dynamically switch between main tools and text config
        canvasView.onSelectionChanged = { selected ->
            if (selected is TextElement) {
                mainToolBar.visibility = View.GONE
                textConfigBar.visibility = View.VISIBLE
                updateTextConfigUi(selected)
            } else {
                textConfigBar.visibility = View.GONE
                mainToolBar.visibility = View.VISIBLE
            }
        }

        // Spec Dialog
        findViewById<View>(R.id.specContainer).setOnClickListener {
            PaperSpecDialog().apply {
                onSpecSelected = { spec ->
                    currentSpec = spec
                    updateCanvasGeometry()
                    showToast("已选择耗材: ${spec.name}")
                }
            }.show(supportFragmentManager, "PaperSpecDialog")
        }

        // Rotate Canvas
        findViewById<View>(R.id.btnRotateCanvas).setOnClickListener {
            isLandscape = !isLandscape
            updateCanvasGeometry()
            showToast(if (isLandscape) "已切换为横向模式" else "已切换为纵向模式")
        }

        // BLE Device Scanner Dialog
        findViewById<View>(R.id.bleStatusContainer).setOnClickListener {
            DeviceScanDialog().show(supportFragmentManager, "DeviceScanDialog")
        }

        // Undo / Redo
        btnUndo.setOnClickListener { canvasView.undo() }
        btnRedo.setOnClickListener { canvasView.redo() }
        canvasView.onHistoryChanged = { canUndo, canRedo ->
            btnUndo.isEnabled = canUndo
            btnRedo.isEnabled = canRedo
        }

        // Clear Canvas
        findViewById<View>(R.id.btnClear).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("清空画板")
                .setMessage("确定清空当前画板上的所有元素吗？")
                .setPositiveButton("清空") { _, _ ->
                    canvasView.clearCanvas()
                    showToast("画板已清空")
                }
                .setNegativeButton("取消", null)
                .show()
        }

        // Add Text
        findViewById<View>(R.id.btnAddText).setOnClickListener {
            TextEditDialog().apply {
                onConfirm = { textElem ->
                    canvasView.addElement(textElem)
                }
            }.show(supportFragmentManager, "TextEditDialog")
        }

        // Add Barcode / QR Code
        findViewById<View>(R.id.btnAddBarcode).setOnClickListener {
            BarcodeDialog().apply {
                onConfirm = { barcodeElem ->
                    canvasView.addElement(barcodeElem)
                }
            }.show(supportFragmentManager, "BarcodeDialog")
        }

        // Templates Menu
        findViewById<View>(R.id.btnTemplates).setOnClickListener {
            showTemplatesMenu()
        }

        // --- Text Configuration Bar Listeners ---
        btnConfigEditText.setOnClickListener {
            val sel = canvasView.getSelectedElement() as? TextElement ?: return@setOnClickListener
            TextEditDialog().apply {
                textElement = sel
                onConfirm = { updated ->
                    sel.text = updated.text
                    sel.textSize = updated.textSize
                    sel.isBold = updated.isBold
                    sel.isCentered = updated.isCentered
                    canvasView.notifyElementModified()
                    updateTextConfigUi(sel)
                }
            }.show(supportFragmentManager, "TextEditDialog")
        }

        btnConfigSizeMinus.setOnClickListener {
            val sel = canvasView.getSelectedElement() as? TextElement ?: return@setOnClickListener
            val newSize = (sel.textSize - 2f).coerceAtLeast(8f)
            sel.textSize = newSize
            updateTextConfigUi(sel)
            canvasView.notifyElementModified()
        }

        btnConfigSizePlus.setOnClickListener {
            val sel = canvasView.getSelectedElement() as? TextElement ?: return@setOnClickListener
            val newSize = (sel.textSize + 2f).coerceAtMost(36f)
            sel.textSize = newSize
            updateTextConfigUi(sel)
            canvasView.notifyElementModified()
        }

        btnConfigBold.setOnClickListener {
            val sel = canvasView.getSelectedElement() as? TextElement ?: return@setOnClickListener
            sel.isBold = !sel.isBold
            updateTextConfigUi(sel)
            canvasView.notifyElementModified()
        }

        btnConfigAlign.setOnClickListener {
            val sel = canvasView.getSelectedElement() as? TextElement ?: return@setOnClickListener
            sel.isCentered = !sel.isCentered
            if (sel.isCentered) {
                sel.x = canvasView.canvasDotWidth / 2f
            } else {
                sel.x = 10f
            }
            updateTextConfigUi(sel)
            canvasView.notifyElementModified()
        }

        btnConfigDelete.setOnClickListener {
            canvasView.removeSelected()
        }

        btnConfigDone.setOnClickListener {
            canvasView.deselectAll()
        }

        // Execute Print
        btnPrint.setOnClickListener {
            val state = bleManager.connectionState.value
            if (state !is BleConnectionState.Connected) {
                showToast("请先连接得力 Q1A 打印机")
                DeviceScanDialog().show(supportFragmentManager, "DeviceScanDialog")
                return@setOnClickListener
            }

            val bmp = canvasView.exportToBitmap()
            PrintDialog().apply {
                previewBitmap = bmp
                isLandscape = this@MainActivity.isLandscape
            }.show(supportFragmentManager, "PrintDialog")
        }
    }

    private fun showTemplatesMenu() {
        val options = arrayOf(
            "🔩 银色内六角螺丝 (M3x6)",
            "⚡ 100Ω电阻 (1206)"
        )
        AlertDialog.Builder(this)
            .setTitle("选择标签模版")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> applyTemplate { w, h -> LabelTemplates.applyScrewTemplate(w, h) }
                    1 -> applyTemplate { w, h -> LabelTemplates.applyResistorTemplate(w, h) }
                }
                showToast("模版应用成功")
            }
            .show()
    }

    private fun applyTemplate(builder: (w: Float, h: Float) -> List<CanvasElement>) {
        val w = canvasView.canvasDotWidth.toFloat()
        val h = canvasView.canvasDotHeight.toFloat()
        val elements = builder(w, h)
        canvasView.setElements(elements)
    }

    private fun observeBleState() {
        lifecycleScope.launch {
            bleManager.connectionState.collectLatest { state ->
                when (state) {
                    is BleConnectionState.Connected -> {
                        tvBleStatus.text = state.deviceName
                        statusIndicator.setBackgroundResource(R.drawable.bg_dot_connected)
                    }
                    is BleConnectionState.Connecting -> {
                        tvBleStatus.text = "正在连接..."
                        statusIndicator.setBackgroundResource(R.drawable.bg_dot_connecting)
                    }
                    is BleConnectionState.Disconnected -> {
                        tvBleStatus.text = "未连接设备"
                        statusIndicator.setBackgroundResource(R.drawable.bg_dot_disconnected)
                    }
                    is BleConnectionState.Printing -> {
                        tvBleStatus.text = state.status
                        statusIndicator.setBackgroundResource(R.drawable.bg_dot_connecting)
                    }
                    is BleConnectionState.Error -> {
                        tvBleStatus.text = "连接异常"
                        statusIndicator.setBackgroundResource(R.drawable.bg_dot_disconnected)
                        showToast(state.message)
                    }
                }
            }
        }
    }

    private fun showToast(msg: String) {
        Snackbar.make(findViewById(android.R.id.content), msg, Snackbar.LENGTH_SHORT).show()
    }
}
