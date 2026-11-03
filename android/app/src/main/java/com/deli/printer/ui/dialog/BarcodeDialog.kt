package com.deli.printer.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.deli.printer.R
import com.deli.printer.barcode.BarcodeEncoder
import com.deli.printer.model.BarcodeElement
import com.deli.printer.model.BarcodeType
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class BarcodeDialog : BottomSheetDialogFragment() {

    var onConfirm: ((BarcodeElement) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_barcode, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etContent: EditText = view.findViewById(R.id.etBarcodeContent)
        val rgType: RadioGroup = view.findViewById(R.id.rgBarcodeType)
        val btnGenerate: Button = view.findViewById(R.id.btnGenerateBarcode)

        btnGenerate.setOnClickListener {
            val content = etContent.text.toString().trim()
            if (content.isEmpty()) {
                Toast.makeText(requireContext(), "请输入内容", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val isQr = rgType.checkedRadioButtonId == R.id.rbTypeQr
            if (isQr) {
                val bmp = BarcodeEncoder.generateQrCode(content, 64)
                if (bmp != null) {
                    onConfirm?.invoke(
                        BarcodeElement(
                            x = 20f, y = 20f,
                            content = content,
                            type = BarcodeType.QR_CODE,
                            width = 64f, height = 64f,
                            bitmap = bmp
                        )
                    )
                    dismiss()
                } else {
                    Toast.makeText(requireContext(), "生成二维码失败", Toast.LENGTH_SHORT).show()
                }
            } else {
                val bmp = BarcodeEncoder.generateCode128(content, 80, 40)
                if (bmp != null) {
                    onConfirm?.invoke(
                        BarcodeElement(
                            x = 20f, y = 20f,
                            content = content,
                            type = BarcodeType.CODE_128,
                            width = 80f, height = 40f,
                            bitmap = bmp
                        )
                    )
                    dismiss()
                } else {
                    Toast.makeText(requireContext(), "生成条形码失败 (仅支持标准英数字符)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
