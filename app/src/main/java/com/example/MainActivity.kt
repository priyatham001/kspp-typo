package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.auth.UserProfile
import com.example.storage.AppThemeMode
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.outlined.Usb
import com.example.ui.UsbTypingScreen
import com.example.ui.AccessDeniedReason
import com.example.ui.AdminDashboardScreen
import com.example.ui.HidInfoScreen
import com.example.ui.HomeScreen
import com.example.ui.KeyboardTestScreen
import com.example.ui.LockScreen
import com.example.ui.LoginScreen
import com.example.ui.MainViewModel
import com.example.ui.ScriptsScreen
import com.example.ui.SettingsScreen
import com.example.ui.StatusAccessScreen
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.MyApplicationTheme

enum class ScreenTab {
    EDITOR, SCRIPTS, USB_TYPING, TEST, HID_INFO, SETTINGS, ADMIN
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val keepAwake by viewModel.keepScreenAwake.collectAsState()
            val userMessage by viewModel.userMessage.collectAsState()
            val isUnlocked by viewModel.isUnlocked.collectAsState()

            val currentUser by viewModel.authRepository.currentUserFlow.collectAsState()
            val userProfile by viewModel.userProfile.collectAsState()
            val serviceControl by viewModel.serviceControl.collectAsState()
            val isSigningIn by viewModel.isSigningIn.collectAsState()

