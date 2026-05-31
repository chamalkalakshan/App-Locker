package com.locker.app.ui.main

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.locker.app.data.LockedAppsRepository
import com.locker.app.data.RelockMode
import com.locker.app.data.SecurePreferences
import com.locker.app.ui.theme.AppLockerTheme

// ── Navigation ────────────────────────────────────────────────────────────────

private sealed class Screen {
    object Onboarding : Screen()
    object SetPin : Screen()
    data class ConfirmPin(val first: String) : Screen()
    object AppList : Screen()
    object Settings : Screen()
    object ChangePin : Screen()
    object PrivacyPolicy : Screen()
}

// ── Activity ──────────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppLockerTheme { AppLockApp() } }
    }
}

// ── Root composable ───────────────────────────────────────────────────────────

@Composable
private fun AppLockApp() {
    val context = LocalContext.current
    val repo = remember { LockedAppsRepository(context) }
    val secure = remember { SecurePreferences(context) }

    var resumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        (context as? ComponentActivity)?.lifecycle?.addObserver(obs)
        onDispose { (context as? ComponentActivity)?.lifecycle?.removeObserver(obs) }
    }

    val initialScreen: Screen = remember {
        when {
            !repo.isOnboardingComplete() -> Screen.Onboarding
            !secure.hasPin() -> Screen.SetPin
            else -> Screen.AppList
        }
    }
    var screen by remember { mutableStateOf(initialScreen) }

    LaunchedEffect(resumeTick) {
        if (screen is Screen.Onboarding && isServiceEnabled(context)) {
            repo.setOnboardingComplete()
            screen = if (!secure.hasPin()) Screen.SetPin else Screen.AppList
        }
    }

    when (val s = screen) {
        is Screen.Onboarding -> OnboardingScreen(
            serviceEnabled = isServiceEnabled(context),
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            onContinue = {
                if (isServiceEnabled(context)) {
                    repo.setOnboardingComplete()
                    screen = if (!secure.hasPin()) Screen.SetPin else Screen.AppList
                }
            }
        )

        is Screen.SetPin -> SetPinScreen(
            title = "Set a PIN",
            subtitle = "You'll use this to unlock protected apps.",
            onSubmit = { pin -> screen = Screen.ConfirmPin(pin) }
        )

        is Screen.ConfirmPin -> SetPinScreen(
            title = "Confirm PIN",
            subtitle = "Enter the same PIN again to confirm.",
            onSubmit = { pin ->
                if (pin == s.first) {
                    secure.setPin(pin)
                    screen = Screen.AppList
                } else {
                    screen = Screen.SetPin
                }
            }
        )

        is Screen.AppList -> AppListScreen(
            repo = repo,
            onNavigateSettings = { screen = Screen.Settings }
        )

        is Screen.Settings -> SettingsScreen(
            repo = repo,
            secure = secure,
            onBack = { screen = Screen.AppList },
            onChangePin = { screen = Screen.ChangePin },
            onPrivacyPolicy = { screen = Screen.PrivacyPolicy }
        )

        is Screen.ChangePin -> SetPinScreen(
            title = "New PIN",
            subtitle = "Enter a new 6-digit PIN.",
            onSubmit = { pin -> secure.setPin(pin); screen = Screen.Settings }
        )

        is Screen.PrivacyPolicy -> PrivacyPolicyScreen(
            onBack = { screen = Screen.Settings }
        )
    }
}

// ── Onboarding ────────────────────────────────────────────────────────────────

@Composable
private fun OnboardingScreen(
    serviceEnabled: Boolean,
    onOpenSettings: () -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Icon(
            imageVector = Icons.Default.Security,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Welcome to App Locker",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        InfoCard(
            title = "What this app does",
            body = "App Locker shows a lock screen when you open a protected app, requiring your PIN or fingerprint before granting access."
        )
        Spacer(Modifier.height(12.dp))
        InfoCard(
            title = "Why Accessibility permission is needed",
            body = "Android has no public API for detecting the foreground app. The Accessibility Service is the only standard method available. App Locker only reads the package name of the active window — it never reads screen content, text fields, passwords, or any personal data."
        )
        Spacer(Modifier.height(12.dp))
        InfoCard(
            title = "Your privacy",
            body = "No data leaves your device. The app has no internet permission. No analytics, no crash reporting, no cloud sync."
        )
        Spacer(Modifier.height(32.dp))

        if (serviceEnabled) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    "✓ Accessibility service is enabled",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text("Continue →  Set your PIN")
            }
        } else {
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Enable Accessibility Service")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Tap the button above, find \"App Locker\" in the list, and enable it. Then come back here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ── PIN setup ─────────────────────────────────────────────────────────────────

@Composable
private fun SetPinScreen(title: String, subtitle: String, onSubmit: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
        PinDotsRow(length = pin.length)
        Spacer(Modifier.height(28.dp))
        DialPad(
            onDigit = { d ->
                if (pin.length < 6) {
                    pin += d
                    if (pin.length == 6) { onSubmit(pin); pin = "" }
                }
            },
            onDelete = { if (pin.isNotEmpty()) pin = pin.dropLast(1) }
        )
    }
}

