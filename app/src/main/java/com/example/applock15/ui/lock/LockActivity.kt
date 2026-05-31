package com.locker.app.ui.lock

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.locker.app.data.LockedAppsRepository
import com.locker.app.data.SecurePreferences
import com.locker.app.service.AppLockAccessibilityService
import com.locker.app.ui.theme.AppLockerTheme

class LockActivity : FragmentActivity() {

    private lateinit var securePrefs: SecurePreferences
    private lateinit var repository: LockedAppsRepository
    private var targetPackage = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Window security flags ─────────────────────────────────────────────
        //
        // FLAG_SECURE on LockActivity's own window:
        //   • Prevents the lock screen PIN / biometric prompt from appearing in
        //     screenshots or screen recordings.
        //   • Causes Android Recents to show a blank/secure placeholder for
        //     LockActivity's task snapshot instead of the lock UI.
        //
        // setFlags(flag, mask) is the documented pattern for targeting a single flag.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        // Android 12+: additionally opt out of the automatic Recents screenshot
        // that is taken when the activity is backgrounded.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setRecentsScreenshotEnabled(false)
        }

        // Show above the device lock screen; wake the display when triggered.
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        securePrefs   = SecurePreferences(this)
        repository    = LockedAppsRepository(this)
        targetPackage = intent.getStringExtra(EXTRA_PACKAGE) ?: run { finish(); return }

        // Suppress the enter transition animation so LockActivity appears instantly.
        // The theme already sets windowAnimationStyle to @style/Animation.AppLocker.None;
        // this call is a belt-and-suspenders override for the activity transition.
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        // Back must NEVER send the user back into the locked app.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })

        enableEdgeToEdge()
        setContent {
            AppLockerTheme {
                LockScreen(
                    packageName  = targetPackage,
                    hasBiometric = canUseBiometric(),
                    hasPin       = securePrefs.hasPin(),
                    onBiometricAuth = ::showBiometricPrompt,
                    onPinSubmit     = ::verifyPin
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isVisible = true

        // singleTask reuse: update target if a new intent was delivered.
        intent.getStringExtra(EXTRA_PACKAGE)?.takeIf { it.isNotEmpty() }
            ?.let { targetPackage = it }

        // The overlay has covered the screen since the service detected the locked app.
        // Now that our window is composited on screen the overlay is redundant.
        AppLockAccessibilityService.hideOverlay()

        if (canUseBiometric() && securePrefs.hasPin()) showBiometricPrompt()
    }

    override fun onPause() {
        super.onPause()
        isVisible = false
        // pendingLock is intentionally NOT cleared here — only cleared on successful auth.
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_PACKAGE)?.takeIf { it.isNotEmpty() }
            ?.let { targetPackage = it }
    }

    override fun onDestroy() {
        super.onDestroy()
        isVisible = false
    }

    // ── Authentication ────────────────────────────────────────────────────────

    private fun canUseBiometric(): Boolean =
        BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS

    private fun showBiometricPrompt() {
        BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) = onUnlocked()
            }
        ).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock App")
                .setSubtitle(targetPackage.substringAfterLast('.'))
                .setNegativeButtonText("Use PIN")
                .build()
        )
    }

    private fun verifyPin(entered: String) {
        if (entered == securePrefs.getPin()) onUnlocked()
        else Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
    }

    private fun onUnlocked() {
        repository.markTemporarilyUnlocked(targetPackage)
        repository.clearPendingLock()
        AppLockAccessibilityService.hideOverlay()
        finish()
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        // Not calling finish(): keeping the singleTask instance alive lets the
        // service bring it to front instantly without recreating it.
    }

    companion object {
        const val EXTRA_PACKAGE = "pkg"

        /** true while the lock screen is on screen (between onResume and onPause). */
        @Volatile var isVisible = false

        fun createIntent(context: Context, pkg: String): Intent =
            Intent(context, LockActivity::class.java).putExtra(EXTRA_PACKAGE, pkg)
    }
}

// ── Composables ───────────────────────────────────────────────────────────────

@Composable
private fun LockScreen(
    packageName: String,
    hasBiometric: Boolean,
    hasPin: Boolean,
    onBiometricAuth: () -> Unit,
    onPinSubmit: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(!hasBiometric || !hasPin) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "App Locked",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = packageName.substringAfterLast('.'),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(40.dp))

            if (showPin) {
                PinDots(length = pin.length)
                Spacer(Modifier.height(28.dp))
                NumPad(
                    onDigit = { d ->
                        if (pin.length < 6) {
                            pin += d
                            if (pin.length == 6) { onPinSubmit(pin); pin = "" }
                        }
                    },
                    onDelete = { if (pin.isNotEmpty()) pin = pin.dropLast(1) }
                )
                if (hasBiometric) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { pin = ""; showPin = false; onBiometricAuth() }) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Use Biometric")
                    }
                }
            } else {
                Button(
                    onClick = onBiometricAuth,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Authenticate with Biometrics")
                }
                if (hasPin) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { showPin = true }) { Text("Use PIN instead") }
                }
            }
        }
    }
}

@Composable
private fun PinDots(length: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(6) { i ->
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(
                        if (i < length) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}

@Composable
private fun NumPad(onDigit: (String) -> Unit, onDelete: () -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫")
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .then(
                                if (key.isNotEmpty()) Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        if (key == "⌫") onDelete() else onDigit(key)
                                    }
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (key.isNotEmpty()) {
                            Text(
                                text = key,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
