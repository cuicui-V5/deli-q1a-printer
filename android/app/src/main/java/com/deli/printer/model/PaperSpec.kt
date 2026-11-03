package com.deli.printer.model

/**
 * Physical Paper Roll Specifications (物理标签耗材规格)
 */
data class PaperSpec(
    val id: String,
    val name: String,
    val widthMm: Int,
    val lengthMm: Int,
    val isGapPaper: Boolean = true
) {
    // Deli Q1A hardware printhead is physically 12mm (96 dots = 12 bytes/line)
    val widthPx: Int get() = minOf(widthMm * 8, 96)
    val lengthPx: Int get() = lengthMm * 8

    companion object {
        val SPEC_15_30 = PaperSpec("15x30", "15 × 30 mm (标准间隙纸)", 15, 30)
        val SPEC_15_40 = PaperSpec("15x40", "15 × 40 mm (长间隙纸)", 15, 40)
        val SPEC_15_50 = PaperSpec("15x50", "15 × 50 mm (加大间隙纸)", 15, 50)
        val SPEC_12_30 = PaperSpec("12x30", "12 × 30 mm (12mm规格)", 12, 30)
        val SPEC_12_40 = PaperSpec("12x40", "12 × 40 mm (12mm规格)", 12, 40)

        val ALL_SPECS = listOf(
            SPEC_15_30,
            SPEC_15_40,
            SPEC_15_50,
            SPEC_12_30,
            SPEC_12_40
        )
    }
}
