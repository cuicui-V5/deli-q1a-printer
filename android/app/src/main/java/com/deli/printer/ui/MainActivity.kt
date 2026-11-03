package com.deli.printer.ui

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
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
import java.io.File

class MainActivity : AppCompatActivity() {

    private val bleManager get() = DeliApp.instance.bleManager
    private lateinit var prefs: SharedPreferences

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
    private lateinit var btnInspectorBold: ImageButton
    private lateinit var btnInspectorAlignLeft: ImageButton
    private lateinit var btnInspectorAlignCenter: ImageButton
    private lateinit var btnInspectorDelete: ImageButton

    private var isUpdatingInspector: Boolean = false
    private var currentDensity: Int = 2 // Default: 2 (高浓度 / 深度)
    private var currentSpec: PaperSpec = PaperSpec.SPEC_15_30
    private var isLandscape: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("deli_printer_prefs", Context.MODE_PRIVATE)
        currentDensity = prefs.getInt("key_density", 2) // Default to High (2)
        val savedSpecName = prefs.getString("key_spec", PaperSpec.SPEC_15_30.name)
        currentSpec = PaperSpec.ALL_SPECS.find { it.name == savedSpecName } ?: PaperSpec.SPEC_15_30

        initViews()
        setupListeners()
        observeBleState()

        // Restore last canvas state or fallback to default
        restoreLastCanvasState()

