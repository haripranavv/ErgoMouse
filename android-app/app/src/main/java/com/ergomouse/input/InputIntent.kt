package com.ergomouse.input

/**
 * Output of the gesture pipeline: TouchEngine -> PalmRejectionFilter ->
 * GestureRecognizer -> SensitivityCurve -> [InputIntent].
 *
 * These are transport-agnostic; PacketCodec turns them into wire bytes.
 * Kept as a sealed class (not a single mega-struct) so the recognizer's
 * `when` blocks stay exhaustive and new gesture types fail to compile
 * anywhere they aren't handled, rather than silently no-op-ing.
 */
sealed class InputIntent {

    data class Move(val dx: Float, val dy: Float, val precisionMode: Boolean, val dragActive: Boolean) : InputIntent()

    data class Click(val button: Button, val down: Boolean) : InputIntent()

    data class Scroll(val dx: Float, val dy: Float) : InputIntent()

    data class Modifier(val ctrl: Boolean, val shift: Boolean, val alt: Boolean) : InputIntent()

    data object DragBegin : InputIntent()
    data object DragEnd : InputIntent()

    enum class Button { LEFT, RIGHT, MIDDLE }
}
