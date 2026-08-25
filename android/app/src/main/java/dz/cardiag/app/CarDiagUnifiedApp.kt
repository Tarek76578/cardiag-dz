package dz.cardiag.app

/**
 * Single production UI entry point.
 *
 * CarDiagModernActivity intentionally launches this function so there is only
 * one canonical Compose application shell. The richer bilingual/theme-aware
 * implementation lives in CarDiagExactApp.kt; keeping this adapter preserves
 * the existing launcher contract while eliminating a second competing shell.
 */
@androidx.compose.runtime.Composable
fun CarDiagUnifiedApp() {
    CarDiagExactApp()
}
