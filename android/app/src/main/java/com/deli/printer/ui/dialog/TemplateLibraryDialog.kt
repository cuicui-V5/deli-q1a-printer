package com.deli.printer.ui.dialog

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deli.printer.R
import com.deli.printer.model.CanvasElement
import com.deli.printer.template.TemplateItem
import com.deli.printer.template.TemplateManager

class TemplateLibraryDialog : BaseBottomSheetDialogFragment() {

    var currentCanvasElements: List<CanvasElement> = emptyList()
    var canvasWidth: Float = 240f
    var canvasHeight: Float = 96f
    var onTemplateSelected: ((List<CanvasElement>) -> Unit)? = null

    private lateinit var rvTemplates: RecyclerView
    private lateinit var adapter: TemplateAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_template_library, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvTemplates = view.findViewById(R.id.rvTemplates)
        rvTemplates.layoutManager = LinearLayoutManager(requireContext())

        val btnSaveCurrent: View = view.findViewById(R.id.btnSaveCurrentAsTemplate)
        btnSaveCurrent.setOnClickListener {
            showSaveTemplateDialog()
        }

        loadTemplates()
    }

    private fun loadTemplates() {
        val list = TemplateManager.getAllTemplates(requireContext(), canvasWidth, canvasHeight)
        adapter = TemplateAdapter(list, { selected ->
            onTemplateSelected?.invoke(selected.elements)
            dismiss()
        }, { toDelete ->
            TemplateManager.deleteUserTemplate(requireContext(), toDelete.id)
            Toast.makeText(requireContext(), "已删除模版: ${toDelete.name}", Toast.LENGTH_SHORT).show()
            loadTemplates()
        })
        rvTemplates.adapter = adapter
    }

    private fun showSaveTemplateDialog() {
        val input = EditText(requireContext()).apply {
            hint = "输入模版名称 (如: 桌面理线标)"
            setSingleLine()
            setPadding(40, 30, 40, 30)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("保存当前画布为新模版")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { "我的模版" }
                TemplateManager.saveUserTemplate(requireContext(), name, currentCanvasElements)
                Toast.makeText(requireContext(), "已存为新模版: $name", Toast.LENGTH_SHORT).show()
                loadTemplates()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private inner class TemplateAdapter(
        private val items: List<TemplateItem>,
        private val onItemClick: (TemplateItem) -> Unit,
        private val onDeleteClick: (TemplateItem) -> Unit
    ) : RecyclerView.Adapter<TemplateAdapter.VH>() {

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val ivThumbnail: ImageView = itemView.findViewById(R.id.ivTemplateThumbnail)
            val tvName: TextView = itemView.findViewById(R.id.tvTemplateName)
            val tvTag: TextView = itemView.findViewById(R.id.tvTemplateTag)
            val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteTemplate)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_template_card, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvName.text = item.name
            holder.tvTag.text = if (item.isCustom) "我的自定义模版" else "官方内置模版"
            holder.btnDelete.visibility = if (item.isCustom) View.VISIBLE else View.GONE

            // Render live thumbnail
            val thumb = TemplateManager.renderThumbnail(holder.itemView.context, item.elements)
            holder.ivThumbnail.setImageBitmap(thumb)

            holder.itemView.setOnClickListener {
                onItemClick(item)
            }

            holder.btnDelete.setOnClickListener {
                AlertDialog.Builder(holder.itemView.context)
                    .setTitle("删除模版")
                    .setMessage("确定删除自定义模版【${item.name}】吗？")
                    .setPositiveButton("删除") { _, _ ->
                        onDeleteClick(item)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
