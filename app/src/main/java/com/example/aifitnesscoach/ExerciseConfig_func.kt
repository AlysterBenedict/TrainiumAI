package com.example.aifitnesscoach

import kotlin.math.atan2

// A simple data class for a landmark's coordinates
data class Landmark_func(val x: Float, val y: Float)

/**
 * Calculates the angle between three landmarks using the more stable atan2 method.
 * This is a direct and robust translation of the final working Python logic.
 */
fun calculateAngle(p1: Landmark_func, p2: Landmark_func, p3: Landmark_func): Double {
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

// NEW: Data class to define the structure for posture validation
data class PostureValidation_func(
    val landmarksToTrack: List<Int>,
    val angleThreshold: Double,
    val feedbackIncorrect: String
)


// Data class to define the structure of an exercise configuration
data class ExerciseConfig_func(
    val name: String,
    val landmarksToTrack: List<Int>,
    val upThreshold: Double? = null, // Nullable for timed exercises
    val downThreshold: Double? = null, // Nullable for timed exercises
    val feedbackMap: Map<String, String>,
    val invertStages: Boolean = false,
    val exerciseType: String = "rep_based", // "rep_based" or "timed"
    val correctFormAngle: Double? = null, // For timed exercises
    val postureValidation: PostureValidation_func? = null, // For posture checks
    val angleLogic: String = "average",
    val thresholds: Map<String, Double> = emptyMap()
)

// Object to hold all our defined exercises
object Exercises_func {
    val list = listOf(
        ExerciseConfig_func(
            name = "SQUAT",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_HIP, WorkoutActivity_ui.LEFT_KNEE, WorkoutActivity_ui.LEFT_ANKLE,
                WorkoutActivity_ui.RIGHT_HIP, WorkoutActivity_ui.RIGHT_KNEE, WorkoutActivity_ui.RIGHT_ANKLE
            ),
            upThreshold = 165.0,
            downThreshold = 90.0,
            feedbackMap = mapOf(
                "up" to "Ready", "down" to "Good Squat!",
                "transition_up" to "Push Up", "transition_down" to "Go Deeper"
            ),
            angleLogic = "min"
        ),
        ExerciseConfig_func(
            name = "BICEP CURL",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_SHOULDER, WorkoutActivity_ui.LEFT_ELBOW, WorkoutActivity_ui.LEFT_WRIST,
                WorkoutActivity_ui.RIGHT_SHOULDER, WorkoutActivity_ui.RIGHT_ELBOW, WorkoutActivity_ui.RIGHT_WRIST
            ),
            upThreshold = 160.0,
            downThreshold = 40.0,
            feedbackMap = mapOf(
                "up" to "Ready", "down" to "Good Rep",
                "transition_up" to "Lower Slowly", "transition_down" to "Curl Up"
            ),
            angleLogic = "min"
        ),
        ExerciseConfig_func(
            name = "PUSH-UP",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_SHOULDER, WorkoutActivity_ui.LEFT_ELBOW, WorkoutActivity_ui.LEFT_WRIST,
                WorkoutActivity_ui.RIGHT_SHOULDER, WorkoutActivity_ui.RIGHT_ELBOW, WorkoutActivity_ui.RIGHT_WRIST
            ),
            upThreshold = 160.0,
            downThreshold = 90.0,
            feedbackMap = mapOf(
                "up" to "Good Rep", "down" to "Ready",
                "transition_up" to "Push Up", "transition_down" to "Go Lower"
            ),
            invertStages = true,
            angleLogic = "min"
        ),
        ExerciseConfig_func(
            name = "LUNGE",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_HIP, WorkoutActivity_ui.LEFT_KNEE, WorkoutActivity_ui.LEFT_ANKLE,
                WorkoutActivity_ui.RIGHT_HIP, WorkoutActivity_ui.RIGHT_KNEE, WorkoutActivity_ui.RIGHT_ANKLE
            ),
            upThreshold = 160.0,
            downThreshold = 100.0,
            feedbackMap = mapOf(
                "up" to "Ready", "down" to "Good Rep",
                "transition_up" to "Push Back Up", "transition_down" to "Step Forward"
            ),
            angleLogic = "min"
        ),
        ExerciseConfig_func(
            name = "PLANK",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_SHOULDER, WorkoutActivity_ui.LEFT_HIP, WorkoutActivity_ui.LEFT_ANKLE
            ),
            exerciseType = "timed",
            correctFormAngle = 160.0,
            feedbackMap = mapOf(
                "correct" to "Hold Position",
                "incorrect" to "Straighten Back"
            )
        ),
        ExerciseConfig_func(
            name = "OVERHEAD PRESS",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_SHOULDER, WorkoutActivity_ui.LEFT_ELBOW, WorkoutActivity_ui.LEFT_WRIST,
                WorkoutActivity_ui.RIGHT_SHOULDER, WorkoutActivity_ui.RIGHT_ELBOW, WorkoutActivity_ui.RIGHT_WRIST
            ),
            upThreshold = 160.0,
            downThreshold = 90.0,
            feedbackMap = mapOf(
                "up" to "Good Rep", "down" to "Ready",
                "transition_up" to "Press Up", "transition_down" to "Lower Slowly"
            ),
            invertStages = true,
            angleLogic = "min"
        ),
        ExerciseConfig_func(
            name = "JUMPING JACKS",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_HIP, WorkoutActivity_ui.LEFT_SHOULDER, WorkoutActivity_ui.LEFT_WRIST,
                WorkoutActivity_ui.RIGHT_HIP, WorkoutActivity_ui.RIGHT_SHOULDER, WorkoutActivity_ui.RIGHT_WRIST
            ),
            upThreshold = 130.0,
            downThreshold = 50.0,
            feedbackMap = mapOf(
                "up" to "Good Rep", "down" to "Ready",
                "transition_up" to "Arms Down", "transition_down" to "Arms Up!"
            ),
            angleLogic = "min"
        ),
        ExerciseConfig_func(
            name = "GLUTE BRIDGE",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_SHOULDER, WorkoutActivity_ui.LEFT_HIP, WorkoutActivity_ui.LEFT_KNEE,
                WorkoutActivity_ui.RIGHT_SHOULDER, WorkoutActivity_ui.RIGHT_HIP, WorkoutActivity_ui.RIGHT_KNEE
            ),
            upThreshold = 160.0,
            downThreshold = 120.0,
            feedbackMap = mapOf(
                "up" to "Good Squeeze", "down" to "Ready",
                "transition_up" to "Lift Hips", "transition_down" to "Lower Slowly"
            ),
            invertStages = true,
            angleLogic = "min"
        ),
        ExerciseConfig_func(
            name = "BENT OVER ROW",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_SHOULDER, WorkoutActivity_ui.LEFT_ELBOW, WorkoutActivity_ui.LEFT_WRIST,
                WorkoutActivity_ui.RIGHT_SHOULDER, WorkoutActivity_ui.RIGHT_ELBOW, WorkoutActivity_ui.RIGHT_WRIST
            ),
            upThreshold = 160.0,
            downThreshold = 90.0,
            feedbackMap = mapOf(
                "up" to "Ready", "down" to "Good Squeeze",
                "transition_up" to "Lower Slowly", "transition_down" to "Pull!"
            ),
            postureValidation = PostureValidation_func(
                landmarksToTrack = listOf(WorkoutActivity_ui.LEFT_SHOULDER, WorkoutActivity_ui.LEFT_HIP, WorkoutActivity_ui.LEFT_KNEE),
                angleThreshold = 110.0,
                feedbackIncorrect = "Bend Over More"
            ),
            angleLogic = "min"
        ),
        ExerciseConfig_func(
            name = "TRICEP DIPS",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_SHOULDER, WorkoutActivity_ui.LEFT_ELBOW, WorkoutActivity_ui.LEFT_WRIST,
                WorkoutActivity_ui.RIGHT_SHOULDER, WorkoutActivity_ui.RIGHT_ELBOW, WorkoutActivity_ui.RIGHT_WRIST
            ),
            upThreshold = 160.0,
            downThreshold = 90.0,
            feedbackMap = mapOf(
                "up" to "Good Press", "down" to "Ready",
                "transition_up" to "Push Up", "transition_down" to "Go Lower"
            ),
            invertStages = true,
            angleLogic = "min"
        ),
        ExerciseConfig_func(
            name = "CALF RAISES",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_KNEE, WorkoutActivity_ui.LEFT_ANKLE, WorkoutActivity_ui.LEFT_HEEL,
                WorkoutActivity_ui.RIGHT_KNEE, WorkoutActivity_ui.RIGHT_ANKLE, WorkoutActivity_ui.RIGHT_HEEL
            ),
            upThreshold = 170.0,
            downThreshold = 150.0,
            feedbackMap = mapOf(
                "up" to "Good Squeeze", "down" to "Ready",
                "transition_up" to "Lift Heels", "transition_down" to "Lower Slowly"
            ),
            invertStages = true,
            angleLogic = "min"
        ),
        ExerciseConfig_func(
            name = "WALL SIT",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_HIP, WorkoutActivity_ui.LEFT_KNEE, WorkoutActivity_ui.LEFT_ANKLE
            ),
            exerciseType = "timed",
            correctFormAngle = 120.0,
            feedbackMap = mapOf(
                "correct" to "Hold Tight!",
                "incorrect" to "Get Lower!"
            ),
            invertStages = true
        ),
        ExerciseConfig_func(
            name = "DEADLIFT",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_SHOULDER, WorkoutActivity_ui.LEFT_HIP, WorkoutActivity_ui.LEFT_KNEE,
                WorkoutActivity_ui.RIGHT_SHOULDER, WorkoutActivity_ui.RIGHT_HIP, WorkoutActivity_ui.RIGHT_KNEE
            ),
            upThreshold = 170.0,
            downThreshold = 90.0,
            feedbackMap = mapOf(
                "up" to "Good Rep!", "down" to "Ready",
                "transition_up" to "Extend Hips", "transition_down" to "Hinge at Hips"
            ),
            invertStages = true,
            angleLogic = "min"
        ),
        ExerciseConfig_func(
            name = "HIGH KNEES",
            landmarksToTrack = emptyList(),
            exerciseType = "knee_height",
            feedbackMap = mapOf(
                "up" to "Good!",
                "down" to "Drive Knee Up!"
            )
        ),
        ExerciseConfig_func(
            name = "PULL-UPS",
            landmarksToTrack = emptyList(),
            exerciseType = "pull_up",
            feedbackMap = mapOf(
                "up" to "Good Rep!",
                "down" to "Pull Up!"
            )
        ),
        ExerciseConfig_func(
            name = "BIRD-DOG",
            landmarksToTrack = emptyList(),
            exerciseType = "bird_dog",
            thresholds = mapOf(
                "extended" to 0.6,
                "contracted" to 0.2
            ),
            feedbackMap = mapOf(
                "out" to "Extend!",
                "in" to "Return"
            )
        ),
        ExerciseConfig_func(
            name = "RUSSIAN TWIST",
            landmarksToTrack = emptyList(),
            exerciseType = "russian_twist",
            thresholds = mapOf(
                "left" to -10.0,
                "right" to 10.0
            ),
            feedbackMap = mapOf(
                "left" to "Twist Left",
                "right" to "Twist Right"
            )
        ),
        ExerciseConfig_func(
            name = "CRUNCHES",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_SHOULDER, WorkoutActivity_ui.LEFT_HIP, WorkoutActivity_ui.LEFT_KNEE
            ),
            upThreshold = 160.0,
            downThreshold = 130.0,
            feedbackMap = mapOf(
                "up" to "Ready", "down" to "Good Crunch",
                "transition_up" to "Lower Down", "transition_down" to "Crunch Up"
            )
        ),
        ExerciseConfig_func(
            name = "LEG RAISES",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_SHOULDER, WorkoutActivity_ui.LEFT_HIP, WorkoutActivity_ui.LEFT_ANKLE
            ),
            upThreshold = 150.0,
            downThreshold = 90.0,
            feedbackMap = mapOf(
                "up" to "Ready", "down" to "Good Rep",
                "transition_up" to "Lower Slowly", "transition_down" to "Raise Legs"
            )
        ),
        ExerciseConfig_func(
            name = "MOUNTAIN CLIMBER",
            landmarksToTrack = emptyList(),
            exerciseType = "mountain_climber",
            thresholds = mapOf(
                "close" to 0.2,
                "far" to 0.4
            ),
            feedbackMap = mapOf(
                "forward" to "Knee to Elbow!",
                "back" to "Switch"
            )
        ),
        ExerciseConfig_func(
            name = "SIDE LUNGES",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_HIP, WorkoutActivity_ui.LEFT_KNEE, WorkoutActivity_ui.LEFT_ANKLE
            ),
            upThreshold = 160.0,
            downThreshold = 110.0,
            feedbackMap = mapOf(
                "up" to "Ready", "down" to "Good Lunge",
                "transition_up" to "Push Back", "transition_down" to "Lunge Out"
            )
        ),
        ExerciseConfig_func(
            name = "SUPERMAN",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_ANKLE, WorkoutActivity_ui.LEFT_HIP, WorkoutActivity_ui.LEFT_SHOULDER
            ),
            upThreshold = 170.0,
            downThreshold = 150.0,
            feedbackMap = mapOf(
                "up" to "Ready", "down" to "Lift!",
                "transition_up" to "Lower Slowly", "transition_down" to "Lift!"
            )
        ),
        ExerciseConfig_func(
            name = "BURPEES",
            landmarksToTrack = emptyList(),
            exerciseType = "burpee",
            feedbackMap = emptyMap()
        ),
        ExerciseConfig_func(
            name = "SIDE PLANK",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_ANKLE, WorkoutActivity_ui.LEFT_HIP, WorkoutActivity_ui.LEFT_SHOULDER
            ),
            exerciseType = "timed",
            correctFormAngle = 150.0,
            feedbackMap = mapOf(
                "correct" to "Hold Straight!",
                "incorrect" to "Lift Hips!"
            )
        ),
        ExerciseConfig_func(
            name = "LATERAL RAISES",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_HIP, WorkoutActivity_ui.LEFT_SHOULDER, WorkoutActivity_ui.LEFT_ELBOW,
                WorkoutActivity_ui.RIGHT_HIP, WorkoutActivity_ui.RIGHT_SHOULDER, WorkoutActivity_ui.RIGHT_ELBOW
            ),
            upThreshold = 90.0,
            downThreshold = 20.0,
            feedbackMap = mapOf(
                "up" to "Ready", "down" to "Good Rep",
                "transition_up" to "Lower Slowly", "transition_down" to "Raise Arms"
            ),
            angleLogic = "max"
        ),
        ExerciseConfig_func(
            name = "SUMO SQUAT",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_HIP, WorkoutActivity_ui.LEFT_KNEE, WorkoutActivity_ui.LEFT_ANKLE,
                WorkoutActivity_ui.RIGHT_HIP, WorkoutActivity_ui.RIGHT_KNEE, WorkoutActivity_ui.RIGHT_ANKLE
            ),
            upThreshold = 165.0,
            downThreshold = 80.0,
            feedbackMap = mapOf(
                "up" to "Ready", "down" to "Good Squat!",
                "transition_up" to "Push Up", "transition_down" to "Go Deeper"
            ),
            angleLogic = "min"
        ),
        ExerciseConfig_func(
            name = "PIKE PUSH-UP",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_SHOULDER, WorkoutActivity_ui.LEFT_ELBOW, WorkoutActivity_ui.LEFT_WRIST
            ),
            upThreshold = 160.0,
            downThreshold = 100.0,
            feedbackMap = mapOf(
                "up" to "Good Rep", "down" to "Ready",
                "transition_up" to "Press Up", "transition_down" to "Lower Head"
            ),
            invertStages = true
        ),
        ExerciseConfig_func(
            name = "REVERSE CRUNCHES",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_SHOULDER, WorkoutActivity_ui.LEFT_HIP, WorkoutActivity_ui.LEFT_KNEE
            ),
            upThreshold = 120.0,
            downThreshold = 80.0,
            feedbackMap = mapOf(
                "up" to "Ready", "down" to "Good Rep",
                "transition_up" to "Lower Legs", "transition_down" to "Knees to Chest"
            )
        ),
        ExerciseConfig_func(
            name = "PLANK JACKS",
            landmarksToTrack = emptyList(),
            exerciseType = "plank_jacks",
            thresholds = mapOf(
                "out" to 0.4,
                "in" to 0.2
            ),
            feedbackMap = mapOf(
                "out" to "Legs Out!",
                "in" to "Legs In!"
            )
        ),
        ExerciseConfig_func(
            name = "GOOD MORNINGS",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_SHOULDER, WorkoutActivity_ui.LEFT_HIP, WorkoutActivity_ui.LEFT_KNEE
            ),
            upThreshold = 170.0,
            downThreshold = 100.0,
            feedbackMap = mapOf(
                "up" to "Ready", "down" to "Good Hinge",
                "transition_up" to "Squeeze Glutes", "transition_down" to "Hinge Forward"
            )
        ),
        ExerciseConfig_func(
            name = "DONKEY KICKS",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_SHOULDER, WorkoutActivity_ui.LEFT_HIP, WorkoutActivity_ui.LEFT_ANKLE
            ),
            upThreshold = 120.0,
            downThreshold = 90.0,
            feedbackMap = mapOf(
                "up" to "Good Kick!", "down" to "Ready",
                "transition_up" to "Kick Up", "transition_down" to "Return"
            ),
            invertStages = true
        ),
        ExerciseConfig_func(
            name = "FIRE HYDRANTS",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_HIP, WorkoutActivity_ui.LEFT_KNEE, WorkoutActivity_ui.RIGHT_KNEE
            ),
            upThreshold = 100.0,
            downThreshold = 90.0,
            feedbackMap = mapOf(
                "up" to "Good Lift!", "down" to "Ready",
                "transition_up" to "Lift Knee", "transition_down" to "Return"
            ),
            invertStages = true
        ),
        ExerciseConfig_func(
            name = "SHOULDER TAPS",
            landmarksToTrack = emptyList(),
            exerciseType = "shoulder_taps",
            thresholds = mapOf(
                "tap" to 0.1,
                "release" to 0.2
            ),
            feedbackMap = mapOf(
                "tap" to "Tap!",
                "release" to "Return Hand"
            )
        ),
        ExerciseConfig_func(
            name = "WALL PUSH-UPS",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_SHOULDER, WorkoutActivity_ui.LEFT_ELBOW, WorkoutActivity_ui.LEFT_WRIST
            ),
            upThreshold = 160.0,
            downThreshold = 90.0,
            feedbackMap = mapOf(
                "up" to "Good Press", "down" to "Ready",
                "transition_up" to "Push Away", "transition_down" to "Lean In"
            ),
            invertStages = true
        ),
        ExerciseConfig_func(
            name = "ARM CIRCLES",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_ELBOW, WorkoutActivity_ui.LEFT_SHOULDER, WorkoutActivity_ui.LEFT_HIP
            ),
            exerciseType = "timed",
            correctFormAngle = 30.0,
            feedbackMap = mapOf(
                "correct" to "Keep Circling",
                "incorrect" to "Raise Arms"
            )
        ),
        ExerciseConfig_func(
            name = "TORSO TWISTS",
            landmarksToTrack = emptyList(),
            exerciseType = "russian_twist",
            thresholds = mapOf(
                "left" to -5.0,
                "right" to 5.0
            ),
            feedbackMap = mapOf(
                "left" to "Twist Left",
                "right" to "Twist Right"
            )
        ),
        ExerciseConfig_func(
            name = "REVERSE LUNGES",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_HIP, WorkoutActivity_ui.LEFT_KNEE, WorkoutActivity_ui.LEFT_ANKLE
            ),
            upThreshold = 160.0,
            downThreshold = 100.0,
            feedbackMap = mapOf(
                "up" to "Ready", "down" to "Good Lunge",
                "transition_up" to "Return", "transition_down" to "Step Back"
            )
        ),
        ExerciseConfig_func(
            name = "FORWARD FOLD",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_SHOULDER, WorkoutActivity_ui.LEFT_HIP, WorkoutActivity_ui.LEFT_KNEE
            ),
            exerciseType = "timed",
            correctFormAngle = 160.0,
            feedbackMap = mapOf(
                "correct" to "Hold Stretch",
                "incorrect" to "Straighten Back"
            )
        ),
        ExerciseConfig_func(
            name = "CAT-COW STRETCH",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_SHOULDER, WorkoutActivity_ui.LEFT_HIP, WorkoutActivity_ui.LEFT_KNEE
            ),
            upThreshold = 100.0,
            downThreshold = 80.0,
            feedbackMap = mapOf(
                "up" to "Cow Pose", "down" to "Cat Pose",
                "transition_up" to "Arch Back", "transition_down" to "Round Spine"
            ),
            invertStages = true
        ),
        ExerciseConfig_func(
            name = "CHILD'S POSE",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_SHOULDER, WorkoutActivity_ui.LEFT_HIP, WorkoutActivity_ui.LEFT_KNEE
            ),
            exerciseType = "timed",
            correctFormAngle = 80.0,
            feedbackMap = mapOf(
                "correct" to "Hold and Breathe",
                "incorrect" to "Sit Back on Heels"
            ),
            invertStages = true
        ),
        ExerciseConfig_func(
            name = "COBRA POSE",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_HIP, WorkoutActivity_ui.LEFT_SHOULDER, WorkoutActivity_ui.LEFT_ELBOW
            ),
            exerciseType = "timed",
            correctFormAngle = 150.0,
            feedbackMap = mapOf(
                "correct" to "Hold Pose",
                "incorrect" to "Lift Chest"
            )
        ),
        ExerciseConfig_func(
            name = "DOWNWARD DOG",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_ANKLE, WorkoutActivity_ui.LEFT_HIP, WorkoutActivity_ui.LEFT_WRIST
            ),
            exerciseType = "timed",
            correctFormAngle = 150.0,
            feedbackMap = mapOf(
                "correct" to "Hold the V-Shape",
                "incorrect" to "Push Hips Up"
            )
        ),
        ExerciseConfig_func(
            name = "DIAMOND PUSH-UP",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_SHOULDER, WorkoutActivity_ui.LEFT_ELBOW, WorkoutActivity_ui.LEFT_WRIST
            ),
            upThreshold = 160.0,
            downThreshold = 90.0,
            feedbackMap = mapOf(
                "up" to "Good Rep", "down" to "Ready",
                "transition_up" to "Push Up", "transition_down" to "Go Lower"
            ),
            invertStages = true
        ),
        ExerciseConfig_func(
            name = "FLUTTER KICKS",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_HIP, WorkoutActivity_ui.LEFT_KNEE, WorkoutActivity_ui.LEFT_ANKLE
            ),
            exerciseType = "timed",
            correctFormAngle = 160.0,
            feedbackMap = mapOf(
                "correct" to "Keep Kicking",
                "incorrect" to "Keep Legs Straight"
            )
        ),
        ExerciseConfig_func(
            name = "SCISSOR KICKS",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_HIP, WorkoutActivity_ui.LEFT_KNEE, WorkoutActivity_ui.LEFT_ANKLE
            ),
            exerciseType = "timed",
            correctFormAngle = 160.0,
            feedbackMap = mapOf(
                "correct" to "Keep Crossing",
                "incorrect" to "Keep Legs Straight"
            )
        ),
        ExerciseConfig_func(
            name = "INCHWORM",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_SHOULDER, WorkoutActivity_ui.LEFT_HIP, WorkoutActivity_ui.LEFT_ANKLE
            ),
            upThreshold = 160.0,
            downThreshold = 90.0,
            feedbackMap = mapOf(
                "up" to "Walk Feet In", "down" to "Walk Hands Out",
                "transition_up" to "Walk Feet In", "transition_down" to "Walk Hands Out"
            ),
            invertStages = true
        ),
        ExerciseConfig_func(
            name = "HIGH PLANK TO LOW PLANK",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_SHOULDER, WorkoutActivity_ui.LEFT_ELBOW, WorkoutActivity_ui.LEFT_WRIST
            ),
            upThreshold = 160.0,
            downThreshold = 90.0,
            feedbackMap = mapOf(
                "up" to "High Plank", "down" to "Low Plank",
                "transition_up" to "Up to Hands", "transition_down" to "Down to Elbows"
            ),
            invertStages = true
        ),
        ExerciseConfig_func(
            name = "BOXER SHUFFLE",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_KNEE, WorkoutActivity_ui.LEFT_HIP, WorkoutActivity_ui.LEFT_SHOULDER
            ),
            exerciseType = "timed",
            correctFormAngle = 150.0,
            feedbackMap = mapOf(
                "correct" to "Keep Shuffling",
                "incorrect" to "Stay on Toes"
            )
        ),
        ExerciseConfig_func(
            name = "SIDE BEND",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_SHOULDER, WorkoutActivity_ui.LEFT_HIP, WorkoutActivity_ui.LEFT_KNEE
            ),
            upThreshold = 170.0,
            downThreshold = 155.0,
            feedbackMap = mapOf(
                "up" to "Ready", "down" to "Good Bend",
                "transition_up" to "Return to Center", "transition_down" to "Bend Sideways"
            )
        ),
        ExerciseConfig_func(
            name = "T-POSE HOLD",
            landmarksToTrack = listOf(
                WorkoutActivity_ui.LEFT_HIP, WorkoutActivity_ui.LEFT_SHOULDER, WorkoutActivity_ui.LEFT_WRIST
            ),
            exerciseType = "timed",
            correctFormAngle = 160.0,
            feedbackMap = mapOf(
                "correct" to "Hold Strong!",
                "incorrect" to "Straighten Arms"
            )
        )
    )
}
