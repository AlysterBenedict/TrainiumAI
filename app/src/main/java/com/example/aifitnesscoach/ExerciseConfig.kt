package com.example.aifitnesscoach

// NEW: Data class to define the structure for posture validation
data class PostureValidation(
    val landmarksToTrack: List<Int>,
    val angleThreshold: Double,
    val feedbackIncorrect: String
)

// Data class to define the structure of an exercise configuration
data class ExerciseConfig(
    val name: String,
    val landmarksToTrack: List<Int>,
    val upThreshold: Double? = null, // Nullable for timed exercises
    val downThreshold: Double? = null, // Nullable for timed exercises
    val feedbackMap: Map<String, String>,
    val invertStages: Boolean = false,
    val exerciseType: String = "rep_based", // "rep_based" or "timed"
    val correctFormAngle: Double? = null, // For timed exercises
    val postureValidation: PostureValidation? = null, // NEW: For posture checks
    val angleLogic: String = "average",
    val thresholds: Map<String, Double> = emptyMap()
)

// Object to hold all our defined exercises
object Exercises {
    val list = listOf(
        ExerciseConfig(
            name = "SQUAT",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_HIP, WorkoutActivity.LEFT_KNEE, WorkoutActivity.LEFT_ANKLE,
                WorkoutActivity.RIGHT_HIP, WorkoutActivity.RIGHT_KNEE, WorkoutActivity.RIGHT_ANKLE
            ),
            upThreshold = 165.0,
            downThreshold = 90.0,
            feedbackMap = mapOf(
                "up" to "Ready", "down" to "Good Squat!",
                "transition_up" to "Push Up", "transition_down" to "Go Deeper"
            ),
            angleLogic = "min"
        ),
        ExerciseConfig(
            name = "BICEP CURL",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_SHOULDER, WorkoutActivity.LEFT_ELBOW, WorkoutActivity.LEFT_WRIST,
                WorkoutActivity.RIGHT_SHOULDER, WorkoutActivity.RIGHT_ELBOW, WorkoutActivity.RIGHT_WRIST
            ),
            upThreshold = 160.0,
            downThreshold = 40.0,
            feedbackMap = mapOf(
                "up" to "Ready", "down" to "Good Rep",
                "transition_up" to "Lower Slowly", "transition_down" to "Curl Up"
            ),
            angleLogic = "min"
        ),
        ExerciseConfig(
            name = "PUSH-UP",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_SHOULDER, WorkoutActivity.LEFT_ELBOW, WorkoutActivity.LEFT_WRIST,
                WorkoutActivity.RIGHT_SHOULDER, WorkoutActivity.RIGHT_ELBOW, WorkoutActivity.RIGHT_WRIST
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
        ExerciseConfig(
            name = "LUNGE",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_HIP, WorkoutActivity.LEFT_KNEE, WorkoutActivity.LEFT_ANKLE,
                WorkoutActivity.RIGHT_HIP, WorkoutActivity.RIGHT_KNEE, WorkoutActivity.RIGHT_ANKLE
            ),
            upThreshold = 160.0,
            downThreshold = 100.0,
            feedbackMap = mapOf(
                "up" to "Ready", "down" to "Good Rep",
                "transition_up" to "Push Back Up", "transition_down" to "Step Forward"
            ),
            angleLogic = "min"
        ),
        ExerciseConfig(
            name = "PLANK",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_SHOULDER, WorkoutActivity.LEFT_HIP, WorkoutActivity.LEFT_ANKLE
            ),
            exerciseType = "timed",
            correctFormAngle = 160.0,
            feedbackMap = mapOf(
                "correct" to "Hold Position",
                "incorrect" to "Straighten Back"
            )
        ),
        ExerciseConfig(
            name = "OVERHEAD PRESS",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_SHOULDER, WorkoutActivity.LEFT_ELBOW, WorkoutActivity.LEFT_WRIST,
                WorkoutActivity.RIGHT_SHOULDER, WorkoutActivity.RIGHT_ELBOW, WorkoutActivity.RIGHT_WRIST
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
        ExerciseConfig(
            name = "JUMPING JACKS",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_HIP, WorkoutActivity.LEFT_SHOULDER, WorkoutActivity.LEFT_WRIST,
                WorkoutActivity.RIGHT_HIP, WorkoutActivity.RIGHT_SHOULDER, WorkoutActivity.RIGHT_WRIST
            ),
            upThreshold = 130.0,
            downThreshold = 50.0,
            feedbackMap = mapOf(
                "up" to "Good Rep", "down" to "Ready",
                "transition_up" to "Arms Down", "transition_down" to "Arms Up!"
            ),
            angleLogic = "min"
        ),
        ExerciseConfig(
            name = "GLUTE BRIDGE",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_SHOULDER, WorkoutActivity.LEFT_HIP, WorkoutActivity.LEFT_KNEE,
                WorkoutActivity.RIGHT_SHOULDER, WorkoutActivity.RIGHT_HIP, WorkoutActivity.RIGHT_KNEE
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
        ExerciseConfig(
            name = "BENT OVER ROW",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_SHOULDER, WorkoutActivity.LEFT_ELBOW, WorkoutActivity.LEFT_WRIST,
                WorkoutActivity.RIGHT_SHOULDER, WorkoutActivity.RIGHT_ELBOW, WorkoutActivity.RIGHT_WRIST
            ),
            upThreshold = 160.0,
            downThreshold = 90.0,
            feedbackMap = mapOf(
                "up" to "Ready", "down" to "Good Squeeze",
                "transition_up" to "Lower Slowly", "transition_down" to "Pull!"
            ),
            postureValidation = PostureValidation(
                landmarksToTrack = listOf(WorkoutActivity.LEFT_SHOULDER, WorkoutActivity.LEFT_HIP, WorkoutActivity.LEFT_KNEE),
                angleThreshold = 110.0,
                feedbackIncorrect = "Bend Over More"
            ),
            angleLogic = "min"
        ),
        ExerciseConfig(
            name = "TRICEP DIPS",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_SHOULDER, WorkoutActivity.LEFT_ELBOW, WorkoutActivity.LEFT_WRIST,
                WorkoutActivity.RIGHT_SHOULDER, WorkoutActivity.RIGHT_ELBOW, WorkoutActivity.RIGHT_WRIST
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
        ExerciseConfig(
            name = "CALF RAISES",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_KNEE, WorkoutActivity.LEFT_ANKLE, WorkoutActivity.LEFT_HEEL,
                WorkoutActivity.RIGHT_KNEE, WorkoutActivity.RIGHT_ANKLE, WorkoutActivity.RIGHT_HEEL
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
        ExerciseConfig(
            name = "WALL SIT",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_HIP, WorkoutActivity.LEFT_KNEE, WorkoutActivity.LEFT_ANKLE
            ),
            exerciseType = "timed",
            correctFormAngle = 120.0,
            feedbackMap = mapOf(
                "correct" to "Hold Tight!",
                "incorrect" to "Get Lower!"
            ),
            invertStages = true
        ),
        ExerciseConfig(
            name = "DEADLIFT",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_SHOULDER, WorkoutActivity.LEFT_HIP, WorkoutActivity.LEFT_KNEE,
                WorkoutActivity.RIGHT_SHOULDER, WorkoutActivity.RIGHT_HIP, WorkoutActivity.RIGHT_KNEE
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
        ExerciseConfig(
            name = "HIGH KNEES",
            landmarksToTrack = emptyList(),
            exerciseType = "knee_height",
            feedbackMap = mapOf(
                "up" to "Good!",
                "down" to "Drive Knee Up!"
            )
        ),
        ExerciseConfig(
            name = "PULL-UPS",
            landmarksToTrack = emptyList(),
            exerciseType = "pull_up",
            feedbackMap = mapOf(
                "up" to "Good Rep!",
                "down" to "Pull Up!"
            )
        ),
        ExerciseConfig(
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
        ExerciseConfig(
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
        ExerciseConfig(
            name = "CRUNCHES",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_SHOULDER, WorkoutActivity.LEFT_HIP, WorkoutActivity.LEFT_KNEE
            ),
            upThreshold = 160.0,
            downThreshold = 130.0,
            feedbackMap = mapOf(
                "up" to "Ready", "down" to "Good Crunch",
                "transition_up" to "Lower Down", "transition_down" to "Crunch Up"
            )
        ),
        ExerciseConfig(
            name = "LEG RAISES",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_SHOULDER, WorkoutActivity.LEFT_HIP, WorkoutActivity.LEFT_ANKLE
            ),
            upThreshold = 150.0,
            downThreshold = 90.0,
            feedbackMap = mapOf(
                "up" to "Ready", "down" to "Good Rep",
                "transition_up" to "Lower Slowly", "transition_down" to "Raise Legs"
            )
        ),
        ExerciseConfig(
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
        ExerciseConfig(
            name = "SIDE LUNGES",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_HIP, WorkoutActivity.LEFT_KNEE, WorkoutActivity.LEFT_ANKLE
            ),
            upThreshold = 160.0,
            downThreshold = 110.0,
            feedbackMap = mapOf(
                "up" to "Ready", "down" to "Good Lunge",
                "transition_up" to "Push Back", "transition_down" to "Lunge Out"
            )
        ),
        ExerciseConfig(
            name = "SUPERMAN",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_ANKLE, WorkoutActivity.LEFT_HIP, WorkoutActivity.LEFT_SHOULDER
            ),
            upThreshold = 170.0,
            downThreshold = 150.0,
            feedbackMap = mapOf(
                "up" to "Ready", "down" to "Lift!",
                "transition_up" to "Lower Slowly", "transition_down" to "Lift!"
            )
        ),
        ExerciseConfig(
            name = "BURPEES",
            landmarksToTrack = emptyList(),
            exerciseType = "burpee",
            feedbackMap = emptyMap()
        ),
        ExerciseConfig(
            name = "SIDE PLANK",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_ANKLE, WorkoutActivity.LEFT_HIP, WorkoutActivity.LEFT_SHOULDER
            ),
            exerciseType = "timed",
            correctFormAngle = 150.0,
            feedbackMap = mapOf(
                "correct" to "Hold Straight!",
                "incorrect" to "Lift Hips!"
            )
        ),
        ExerciseConfig(
            name = "LATERAL RAISES",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_HIP, WorkoutActivity.LEFT_SHOULDER, WorkoutActivity.LEFT_ELBOW,
                WorkoutActivity.RIGHT_HIP, WorkoutActivity.RIGHT_SHOULDER, WorkoutActivity.RIGHT_ELBOW
            ),
            upThreshold = 90.0,
            downThreshold = 20.0,
            feedbackMap = mapOf(
                "up" to "Ready", "down" to "Good Rep",
                "transition_up" to "Lower Slowly", "transition_down" to "Raise Arms"
            ),
            angleLogic = "max"
        ),
        ExerciseConfig(
            name = "SUMO SQUAT",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_HIP, WorkoutActivity.LEFT_KNEE, WorkoutActivity.LEFT_ANKLE,
                WorkoutActivity.RIGHT_HIP, WorkoutActivity.RIGHT_KNEE, WorkoutActivity.RIGHT_ANKLE
            ),
            upThreshold = 165.0,
            downThreshold = 80.0,
            feedbackMap = mapOf(
                "up" to "Ready", "down" to "Good Squat!",
                "transition_up" to "Push Up", "transition_down" to "Go Deeper"
            ),
            angleLogic = "min"
        ),
        ExerciseConfig(
            name = "PIKE PUSH-UP",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_SHOULDER, WorkoutActivity.LEFT_ELBOW, WorkoutActivity.LEFT_WRIST
            ),
            upThreshold = 160.0,
            downThreshold = 100.0,
            feedbackMap = mapOf(
                "up" to "Good Rep", "down" to "Ready",
                "transition_up" to "Press Up", "transition_down" to "Lower Head"
            ),
            invertStages = true
        ),
        ExerciseConfig(
            name = "REVERSE CRUNCHES",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_SHOULDER, WorkoutActivity.LEFT_HIP, WorkoutActivity.LEFT_KNEE
            ),
            upThreshold = 120.0,
            downThreshold = 80.0,
            feedbackMap = mapOf(
                "up" to "Ready", "down" to "Good Rep",
                "transition_up" to "Lower Legs", "transition_down" to "Knees to Chest"
            )
        ),
        ExerciseConfig(
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
        ExerciseConfig(
            name = "GOOD MORNINGS",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_SHOULDER, WorkoutActivity.LEFT_HIP, WorkoutActivity.LEFT_KNEE
            ),
            upThreshold = 170.0,
            downThreshold = 100.0,
            feedbackMap = mapOf(
                "up" to "Ready", "down" to "Good Hinge",
                "transition_up" to "Squeeze Glutes", "transition_down" to "Hinge Forward"
            )
        ),
        ExerciseConfig(
            name = "DONKEY KICKS",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_SHOULDER, WorkoutActivity.LEFT_HIP, WorkoutActivity.LEFT_ANKLE
            ),
            upThreshold = 120.0,
            downThreshold = 90.0,
            feedbackMap = mapOf(
                "up" to "Good Kick!", "down" to "Ready",
                "transition_up" to "Kick Up", "transition_down" to "Return"
            ),
            invertStages = true
        ),
        ExerciseConfig(
            name = "FIRE HYDRANTS",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_HIP, WorkoutActivity.LEFT_KNEE, WorkoutActivity.RIGHT_KNEE
            ),
            upThreshold = 100.0,
            downThreshold = 90.0,
            feedbackMap = mapOf(
                "up" to "Good Lift!", "down" to "Ready",
                "transition_up" to "Lift Knee", "transition_down" to "Return"
            ),
            invertStages = true
        ),
        ExerciseConfig(
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
        ExerciseConfig(
            name = "WALL PUSH-UPS",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_SHOULDER, WorkoutActivity.LEFT_ELBOW, WorkoutActivity.LEFT_WRIST
            ),
            upThreshold = 160.0,
            downThreshold = 90.0,
            feedbackMap = mapOf(
                "up" to "Good Press", "down" to "Ready",
                "transition_up" to "Push Away", "transition_down" to "Lean In"
            ),
            invertStages = true
        ),
        ExerciseConfig(
            name = "ARM CIRCLES",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_ELBOW, WorkoutActivity.LEFT_SHOULDER, WorkoutActivity.LEFT_HIP
            ),
            exerciseType = "timed",
            correctFormAngle = 30.0,
            feedbackMap = mapOf(
                "correct" to "Keep Circling",
                "incorrect" to "Raise Arms"
            )
        ),
        ExerciseConfig(
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
        ExerciseConfig(
            name = "REVERSE LUNGES",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_HIP, WorkoutActivity.LEFT_KNEE, WorkoutActivity.LEFT_ANKLE
            ),
            upThreshold = 160.0,
            downThreshold = 100.0,
            feedbackMap = mapOf(
                "up" to "Ready", "down" to "Good Lunge",
                "transition_up" to "Return", "transition_down" to "Step Back"
            )
        ),
        ExerciseConfig(
            name = "FORWARD FOLD",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_SHOULDER, WorkoutActivity.LEFT_HIP, WorkoutActivity.LEFT_KNEE
            ),
            exerciseType = "timed",
            correctFormAngle = 160.0,
            feedbackMap = mapOf(
                "correct" to "Hold Stretch",
                "incorrect" to "Straighten Back"
            )
        ),
        ExerciseConfig(
            name = "CAT-COW STRETCH",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_SHOULDER, WorkoutActivity.LEFT_HIP, WorkoutActivity.LEFT_KNEE
            ),
            upThreshold = 100.0,
            downThreshold = 80.0,
            feedbackMap = mapOf(
                "up" to "Cow Pose", "down" to "Cat Pose",
                "transition_up" to "Arch Back", "transition_down" to "Round Spine"
            ),
            invertStages = true
        ),
        ExerciseConfig(
            name = "CHILD'S POSE",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_SHOULDER, WorkoutActivity.LEFT_HIP, WorkoutActivity.LEFT_KNEE
            ),
            exerciseType = "timed",
            correctFormAngle = 80.0,
            feedbackMap = mapOf(
                "correct" to "Hold and Breathe",
                "incorrect" to "Sit Back on Heels"
            ),
            invertStages = true
        ),
        ExerciseConfig(
            name = "COBRA POSE",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_HIP, WorkoutActivity.LEFT_SHOULDER, WorkoutActivity.LEFT_ELBOW
            ),
            exerciseType = "timed",
            correctFormAngle = 150.0,
            feedbackMap = mapOf(
                "correct" to "Hold Pose",
                "incorrect" to "Lift Chest"
            )
        ),
        ExerciseConfig(
            name = "DOWNWARD DOG",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_ANKLE, WorkoutActivity.LEFT_HIP, WorkoutActivity.LEFT_WRIST
            ),
            exerciseType = "timed",
            correctFormAngle = 150.0,
            feedbackMap = mapOf(
                "correct" to "Hold the V-Shape",
                "incorrect" to "Push Hips Up"
            )
        ),
        ExerciseConfig(
            name = "DIAMOND PUSH-UP",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_SHOULDER, WorkoutActivity.LEFT_ELBOW, WorkoutActivity.LEFT_WRIST
            ),
            upThreshold = 160.0,
            downThreshold = 90.0,
            feedbackMap = mapOf(
                "up" to "Good Rep", "down" to "Ready",
                "transition_up" to "Push Up", "transition_down" to "Go Lower"
            ),
            invertStages = true
        ),
        ExerciseConfig(
            name = "FLUTTER KICKS",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_HIP, WorkoutActivity.LEFT_KNEE, WorkoutActivity.LEFT_ANKLE
            ),
            exerciseType = "timed",
            correctFormAngle = 160.0,
            feedbackMap = mapOf(
                "correct" to "Keep Kicking",
                "incorrect" to "Keep Legs Straight"
            )
        ),
        ExerciseConfig(
            name = "SCISSOR KICKS",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_HIP, WorkoutActivity.LEFT_KNEE, WorkoutActivity.LEFT_ANKLE
            ),
            exerciseType = "timed",
            correctFormAngle = 160.0,
            feedbackMap = mapOf(
                "correct" to "Keep Crossing",
                "incorrect" to "Keep Legs Straight"
            )
        ),
        ExerciseConfig(
            name = "INCHWORM",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_SHOULDER, WorkoutActivity.LEFT_HIP, WorkoutActivity.LEFT_ANKLE
            ),
            upThreshold = 160.0,
            downThreshold = 90.0,
            feedbackMap = mapOf(
                "up" to "Walk Feet In", "down" to "Walk Hands Out",
                "transition_up" to "Walk Feet In", "transition_down" to "Walk Hands Out"
            ),
            invertStages = true
        ),
        ExerciseConfig(
            name = "HIGH PLANK TO LOW PLANK",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_SHOULDER, WorkoutActivity.LEFT_ELBOW, WorkoutActivity.LEFT_WRIST
            ),
            upThreshold = 160.0,
            downThreshold = 90.0,
            feedbackMap = mapOf(
                "up" to "High Plank", "down" to "Low Plank",
                "transition_up" to "Up to Hands", "transition_down" to "Down to Elbows"
            ),
            invertStages = true
        ),
        ExerciseConfig(
            name = "BOXER SHUFFLE",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_KNEE, WorkoutActivity.LEFT_HIP, WorkoutActivity.LEFT_SHOULDER
            ),
            exerciseType = "timed",
            correctFormAngle = 150.0,
            feedbackMap = mapOf(
                "correct" to "Keep Shuffling",
                "incorrect" to "Stay on Toes"
            )
        ),
        ExerciseConfig(
            name = "SIDE BEND",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_SHOULDER, WorkoutActivity.LEFT_HIP, WorkoutActivity.LEFT_KNEE
            ),
            upThreshold = 170.0,
            downThreshold = 155.0,
            feedbackMap = mapOf(
                "up" to "Ready", "down" to "Good Bend",
                "transition_up" to "Return to Center", "transition_down" to "Bend Sideways"
            )
        ),
        ExerciseConfig(
            name = "T-POSE HOLD",
            landmarksToTrack = listOf(
                WorkoutActivity.LEFT_HIP, WorkoutActivity.LEFT_SHOULDER, WorkoutActivity.LEFT_WRIST
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