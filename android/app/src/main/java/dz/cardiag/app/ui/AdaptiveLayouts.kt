package dz.cardiag.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Adaptive sizing for phones, small tablets and large tablets/landscape.
 * The app uses the resulting window-size class to switch between compact,
 * medium and expanded layouts.
 */
@Immutable
data class CarDiagWindowSize(
    val widthClass: WidthClass,
    val heightClass: HeightClass
) {
    enum class WidthClass { Compact, Medium, Expanded }
    enum class HeightClass { Compact, Medium, Expanded }

    val isCompact: Boolean get() = widthClass == WidthClass.Compact
    val isMedium: Boolean get() = widthClass == WidthClass.Medium
    val isExpanded: Boolean get() = widthClass == WidthClass.Expanded
}

private const val CompactThresholdDp = 600
private const val ExpandedThresholdDp = 840

@Composable
fun rememberCarDiagWindowSize(): CarDiagWindowSize {
    val config = LocalConfiguration.current
    val width = config.screenWidthDp
    val height = config.screenHeightDp
    val widthClass = when {
        width < CompactThresholdDp -> CarDiagWindowSize.WidthClass.Compact
        width < ExpandedThresholdDp -> CarDiagWindowSize.WidthClass.Medium
        else -> CarDiagWindowSize.WidthClass.Expanded
    }
    val heightClass = when {
        height < CompactThresholdDp -> CarDiagWindowSize.HeightClass.Compact
        height < ExpandedThresholdDp -> CarDiagWindowSize.HeightClass.Medium
        else -> CarDiagWindowSize.HeightClass.Expanded
    }
    return CarDiagWindowSize(widthClass, heightClass)
}
