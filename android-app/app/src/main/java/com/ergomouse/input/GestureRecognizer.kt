package com.ergomouse.input

import android.view.MotionEvent
import kotlin.math.hypot
import kotlin.math.min

class GestureRecognizer(private val onIntentDetected: (InputIntent) -> Unit) {
    private var activePointerId: Int = -1
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    private var scrollLastY: Float = 0f
    private var downTime: Long = 0

    // --- PHASE 3: MOUSE ACCELERATION KINEMATICS ---
    private fun applyAcceleration(rawDx: Float, rawDy: Float): Pair<Float, Float> {
        // Calculate the speed (magnitude) of the finger swipe
        val speed = hypot(rawDx.toDouble(), rawDy.toDouble()).toFloat()

        // 🎛️ TUNING KNOBS (Feel free to adjust these to your liking!)
        val baseSensitivity = 1.4f // Overall speed multiplier
        val precisionThreshold = 3.0f // Speeds below this trigger precision mode
        val accelThreshold = 15.0f // Speeds above this trigger fast acceleration
        val maxMultiplier = 4.0f // Speed cap so the cursor doesn't teleport

        val multiplier =
                when {
                    speed < precisionThreshold -> 0.4f // 🎯 Precision aiming (slows cursor down)
                    speed < accelThreshold -> 1.0f // 🚶 Normal linear tracking
                    else -> { // 🚀 Fast flick acceleration
                        val extraSpeed = speed - accelThreshold
                        // Increase multiplier by 8% for every pixel of speed over the threshold
                        val accelFactor = 1.0f + (extraSpeed * 0.08f)
                        min(accelFactor, maxMultiplier)
                    }
                }

        val finalDx = rawDx * baseSensitivity * multiplier
        val finalDy = rawDy * baseSensitivity * multiplier

        return Pair(finalDx, finalDy)
    }

    fun processMotionEvent(event: MotionEvent, classification: PalmRejectionFilter.Classification) {
        val action = event.actionMasked
        val actionIndex = event.actionIndex
        val pointerId = event.getPointerId(actionIndex)

        if (classification == PalmRejectionFilter.Classification.PALM) return

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = pointerId
                lastX = event.x
                lastY = event.y
                downTime = event.eventTime
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    scrollLastY = event.getY(actionIndex)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1 && activePointerId != -1) {
                    val pointerIndex = event.findPointerIndex(activePointerId)
                    if (pointerIndex != -1) {
                        val currentX = event.getX(pointerIndex)
                        val currentY = event.getY(pointerIndex)

                        val rawDx = currentX - lastX
                        val rawDy = currentY - lastY

                        // Pass raw movement through our new physics engine
                        val (accelDx, accelDy) = applyAcceleration(rawDx, rawDy)

                        onIntentDetected(InputIntent.Move(accelDx, accelDy, false, false))

                        lastX = currentX
                        lastY = currentY
                    }
                } else if (event.pointerCount == 2) {
                    val currentScrollY = event.getY(1)
                    val dy = currentScrollY - scrollLastY
                    if (Math.abs(dy) > 2f) {
                        onIntentDetected(InputIntent.Scroll(0f, dy))
                        scrollLastY = currentScrollY
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (pointerId == activePointerId) {
                    val duration = event.eventTime - downTime
                    if (duration < 200) {
                        onIntentDetected(InputIntent.Click(InputIntent.Button.LEFT, true))
                        onIntentDetected(InputIntent.Click(InputIntent.Button.LEFT, false))
                    }
                    activePointerId = -1
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (pointerId == activePointerId) activePointerId = -1
            }
        }
    }
}
