package com.deli.printer.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import com.deli.printer.R
import com.deli.printer.model.PaperSpec
import com.deli.printer.protocol.DeliProtocol

class SettingsDialog : BaseBottomSheetDialogFragment() {

    var currentDensity: Int = 1 // Default: 1 (Normal / 标准推荐)
    var currentSpec: PaperSpec = PaperSpec.SPEC_15_30
    var onSettingsSaved: ((density: Int, spec: PaperSpec) -> Unit)? = null
    var onScanRequested: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rgDensity: RadioGroup = view.findViewById(R.id.rgSettingsDensity)
        val rgPaper: RadioGroup = view.findViewById(R.id.rgSettingsPaper)
        val btnScan: View = view.findViewById(R.id.btnSettingsScan)
        val btnSave: View = view.findViewById(R.id.btnSaveSettings)
        val tvMac: TextView = view.findViewById(R.id.tvSettingsDeviceMac)

        tvMac.text = "默认绑定打印机: ${DeliProtocol.DEFAULT_MAC}"

        // Set initial density selection
        when (currentDensity) {
            0 -> rgDensity.check(R.id.rbSettingsDensityLight)
            1 -> rgDensity.check(R.id.rbSettingsDensityNormal)
            else -> rgDensity.check(R.id.rbSettingsDensityDark)
        }

        // Set initial paper spec selection
        when (currentSpec) {
            PaperSpec.SPEC_12_40 -> rgPaper.check(R.id.rbPaper12_40)
            PaperSpec.SPEC_15_40 -> rgPaper.check(R.id.rbPaper15_40)
            PaperSpec.SPEC_15_50 -> rgPaper.check(R.id.rbPaper15_50)
            else -> rgPaper.check(R.id.rbPaper15_30)
        }

        btnScan.setOnClickListener {
            dismiss()
            onScanRequested?.invoke()
        }

        btnSave.setOnClickListener {
            val selectedDensity = when (rgDensity.checkedRadioButtonId) {
                R.id.rbSettingsDensityLight -> 0
                R.id.rbSettingsDensityNormal -> 1
                else -> 2 // 深度 (高浓度)
            }

            val selectedSpec = when (rgPaper.checkedRadioButtonId) {
                R.id.rbPaper12_40 -> PaperSpec.SPEC_12_40
                R.id.rbPaper15_40 -> PaperSpec.SPEC_15_40
                R.id.rbPaper15_50 -> PaperSpec.SPEC_15_50
                else -> PaperSpec.SPEC_15_30
            }

            onSettingsSaved?.invoke(selectedDensity, selectedSpec)
            dismiss()
        }
    }
}
