package com.example.aifitnesscoach

import kotlin.math.atan2

// A simple data class for a landmark's coordinates
data class Landmark(val x: Float, val y: Float)

/**
 * Calculates the angle between three landmarks using the more stable atan2 method.
 * This is a direct and robust translation of the final working Python logic.
 */
fun calculateAngle(p1: Landmark, p2: Landmark, p3: Landmark): Double {
    val radians = atan2(p3.y - p2.y, p3.x - p2.x) - atan2(p1.y - p2.y, p1.x - p2.x)
    var angle = Math.toDegrees(radians.toDouble())

    // Ensure the angle is always positive
    if (angle < 0) {
        angle += 360
    }
    // We are interested in the interior angle, so if it's > 180, subtract from 360
    if (angle > 180) {
        angle = 360 - angle
    }
    return angle
}

