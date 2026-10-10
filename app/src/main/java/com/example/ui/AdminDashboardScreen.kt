package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import com.example.usb.CompanionInfo
import com.example.usb.UsbConnectionState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.auth.AuditLog
import com.example.auth.UserProfile
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.theme.StatusYellow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentProfile by viewModel.userProfile.collectAsState()
    val allUsers by viewModel.adminAllUsers.collectAsState()
    val auditLogs by viewModel.adminAuditLogs.collectAsState()
    val serviceControl by viewModel.serviceControl.collectAsState()

    val scope = rememberCoroutineScope()
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // Dialog state holders
    var targetUserForAction by remember { mutableStateOf<Pair<UserProfile, String>?>(null) }
    var userForComment by remember { mutableStateOf<UserProfile?>(null) }
    var commentTextInput by remember { mutableStateOf("") }
    var requestedServiceMode by remember { mutableStateOf<String?>(null) }

    val adminId = currentProfile?.userId ?: ""
    val adminEmail = currentProfile?.email ?: "nani68629@gmail.com"
    val isSuperAdmin = currentProfile?.isSuperAdmin == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ADMIN CONTROL CENTER", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            if (isSuperAdmin) "SUPER_ADMIN ACCESS" else "ADMINISTRATOR ACCESS",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tabs: 0: Dashboard, 1: Pending, 2: Users, 3: Service, 4: Logs
            PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("Overview") }
                )
                val pendingCount = allUsers.count { it.status == UserProfile.STATUS_PENDING }
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("Pending ($pendingCount)") }
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    text = { Text("Users") }
                )
                Tab(
                    selected = selectedTabIndex == 3,
                    onClick = { selectedTabIndex = 3 },
                    text = { Text("Service") }
                )
                Tab(
                    selected = selectedTabIndex == 4,
                    onClick = { selectedTabIndex = 4 },
                    text = { Text("Logs") }
                )
                Tab(
                    selected = selectedTabIndex == 5,
                    onClick = { selectedTabIndex = 5 },
                    text = { Text("Companion (.exe)") }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                when (selectedTabIndex) {
                    0 -> OverviewTab(
                        allUsers = allUsers,
                        serviceControl = serviceControl,
                        onNavigateToPending = { selectedTabIndex = 1 },
                        onNavigateToUsers = { selectedTabIndex = 2 }
                    )
                    1 -> PendingUsersTab(
                        users = allUsers.filter { it.status == UserProfile.STATUS_PENDING },
                        onApprove = { targetUserForAction = it to "APPROVE" },
                        onReject = { targetUserForAction = it to "REJECT" }
                    )
                    2 -> AllUsersManagementTab(
                        users = allUsers,
                        onExtend = { targetUserForAction = it to "EXTEND_8H" },
                        onDecrease = { targetUserForAction = it to "DECREASE_1H" },
                        onPermanent = { targetUserForAction = it to "PERMANENT" },
                        onSuspend = { targetUserForAction = it to "SUSPEND" },
                        onRestore = { targetUserForAction = it to "RESTORE" },
                        onTerminate = { targetUserForAction = it to "TERMINATE" },
                        onDelete = { targetUserForAction = it to "DELETE" },
                        onComment = {
                            userForComment = it
                            commentTextInput = it.adminComment ?: ""
                        }
                    )
                    3 -> ServiceControlTab(
                        serviceControl = serviceControl,
                        isSuperAdmin = isSuperAdmin,
                        onSelectMode = { requestedServiceMode = it },
                        onSendBroadcast = { title, msg ->
                            scope.launch {
                                viewModel.adminSendBroadcastMessage(title, msg, adminId, adminEmail)
                            }
                        },
                        onClearBroadcast = {
                            scope.launch {
                                viewModel.adminClearBroadcastMessage(adminId, adminEmail)
                            }
                        }
                    )
                    4 -> AuditLogsTab(auditLogs = auditLogs)
                    5 -> WindowsCompanionTab(viewModel = viewModel, adminEmail = adminEmail)
                }
            }
        }
    }

    // Dialog for Admin Comment
    userForComment?.let { target ->
        AlertDialog(
            onDismissRequest = { userForComment = null },
            title = { Text("Admin Comment / Note") },
            text = {
                Column {
                    Text(
                        "User: ${target.displayName} (${target.email})",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = commentTextInput,
                        onValueChange = { commentTextInput = it },
                        label = { Text("Comment / Note") },
                        placeholder = { Text("Access approved for project testing until further notice.") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            viewModel.adminUpdateUserComment(target.userId, commentTextInput, adminId, adminEmail)
                        }
                        userForComment = null
                    }
                ) {
                    Text("SAVE NOTE")
                }
            },
            dismissButton = {
                TextButton(onClick = { userForComment = null }) {
                    Text("CANCEL")
                }
            }
        )
    }

    // Dialog for Service Mode Change
    requestedServiceMode?.let { mode ->
        val isDestructive = mode == "DISABLED"
        AlertDialog(
            onDismissRequest = { requestedServiceMode = null },
            title = {
                Text(
                    when (mode) {
                        "ACTIVE" -> "Set Service: ACTIVE"
                        "MAINTENANCE" -> "Set Service: MAINTENANCE MODE"
                        else -> "Set Service: DISABLED"
                    }
                )
            },
            text = {
                Text(
                    when (mode) {
                        "ACTIVE" -> "Restore normal application operation for all approved users?"
                        "MAINTENANCE" -> "Place REPLICA under maintenance? Normal users will see \"REPLICA is currently under maintenance. Please try again later.\" while admin panel remains accessible."
                        else -> "Stop/disable REPLICA globally? Normal users will see \"REPLICA service is currently unavailable.\" Active sessions will be stopped."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            viewModel.adminSetServiceMode(mode, adminId, adminEmail)
                        }
                        requestedServiceMode = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when (mode) {
                            "ACTIVE" -> StatusGreen
                            "MAINTENANCE" -> StatusYellow
                            else -> ErrorRed
                        }
                    )
                ) {
                    Text("CONFIRM: $mode", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { requestedServiceMode = null }) {
                    Text("CANCEL")
                }
            }
        )
    }

    // Confirmation dialog for User Actions
    targetUserForAction?.let { (user, action) ->
        val isDestructive = action == "TERMINATE" || action == "REJECT" || action == "SUSPEND" || action == "DELETE"
        AlertDialog(
            onDismissRequest = { targetUserForAction = null },
            title = {
                Text(
                    text = when (action) {
                        "EXTEND_8H" -> "Continue / Extend Access (+8 hrs)"
                        "DECREASE_1H" -> "Decrease Access (-1 hr)"
                        "PERMANENT" -> "Grant Permanent Access"
                        "TERMINATE" -> "Terminate Access & Remote Wipe"
                        "DELETE" -> "Delete User From REPLICA"
                        "REJECT" -> "Reject User Request"
                        "SUSPEND" -> "Suspend User Access"
                        "RESTORE" -> "Restore User Access"
                        "APPROVE" -> "Approve User Request"
                        else -> "Confirm Action"
                    }
                )
            },
            text = {
                Text(
                    text = when (action) {
                        "EXTEND_8H" -> "Continue REPLICA access for ${user.displayName} (${user.email}) for another 8 hours?"
                        "DECREASE_1H" -> "Decrease remaining access time for ${user.displayName} (${user.email}) by 1 hour?"
                        "PERMANENT" -> "Grant permanent, unlimited REPLICA access to ${user.displayName} (${user.email})?"
                        "TERMINATE" -> "Terminate access for ${user.displayName}? Their active auto-typing will stop and all local REPLICA scripts on their device will be wiped."
                        "DELETE" -> "Permanently remove ${user.displayName} (${user.email}) from REPLICA?"
                        "REJECT" -> "Reject access request for ${user.displayName}?"
                        "SUSPEND" -> "Suspend access for ${user.displayName}?"
                        "RESTORE" -> "Restore REPLICA access for ${user.displayName} with a new 8-hour period?"
                        "APPROVE" -> "Approve REPLICA access for ${user.displayName} with 8 hours of access?"
                        else -> "Proceed with $action for ${user.displayName}?"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            when (action) {
                                "APPROVE" -> viewModel.adminApproveUser(user.userId, adminId, adminEmail)
                                "EXTEND_8H" -> viewModel.adminExtendAccess(user.userId, 8, adminId, adminEmail)
                                "DECREASE_1H" -> viewModel.adminDecreaseAccess(user.userId, 1, adminId, adminEmail)
                                "PERMANENT" -> viewModel.adminGrantPermanentAccess(user.userId, adminId, adminEmail)
                                "REJECT" -> viewModel.adminRejectUser(user.userId, adminId, adminEmail)
                                "SUSPEND" -> viewModel.adminSuspendUser(user.userId, adminId, adminEmail)
                                "RESTORE" -> viewModel.adminRestoreUser(user.userId, adminId, adminEmail)
                                "TERMINATE" -> viewModel.adminTerminateUser(user.userId, adminId, adminEmail)
                                "DELETE" -> viewModel.adminDeleteUser(user.userId, adminId, adminEmail)
                            }
                        }
                        targetUserForAction = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDestructive) ErrorRed else StatusGreen
                    )
                ) {
                    Text(
                        when (action) {
                            "EXTEND_8H" -> "CONTINUE (+8h)"
                            "DECREASE_1H" -> "DECREASE (-1h)"
                            "PERMANENT" -> "GRANT PERMANENT"
                            "TERMINATE" -> "TERMINATE & WIPE"
                            "DELETE" -> "DELETE USER"
                            else -> action
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { targetUserForAction = null }) {
                    Text("CANCEL")
                }
            }
        )
    }
}

