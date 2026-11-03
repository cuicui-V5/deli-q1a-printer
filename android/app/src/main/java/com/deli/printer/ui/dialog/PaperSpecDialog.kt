package com.deli.printer.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import com.deli.printer.R
import com.deli.printer.model.PaperSpec
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class PaperSpecDialog : BaseBottomSheetDialogFragment() {

    var onSpecSelected: ((PaperSpec) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_paper_spec, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val listView: ListView = view.findViewById(R.id.lvPaperSpecs)
        val specs = PaperSpec.ALL_SPECS
        val names = specs.map { it.name }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, names)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            onSpecSelected?.invoke(specs[position])
            dismiss()
        }
    }
}