        // Check BLE Permissions & Auto Connect
        if (!BlePermissionHelper.hasPermissions(this)) {
            BlePermissionHelper.requestPermissions(this)
        } else {
            autoConnectPrinter()
        }
    }

    override fun onPause() {
        super.onPause()
        saveCanvasState()
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

    private fun saveCanvasState() {
        try {
            val json = CanvasElementSerializer.toJson(canvasView.getAllElements())
            File(filesDir, "last_canvas.json").writeText(json)
        } catch (_: Exception) {}
    }

    private fun restoreLastCanvasState() {
        val file = File(filesDir, "last_canvas.json")
        if (file.exists()) {
            try {
                val json = file.readText()
                val restored = CanvasElementSerializer.fromJson(json)
                if (restored.isNotEmpty()) {
                    canvasView.setElements(restored)
                    return
                }
            } catch (_: Exception) {}
        }
        // Fallback default
        applyTemplate { w, h -> LabelTemplates.createDefaultExample(w, h) }
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
        val textPrimary = ContextCompat.getColor(this, R.color.text_primary)
        val white = ContextCompat.getColor(this, R.color.white)

        if (elem.isBold) {
            btnInspectorBold.setBackgroundResource(R.drawable.bg_btn_selected)
            btnInspectorBold.setColorFilter(white)
        } else {
            btnInspectorBold.setBackgroundResource(R.drawable.bg_btn_secondary)
            btnInspectorBold.setColorFilter(textPrimary)
        }

        if (!elem.isCentered) {
            btnInspectorAlignLeft.setBackgroundResource(R.drawable.bg_btn_selected)
            btnInspectorAlignLeft.setColorFilter(white)

            btnInspectorAlignCenter.setBackgroundResource(R.drawable.bg_btn_secondary)
            btnInspectorAlignCenter.setColorFilter(textPrimary)
        } else {
            btnInspectorAlignCenter.setBackgroundResource(R.drawable.bg_btn_selected)
            btnInspectorAlignCenter.setColorFilter(white)

            btnInspectorAlignLeft.setBackgroundResource(R.drawable.bg_btn_secondary)
            btnInspectorAlignLeft.setColorFilter(textPrimary)
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

        canvasView.onElementModified = {
            saveCanvasState()
        }

        // Direct Text Input Live Editing
        etInspectorText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isUpdatingInspector) return
                val sel = canvasView.getSelectedElement() as? TextElement ?: return
                sel.text = s?.toString() ?: ""
                canvasView.invalidate()
                saveCanvasState()
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
                saveCanvasState()
            }
        })

        // Bold Toggle
        btnInspectorBold.setOnClickListener {
            val sel = canvasView.getSelectedElement() as? TextElement ?: return@setOnClickListener
            sel.isBold = !sel.isBold
            updateInspectorStyleButtons(sel)
            canvasView.notifyElementModified()
            saveCanvasState()
        }

        // Align Left
        btnInspectorAlignLeft.setOnClickListener {
            val sel = canvasView.getSelectedElement() as? TextElement ?: return@setOnClickListener
            sel.isCentered = false
            sel.x = 10f
            updateInspectorStyleButtons(sel)
            canvasView.notifyElementModified()
            saveCanvasState()
        }

        // Align Center
        btnInspectorAlignCenter.setOnClickListener {
            val sel = canvasView.getSelectedElement() as? TextElement ?: return@setOnClickListener
            sel.isCentered = true
            sel.x = canvasView.canvasDotWidth / 2f
            updateInspectorStyleButtons(sel)
            canvasView.notifyElementModified()
            saveCanvasState()
        }

        // Delete Button
        btnInspectorDelete.setOnClickListener {
            hideSoftKeyboard()
            canvasView.removeSelected()
            saveCanvasState()
        }

        // Done Button
        btnInspectorDone.setOnClickListener {
            hideSoftKeyboard()
            canvasView.deselectAll()
            saveCanvasState()
        }

        // Settings Dialog
        findViewById<View>(R.id.btnSettings).setOnClickListener {
            SettingsDialog().apply {
                currentDensity = this@MainActivity.currentDensity
                currentSpec = this@MainActivity.currentSpec
                onSettingsSaved = { newDensity, newSpec ->
                    this@MainActivity.currentDensity = newDensity
                    this@MainActivity.currentSpec = newSpec
                    prefs.edit()
                        .putInt("key_density", newDensity)
                        .putString("key_spec", newSpec.name)
                        .apply()
                    updateCanvasGeometry()
                    val densityName = when (newDensity) {
                        0 -> "浅度"
                        1 -> "标准"
                        else -> "高浓度"
                    }
                    showToast("设置已保存: 浓度[$densityName], 规格[${newSpec.name}]")
                }
                onScanRequested = {
                    DeviceScanDialog().show(supportFragmentManager, "DeviceScanDialog")
                }
            }.show(supportFragmentManager, "SettingsDialog")
        }

        // Spec Container Click -> Quick switch spec
        findViewById<View>(R.id.specContainer).setOnClickListener {
            PaperSpecDialog().apply {
                onSpecSelected = { spec ->
                    currentSpec = spec
                    prefs.edit().putString("key_spec", spec.name).apply()
                    updateCanvasGeometry()
                    showToast("已切换耗材规格: ${spec.name}")
                }
            }.show(supportFragmentManager, "PaperSpecDialog")
        }

        // Rotate Canvas
        findViewById<View>(R.id.btnRotateCanvas).setOnClickListener {
            isLandscape = !isLandscape
            updateCanvasGeometry()
            showToast(if (isLandscape) "已切换为横向" else "已切换为纵向")
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
            saveCanvasState()
        }

        // Clear Canvas
        findViewById<View>(R.id.btnClear).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("清空画板")
                .setMessage("确定清空当前画板上的所有元素吗？")
                .setPositiveButton("清空") { _, _ ->
                    canvasView.clearCanvas()
                    saveCanvasState()
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
            saveCanvasState()
            etInspectorText.requestFocus()
        }

        // Add Barcode / QR Code
        findViewById<View>(R.id.btnAddBarcode).setOnClickListener {
            BarcodeDialog().apply {
                onConfirm = { barcodeElem ->
                    canvasView.addElement(barcodeElem)
                    saveCanvasState()
                }
            }.show(supportFragmentManager, "BarcodeDialog")
        }

        // Visual Templates Dialog
        findViewById<View>(R.id.btnTemplates).setOnClickListener {
            TemplateLibraryDialog().apply {
                currentCanvasElements = canvasView.getAllElements()
                canvasWidth = canvasView.canvasDotWidth.toFloat()
                canvasHeight = canvasView.canvasDotHeight.toFloat()
                onTemplateSelected = { elements ->
                    canvasView.setElements(elements)
                    saveCanvasState()
                    showToast("模版载入成功")
                }
            }.show(supportFragmentManager, "TemplateLibraryDialog")
        }

        // Execute Direct Print
        btnPrint.setOnClickListener {
            val state = bleManager.connectionState.value
            if (state !is BleConnectionState.Connected) {
                showToast("打印机未连接，正在连接默认设备...")
                autoConnectPrinter()
                DeviceScanDialog().show(supportFragmentManager, "DeviceScanDialog")
                return@setOnClickListener
            }

            val bmp = canvasView.exportToBitmap()
            showToast("正在发送至得力 Q1A 打印...")

            lifecycleScope.launch {
                val success = bleManager.printBitmap(
                    bitmap = bmp,
                    density = currentDensity, // Default: High (2)
                    copies = 1,
                    isLandscape = this@MainActivity.isLandscape
                )
                if (success) {
                    showToast("打印完成！")
                }
            }
        }
    }

    private fun hideSoftKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(etInspectorText.windowToken, 0)
    }

    private fun applyTemplate(builder: (w: Float, h: Float) -> List<CanvasElement>) {
        val w = canvasView.canvasDotWidth.toFloat()
        val h = canvasView.canvasDotHeight.toFloat()
        val elements = builder(w, h)
        canvasView.setElements(elements)
        saveCanvasState()
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
                        tvBleStatus.text = "连接中..."
                        statusIndicator.setBackgroundResource(R.drawable.bg_dot_connecting)
                    }
                    is BleConnectionState.Disconnected -> {
                        tvBleStatus.text = "未连接"
                        statusIndicator.setBackgroundResource(R.drawable.bg_dot_disconnected)
                    }
                    is BleConnectionState.Printing -> {
                        tvBleStatus.text = state.status
                        statusIndicator.setBackgroundResource(R.drawable.bg_dot_connecting)
                    }
                    is BleConnectionState.Error -> {
                        tvBleStatus.text = "异常"
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
