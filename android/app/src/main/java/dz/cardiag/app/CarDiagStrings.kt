package dz.cardiag.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * True when the current layout direction is Right-To-Left (Arabic).
 * Use this to make Composable decisions that cannot be expressed through
 * `stringResource` alone, e.g. switching animations or visual icons.
 */
@Composable
fun isRtl(): Boolean = LocalLayoutDirection.current == LayoutDirection.Rtl
