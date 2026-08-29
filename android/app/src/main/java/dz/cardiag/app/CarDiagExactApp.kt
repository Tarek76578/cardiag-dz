package dz.cardiag.app

/**
 * Backwards-compatible entry. The launcher calls
 * [CarDiagModernActivity] which forwards to [CarDiagUnifiedApp]; this
 * function exists so older callers (tests, previews) that import the
 * canonical name still resolve to the single production UI.
 */
@androidx.compose.runtime.Composable
fun CarDiagExactApp() {
    CarDiagUnifiedApp()
}