@Composable
fun OverviewTab(
    allUsers: List<UserProfile>,
    serviceControl: com.example.auth.ServiceControl,
    onNavigateToPending: () -> Unit,
    onNavigateToUsers: () -> Unit
) {
    val pending = allUsers.count { it.status == UserProfile.STATUS_PENDING }
    val approved = allUsers.count { it.status == UserProfile.STATUS_APPROVED }
    val suspended = allUsers.count { it.status == UserProfile.STATUS_SUSPENDED }
    val terminated = allUsers.count { it.status == UserProfile.STATUS_TERMINATED }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (serviceControl.serviceEnabled) StatusGreen.copy(alpha = 0.15f) else StatusRed.copy(alpha = 0.15f)
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "REPLICA SERVICE STATUS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (serviceControl.serviceEnabled) "● ACTIVE & ENABLED" else "● DISABLED GLOBALLY",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (serviceControl.serviceEnabled) StatusGreen else StatusRed
                    )
                }
            }
        }

        Text("User Statistics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard("PENDING", pending, StatusYellow, Modifier.weight(1f), onClick = onNavigateToPending)
            StatCard("APPROVED", approved, StatusGreen, Modifier.weight(1f), onClick = onNavigateToUsers)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard("SUSPENDED", suspended, StatusYellow, Modifier.weight(1f), onClick = onNavigateToUsers)
            StatCard("TERMINATED", terminated, ErrorRed, Modifier.weight(1f), onClick = onNavigateToUsers)
        }
    }
}

