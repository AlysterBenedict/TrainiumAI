package com.example.aifitnesscoach

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource

// Color Tokens defined as dynamic state properties
var BrandLime by mutableStateOf(Color(0xFFCCFF00))
var PrimaryFixed by mutableStateOf(Color(0xFFC3F400))
var PrimaryFixedDim by mutableStateOf(Color(0xFFABD600))
var OnPrimaryFixed by mutableStateOf(Color(0xFF161E00))
var GradientEnd by mutableStateOf(Color(0xFFE5FF54))

var BackgroundBlack by mutableStateOf(Color(0xFF000000))
var SurfaceLow by mutableStateOf(Color(0xFF111111))
var SurfaceContainer by mutableStateOf(Color(0xFF1F1F1F))
var SurfaceVariant by mutableStateOf(Color(0xFF353535))

var TextPrimary by mutableStateOf(Color(0xFFFFFFFF))
var TextSecondary by mutableStateOf(Color(0xFF9CA3AF))
var CardOverlayColor by mutableStateOf(Color(0xFFFFFFFF))
val AccentRed = Color(0xFFFF5252)

// Helper to update the global color tokens based on palette and dark mode
data class ThemeColors(
    val brand: Color,
    val primaryF: Color,
    val primaryFD: Color,
    val onPrimaryF: Color,
    val gradEnd: Color,
    val background: Color,
    val surfaceLow: Color,
    val surfaceContainer: Color,
    val surfaceVariant: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val cardOverlay: Color
)

fun getTargetThemeColors(
    context: Context,
    palette: String,
    isDark: Boolean
): ThemeColors {
    val (primaryColors, otherColors) = when (palette) {
        "red" -> Triple(Color(0xFFFF3B30), Color(0xFFFF8A80), Color(0xFFFF5252)) to Pair(Color(0xFFFFFFFF), Color(0xFFFF5E5E))
        "blue" -> Triple(Color(0xFF007AFF), Color(0xFF82B1FF), Color(0xFF448AFF)) to Pair(Color(0xFFFFFFFF), Color(0xFF5AC8FA))
        "purple" -> Triple(Color(0xFFAF52DE), Color(0xFFE040FB), Color(0xFFD500F9)) to Pair(Color(0xFFFFFFFF), Color(0xFFBF5AF2))
        "orange" -> Triple(Color(0xFFFF9500), Color(0xFFFFB74D), Color(0xFFFFA726)) to Pair(Color(0xFF000000), Color(0xFFFFD60A))
        "pink" -> Triple(Color(0xFFFF2D55), Color(0xFFFF80AB), Color(0xFFFF4081)) to Pair(Color(0xFFFFFFFF), Color(0xFFFF6480))
        "yellow" -> Triple(Color(0xFFFFCC00), Color(0xFFFFE082), Color(0xFFFFD54F)) to Pair(Color(0xFF000000), Color(0xFFFFF066))
        "material" -> {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val colorScheme = if (isDark) {
                    dynamicDarkColorScheme(context)
                } else {
                    dynamicLightColorScheme(context)
                }
                Triple(colorScheme.primary, colorScheme.primaryContainer, colorScheme.secondary) to Pair(colorScheme.onPrimaryContainer, colorScheme.tertiary)
            } else {
                Triple(Color(0xFFCCFF00), Color(0xFFC3F400), Color(0xFFABD600)) to Pair(Color(0xFF161E00), Color(0xFFE5FF54))
            }
        }
        else -> { // "lime"
            Triple(Color(0xFFCCFF00), Color(0xFFC3F400), Color(0xFFABD600)) to Pair(Color(0xFF161E00), Color(0xFFE5FF54))
        }
    }

    val (brand, primaryF, primaryFD) = primaryColors
    val (onPrimaryF, gradEnd) = otherColors

    val bg: Color
    val surfLow: Color
    val surfContainer: Color
    val surfVar: Color
    val textPrim: Color
    val textSec: Color
    val overlay: Color

    if (isDark) {
        bg = Color(0xFF000000)
        surfLow = Color(0xFF111111)
        surfContainer = Color(0xFF1F1F1F)
        surfVar = Color(0xFF353535)
        textPrim = Color(0xFFFFFFFF)
        textSec = Color(0xFF9CA3AF)
        overlay = Color.White
    } else {
        bg = Color(0xFFF9F9F9)
        surfLow = Color(0xFFEEEEEE)
        surfContainer = Color(0xFFE0E0E0)
        surfVar = Color(0xFFCCCCCC)
        textPrim = Color(0xFF111111)
        textSec = Color(0xFF555555)
        overlay = Color.Black
    }

    return ThemeColors(
        brand = brand,
        primaryF = primaryF,
        primaryFD = primaryFD,
        onPrimaryF = onPrimaryF,
        gradEnd = gradEnd,
        background = bg,
        surfaceLow = surfLow,
        surfaceContainer = surfContainer,
        surfaceVariant = surfVar,
        textPrimary = textPrim,
        textSecondary = textSec,
        cardOverlay = overlay
    )
}

