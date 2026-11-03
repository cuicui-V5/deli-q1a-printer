package com.deli.printer.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.deli.printer.R
import com.deli.printer.model.TextElement
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class TextEditDialog : BaseBottomSheetDialogFragment() {

    var textElement: TextElement? = null
    var onConfirm: ((TextElement) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_text_edit, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etText: EditText = view.findViewById(R.id.etTextInput)
        val sbSize: SeekBar = view.findViewById(R.id.sbTextSize)
        val tvSizeValue: TextView = view.findViewById(R.id.tvTextSizeValue)
        val cbBold: CheckBox = view.findViewById(R.id.cbBold)
        val cbCenter: CheckBox = view.findViewById(R.id.cbCenter)
        val btnSave: Button = view.findViewById(R.id.btnSaveText)

        val elem = textElement
        if (elem != null) {
            etText.setText(elem.text)
            sbSize.progress = elem.textSize.toInt()
            tvSizeValue.text = "${elem.textSize.toInt()} pt"
            cbBold.isChecked = elem.isBold
            cbCenter.isChecked = elem.isCentered
        } else {
            etText.setText("文本内容")
            sbSize.progress = 16
            tvSizeValue.text = "16 pt"
        }

        sbSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvSizeValue.text = "${progress.coerceAtLeast(8)} pt"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnSave.setOnClickListener {
            val content = etText.text.toString().trim()
            if (content.isEmpty()) {
                Toast.makeText(requireContext(), "请输入文本内容", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val size = sbSize.progress.coerceAtLeast(8).toFloat()
            val bold = cbBold.isChecked
            val center = cbCenter.isChecked

            val result = elem?.apply {
                this.text = content
                this.textSize = size
                this.isBold = bold
                this.isCentered = center
            } ?: TextElement(
                x = 20f,
                y = 20f,
                text = content,
                textSize = size,
                isBold = bold,
                isCentered = center
            )

            onConfirm?.invoke(result)
            dismiss()
        }
    }
}
