package com.deli.printer.ui.dialog

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.deli.printer.DeliApp
import com.deli.printer.R
import com.deli.printer.ble.BleConnectionState
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PrintDialog : BottomSheetDialogFragment() {

    private val bleManager get() = DeliApp.instance.bleManager

    var previewBitmap: Bitmap? = null
    var isLandscape: Boolean = true
    var onPrintSuccess: (() -> Unit)? = null

    private var copies = 1
    private var density = 2 // 2: Dark (浓黑清晰默认)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_print, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ivPreview: ImageView = view.findViewById(R.id.ivPrintPreview)
        val tvCopies: TextView = view.findViewById(R.id.tvCopies)
        val btnMinus: Button = view.findViewById(R.id.btnMinusCopies)
        val btnPlus: Button = view.findViewById(R.id.btnPlusCopies)
        val rgDensity: RadioGroup = view.findViewById(R.id.rgDensity)
        val btnExecute: Button = view.findViewById(R.id.btnExecutePrint)
        val printProgress: ProgressBar = view.findViewById(R.id.printProgress)
        val tvPrintStatus: TextView = view.findViewById(R.id.tvPrintProgressStatus)

        previewBitmap?.let {
            ivPreview.setImageBitmap(it)
        }

        tvCopies.text = "$copies 份"
        btnMinus.setOnClickListener {
            if (copies > 1) {
                copies--
                tvCopies.text = "$copies 份"
            }
        }
        btnPlus.setOnClickListener {
            if (copies < 20) {
                copies++
                tvCopies.text = "$copies 份"
            }
        }

        rgDensity.setOnCheckedChangeListener { _, checkedId ->
            density = when (checkedId) {
                R.id.rbDensityLight -> 0
                R.id.rbDensityDark -> 2
                else -> 1
            }
        }

        btnExecute.setOnClickListener {
            val bmp = previewBitmap ?: return@setOnClickListener
            btnExecute.isEnabled = false
            printProgress.visibility = View.VISIBLE
            tvPrintStatus.visibility = View.VISIBLE

            lifecycleScope.launch {
                val success = bleManager.printBitmap(
                    bitmap = bmp,
                    density = density,
                    copies = copies,
                    isLandscape = isLandscape
                )
                if (success) {
                    Toast.makeText(requireContext(), "打印成功完成！", Toast.LENGTH_SHORT).show()
                    onPrintSuccess?.invoke()
                    dismiss()
                } else {
                    btnExecute.isEnabled = true
                    printProgress.visibility = View.INVISIBLE
                }
            }
        }

        lifecycleScope.launch {
            bleManager.connectionState.collectLatest { state ->
                when (state) {
                    is BleConnectionState.Printing -> {
                        printProgress.progress = state.progressPercent
                        tvPrintStatus.text = state.status
                    }
                    is BleConnectionState.Error -> {
                        tvPrintStatus.text = "错误: ${state.message}"
                        btnExecute.isEnabled = true
                        printProgress.visibility = View.INVISIBLE
                    }
                    else -> {}
                }
            }
        }
    }
}
