package com.example.ui

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
                            "Admin: $adminEmail • ${if (isSuperAdmin) "SUPER_ADMIN" else "ADMIN"}",
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
                        onSelectMode = { requestedServiceMode = it }
                    )
                    4 -> AuditLogsTab(auditLogs = auditLogs)
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
                                        modifier = Modifier.padding(end = 6.dp)
                                    ) {
                                        Text("CONTINUE (+8h)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = { onPermanent(user) },
                                        modifier = Modifier.padding(end = 6.dp)
                                    ) {
                                        Text("PERMANENT", fontSize = 11.sp)
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
    onSelectMode: (String) -> Unit
) {
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