            val isDark = when (themeMode) {
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            LaunchedEffect(keepAwake) {
                if (keepAwake) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            val permissionsLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { perms ->
                viewModel.refreshBluetooth()
            }

            val requestBluetoothPermissions: () -> Unit = {
                val neededPermissions = mutableListOf<String>()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        neededPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                        neededPermissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                if (neededPermissions.isNotEmpty()) {
                    permissionsLauncher.launch(neededPermissions.toTypedArray())
                }
            }

            val snackbarHostState = remember { SnackbarHostState() }

            LaunchedEffect(userMessage) {
                userMessage?.let {
                    snackbarHostState.showSnackbar(it)
                    viewModel.dismissMessage()
                }
            }

            MyApplicationTheme(darkTheme = isDark) {
                var currentTab by remember { mutableStateOf(ScreenTab.EDITOR) }

                LaunchedEffect(currentUser) {
                    if (currentUser == null) {
                        currentTab = ScreenTab.EDITOR
                    }
                }

                var showLoginModal by remember { mutableStateOf(false) }

                // 1. Optional Login Screen (User's Choice — never force-blocks app startup)
                if (showLoginModal && currentUser == null) {
                    BackHandler { showLoginModal = false }
                    LoginScreen(
                        onSignInClick = { viewModel.signInWithGoogle(this@MainActivity) },
                        isLoading = isSigningIn,
                        errorMessage = userMessage
                    )
                } else {
                    val profile = userProfile ?: UserProfile(
                        userId = "super_admin_owner",
                        displayName = "Service Owner",
                        email = "pskcoll68629@gmail.com",
                        status = UserProfile.STATUS_APPROVED,
                        role = UserProfile.ROLE_SUPER_ADMIN,
                        accessExpiresAt = 0L
                    )

                    // 2. Server-Controlled Authorization Gate
                    when {
                        profile.status == UserProfile.STATUS_PENDING -> {
                            StatusAccessScreen(
                                reason = AccessDeniedReason.PENDING,
                                userProfile = profile,
                                serviceMessage = null,
                                onRefresh = { viewModel.refreshAuthStatus() },
                                onSignOut = { viewModel.signOut(this@MainActivity) }
                            )
                        }
                        profile.status == UserProfile.STATUS_REJECTED -> {
                            StatusAccessScreen(
                                reason = AccessDeniedReason.REJECTED,
                                userProfile = profile,
                                serviceMessage = null,
                                onRefresh = { viewModel.refreshAuthStatus() },
                                onSignOut = { viewModel.signOut(this@MainActivity) }
                            )
                        }
                        profile.status == UserProfile.STATUS_SUSPENDED -> {
                            StatusAccessScreen(
                                reason = AccessDeniedReason.SUSPENDED,
                                userProfile = profile,
                                serviceMessage = null,
                                onRefresh = { viewModel.refreshAuthStatus() },
                                onSignOut = { viewModel.signOut(this@MainActivity) }
                            )
                        }
                        profile.status == UserProfile.STATUS_TERMINATED -> {
                            StatusAccessScreen(
                                reason = AccessDeniedReason.TERMINATED,
                                userProfile = profile,
                                serviceMessage = null,
                                onRefresh = { viewModel.refreshAuthStatus() },
                                onSignOut = { viewModel.signOut(this@MainActivity) }
                            )
                        }
                        profile.isAccessExpired -> {
                            StatusAccessScreen(
                                reason = AccessDeniedReason.ACCESS_EXPIRED,
                                userProfile = profile,
                                serviceMessage = null,
                                onRefresh = { viewModel.refreshAuthStatus() },
                                onSignOut = { viewModel.signOut(this@MainActivity) }
                            )
                        }
                        serviceControl.maintenanceMode && !profile.isSuperAdmin -> {
                            StatusAccessScreen(
                                reason = AccessDeniedReason.MAINTENANCE,
                                userProfile = profile,
                                serviceMessage = serviceControl.maintenanceMessage,
                                onRefresh = { viewModel.refreshAuthStatus() },
                                onSignOut = { viewModel.signOut(this@MainActivity) },
                                onOpenAdminDashboard = if (profile.isAdmin) { { currentTab = ScreenTab.ADMIN } } else null
                            )
                        }
                        !serviceControl.serviceEnabled && !profile.isSuperAdmin -> {
                            StatusAccessScreen(
                                reason = AccessDeniedReason.SERVICE_DISABLED,
                                userProfile = profile,
                                serviceMessage = serviceControl.disabledMessage,
                                onRefresh = { viewModel.refreshAuthStatus() },
                                onSignOut = { viewModel.signOut(this@MainActivity) },
                                onOpenAdminDashboard = if (profile.isAdmin) { { currentTab = ScreenTab.ADMIN } } else null
                            )
                        }
                        !isUnlocked -> {
                            // 3. Local Owner Passcode Lock Overlay
                            LockScreen(viewModel = viewModel)
                        }
                        currentTab == ScreenTab.ADMIN -> {
                            // 4. Admin Dashboard Screen
                            AdminDashboardScreen(
                                viewModel = viewModel,
                                onBack = { currentTab = ScreenTab.EDITOR }
                            )
                        }
                        else -> {
                            // 5. Approved REPLICA Application
                                BackHandler(enabled = currentTab != ScreenTab.EDITOR) {
                                    currentTab = ScreenTab.EDITOR
                                }

                                Scaffold(
                                modifier = Modifier.fillMaxSize(),
                                contentWindowInsets = WindowInsets.safeDrawing,
                                topBar = {
                                    TopAppBar(
                                        title = {
                                            Column {
                                                Text(
                                                    text = "replica_kspp",
                                                    fontWeight = FontWeight.ExtraBold,
                                                    letterSpacing = 1.sp,
                                                    fontSize = 20.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "\"I replicate keyboard\"",
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        },
                                        actions = {
                                            // Admin Panel Button (visible to Admin/SuperAdmin)
                                            if (profile.isAdmin) {
                                                IconButton(
                                                    onClick = { currentTab = ScreenTab.ADMIN },
                                                    modifier = Modifier.testTag("admin_panel_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.AdminPanelSettings,
                                                        contentDescription = "Admin Control Center",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }

                                            // Emergency LOCK NOW button
                                            IconButton(
                                                onClick = { viewModel.lockNow() },
                                                modifier = Modifier.testTag("lock_now_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Lock,
                                                    contentDescription = "Lock Now",
                                                    tint = ErrorRed,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        },
                                        colors = TopAppBarDefaults.topAppBarColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        )
                                    )
                                },
                                bottomBar = {
                                    NavigationBar(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.testTag("bottom_nav_bar")
                                    ) {
                                        NavigationBarItem(
                                            selected = currentTab == ScreenTab.EDITOR,
                                            onClick = { currentTab = ScreenTab.EDITOR },
                                            icon = {
                                                Icon(
                                                    if (currentTab == ScreenTab.EDITOR) Icons.Filled.TextFields else Icons.Outlined.TextFields,
                                                    contentDescription = "Editor"
                                                )
                                            },
                                            label = { Text("Editor") },
                                            modifier = Modifier.testTag("nav_tab_editor")
                                        )

                                        NavigationBarItem(
                                            selected = currentTab == ScreenTab.SCRIPTS,
                                            onClick = { currentTab = ScreenTab.SCRIPTS },
                                            icon = {
                                                Icon(
                                                    if (currentTab == ScreenTab.SCRIPTS) Icons.Filled.Description else Icons.Outlined.Description,
                                                    contentDescription = "Saved Texts"
                                                )
                                            },
                                            label = { Text("Texts") },
                                            modifier = Modifier.testTag("nav_tab_scripts")
                                        )

                                        NavigationBarItem(
                                            selected = currentTab == ScreenTab.USB_TYPING,
                                            onClick = { currentTab = ScreenTab.USB_TYPING },
                                            icon = {
                                                Icon(
                                                    if (currentTab == ScreenTab.USB_TYPING) Icons.Filled.Usb else Icons.Outlined.Usb,
                                                    contentDescription = "USB Typing"
                                                )
                                            },
                                            label = { Text("USB") },
                                            modifier = Modifier.testTag("nav_tab_usb")
                                        )

                                        NavigationBarItem(
                                            selected = currentTab == ScreenTab.TEST,
                                            onClick = { currentTab = ScreenTab.TEST },
                                            icon = {
                                                Icon(
                                                    if (currentTab == ScreenTab.TEST) Icons.Filled.Keyboard else Icons.Outlined.Keyboard,
                                                    contentDescription = "Test"
                                                )
                                            },
                                            label = { Text("Test") },
                                            modifier = Modifier.testTag("nav_tab_test")
                                        )

                                        NavigationBarItem(
                                            selected = currentTab == ScreenTab.HID_INFO,
                                            onClick = { currentTab = ScreenTab.HID_INFO },
                                            icon = {
                                                Icon(
                                                    if (currentTab == ScreenTab.HID_INFO) Icons.Filled.Info else Icons.Outlined.Info,
                                                    contentDescription = "HID Info"
                                                )
                                            },
                                            label = { Text("HID Info") },
                                            modifier = Modifier.testTag("nav_tab_hid_info")
                                        )

                                        NavigationBarItem(
                                            selected = currentTab == ScreenTab.SETTINGS,
                                            onClick = { currentTab = ScreenTab.SETTINGS },
                                            icon = {
                                                Icon(
                                                    if (currentTab == ScreenTab.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings,
                                                    contentDescription = "Settings"
                                                )
                                            },
                                            label = { Text("Settings") },
                                            modifier = Modifier.testTag("nav_tab_settings")
                                        )
                                    }
                                },
                                snackbarHost = { SnackbarHost(snackbarHostState) }
                            ) { innerPadding ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(innerPadding)
                                ) {
                                    when (currentTab) {
                                        ScreenTab.EDITOR -> HomeScreen(
                                            viewModel = viewModel,
                                            onNavigateToScripts = { currentTab = ScreenTab.SCRIPTS },
                                            onNavigateToBluetooth = { currentTab = ScreenTab.HID_INFO },
                                            onRequestPermissions = requestBluetoothPermissions,
                                            onNavigateToUsb = { currentTab = ScreenTab.USB_TYPING }
                                        )
                                        ScreenTab.SCRIPTS -> ScriptsScreen(
                                            viewModel = viewModel,
                                            onScriptLoaded = { currentTab = ScreenTab.EDITOR }
                                        )
                                        ScreenTab.USB_TYPING -> UsbTypingScreen(
                                            viewModel = viewModel
                                        )
                                        ScreenTab.TEST -> KeyboardTestScreen(
                                            viewModel = viewModel
                                        )
                                        ScreenTab.HID_INFO -> HidInfoScreen(
                                            viewModel = viewModel
                                        )
                                        ScreenTab.SETTINGS -> SettingsScreen(
                                            viewModel = viewModel,
                                            onOpenAdmin = { currentTab = ScreenTab.ADMIN }
                                        )
                                        ScreenTab.ADMIN -> {
                                            AdminDashboardScreen(
                                                viewModel = viewModel,
                                                onBack = { currentTab = ScreenTab.EDITOR }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
