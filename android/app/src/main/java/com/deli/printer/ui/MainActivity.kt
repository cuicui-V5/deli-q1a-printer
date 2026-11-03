package com.deli.printer.ui

import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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

    // Bottom Panels
    private lateinit var mainToolBar: View
    private lateinit var textInspectorPanel: View

    // Text Inspector Controls
    private lateinit var btnInspectorDone: Button
    private lateinit var etInspectorText: EditText
    private lateinit var sbInspectorTextSize: SeekBar
    private lateinit var tvInspectorSizeValue: TextView
    private lateinit var btnInspectorBold: Button
    private lateinit var btnInspectorAlignLeft: Button
    private lateinit var btnInspectorAlignCenter: Button
    private lateinit var btnInspectorDelete: Button

    private var isUpdatingInspector: Boolean = false
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
        } catch (_: Exception) {
            // Silently ignore
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
        textInspectorPanel = findViewById(R.id.textInspectorPanel)

        // Inspector Controls
        btnInspectorDone = findViewById(R.id.btnInspectorDone)
        etInspectorText = findViewById(R.id.etInspectorText)
        sbInspectorTextSize = findViewById(R.id.sbInspectorTextSize)
        tvInspectorSizeValue = findViewById(R.id.tvInspectorSizeValue)
        btnInspectorBold = findViewById(R.id.btnInspectorBold)
        btnInspectorAlignLeft = findViewById(R.id.btnInspectorAlignLeft)
        btnInspectorAlignCenter = findViewById(R.id.btnInspectorAlignCenter)
        btnInspectorDelete = findViewById(R.id.btnInspectorDelete)

        updateCanvasGeometry()
    }

    private fun updateCanvasGeometry() {
        val wDots = if (isLandscape) currentSpec.lengthPx else currentSpec.widthPx
        val hDots = if (isLandscape) currentSpec.widthPx else currentSpec.lengthPx

        canvasView.setCanvasDimensions(wDots, hDots)
        tvPaperSpec.text = "${currentSpec.widthMm}×${currentSpec.lengthMm} mm (${if (isLandscape) "横向" else "纵向"})"
    }

    private fun populateInspector(elem: TextElement) {
        isUpdatingInspector = true

        etInspectorText.setText(elem.text)
        etInspectorText.setSelection(elem.text.length)

        val prog = (elem.textSize.toInt() - 8).coerceIn(0, 28)
        sbInspectorTextSize.progress = prog
        tvInspectorSizeValue.text = "${elem.textSize.toInt()} pt"

        updateInspectorStyleButtons(elem)

        isUpdatingInspector = false
    }

    private fun updateInspectorStyleButtons(elem: TextElement) {
        val white = ContextCompat.getColor(this, R.color.white)
        val textPrimary = ContextCompat.getColor(this, R.color.text_primary)

        if (elem.isBold) {
            btnInspectorBold.setBackgroundResource(R.drawable.bg_btn_selected)
            btnInspectorBold.setTextColor(white)
            btnInspectorBold.compoundDrawables[0]?.setTint(white)
        } else {
            btnInspectorBold.setBackgroundResource(R.drawable.bg_btn_secondary)
            btnInspectorBold.setTextColor(textPrimary)
            btnInspectorBold.compoundDrawables[0]?.setTint(textPrimary)
        }

        if (!elem.isCentered) {
            btnInspectorAlignLeft.setBackgroundResource(R.drawable.bg_btn_selected)
            btnInspectorAlignLeft.setTextColor(white)
            btnInspectorAlignLeft.compoundDrawables[0]?.setTint(white)

            btnInspectorAlignCenter.setBackgroundResource(R.drawable.bg_btn_secondary)
            btnInspectorAlignCenter.setTextColor(textPrimary)
            btnInspectorAlignCenter.compoundDrawables[0]?.setTint(textPrimary)
        } else {
            btnInspectorAlignCenter.setBackgroundResource(R.drawable.bg_btn_selected)
            btnInspectorAlignCenter.setTextColor(white)
            btnInspectorAlignCenter.compoundDrawables[0]?.setTint(white)

            btnInspectorAlignLeft.setBackgroundResource(R.drawable.bg_btn_secondary)
            btnInspectorAlignLeft.setTextColor(textPrimary)
            btnInspectorAlignLeft.compoundDrawables[0]?.setTint(textPrimary)
        }
    }

    private fun setupListeners() {
        // Selection change listener: dynamically switch between main tools and text inspector
        canvasView.onSelectionChanged = { selected ->
            if (selected is TextElement) {
                mainToolBar.visibility = View.GONE
                textInspectorPanel.visibility = View.VISIBLE
                populateInspector(selected)
            } else {
                hideSoftKeyboard()
                textInspectorPanel.visibility = View.GONE
                mainToolBar.visibility = View.VISIBLE
            }
        }

        // Real-time canvas handle drag scaling
        canvasView.onElementSizeChanged = { newSize ->
            isUpdatingInspector = true
            val prog = (newSize.toInt() - 8).coerceIn(0, 28)
            sbInspectorTextSize.progress = prog
            tvInspectorSizeValue.text = "${newSize.toInt()} pt"
            isUpdatingInspector = false
        }

        // Direct Text Input Live Editing
        etInspectorText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isUpdatingInspector) return
                val sel = canvasView.getSelectedElement() as? TextElement ?: return
                sel.text = s?.toString() ?: ""
                canvasView.invalidate()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Font Size Slider
        sbInspectorTextSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || isUpdatingInspector) return
                val sel = canvasView.getSelectedElement() as? TextElement ?: return
                val newSize = (progress + 8).toFloat()
                sel.textSize = newSize
                tvInspectorSizeValue.text = "${newSize.toInt()} pt"
                canvasView.invalidate()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                canvasView.notifyElementModified()
            }
        })

        // Bold Toggle
        btnInspectorBold.setOnClickListener {
            val sel = canvasView.getSelectedElement() as? TextElement ?: return@setOnClickListener
            sel.isBold = !sel.isBold
            updateInspectorStyleButtons(sel)
            canvasView.notifyElementModified()
        }

        // Align Left
        btnInspectorAlignLeft.setOnClickListener {
            val sel = canvasView.getSelectedElement() as? TextElement ?: return@setOnClickListener
            sel.isCentered = false
            sel.x = 10f
            updateInspectorStyleButtons(sel)
            canvasView.notifyElementModified()
        }

        // Align Center
        btnInspectorAlignCenter.setOnClickListener {
            val sel = canvasView.getSelectedElement() as? TextElement ?: return@setOnClickListener
            sel.isCentered = true
            sel.x = canvasView.canvasDotWidth / 2f
            updateInspectorStyleButtons(sel)
            canvasView.notifyElementModified()
        }

        // Delete Button
        btnInspectorDelete.setOnClickListener {
            hideSoftKeyboard()
            canvasView.removeSelected()
        }

        // Done Button
        btnInspectorDone.setOnClickListener {
            hideSoftKeyboard()
            canvasView.deselectAll()
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

        // Add Text: Directly add and auto-select to open inspector
        findViewById<View>(R.id.btnAddText).setOnClickListener {
            val w = canvasView.canvasDotWidth.toFloat()
            val h = canvasView.canvasDotHeight.toFloat()
            val newElem = TextElement(
                x = w / 2f,
                y = (h - 22f) / 2f,
                text = "新建文本",
                textSize = 22f,
                isBold = true,
                isCentered = true
            )
            canvasView.addElement(newElem)
            etInspectorText.requestFocus()
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

    private fun hideSoftKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(etInspectorText.windowToken, 0)
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
