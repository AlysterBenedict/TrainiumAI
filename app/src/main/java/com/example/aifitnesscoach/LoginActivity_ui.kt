package com.example.aifitnesscoach

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class LoginActivity_ui : AppCompatActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var auth: FirebaseAuth
    private var isLoading = mutableStateOf(false)

    companion object {
        private const val RC_SIGN_IN = 9001
        private const val TAG = "LoginActivity_ui"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
        auth = Firebase.auth

        setContent {
            TrainiumTheme {
                LoginScreen(
                    isLoading = isLoading.value,
                    onSignInClicked = { signIn() }
                )
            }
        }
    }

    private fun signIn() {
        isLoading.value = true
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                Log.d(TAG, "firebaseAuthWithGoogle:" + account.id)
                firebaseAuthWithGoogle(account)
            } catch (e: ApiException) {
                Log.w(TAG, "Google sign in failed", e)
                Toast.makeText(this, "Google sign in failed", Toast.LENGTH_SHORT).show()
                isLoading.value = false
            }
        }
    }

    private fun firebaseAuthWithGoogle(acct: GoogleSignInAccount) {
        val credential = GoogleAuthProvider.getCredential(acct.idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithCredential:success")
                    Toast.makeText(this, "Authentication Success.", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, HomeActivity_ui::class.java))
                    finish()
                } else {
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    Toast.makeText(this, "Authentication Failed.", Toast.LENGTH_SHORT).show()
                    isLoading.value = false
                }
            }
    }
}

@Composable
fun LoginScreen(
    isLoading: Boolean,
    onSignInClicked: () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack),
        contentAlignment = Alignment.Center
    ) {
        // Ambient glow at top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.45f)
                .align(Alignment.TopCenter)
                .background(
                    Brush.radialGradient(
                        colors = listOf(BrandLime.copy(alpha = 0.15f), Color.Transparent),
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // Premium glass logo card
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(CardOverlayColor.copy(alpha = 0.04f))
                    .border(1.dp, CardOverlayColor.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_playstore),
                    contentDescription = "Logo",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "TRAINIUM",
                color = TextPrimary,
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "AI FITNESS COACH",
                color = TextSecondary.copy(alpha = 0.65f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            )

            Spacer(modifier = Modifier.height(48.dp))

            var showGuestDialog by remember { mutableStateOf(false) }
            var guestName by remember { mutableStateOf("") }

            if (isLoading) {
                CircularProgressIndicator(
                    color = BrandLime,
                    modifier = Modifier.size(48.dp)
                )
            } else {
                // Sleek Premium Google Sign-In Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFF1E201E))
                        .border(1.dp, CardOverlayColor.copy(alpha = 0.08f), RoundedCornerShape(28.dp))
                        .bounceClick { onSignInClicked() },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.google_logo),
                            contentDescription = "Google Logo",
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = "Sign in with Google",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                // Create Local Account Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(CardOverlayColor.copy(alpha = 0.05f))
                        .border(1.dp, CardOverlayColor.copy(alpha = 0.12f), RoundedCornerShape(28.dp))
                        .bounceClick { showGuestDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Guest",
                            tint = BrandLime,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = "Local Account",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            if (showGuestDialog) {
                AlertDialog(
                    onDismissRequest = { showGuestDialog = false },
                    title = {
                        Text(
                            text = "Create Local Account",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    },
                    text = {
                        Column {
                            Text(
                                text = "Enter your name to start training locally. Your progress will be saved offline on this device.",
                                color = TextSecondary,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            OutlinedTextField(
                                value = guestName,
                                onValueChange = { guestName = it },
                                label = { Text("Your Name", color = TextSecondary) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedBorderColor = BrandLime,
                                    unfocusedBorderColor = CardOverlayColor.copy(alpha = 0.2f),
                                    focusedLabelColor = BrandLime,
                                    cursorColor = BrandLime
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (guestName.isNotBlank()) {
                                    showGuestDialog = false
                                    val globalPrefs = context.getSharedPreferences("global_prefs", Context.MODE_PRIVATE)
                                    globalPrefs.edit()
                                        .putBoolean("is_local_user", true)
                                        .putString("local_user_name", guestName.trim())
                                        .apply()

                                    val intent = Intent(context, HomeActivity_ui::class.java)
                                    context.startActivity(intent)
                                    (context as? AppCompatActivity)?.finish()
                                } else {
                                    Toast.makeText(context, "Please enter your name", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandLime,
                                contentColor = BackgroundBlack
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text("Proceed", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showGuestDialog = false }
                        ) {
                            Text("Cancel", color = CardOverlayColor.copy(alpha = 0.6f))
                        }
                    },
                    containerColor = Color(0xFF1E201E),
                    shape = RoundedCornerShape(24.dp)
                )
            }
        }
    }
}
