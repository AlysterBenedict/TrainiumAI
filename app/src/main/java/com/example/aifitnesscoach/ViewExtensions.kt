package com.example.aifitnesscoach

import android.view.MotionEvent
import android.view.View
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

// The lambda now provides the View that was clicked
fun View.setOnBounceClickListener(onClick: (View) -> Unit) {
    val scaleXAnim = SpringAnimation(this, SpringAnimation.SCALE_X, 1f)
    val scaleYAnim = SpringAnimation(this, SpringAnimation.SCALE_Y, 1f)

    setOnTouchListener { view, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                scaleXAnim.spring.stiffness = SpringForce.STIFFNESS_LOW
                scaleXAnim.animateToFinalPosition(0.9f)
                scaleYAnim.spring.stiffness = SpringForce.STIFFNESS_LOW
                scaleYAnim.animateToFinalPosition(0.9f)
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                scaleXAnim.spring.stiffness = SpringForce.STIFFNESS_MEDIUM
                scaleXAnim.animateToFinalPosition(1f)
                scaleYAnim.spring.stiffness = SpringForce.STIFFNESS_MEDIUM
                scaleYAnim.animateToFinalPosition(1f)

                if (event.action == MotionEvent.ACTION_UP) {
                    // Pass the view into the click lambda
                    onClick(view)
                }
                true
            }
            else -> false
        }
    }
}