// ── App list ──────────────────────────────────────────────────────────────────

/** Preloaded app entry — label is resolved once so filter/sort never call PackageManager. */
private data class AppEntry(val info: ApplicationInfo, val label: String)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun AppListScreen(repo: LockedAppsRepository, onNavigateSettings: () -> Unit) {
    val context = LocalContext.current
    var lockedApps by remember { mutableStateOf(repo.getLockedApps()) }
    var query by remember { mutableStateOf("") }

    // Load all launchable apps once; labels are resolved here so they are never
    // re-read inside the filter/sort logic that runs on every keystroke.
    val allApps: List<AppEntry> = remember {
        val pm = context.packageManager
        val launchers = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0
        ).map { it.activityInfo.packageName }.toSet()

        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { app ->
                app.packageName != context.packageName &&
                    app.packageName !in launchers &&
                    pm.getLaunchIntentForPackage(app.packageName) != null
            }
            .map { AppEntry(it, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.label.lowercase() }
    }

    // Derived list — recomputed only when query or the locked-apps set changes.
    // lockedSection always appears first; within each group apps remain alphabetical.
    val isSearching = query.isNotBlank()
    val lockedSection: List<AppEntry>
    val otherSection: List<AppEntry>
    remember(query, lockedApps) {
        val q = query.trim()
        val filtered = if (q.isBlank()) allApps
        else allApps.filter { e ->
            e.label.contains(q, ignoreCase = true) ||
                e.info.packageName.contains(q, ignoreCase = true)
        }
        filtered.partition { it.info.packageName in lockedApps }
    }.also { (l, o) ->
        lockedSection = l
        otherSection = o
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("App Locker")
                        if (lockedApps.isNotEmpty()) {
                            Text(
                                "${lockedApps.size} app${if (lockedApps.size == 1) "" else "s"} locked",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Search bar ────────────────────────────────────────────────────
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search apps…") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large
            )

            // ── App list ──────────────────────────────────────────────────────
            LazyColumn(modifier = Modifier.weight(1f)) {
                if (!isSearching) {
                    // ── Locked section ────────────────────────────────────────
                    if (lockedSection.isNotEmpty()) {
                        stickyHeader(key = "header_locked") {
                            AppListSectionHeader(
                                "Locked  ·  ${lockedSection.size}",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(lockedSection, key = { it.info.packageName }) { entry ->
                            AppListRow(
                                entry = entry,
                                isLocked = true,
                                onToggle = {
                                    repo.toggleLock(entry.info.packageName)
                                    lockedApps = repo.getLockedApps()
                                }
                            )
                        }
                    }

                    // ── Other / all apps section ──────────────────────────────
                    stickyHeader(key = "header_other") {
                        AppListSectionHeader(
                            if (lockedSection.isEmpty()) "All apps  ·  ${otherSection.size}"
                            else "Other apps  ·  ${otherSection.size}"
                        )
                    }
                    items(otherSection, key = { it.info.packageName }) { entry ->
                        AppListRow(
                            entry = entry,
                            isLocked = false,
                            onToggle = {
                                repo.toggleLock(entry.info.packageName)
                                lockedApps = repo.getLockedApps()
                            }
                        )
                    }
                } else {
                    // ── Search results: locked matches first, then others ─────
                    val results = lockedSection + otherSection
                    if (results.isEmpty()) {
                        item(key = "empty") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No apps match \"${query.trim()}\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        items(results, key = { it.info.packageName }) { entry ->
                            AppListRow(
                                entry = entry,
                                isLocked = entry.info.packageName in lockedApps,
                                onToggle = {
                                    repo.toggleLock(entry.info.packageName)
                                    lockedApps = repo.getLockedApps()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppListSectionHeader(
    text: String,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = tint,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

@Composable
private fun AppListRow(entry: AppEntry, isLocked: Boolean, onToggle: () -> Unit) {
    ListItem(
        headlineContent = { Text(entry.label) },
        supportingContent = {
            Text(
                entry.info.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = { AppIconImage(packageName = entry.info.packageName) },
        trailingContent = {
            Switch(checked = isLocked, onCheckedChange = { onToggle() })
        }
    )
    HorizontalDivider()
}

@Composable
private fun AppIconImage(packageName: String) {
    val context = LocalContext.current
    val bmp = remember(packageName) {
        runCatching {
            val d = context.packageManager.getApplicationIcon(packageName)
            val b = Bitmap.createBitmap(
                d.intrinsicWidth.coerceAtLeast(48),
                d.intrinsicHeight.coerceAtLeast(48),
                Bitmap.Config.ARGB_8888
            )
            Canvas(b).let { c -> d.setBounds(0, 0, b.width, b.height); d.draw(c) }
            b.asImageBitmap()
        }.getOrNull()
    }
    if (bmp != null) {
        Image(
            painter = BitmapPainter(bmp),
            contentDescription = null,
            modifier = Modifier.size(40.dp)
        )
    } else {
        Icon(Icons.Default.Android, contentDescription = null, modifier = Modifier.size(40.dp))
    }
}

// ── Settings ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    repo: LockedAppsRepository,
    secure: SecurePreferences,
    onBack: () -> Unit,
    onChangePin: () -> Unit,
    onPrivacyPolicy: () -> Unit
) {
    val context = LocalContext.current
    var antiTamper by remember { mutableStateOf(repo.isAntiTamperEnabled()) }
    var relockMode by remember { mutableStateOf(repo.getRelockMode()) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SectionLabel("Authentication")
            ListItem(
                headlineContent = { Text("Change PIN") },
                supportingContent = { Text("Update your 6-digit unlock PIN") },
                leadingContent = { Icon(Icons.Default.Lock, null) },
                trailingContent = { TextButton(onClick = onChangePin) { Text("Change") } }
            )
            HorizontalDivider()

            SectionLabel("Relock behavior")
            RelockBehaviorItem(
                current = relockMode,
                onSelect = { mode -> relockMode = mode; repo.setRelockMode(mode) }
            )
            HorizontalDivider()

            SectionLabel("Protection")
            ListItem(
                headlineContent = { Text("Anti-tamper") },
                supportingContent = {
                    Text("Lock Settings and package installer to prevent circumvention")
                },
                leadingContent = { Icon(Icons.Default.Security, null) },
                trailingContent = {
                    Switch(
                        checked = antiTamper,
                        onCheckedChange = { v -> antiTamper = v; repo.setAntiTamperEnabled(v) }
                    )
                }
            )
            HorizontalDivider()

            SectionLabel("About & Privacy")
            ListItem(
                headlineContent = { Text("Privacy Policy") },
                supportingContent = { Text("No data leaves your device — tap to read the full policy") },
                leadingContent = { Icon(Icons.Default.Policy, null) },
                modifier = Modifier.clickable(onClick = onPrivacyPolicy)
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Disable App Lock") },
                supportingContent = { Text("Opens Android Accessibility Settings — find App Locker and toggle it off") },
                leadingContent = { Icon(Icons.Default.Settings, null) },
                modifier = Modifier.clickable {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Delete All Data") },
                supportingContent = { Text("Remove all locked apps, reset PIN, and clear all settings") },
                leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable { showDeleteDialog = true }
            )
            HorizontalDivider()
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete All Data?") },
            text = {
                Text("This will remove all locked apps, clear your PIN, and reset all App Locker settings. The action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        repo.setLockedApps(emptySet())
                        secure.clearPin()
                        repo.setAntiTamperEnabled(false)
                        antiTamper = false
                        showDeleteDialog = false
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ── Relock behavior picker ────────────────────────────────────────────────────

@Composable
private fun RelockBehaviorItem(
    current: RelockMode,
    onSelect: (RelockMode) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(current.label) },
        supportingContent = { Text(current.description) },
        leadingContent = { Icon(Icons.Default.Timer, contentDescription = null) },
        trailingContent = {
            Text(
                text = "Change",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge
            )
        },
        modifier = Modifier.clickable { showDialog = true }
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Relock behavior") },
            text = {
                Column {
                    Text(
                        "Choose when protected apps should lock again after you unlock them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    RelockMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(mode); showDialog = false }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = mode == current,
                                onClick = { onSelect(mode); showDialog = false }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(mode.label, fontWeight = FontWeight.Medium)
                                Text(
                                    mode.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text("Dismiss") }
            }
        )
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp)
    )
}

// ── Shared PIN pad ────────────────────────────────────────────────────────────

@Composable
private fun PinDotsRow(length: Int) {
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
private fun DialPad(onDigit: (String) -> Unit, onDelete: () -> Unit) {
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

// ── Helpers ───────────────────────────────────────────────────────────────────

// ── Privacy Policy screen ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivacyPolicyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PolicySection(
                icon = Icons.Default.Info,
                title = "What App Locker does",
                body = "App Locker shows a lock screen when you open a protected app. You choose which apps to protect. Authentication is by PIN or biometrics. Everything is controlled by you, on your device."
            )

            PolicySection(
                icon = Icons.Default.Security,
                title = "Data we collect",
                body = "None. App Locker collects no personal data, usage statistics, analytics, crash reports, or any other information. Your list of locked apps and your PIN are stored locally on your device and never leave it."
            )

            PolicySection(
                icon = Icons.Default.Lock,
                title = "Accessibility Service",
                body = "App Locker uses Android's Accessibility Service for one purpose only: to detect which app is currently in the foreground. When a protected app opens, App Locker intercepts it and shows the lock screen.\n\nWhat App Locker does NOT do with Accessibility:\n• Read screen content, passwords, or text fields\n• Perform gestures or simulate taps\n• Intercept keystrokes\n• Read notifications\n• Inspect app layouts or view hierarchies\n• Capture screenshots\n• Record or transmit anything\n\nThe service configuration explicitly sets canRetrieveWindowContent=\"false\" and canPerformGestures=\"false\"."
            )

            PolicySection(
                icon = Icons.Default.Info,
                title = "Permissions and why",
                body = "USE_BIOMETRIC — required to show the fingerprint / face authentication prompt.\n\nWAKE_LOCK — required to turn the screen on when a protected app is launched while the device is asleep.\n\nACCESSIBILITY SERVICE — required to detect the foreground app (see above).\n\nAbsent permissions: INTERNET, SYSTEM_ALERT_WINDOW, BIND_DEVICE_ADMIN, READ_PHONE_STATE, QUERY_ALL_PACKAGES, CAMERA, CONTACTS, LOCATION, MICROPHONE, STORAGE — none of these are requested."
            )

            PolicySection(
                icon = Icons.Default.Android,
                title = "What is stored locally",
                body = "• Your list of locked app packages (in SharedPreferences)\n• Your PIN, encrypted with AES-256-GCM via EncryptedSharedPreferences\n• Your chosen relock mode and anti-tamper preference\n• Whether onboarding was completed\n\nNothing else. There is no database, no cloud storage, no remote sync."
            )

            PolicySection(
                icon = Icons.Default.Security,
                title = "How to remove all data",
                body = "Go to Settings → About & Privacy → Delete All Data. This clears your PIN, your locked-app list, and all preferences.\n\nAlternatively, uninstalling the app removes everything."
            )

            PolicySection(
                icon = Icons.Default.Info,
                title = "Google Play Protect note",
                body = "Play Protect may warn you when installing any Accessibility app from outside the Play Store, because Accessibility Services have broad capabilities that can be misused by malicious apps.\n\nThis warning is a general precaution — not specific evidence that App Locker is harmful. App Locker's accessibility configuration is deliberately minimal: it only listens for window-change events and reads the package name of the foreground app. It does not read content, perform gestures, or access sensitive data.\n\nTo avoid this warning entirely, install App Locker from the Google Play Store if a Play listing is available."
            )

            Spacer(Modifier.height(16.dp))
            Text(
                "App Locker is open-source. You can inspect the source code to verify all of the above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PolicySection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(24.dp)
                    .padding(top = 2.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun isServiceEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    // The system stores services as "pkg/ClassName" (full or short form); check both
    return enabled.split(':').any { entry ->
        entry.contains(context.packageName, ignoreCase = true) &&
            entry.contains("AppLockAccessibilityService", ignoreCase = true)
    }
}