@Composable
fun StatCard(label: String, count: Int, color: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.height(4.dp))
            Text("$count", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
fun PendingUsersTab(
    users: List<UserProfile>,
    onApprove: (UserProfile) -> Unit,
    onReject: (UserProfile) -> Unit
) {
    if (users.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No pending requests.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(users, key = { it.userId }) { user ->
                UserCard(
                    user = user,
                    actions = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onReject(user) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
                            ) {
                                Text("REJECT", fontSize = 12.sp)
                            }
                            Button(
                                onClick = { onApprove(user) },
                                colors = ButtonDefaults.buttonColors(containerColor = StatusGreen)
                            ) {
                                Text("APPROVE (8h)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AllUsersManagementTab(
    users: List<UserProfile>,
    onExtend: (UserProfile) -> Unit,
    onDecrease: (UserProfile) -> Unit,
    onPermanent: (UserProfile) -> Unit,
    onSuspend: (UserProfile) -> Unit,
    onRestore: (UserProfile) -> Unit,
    onTerminate: (UserProfile) -> Unit,
    onDelete: (UserProfile) -> Unit,
    onComment: (UserProfile) -> Unit
) {
    var filterStatus by remember { mutableStateOf("ALL") }

    val filtered = when (filterStatus) {
        "ALL" -> users
        else -> users.filter { it.status == filterStatus }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("ALL", UserProfile.STATUS_APPROVED, UserProfile.STATUS_SUSPENDED, UserProfile.STATUS_TERMINATED).forEach { s ->
                FilterChip(
                    selected = filterStatus == s,
                    onClick = { filterStatus = s },
                    label = { Text(s, fontSize = 11.sp) }
                )
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(filtered, key = { it.userId }) { user ->
                UserCard(
                    user = user,
                    actions = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = { onComment(user) },
                                    modifier = Modifier.padding(end = 6.dp)
                                ) {
                                    Icon(Icons.Default.Comment, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (user.adminComment.isNullOrBlank()) "ADD NOTE" else "EDIT NOTE", fontSize = 11.sp)
                                }

                                if (user.status == UserProfile.STATUS_APPROVED && !user.isAdmin) {
                                    Button(
                                        onClick = { onExtend(user) },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.padding(end = 4.dp)
                                    ) {
                                        Text("+8h", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = { onDecrease(user) },
                                        modifier = Modifier.padding(end = 4.dp)
                                    ) {
                                        Text("-1h", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = { onPermanent(user) },
                                        modifier = Modifier.padding(end = 4.dp)
                                    ) {
                                        Text("PERM", fontSize = 11.sp)
                                    }
                                }

                                if (user.status == UserProfile.STATUS_SUSPENDED) {
                                    Button(
                                        onClick = { onRestore(user) },
                                        colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                                        modifier = Modifier.padding(end = 6.dp)
                                    ) {
                                        Text("RESTORE", fontSize = 11.sp)
                                    }
                                } else if (user.status == UserProfile.STATUS_APPROVED && !user.isAdmin) {
                                    OutlinedButton(
                                        onClick = { onSuspend(user) },
                                        modifier = Modifier.padding(end = 6.dp)
                                    ) {
                                        Text("SUSPEND", fontSize = 11.sp)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                if (user.status != UserProfile.STATUS_TERMINATED && !user.isAdmin) {
                                    Button(
                                        onClick = { onTerminate(user) },
                                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                                        modifier = Modifier.padding(end = 6.dp)
                                    ) {
                                        Text("TERMINATE & WIPE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (!user.isAdmin) {
                                    OutlinedButton(
                                        onClick = { onDelete(user) },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
                                    ) {
                                        Text("DELETE", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun UserCard(
    user: UserProfile,
    actions: @Composable () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    val regDate = remember(user.registrationDate) { dateFormat.format(Date(user.registrationDate)) }
    val lastLogin = remember(user.lastLogin) { dateFormat.format(Date(user.lastLogin)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (user.photoUrl != null) {
                        AsyncImage(
                            model = user.photoUrl,
                            contentDescription = user.displayName,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(user.displayName.take(1).uppercase(), fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(user.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(user.email, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Status Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when (user.status) {
                                UserProfile.STATUS_APPROVED -> StatusGreen.copy(alpha = 0.2f)
                                UserProfile.STATUS_PENDING -> StatusYellow.copy(alpha = 0.2f)
                                UserProfile.STATUS_SUSPENDED -> StatusYellow.copy(alpha = 0.2f)
                                else -> ErrorRed.copy(alpha = 0.2f)
                            }
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        user.status,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (user.status) {
                            UserProfile.STATUS_APPROVED -> StatusGreen
                            UserProfile.STATUS_PENDING -> StatusYellow
                            UserProfile.STATUS_SUSPENDED -> StatusYellow
                            else -> ErrorRed
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Access Time / Expiry display
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.HourglassTop,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (user.isAccessExpired) ErrorRed else StatusGreen
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Access: ${user.getRemainingTimeFormatted()}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (user.isAccessExpired) ErrorRed else StatusGreen
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                "Role: ${user.role} • Registered: $regDate • Last Login: $lastLogin",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!user.adminComment.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(10.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Comment,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Admin Note:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            user.adminComment,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (!user.adminCommentBy.isNullOrBlank()) {
                            Text(
                                "Added by ${user.adminCommentBy}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            actions()
        }
    }
}

@Composable
fun ServiceControlTab(
    serviceControl: com.example.auth.ServiceControl,
    isSuperAdmin: Boolean,
    onSelectMode: (String) -> Unit,
    onSendBroadcast: (String, String) -> Unit,
    onClearBroadcast: () -> Unit
) {
    var broadcastTitleInput by remember(serviceControl.broadcastTitle) {
        mutableStateOf(serviceControl.broadcastTitle ?: "IMPORTANT NOTICE")
    }
    var broadcastMessageInput by remember(serviceControl.broadcastMessage) {
        mutableStateOf(serviceControl.broadcastMessage ?: "")
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("GLOBAL REPLICA SERVICE CONTROL", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                Text(
                    "Controls whether REPLICA auto-typing and Bluetooth services are available to all users across the system.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Current Status: ", fontWeight = FontWeight.Medium)
                    val currentMode = serviceControl.currentMode
                    Text(
                        currentMode,
                        fontWeight = FontWeight.ExtraBold,
                        color = when (currentMode) {
                            "ACTIVE" -> StatusGreen
                            "MAINTENANCE" -> StatusYellow
                            else -> StatusRed
                        }
                    )
                }

                if (serviceControl.isUnavailable) {
                    Text(
                        "Message shown to users: \"${serviceControl.effectiveMessage}\"",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isSuperAdmin) {
                    Text("Change Service Status:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onSelectMode("ACTIVE") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (serviceControl.currentMode == "ACTIVE") StatusGreen else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (serviceControl.currentMode == "ACTIVE") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text("ACTIVE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onSelectMode("MAINTENANCE") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (serviceControl.currentMode == "MAINTENANCE") StatusYellow else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (serviceControl.currentMode == "MAINTENANCE") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text("MAINTENANCE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onSelectMode("DISABLED") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (serviceControl.currentMode == "DISABLED") ErrorRed else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (serviceControl.currentMode == "DISABLED") MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text("DISABLED", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Text(
                        "Only SUPER_ADMIN can change the global REPLICA service status.",
                        color = ErrorRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Broadcast Announcement Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("BROADCAST ANNOUNCEMENT TO USERS", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                Text(
                    "Send a banner announcement message that displays prominently on all user screens.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!serviceControl.broadcastMessage.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                "Active Announcement: ${serviceControl.broadcastTitle ?: ""}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                serviceControl.broadcastMessage ?: "",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = broadcastTitleInput,
                    onValueChange = { broadcastTitleInput = it },
                    label = { Text("Announcement Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = broadcastMessageInput,
                    onValueChange = { broadcastMessageInput = it },
                    label = { Text("Message to Users") },
                    placeholder = { Text("e.g. System update completed. New ZipShare programs added!") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (broadcastMessageInput.isNotBlank()) {
                                onSendBroadcast(broadcastTitleInput, broadcastMessageInput)
                            }
                        },
                        enabled = broadcastMessageInput.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("BROADCAST MESSAGE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    if (!serviceControl.broadcastMessage.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = onClearBroadcast,
                            modifier = Modifier.weight(0.6f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
                        ) {
                            Text("CLEAR", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuditLogsTab(auditLogs: List<AuditLog>) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()) }

    if (auditLogs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No audit logs recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(auditLogs, key = { it.id }) { log ->
                val time = remember(log.timestamp) { dateFormat.format(Date(log.timestamp)) }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                log.action,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(time, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("Admin: ${log.adminEmail}", fontSize = 11.sp)
                        log.targetUserId?.let {
                            Text("Target User: $it", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WindowsCompanionTab(
    viewModel: MainViewModel,
    adminEmail: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val companionInfo by viewModel.companionInfo.collectAsState()
    val usbConnectionState by viewModel.usbConnectionState.collectAsState()

    var showUploadDialog by remember { mutableStateOf(false) }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var newVersionInput by remember { mutableStateOf("1.0.2") }
    var newNotesInput by remember { mutableStateOf("Updated Windows companion with USB high-speed typing support.") }
    var isUploading by remember { mutableStateOf(false) }
    var isTestingConnection by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            showUploadDialog = true
        }
    }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    val lastUpdatedDate = remember(companionInfo.lastUpdated) {
        dateFormat.format(Date(companionInfo.lastUpdated))
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Laptop, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Windows Companion Manager",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Upload, update, replace, and distribute the Windows companion .exe to normal users for USB auto-typing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Active Version Details Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Current Active Companion",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(StatusGreen.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "v${companionInfo.version}",
                                fontWeight = FontWeight.ExtraBold,
                                color = StatusGreen,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Metadata details
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("File Name:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(companionInfo.fileName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("File Size:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val sizeKb = (companionInfo.fileSize / 1024f)
                        Text("%.1f KB (%d bytes)".format(sizeKb, companionInfo.fileSize), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Protocol Version:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("v${companionInfo.protocolVersion} (Compatible)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Last Updated:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(lastUpdatedDate, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Uploaded By:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(companionInfo.uploadedBy, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // Checksum
                    companionInfo.sha256?.let { sha ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("SHA-256 Checksum", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("SHA256", sha))
                                        Toast.makeText(context, "SHA256 copied", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(14.dp))
                                }
                            }
                            Text(
                                text = sha,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 14.sp
                            )
                        }
                    }

                    if (companionInfo.releaseNotes.isNotBlank()) {
                        Text(
                            text = "Release Notes: ${companionInfo.releaseNotes}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // Admin Action Controls Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Admin Companion Actions",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )

                    // 1. Upload / Replace .exe
                    Button(
                        onClick = { filePickerLauncher.launch("*/*") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_upload_companion_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Upload / Replace Companion (.exe)", fontWeight = FontWeight.Bold)
                    }

                    // 2. Share .exe directly to users
                    OutlinedButton(
                        onClick = { viewModel.shareCompanionFile(context) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_share_companion_exe_btn")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share Companion (.exe) via Share Sheet")
                    }

                    // 3. Share Download Link
                    OutlinedButton(
                        onClick = { viewModel.shareCompanionDownloadLink(context, "http://127.0.0.1:3000") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_share_companion_link_btn")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share Companion Download Link")
                    }
                }
            }
        }

        // Connection Test & Diagnostics Simulator Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Diagnostics & Handshake Verification",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Verify whether the Windows companion is responding on the USB ADB port (8989).",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (val s = usbConnectionState) {
                                is UsbConnectionState.Synced -> "Status: SYNCED (Latency: ${s.latencyMs}ms)"
                                is UsbConnectionState.Connecting -> "Status: PROBING..."
                                is UsbConnectionState.Connected -> "Status: CONNECTED (Verifying)"
                                is UsbConnectionState.Incompatible -> "Status: INCOMPATIBLE (${s.reason})"
                                is UsbConnectionState.Error -> "Status: ERROR (${s.message})"
                                is UsbConnectionState.Disconnected -> "Status: DISCONNECTED (${s.reason})"
                            },
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = when (usbConnectionState) {
                                is UsbConnectionState.Synced -> StatusGreen
                                is UsbConnectionState.Connecting, is UsbConnectionState.Connected -> StatusYellow
                                is UsbConnectionState.Incompatible -> MaterialTheme.colorScheme.error
                                else -> StatusRed
                            }
                        )
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                isTestingConnection = true
                                viewModel.checkUsbConnection()
                                isTestingConnection = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        if (isTestingConnection) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onSecondary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Testing...")
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Handshake & Ping Port 8989")
                        }
                    }
                }
            }
        }
    }

    // Dialog: Upload / Replace Companion
    if (showUploadDialog && selectedFileUri != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isUploading) {
                    showUploadDialog = false
                    selectedFileUri = null
                }
            },
            title = { Text("Upload & Replace Companion (.exe)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Selected file: ${selectedFileUri?.lastPathSegment ?: "companion.exe"}",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = newVersionInput,
                        onValueChange = { newVersionInput = it },
                        label = { Text("Companion Version") },
                        placeholder = { Text("e.g. 1.0.2") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newNotesInput,
                        onValueChange = { newNotesInput = it },
                        label = { Text("Release Notes") },
                        placeholder = { Text("Describe changes...") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isUploading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Uploading companion file...", fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = selectedFileUri ?: return@Button
                        scope.launch {
                            isUploading = true
                            val ok = viewModel.adminUploadCompanion(
                                fileUri = uri,
                                version = newVersionInput,
                                notes = newNotesInput,
                                adminEmail = adminEmail
                            )
                            isUploading = false
                            if (ok) {
                                showUploadDialog = false
                                selectedFileUri = null
                            }
                        }
                    },
                    enabled = !isUploading && newVersionInput.isNotBlank()
                ) {
                    Text("Upload & Update")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showUploadDialog = false
                        selectedFileUri = null
                    },
                    enabled = !isUploading
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

