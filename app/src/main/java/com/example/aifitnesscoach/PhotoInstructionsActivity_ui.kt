package com.example.aifitnesscoach

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class PhotoInstructionsActivity_ui : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TrainiumTheme {
                PhotoInstructionsScreen(
                    onStartScan = {
                        startActivity(Intent(this, CameraCaptureActivity_ui::class.java))
                        finish()
                    },
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
fun PhotoInstructionsScreen(
    onStartScan: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack),
        contentAlignment = Alignment.Center
    ) {
        // Ambient glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.4f)
                .align(Alignment.TopCenter)
                .background(
                    Brush.radialGradient(
                        colors = listOf(BrandLime.copy(alpha = 0.1f), Color.Transparent),
                    )
                )
        )

        val scrollState = rememberScrollState()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp)
                .padding(top = 88.dp, bottom = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 1. Red Privacy Disclaimer Above Camera Icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentRed.copy(alpha = 0.08f))
                    .border(1.dp, AccentRed.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Privacy Scan Mode",
                    tint = AccentRed,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "100% ON-DEVICE • PHOTOS NEVER LEAVE YOUR PHONE",
                    color = AccentRed,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Camera Circle Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(BrandLime.copy(alpha = 0.08f))
                    .border(2.dp, BrandLime, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Camera Scan",
                    tint = BrandLime,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Biometric Scan Setup",
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "To design a personalized fitness plan, we require a frontal-view photo and a side-view photo to determine key biometric dimensions.",
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            // 3. Side-by-side Posture Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Card 1: Frontal Pose
                TrainiumGlassCard(
                    isActive = false,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "FRONT VIEW",
                            color = BrandLime,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        PoseIllustration(
                            isFront = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        BulletPoint("A-Pose stance")
                        BulletPoint("Arms 45° apart")
                        BulletPoint("Stand straight")
                        BulletPoint("Feet apart")
                    }
                }

                // Card 2: Side Pose
                TrainiumGlassCard(
                    isActive = false,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "SIDE PROFILE",
                            color = BrandLime,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        PoseIllustration(
                            isFront = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        BulletPoint("Profile view")
                        BulletPoint("Arm straight down")
                        BulletPoint("Face forward")
                        BulletPoint("Feet together")
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 4. Tips Box (Glass card)
            TrainiumGlassCard(
                isActive = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(BrandLime.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = BrandLime,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Tip: Stand 6-8 feet away in a well-lit area. Ensure your full body is visible in the frame from head to toe.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 5. Action Button
            TrainiumButton(
                text = "START SCAN",
                onClick = onStartScan
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Subpage Header (drawn last so it stays on top of the scrollable content)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(72.dp)
                .background(BackgroundBlack.copy(alpha = 0.85f))
                .border(width = 1.dp, color = CardOverlayColor.copy(alpha = 0.05f))
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Scan Setup",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun BulletPoint(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp, horizontal = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(BrandLime)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun PoseIllustration(
    isFront: Boolean,
    modifier: Modifier = Modifier
) {
    val brandColor = BrandLime
    val secondaryColor = TextSecondary

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Common dimensions
        val centerX = w / 2f
        val headRadius = h * 0.09f
        val headCenterY = h * 0.16f
        val neckTop = headCenterY + headRadius
        val neckHeight = h * 0.025f
        val neckBottom = neckTop + neckHeight
        val shoulderY = neckBottom
        val shoulderHalfWidth = w * 0.22f
        val hipY = h * 0.52f
        val hipHalfWidth = w * 0.15f
        val kneeY = h * 0.72f
        val ankleY = h * 0.9f

        // Draw background scanning grid rings
        val gridColor = brandColor.copy(alpha = 0.08f)
        val strokeWidthGrid = 1.dp.toPx()
        drawCircle(
            color = gridColor,
            radius = h * 0.35f,
            center = androidx.compose.ui.geometry.Offset(centerX, h * 0.5f),
            style = Stroke(width = strokeWidthGrid, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
        )
        drawCircle(
            color = gridColor,
            radius = h * 0.2f,
            center = androidx.compose.ui.geometry.Offset(centerX, h * 0.5f),
            style = Stroke(width = strokeWidthGrid)
        )

        // Draw horizontal scanning lasers
        val laserColor = brandColor.copy(alpha = 0.25f)
        drawLine(
            color = laserColor,
            start = androidx.compose.ui.geometry.Offset(0f, shoulderY),
            end = androidx.compose.ui.geometry.Offset(w, shoulderY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
        )
        drawLine(
            color = laserColor,
            start = androidx.compose.ui.geometry.Offset(0f, hipY),
            end = androidx.compose.ui.geometry.Offset(w, hipY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
        )

        if (isFront) {
            // FRONT POSE (A-POSE)
            val head = androidx.compose.ui.geometry.Offset(centerX, headCenterY)
            val neck = androidx.compose.ui.geometry.Offset(centerX, neckBottom)
            val leftShoulder = androidx.compose.ui.geometry.Offset(centerX - shoulderHalfWidth, shoulderY)
            val rightShoulder = androidx.compose.ui.geometry.Offset(centerX + shoulderHalfWidth, shoulderY)
            
            // Arms apart at angle
            val armLength = h * 0.24f
            val armAngle = Math.toRadians(35.0)
            val armDx = (armLength * Math.sin(armAngle)).toFloat()
            val armDy = (armLength * Math.cos(armAngle)).toFloat()
            
            val leftElbow = androidx.compose.ui.geometry.Offset(centerX - shoulderHalfWidth - armDx * 0.5f, shoulderY + armDy * 0.5f)
            val leftWrist = androidx.compose.ui.geometry.Offset(centerX - shoulderHalfWidth - armDx, shoulderY + armDy)
            
            val rightElbow = androidx.compose.ui.geometry.Offset(centerX + shoulderHalfWidth + armDx * 0.5f, shoulderY + armDy * 0.5f)
            val rightWrist = androidx.compose.ui.geometry.Offset(centerX + shoulderHalfWidth + armDx, shoulderY + armDy)
            
            val midHip = androidx.compose.ui.geometry.Offset(centerX, hipY)
            val leftHip = androidx.compose.ui.geometry.Offset(centerX - hipHalfWidth, hipY)
            val rightHip = androidx.compose.ui.geometry.Offset(centerX + hipHalfWidth, hipY)
            
            // Legs slightly apart
            val legLength = ankleY - hipY
            val legAngle = Math.toRadians(8.0)
            val legDx = (legLength * Math.sin(legAngle)).toFloat()
            
            val leftKnee = androidx.compose.ui.geometry.Offset(centerX - hipHalfWidth - legDx * 0.5f, kneeY)
            val leftAnkle = androidx.compose.ui.geometry.Offset(centerX - hipHalfWidth - legDx, ankleY)
            
            val rightKnee = androidx.compose.ui.geometry.Offset(centerX + hipHalfWidth + legDx * 0.5f, kneeY)
            val rightAnkle = androidx.compose.ui.geometry.Offset(centerX + hipHalfWidth + legDx, ankleY)

            // 1. Silhouette / Glow Path
            val bodyPath = Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(head, headRadius))
            }
            drawPath(bodyPath, color = brandColor.copy(alpha = 0.12f))
            drawPath(bodyPath, color = brandColor.copy(alpha = 0.4f), style = Stroke(width = 2.dp.toPx()))

            // 2. Skeletal Connections
            val strokeWidthBone = 3.dp.toPx()
            val boneColor = brandColor

            // Spine & Shoulders
            drawLine(color = boneColor, start = head, end = neck, strokeWidth = strokeWidthBone, cap = StrokeCap.Round)
            drawLine(color = boneColor, start = neck, end = midHip, strokeWidth = strokeWidthBone, cap = StrokeCap.Round)
            drawLine(color = boneColor, start = leftShoulder, end = rightShoulder, strokeWidth = strokeWidthBone, cap = StrokeCap.Round)
            
            // Left Arm
            drawLine(color = boneColor, start = leftShoulder, end = leftElbow, strokeWidth = strokeWidthBone, cap = StrokeCap.Round)
            drawLine(color = boneColor, start = leftElbow, end = leftWrist, strokeWidth = strokeWidthBone, cap = StrokeCap.Round)
            
            // Right Arm
            drawLine(color = boneColor, start = rightShoulder, end = rightElbow, strokeWidth = strokeWidthBone, cap = StrokeCap.Round)
            drawLine(color = boneColor, start = rightElbow, end = rightWrist, strokeWidth = strokeWidthBone, cap = StrokeCap.Round)
            
            // Hips & Legs
            drawLine(color = boneColor, start = leftHip, end = rightHip, strokeWidth = strokeWidthBone, cap = StrokeCap.Round)
            drawLine(color = boneColor, start = neck, end = leftHip, strokeWidth = strokeWidthBone, cap = StrokeCap.Round)
            drawLine(color = boneColor, start = neck, end = rightHip, strokeWidth = strokeWidthBone, cap = StrokeCap.Round)
            
            drawLine(color = boneColor, start = leftHip, end = leftKnee, strokeWidth = strokeWidthBone, cap = StrokeCap.Round)
            drawLine(color = boneColor, start = leftKnee, end = leftAnkle, strokeWidth = strokeWidthBone, cap = StrokeCap.Round)
            
            drawLine(color = boneColor, start = rightHip, end = rightKnee, strokeWidth = strokeWidthBone, cap = StrokeCap.Round)
            drawLine(color = boneColor, start = rightKnee, end = rightAnkle, strokeWidth = strokeWidthBone, cap = StrokeCap.Round)

            // 3. Joint Nodes
            val joints = listOf(neck, leftShoulder, rightShoulder, leftElbow, rightElbow, leftWrist, rightWrist, leftHip, rightHip, leftKnee, rightKnee, leftAnkle, rightAnkle)
            joints.forEach { joint ->
                drawCircle(color = BackgroundBlack, radius = 5.dp.toPx(), center = joint)
                drawCircle(color = brandColor, radius = 3.dp.toPx(), center = joint)
            }

            // Draw arm angles indicators
            val arcSize = h * 0.12f
            drawArc(
                color = secondaryColor.copy(alpha = 0.5f),
                startAngle = 90f,
                sweepAngle = 35f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(leftShoulder.x - arcSize / 2, leftShoulder.y - arcSize / 2),
                size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f))
            )
            drawArc(
                color = secondaryColor.copy(alpha = 0.5f),
                startAngle = 55f,
                sweepAngle = 35f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(rightShoulder.x - arcSize / 2, rightShoulder.y - arcSize / 2),
                size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f))
            )

        } else {
            // SIDE POSE (PROFILE VIEW)
            val profileX = centerX - w * 0.05f
            
            val head = androidx.compose.ui.geometry.Offset(profileX, headCenterY)
            val neck = androidx.compose.ui.geometry.Offset(profileX - w * 0.02f, neckBottom)
            val shoulder = androidx.compose.ui.geometry.Offset(profileX - w * 0.02f, shoulderY)
            val hip = androidx.compose.ui.geometry.Offset(profileX - w * 0.03f, hipY)
            val knee = androidx.compose.ui.geometry.Offset(profileX - w * 0.01f, kneeY)
            val ankle = androidx.compose.ui.geometry.Offset(profileX - w * 0.01f, ankleY)
            
            // Arm straight down at the side
            val armLength = h * 0.25f
            val elbow = androidx.compose.ui.geometry.Offset(profileX - w * 0.02f, shoulderY + armLength * 0.5f)
            val wrist = androidx.compose.ui.geometry.Offset(profileX - w * 0.02f, shoulderY + armLength)

            // 1. Silhouette Path
            val bodyPath = Path().apply {
                moveTo(head.x - headRadius, head.y)
                cubicTo(
                    head.x - headRadius, head.y - headRadius,
                    head.x + headRadius * 0.8f, head.y - headRadius,
                    head.x + headRadius * 0.8f, head.y
                )
                // Nose profile
                lineTo(head.x + headRadius * 1.2f, head.y + headRadius * 0.1f)
                lineTo(head.x + headRadius * 0.7f, head.y + headRadius * 0.3f)
                // Chin profile
                lineTo(head.x + headRadius * 0.8f, head.y + headRadius * 0.5f)
                lineTo(head.x + headRadius * 0.3f, head.y + headRadius)
                lineTo(head.x - headRadius * 0.8f, head.y + headRadius * 0.9f)
                close()
            }
            drawPath(bodyPath, color = brandColor.copy(alpha = 0.12f))
            drawPath(bodyPath, color = brandColor.copy(alpha = 0.4f), style = Stroke(width = 2.dp.toPx()))

            // 2. Skeletal Connections
            val strokeWidthBone = 3.dp.toPx()
            val boneColor = brandColor
            
            // Spine & Legs
            drawLine(color = boneColor, start = head, end = neck, strokeWidth = strokeWidthBone, cap = StrokeCap.Round)
            drawLine(color = boneColor, start = neck, end = hip, strokeWidth = strokeWidthBone, cap = StrokeCap.Round)
            drawLine(color = boneColor, start = hip, end = knee, strokeWidth = strokeWidthBone, cap = StrokeCap.Round)
            drawLine(color = boneColor, start = knee, end = ankle, strokeWidth = strokeWidthBone, cap = StrokeCap.Round)
            
            // Arm straight down
            drawLine(color = boneColor, start = shoulder, end = elbow, strokeWidth = strokeWidthBone, cap = StrokeCap.Round)
            drawLine(color = boneColor, start = elbow, end = wrist, strokeWidth = strokeWidthBone, cap = StrokeCap.Round)

            // 3. Joint Nodes
            val joints = listOf(neck, shoulder, elbow, wrist, hip, knee, ankle)
            joints.forEach { joint ->
                drawCircle(color = BackgroundBlack, radius = 5.dp.toPx(), center = joint)
                drawCircle(color = brandColor, radius = 3.dp.toPx(), center = joint)
            }

            // Posture alignment line
            drawLine(
                color = secondaryColor.copy(alpha = 0.5f),
                start = androidx.compose.ui.geometry.Offset(profileX - w * 0.15f, headCenterY - headRadius),
                end = androidx.compose.ui.geometry.Offset(profileX - w * 0.15f, ankleY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
            )
        }
    }
}

