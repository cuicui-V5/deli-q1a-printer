package com.deli.printer.ui

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.deli.printer.DeliApp
import com.deli.printer.R
import com.deli.printer.ble.BleConnectionState
import com.deli.printer.ble.BlePermissionHelper
import com.deli.printer.model.*
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

    private var currentSpec: PaperSpec = PaperSpec.SPEC_15_30
    private var isLandscape: Boolean = true

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        val maxDim = 80f
                        val scale = minOf(maxDim / bmp.width, maxDim / bmp.height)
                        val w = (bmp.width * scale).coerceAtLeast(20f)
                        val h = (bmp.height * scale).coerceAtLeast(20f)

                        canvasView.addElement(
                            ImageElement(
                                x = 20f, y = 20f,
                                width = w, height = h,
                                bitmap = bmp
                            )
                        )
                        showToast("图片添加成功")
                    }
                }
            } catch (e: Exception) {
                showToast("无法加载图片: ${e.message}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        observeBleState()

        // Apply default template
        applyTemplate { w, h -> LabelTemplates.applyFoodTemplate(w, h) }

        // Check BLE Permissions
        if (!BlePermissionHelper.hasPermissions(this)) {
            BlePermissionHelper.requestPermissions(this)
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

        updateCanvasGeometry()
    }

    private fun updateCanvasGeometry() {
        val wDots = if (isLandscape) currentSpec.lengthPx else currentSpec.widthPx
        val hDots = if (isLandscape) currentSpec.widthPx else currentSpec.lengthPx

        canvasView.setCanvasDimensions(wDots, hDots)
        tvPaperSpec.text = "${currentSpec.widthMm}×${currentSpec.lengthMm} mm (${if (isLandscape) "横向" else "纵向"})"
    }

    private fun setupListeners() {
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

        // Add Shapes / Borders
        findViewById<View>(R.id.btnAddShapes).setOnClickListener {
            showShapesMenu()
        }

        // Templates Menu
        findViewById<View>(R.id.btnTemplates).setOnClickListener {
            showTemplatesMenu()
        }

        // Pick Image
        findViewById<View>(R.id.btnAddImage).setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
            }
            pickImageLauncher.launch(intent)
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

    private fun showShapesMenu() {
        val options = arrayOf("安全圆角边框", "水平分割线", "实心矩形")
        AlertDialog.Builder(this)
            .setTitle("添加图形/边框")
            .setItems(options) { _, which ->
                val w = canvasView.canvasDotWidth.toFloat()
                val h = canvasView.canvasDotHeight.toFloat()
                val mx = if (w > h) 10f else 4f
                val my = if (w > h) 4f else 10f
                when (which) {
                    0 -> canvasView.addElement(
                        ShapeElement(
                            x = mx, y = my,
                            width = w - mx * 2f, height = h - my * 2f,
                            shapeType = ShapeType.ROUNDED_BORDER,
                            strokeWidth = 2f,
                            cornerRadius = 6f
                        )
                    )
                    1 -> canvasView.addElement(
                        ShapeElement(
                            x = 16f, y = h / 2f,
                            width = w - 32f, height = 1.5f,
                            shapeType = ShapeType.DIVIDER_LINE
                        )
                    )
                    2 -> canvasView.addElement(
                        ShapeElement(
                            x = 10f, y = 10f,
                            width = 60f, height = 30f,
                            shapeType = ShapeType.RECTANGLE
                        )
                    )
                }
            }
            .show()
    }

    private fun showTemplatesMenu() {
        val options = arrayOf(
            "🍎 食品赏味保鲜标",
            "🔌 数码线缆标",
            "📦 居家整理收纳标",
            "🏷️ 特惠商超价签标"
        )
        AlertDialog.Builder(this)
            .setTitle("选择标签模版")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> applyTemplate { w, h -> LabelTemplates.applyFoodTemplate(w, h) }
                    1 -> applyTemplate { w, h -> LabelTemplates.applyCableTemplate(w, h) }
                    2 -> applyTemplate { w, h -> LabelTemplates.applyStorageTemplate(w, h) }
                    3 -> applyTemplate { w, h -> LabelTemplates.applyPriceTemplate(w, h) }
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
