package com.deli.printer.ui.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.deli.printer.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

open class BaseBottomSheetDialogFragment : BottomSheetDialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.window?.let { window ->
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog ?: return
        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) as? FrameLayout
        if (bottomSheet != null) {
            val behavior = BottomSheetBehavior.from(bottomSheet)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
            behavior.isFitToContents = true
            bottomSheet.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Ensure bottom navigation bar doesn't cover dialog bottom bar
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val bottomBar = view.findViewById<View>(R.id.dialogBottomBar)
            if (bottomBar != null) {
                bottomBar.setPadding(
                    bottomBar.paddingLeft,
                    bottomBar.paddingTop,
                    bottomBar.paddingRight,
                    navInsets.bottom + 24
                )
            }
            insets
        }
    }
}
