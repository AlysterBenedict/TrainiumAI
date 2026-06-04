package com.example.aifitnesscoach

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Color Tokens
val BrandLime = Color(0xFFCCFF00)
val PrimaryFixed = Color(0xFFC3F400)
val PrimaryFixedDim = Color(0xFFABD600)
val OnPrimaryFixed = Color(0xFF161E00)
val GradientEnd = Color(0xFFE5FF54)

val BackgroundBlack = Color(0xFF000000)
val SurfaceLow = Color(0xFF111111)
val SurfaceContainer = Color(0xFF1F1F1F)
val SurfaceVariant = Color(0xFF353535)

val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF9CA3AF)
val AccentRed = Color(0xFFFF5252)

// Typography
val InterFont = FontFamily.Default // Simple default fallback

val TrainiumTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.01).sp
    ),
    titleLarge = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun TrainiumTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        primary = BrandLime,
        background = BackgroundBlack,
        surface = SurfaceLow,
        onPrimary = Color.Black,
        onBackground = TextPrimary,
        onSurface = TextPrimary
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TrainiumTypography,
        content = content
    )
}

// Bounce interaction modifier
fun Modifier.bounceClick(onClick: () -> Unit) = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "bounceScale")

    this
        .scale(scale)
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                },
                onTap = { onClick() }
            )
        }
}

// Reusable Components
@Composable
fun TrainiumButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(PrimaryFixed, GradientEnd)
                )
            )
            .bounceClick { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Text(
                text = text,
                color = OnPrimaryFixed,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            if (icon != null) {
                Spacer(modifier = Modifier.width(8.dp))
                icon()
            }
        }
    }
}

@Composable
fun TrainiumGlassCard(
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderBrush = if (isActive) {
        Brush.linearGradient(colors = listOf(PrimaryFixed.copy(alpha = 0.6f), PrimaryFixed.copy(alpha = 0.15f)))
    } else {
        SolidColor(Color.White.copy(alpha = 0.08f))
    }

    val backgroundBrush = if (isActive) {
        Brush.verticalGradient(
            colors = listOf(
                PrimaryFixed.copy(alpha = 0.16f),
                PrimaryFixed.copy(alpha = 0.03f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.08f),
                Color.White.copy(alpha = 0.02f)
            )
        )
    }

    val shadowModifier = Modifier.border(1.dp, borderBrush, RoundedCornerShape(16.dp))

    val clickModifier = if (onClick != null) {
        Modifier.bounceClick(onClick)
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .then(clickModifier)
            .then(shadowModifier)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundBrush)
            .padding(16.dp),
        content = content
    )
}

// Exercise Icon Mapper Helper
fun getExerciseIcon(exerciseName: String): androidx.compose.ui.graphics.vector.ImageVector {
    val name = exerciseName.uppercase()
    return when {
        name.contains("SQUAT") -> androidx.compose.material.icons.Icons.Filled.AccessibilityNew
        name.contains("CURL") || name.contains("PRESS") || name.contains("ROW") || name.contains("DIP") || name.contains("DEADLIFT") || name.contains("PULL-UP") || name.contains("RAISE") -> androidx.compose.material.icons.Icons.Filled.FitnessCenter
        name.contains("LUNGE") || name.contains("WALK") || name.contains("FOLD") || name.contains("MORNING") -> androidx.compose.material.icons.Icons.Filled.DirectionsWalk
        name.contains("PLANK") || name.contains("BRIDGE") || name.contains("KICK") || name.contains("HYDRANT") || name.contains("TAP") || name.contains("STRETCH") || name.contains("POSE") || name.contains("DOG") || name.contains("CRUNCH") || name.contains("TWIST") -> androidx.compose.material.icons.Icons.Filled.SelfImprovement
        name.contains("RUN") || name.contains("KNEE") || name.contains("CLIMBER") || name.contains("SHUFFLE") || name.contains("JACK") -> androidx.compose.material.icons.Icons.Filled.DirectionsRun
        name.contains("BURPEE") || name.contains("INCHWORM") || name.contains("PUSH-UP") -> androidx.compose.material.icons.Icons.Filled.SportsGymnastics
        else -> androidx.compose.material.icons.Icons.Filled.FitnessCenter
    }
}

@Composable
fun BottomNavItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(CircleShape)
            .clickable { onClick() }
            .padding(8.dp)
            .width(56.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) BrandLime else TextSecondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (isActive) BrandLime else TextSecondary,
            fontSize = 10.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun TrainiumBottomDock(
    activeTab: String,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .fillMaxWidth()
            .height(76.dp)
            .clip(RoundedCornerShape(38.dp))
            .background(Color(0xFF0F0F0F).copy(alpha = 0.85f))
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.08f), shape = RoundedCornerShape(38.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(
                label = "Home",
                icon = Icons.Default.Home,
                isActive = activeTab == "home",
                onClick = { onTabSelected("home") }
            )
            BottomNavItem(
                label = "Reports",
                icon = Icons.Default.BarChart,
                isActive = activeTab == "reports",
                onClick = { onTabSelected("reports") }
            )
            BottomNavItem(
                label = "Coach",
                icon = Icons.Default.SmartToy,
                isActive = activeTab == "coach",
                onClick = { onTabSelected("coach") }
            )
            BottomNavItem(
                label = "Profile",
                icon = Icons.Default.Person,
                isActive = activeTab == "profile",
                onClick = { onTabSelected("profile") }
            )
        }
    }
}

fun navigateToTab(context: android.content.Context, tab: String) {
    val intent = when (tab) {
        "home" -> Intent(context, HomeActivity_ui::class.java)
        "reports" -> Intent(context, ReportsActivity_ui::class.java)
        "coach" -> Intent(context, ChatbotActivity_ui::class.java)
        "profile" -> Intent(context, ProfileActivity_ui::class.java)
        else -> return
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
    context.startActivity(intent)
}
