package com.example.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.UserProfile
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.StatusRed
import com.example.ui.theme.StatusYellow

enum class AccessDeniedReason {
    PENDING, REJECTED, SUSPENDED, TERMINATED, SERVICE_DISABLED, MAINTENANCE, NETWORK_ERROR, ACCESS_EXPIRED
}

@Composable
fun StatusAccessScreen(
    reason: AccessDeniedReason,
    userProfile: UserProfile?,
    serviceMessage: String?,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    onOpenAdminDashboard: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val (icon, title, message, color) = when (reason) {
        AccessDeniedReason.ACCESS_EXPIRED -> Quadruple(
            Icons.Default.HourglassTop,
            "TRIAL EXPIRED",
            "ADMIN APPROVAL REQUIRED\n\nYour 8-hour free trial access has expired. Please contact administrator (nani68629@gmail.com) to grant continued access.",
            StatusYellow
        )
        AccessDeniedReason.PENDING -> Quadruple(
            Icons.Default.HourglassTop,
            "Approval Required",
            "Your REPLICA access request is waiting for administrator approval.",
            StatusYellow
        )
        AccessDeniedReason.REJECTED -> Quadruple(
            Icons.Default.Block,
            "Access Rejected",
            "Your REPLICA access request was rejected by administrator (nani68629@gmail.com).",
            StatusRed
        )
        AccessDeniedReason.SUSPENDED -> Quadruple(
            Icons.Default.Warning,
            "Account Suspended",
            "Your REPLICA account is temporarily suspended by administrator (nani68629@gmail.com).",
            StatusYellow
        )
        AccessDeniedReason.TERMINATED -> Quadruple(
            Icons.Default.Lock,
            "Access Terminated & Data Cleared",
            "Your REPLICA access has been terminated by administrator (nani68629@gmail.com). All protected services and local data have been terminated.",
            ErrorRed
        )
        AccessDeniedReason.MAINTENANCE -> Quadruple(
            Icons.Default.Warning,
            "REPLICA Under Maintenance",
            serviceMessage ?: "REPLICA is currently under maintenance. Please try again later.",
            StatusYellow
        )
        AccessDeniedReason.SERVICE_DISABLED -> Quadruple(
            Icons.Default.Warning,
            "Service Unavailable",
            serviceMessage ?: "REPLICA service is currently unavailable. Please contact an administrator or try again later.",
            StatusYellow
        )
        AccessDeniedReason.NETWORK_ERROR -> Quadruple(
            Icons.Default.Warning,
            "Connection Required",
            "Unable to verify REPLICA access. Please connect to the internet.",
            StatusYellow
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = color,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                userProfile?.let {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Account: ${it.displayName}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Email: ${it.email}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Status: ${it.status}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
                            if (!it.adminComment.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Admin Note: \"${it.adminComment}\"", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Actions
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if ((reason == AccessDeniedReason.SERVICE_DISABLED || reason == AccessDeniedReason.MAINTENANCE) &&
                        userProfile?.isAdmin == true && onOpenAdminDashboard != null) {
                        Button(
                            onClick = onOpenAdminDashboard,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = "Admin")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("OPEN ADMIN DASHBOARD", fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = onRefresh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("refresh_status_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("CHECK STATUS AGAIN", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sign_out_button")
                    ) {
                        Text("SIGN OUT")
                    }
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