fun applyThemeColors(colors: ThemeColors) {
    BrandLime = colors.brand
    PrimaryFixed = colors.primaryF
    PrimaryFixedDim = colors.primaryFD
    OnPrimaryFixed = colors.onPrimaryF
    GradientEnd = colors.gradEnd
    BackgroundBlack = colors.background
    SurfaceLow = colors.surfaceLow
    SurfaceContainer = colors.surfaceContainer
    SurfaceVariant = colors.surfaceVariant
    TextPrimary = colors.textPrimary
    TextSecondary = colors.textSecondary
    CardOverlayColor = colors.cardOverlay
}

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
    val context = LocalContext.current
    val prefs = remember { context.getTrainiumPrefs("app_prefs") }

    var colorPalette by remember {
        mutableStateOf(prefs.getString("theme_color_palette", "lime") ?: "lime")
    }
    var themeMode by remember {
        mutableStateOf(prefs.getString("theme_mode", "dark") ?: "dark")
    }

    val listener = remember {
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "theme_color_palette") {
                colorPalette = prefs.getString("theme_color_palette", "lime") ?: "lime"
            } else if (key == "theme_mode") {
                themeMode = prefs.getString("theme_mode", "dark") ?: "dark"
            }
        }
    }

    DisposableEffect(prefs, listener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        "system" -> isSystemDark
        else -> true
    }

    // Update colors asynchronously/safely using SideEffect to prevent writing to state during composition
    val targetColors = remember(colorPalette, isDark) { getTargetThemeColors(context, colorPalette, isDark) }
    SideEffect {
        if (BrandLime != targetColors.brand || BackgroundBlack != targetColors.background || CardOverlayColor != targetColors.cardOverlay) {
            applyThemeColors(targetColors)
        }
    }

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = BrandLime,
            background = BackgroundBlack,
            surface = SurfaceLow,
            onPrimary = Color.Black,
            onBackground = TextPrimary,
            onSurface = TextPrimary
        )
    } else {
        lightColorScheme(
            primary = BrandLime,
            background = BackgroundBlack,
            surface = SurfaceLow,
            onPrimary = Color.White,
            onBackground = TextPrimary,
            onSurface = TextPrimary
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TrainiumTypography,
        content = content
    )
}


// Bounce interaction modifier
fun Modifier.bounceClick(onClick: () -> Unit) = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        ),
        label = "bounceScale"
    )

    this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

private fun String.toTitleCase(): String {
    if (this.isBlank()) return this
    return this.split(" ")
        .filter { it.isNotEmpty() }
        .joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }
}

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
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        BrandLime,
                        GradientEnd
                    )
                )
            )
            .bounceClick { onClick() },
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides OnPrimaryFixed) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Text(
                    text = text.toTitleCase(),
                    color = OnPrimaryFixed,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
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
        SolidColor(CardOverlayColor.copy(alpha = 0.08f))                                                                                                                                                                                                                                                                                              
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
                CardOverlayColor.copy(alpha = 0.08f),
                CardOverlayColor.copy(alpha = 0.02f)
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

@Composable
fun TrainiumMetricStyleCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            SurfaceLow.copy(alpha = 0.97f),
            SurfaceLow.copy(alpha = 0.93f)
        )
    )
    val borderBrush = Brush.verticalGradient(
        colors = listOf(
            CardOverlayColor.copy(alpha = 0.18f),
            CardOverlayColor.copy(alpha = 0.04f)
        )
    )

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
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        SurfaceLow.copy(alpha = 0.97f),
                        SurfaceLow.copy(alpha = 0.93f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        CardOverlayColor.copy(alpha = 0.18f),
                        CardOverlayColor.copy(alpha = 0.04f)
                    )
                ),
                shape = RoundedCornerShape(38.dp)
            )
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
                icon = ImageVector.vectorResource(id = R.drawable.google_logo),
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
