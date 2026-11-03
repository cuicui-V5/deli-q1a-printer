package com.deli.printer.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deli.printer.DeliApp
import com.deli.printer.R
import com.deli.printer.protocol.DeliProtocol
import com.deli.printer.ui.adapter.DeviceAdapter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DeviceScanDialog : BaseBottomSheetDialogFragment() {

    private lateinit var adapter: DeviceAdapter
    private val bleManager get() = DeliApp.instance.bleManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_device_scan, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvDevices: RecyclerView = view.findViewById(R.id.rvDevices)
        val progressBar: ProgressBar = view.findViewById(R.id.scanProgressBar)
        val btnRescan: Button = view.findViewById(R.id.btnRescan)
        val btnDirectDefault: Button = view.findViewById(R.id.btnDirectDefault)
        val tvScanStatus: TextView = view.findViewById(R.id.tvScanStatus)

        adapter = DeviceAdapter { item ->
            bleManager.connect(item.device)
            dismiss()
        }

        rvDevices.layoutManager = LinearLayoutManager(requireContext())
        rvDevices.adapter = adapter

        btnDirectDefault.setOnClickListener {
            bleManager.connectMac(DeliProtocol.DEFAULT_MAC)
            dismiss()
        }

        btnRescan.setOnClickListener {
            bleManager.startScan()
        }

        lifecycleScope.launch {
            bleManager.isScanning.collectLatest { scanning ->
                progressBar.visibility = if (scanning) View.VISIBLE else View.INVISIBLE
                btnRescan.isEnabled = !scanning
                tvScanStatus.text = if (scanning) "正在搜索附近的蓝牙打印机..." else "扫描已结束"
            }
        }

        lifecycleScope.launch {
            bleManager.scannedDevices.collectLatest { devices ->
                adapter.setDevices(devices)
            }
        }

        bleManager.startScan()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bleManager.stopScan()
    }
}
