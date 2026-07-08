package com.ergomouse.input

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Classifies each active MotionEvent pointer as THUMB, PALM, or UNKNOWN
 * every touch frame. Must be cheap enough to run at 120-240Hz: no
 * allocation on the hot path, no ML model, just a weighted heuristic
 * fused from contact geometry + calibrated thumb-reach zone.
 *
 * See docs/ARCHITECTURE.md §4.3 for the rationale behind each signal.
 */
class PalmRejectionFilter(private val calibration: ThumbZoneCalibration) {

    enum class Classification { THUMB, PALM, UNKNOWN }

    private val pointerHistory = HashMap<Int, PointerTrack>()

    fun classify(pointerId: Int, sample: TouchSample): Classification {
        val track = pointerHistory.getOrPut(pointerId) { PointerTrack() }
        track.push(sample)

        var thumbScore = 0f

        // 1. Contact ellipse size: thumbs pressing from below tend to have a
        // larger, more elongated footprint than incidental edge contact.
        val ellipseArea = sample.touchMajor * sample.touchMinor
        thumbScore += when {
            ellipseArea > calibration.thumbAreaMin -> 0.3f
            else -> -0.2f
        }

        // 2. Position relative to the calibrated thumb-reach arc.
        val distanceFromArc = calibration.distanceFromThumbArc(sample.x, sample.y)
        thumbScore += if (distanceFromArc < calibration.reachToleranceDp) 0.3f else -0.4f

        // 3. Pressure, when reliable.
        if (sample.pressure > 0f) {
            thumbScore += if (sample.pressure > calibration.pressureFloor) 0.15f else -0.1f
        }

        // 4. Motion coherence: static, low-area, long-duration contact reads
        // as a resting support finger, not the active thumb.
        val durationMs = track.durationMs()
        val totalMovement = track.totalMovementDp()
        if (durationMs > STATIC_HOLD_THRESHOLD_MS && totalMovement < STATIC_MOVEMENT_EPSILON_DP) {
            thumbScore -= 0.5f
        }

        // 5. Edge bezel bias: reject touches hugging the edge opposite the
        // thumb's natural arc (where wrap-around fingers land).
        if (calibration.isNearOppositeBezel(sample.x, sample.y)) {
            thumbScore -= 0.3f
        }

        return when {
            thumbScore >= THUMB_ACCEPT_THRESHOLD -> Classification.THUMB
            thumbScore <= PALM_REJECT_THRESHOLD -> Classification.PALM
            else -> Classification.UNKNOWN
        }
    }

    fun release(pointerId: Int) {
        pointerHistory.remove(pointerId)
    }

    companion object {
        private const val THUMB_ACCEPT_THRESHOLD = 0.25f
        private const val PALM_REJECT_THRESHOLD = -0.25f
        private const val STATIC_HOLD_THRESHOLD_MS = 220L
        private const val STATIC_MOVEMENT_EPSILON_DP = 4f
    }
}

data class TouchSample(
    val x: Float,
    val y: Float,
    val touchMajor: Float,
    val touchMinor: Float,
    val pressure: Float,
    val timestampMs: Long,
)

private class PointerTrack {
    private val samples = ArrayDeque<TouchSample>()

    fun push(sample: TouchSample) {
        samples.addLast(sample)
        while (samples.size > MAX_HISTORY) samples.removeFirst()
    }

    fun durationMs(): Long {
        if (samples.size < 2) return 0
        return samples.last().timestampMs - samples.first().timestampMs
    }

    fun totalMovementDp(): Float {
        if (samples.size < 2) return 0f
        var total = 0f
        var prev = samples.first()
        for (s in samples.drop(1)) {
            total += hypot((s.x - prev.x).toDouble(), (s.y - prev.y).toDouble()).toFloat()
            prev = s
        }
        return total
    }

    companion object { const val MAX_HISTORY = 12 }
}

/**
 * Result of the one-time onboarding calibration (§3.1): the arc the user's
 * thumb naturally sweeps when gripping the phone one-handed, plus derived
 * tolerances used by the classifier above.
 */
data class ThumbZoneCalibration(
    val arcCenterX: Float,
    val arcCenterY: Float,
    val arcRadiusDp: Float,
    val reachToleranceDp: Float,
    val thumbAreaMin: Float,
    val pressureFloor: Float,
    val isLeftHanded: Boolean,
) {
    fun distanceFromThumbArc(x: Float, y: Float): Float {
        val d = hypot((x - arcCenterX).toDouble(), (y - arcCenterY).toDouble()).toFloat()
        return abs(d - arcRadiusDp)
    }

    fun isNearOppositeBezel(x: Float, y: Float): Boolean {
        // The four support fingers wrap around the edge opposite the thumb's
        // dominant side. Left-handed mode mirrors this check.
        return if (isLeftHanded) x > OPPOSITE_BEZEL_THRESHOLD else x < (1f - OPPOSITE_BEZEL_THRESHOLD)
    }

    companion object { private const val OPPOSITE_BEZEL_THRESHOLD = 0.08f }
}
