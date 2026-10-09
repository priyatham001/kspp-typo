import {
  UserProfile,
  ServiceControl,
  AuditLog,
  EIGHT_HOURS_MILLIS,
  isUserSuperAdmin
} from '../types';

const STORAGE_KEYS = {
  CURRENT_USER: 'replica_current_user_profile',
  ALL_USERS: 'replica_all_users',
  SERVICE_CONTROL: 'replica_service_control',
  AUDIT_LOGS: 'replica_audit_logs'
};

const DEFAULT_USERS: UserProfile[] = [
  {
    userId: 'super_admin_nani',
    displayName: 'Nani (Super Admin)',
    email: 'nani68629@gmail.com',
    photoUrl: '/replica_sphere.jpg',
    status: 'APPROVED',
    role: 'SUPER_ADMIN',
    registrationDate: Date.now() - 30 * 86400000,
    lastLogin: Date.now(),
    accessExpiresAt: 0,
    adminComment: 'Primary Server Super Administrator',
    adminCommentBy: 'System',
    adminCommentAt: Date.now() - 30 * 86400000
  },
  {
    userId: 'user_pskcoll3',
    displayName: 'PSK Coll',
    email: 'pskcoll3@gmail.com',
    photoUrl: null,
    status: 'APPROVED',
    role: 'USER',
    registrationDate: Date.now() - 600000,
    lastLogin: Date.now(),
    accessExpiresAt: Date.now() + (7.8 * 3600 * 1000), // ~7h 48m left
    adminComment: 'Approved with 8-Hour Trial Window',
    adminCommentBy: 'nani68629@gmail.com',
    adminCommentAt: Date.now() - 600000
  },
  {
    userId: 'pending_user_dev',
    displayName: 'Alex Rivers',
    email: 'alex.rivers@example.com',
    photoUrl: null,
    status: 'PENDING',
    role: 'USER',
    registrationDate: Date.now() - 1800000,
    lastLogin: Date.now() - 1800000,
    accessExpiresAt: 0,
    adminComment: 'Waiting for verification',
    adminCommentBy: null,
    adminCommentAt: 0
  }
];

const DEFAULT_SERVICE_CONTROL: ServiceControl = {
  serviceEnabled: true,
  maintenanceMode: false,
  disabledMessage: 'REPLICA service is currently unavailable. Please contact an administrator or try again later.',
  maintenanceMessage: 'REPLICA is currently under maintenance. Please try again later.',
  updatedBy: 'nani68629@gmail.com',
  updatedAt: Date.now() - 86400000
};

export const AuthService = {
  getServiceControl(): ServiceControl {
    const raw = localStorage.getItem(STORAGE_KEYS.SERVICE_CONTROL);
    if (!raw) {
      localStorage.setItem(STORAGE_KEYS.SERVICE_CONTROL, JSON.stringify(DEFAULT_SERVICE_CONTROL));
      return DEFAULT_SERVICE_CONTROL;
    }
    try {
      return JSON.parse(raw);
    } catch {
      return DEFAULT_SERVICE_CONTROL;
    }
  },

  setServiceControl(sc: ServiceControl): void {
    localStorage.setItem(STORAGE_KEYS.SERVICE_CONTROL, JSON.stringify(sc));
  },

  getAllUsers(): UserProfile[] {
    const raw = localStorage.getItem(STORAGE_KEYS.ALL_USERS);
    if (!raw) {
      localStorage.setItem(STORAGE_KEYS.ALL_USERS, JSON.stringify(DEFAULT_USERS));
      return DEFAULT_USERS;
    }
    try {
      return JSON.parse(raw);
    } catch {
      return DEFAULT_USERS;
    }
  },

  setAllUsers(users: UserProfile[]): void {
    localStorage.setItem(STORAGE_KEYS.ALL_USERS, JSON.stringify(users));
  },

  getCurrentUser(): UserProfile | null {
    const raw = localStorage.getItem(STORAGE_KEYS.CURRENT_USER);
    if (!raw) {
      // Default to the user session (pskcoll3@gmail.com)
      const defaultUser = this.getAllUsers().find(u => u.email === 'pskcoll3@gmail.com') || DEFAULT_USERS[1];
      this.setCurrentUser(defaultUser);
      return defaultUser;
    }
    try {
      const user: UserProfile = JSON.parse(raw);
      // Refresh current user data from all users store if updated by admin
      const fresh = this.getAllUsers().find(u => u.userId === user.userId);
      return fresh || user;
    } catch {
      return null;
    }
  },

  setCurrentUser(user: UserProfile | null): void {
    if (user) {
      localStorage.setItem(STORAGE_KEYS.CURRENT_USER, JSON.stringify(user));
    } else {
      localStorage.removeItem(STORAGE_KEYS.CURRENT_USER);
    }
  },

  getAuditLogs(): AuditLog[] {
    const raw = localStorage.getItem(STORAGE_KEYS.AUDIT_LOGS);
    if (!raw) {
      const initialLogs: AuditLog[] = [
        {
          id: 'log_1',
          timestamp: Date.now() - 7200000,
          adminId: 'super_admin_nani',
          adminEmail: 'nani68629@gmail.com',
          action: 'SUPER_ADMIN_INITIALIZED',
          targetUserId: 'super_admin_nani',
          result: 'SUCCESS'
        },
        {
          id: 'log_2',
          timestamp: Date.now() - 600000,
          adminId: 'super_admin_nani',
          adminEmail: 'nani68629@gmail.com',
          action: 'USER_REGISTERED_8H_ACCESS',
          targetUserId: 'user_pskcoll3',
          result: 'SUCCESS'
        }
      ];
      localStorage.setItem(STORAGE_KEYS.AUDIT_LOGS, JSON.stringify(initialLogs));
      return initialLogs;
    }
    try {
      return JSON.parse(raw);
    } catch {
      return [];
    }
  },

  recordAuditLog(action: string, targetUserId: string | null = null, adminEmail: string = 'nani68629@gmail.com'): void {
    const logs = this.getAuditLogs();
    const newLog: AuditLog = {
      id: `log_${Date.now()}_${Math.random().toString(36).substring(2, 6)}`,
      timestamp: Date.now(),
      adminId: 'admin',
      adminEmail,
      action,
      targetUserId,
      result: 'SUCCESS'
    };
    logs.unshift(newLog);
    localStorage.setItem(STORAGE_KEYS.AUDIT_LOGS, JSON.stringify(logs.slice(0, 50)));
  },

  // Auth actions
  loginWithGoogle(email: string = 'pskcoll3@gmail.com', displayName: string = 'PSK Coll'): UserProfile {
    const isOwner = isUserSuperAdmin(email);
    const users = this.getAllUsers();
    let existing = users.find(u => u.email.toLowerCase() === email.toLowerCase());

    const now = Date.now();
    if (existing) {
      existing.lastLogin = now;
      if (isOwner) {
        existing.role = 'SUPER_ADMIN';
        existing.status = 'APPROVED';
        existing.accessExpiresAt = 0;
      }
      this.setAllUsers(users);
      this.setCurrentUser(existing);
      return existing;
    }

    // New user registration gets 8-hour access window
    const newProfile: UserProfile = {
      userId: `usr_${Date.now()}`,
      displayName,
      email,
      photoUrl: null,
      status: 'APPROVED',
      role: isOwner ? 'SUPER_ADMIN' : 'USER',
      registrationDate: now,
      lastLogin: now,
      accessExpiresAt: isOwner ? 0 : now + EIGHT_HOURS_MILLIS,
      adminComment: isOwner ? 'Super Admin Account' : 'Auto 8-Hour Trial Window Granted',
      adminCommentBy: isOwner ? 'System' : 'nani68629@gmail.com',
      adminCommentAt: now
    };

    users.unshift(newProfile);
    this.setAllUsers(users);
    this.setCurrentUser(newProfile);
    this.recordAuditLog(
      isOwner ? 'SUPER_ADMIN_INITIALIZED' : 'USER_REGISTERED_8H_ACCESS',
      newProfile.userId,
      email
    );
    return newProfile;
  },

  signOut(): void {
    this.setCurrentUser(null);
  },

  // User submits request for approval
  requestAccess(userId: string): void {
    const users = this.getAllUsers();
    const target = users.find(u => u.userId === userId);
    if (target) {
      target.status = 'PENDING';
      this.setAllUsers(users);
      this.recordAuditLog('USER_REQUESTED_ACCESS_APPROVAL', userId, target.email);
      const curr = this.getCurrentUser();
      if (curr && curr.userId === userId) this.setCurrentUser(target);
    }
  },

  // Admin adds new user directly and grants access
  addUserAndGrantAccess(email: string, displayName: string, hours: number = 8, adminEmail: string = 'nani68629@gmail.com'): UserProfile {
    const users = this.getAllUsers();
    const existing = users.find(u => u.email.toLowerCase() === email.toLowerCase());
    const now = Date.now();
    if (existing) {
      existing.status = 'APPROVED';
      existing.accessExpiresAt = hours === 0 ? 0 : now + (hours * 3600 * 1000);
      existing.adminComment = `Access granted by admin (${hours === 0 ? 'Permanent' : hours + 'h'})`;
      existing.adminCommentBy = adminEmail;
      existing.adminCommentAt = now;
      this.setAllUsers(users);
      this.recordAuditLog(`ACCESS_GRANTED_${hours}H`, existing.userId, adminEmail);
      const curr = this.getCurrentUser();
      if (curr && curr.userId === existing.userId) this.setCurrentUser(existing);
      return existing;
    }
    const newUser: UserProfile = {
      userId: `usr_${Date.now()}`,
      displayName: displayName.trim() || email.split('@')[0],
      email: email.trim(),
      photoUrl: null,
      status: 'APPROVED',
      role: 'USER',
      registrationDate: now,
      lastLogin: 0,
      accessExpiresAt: hours === 0 ? 0 : now + (hours * 3600 * 1000),
      adminComment: `Directly granted access by admin (${hours === 0 ? 'Permanent' : hours + 'h'})`,
      adminCommentBy: adminEmail,
      adminCommentAt: now
    };
    users.unshift(newUser);
    this.setAllUsers(users);
    this.recordAuditLog(`NEW_USER_CREATED_AND_GRANTED_${hours}H`, newUser.userId, adminEmail);
    return newUser;
  },

  // Admin User operations
  approveUser(userId: string, adminEmail: string): void {
    const users = this.getAllUsers();
    const target = users.find(u => u.userId === userId);
    if (target) {
      const wasTerminated = target.status === 'TERMINATED';
      target.status = 'APPROVED';
      target.accessExpiresAt = Date.now() + EIGHT_HOURS_MILLIS;
      this.setAllUsers(users);
      this.recordAuditLog(
        wasTerminated ? 'USER_RE_APPROVED_AFTER_TERMINATION_8H' : 'USER_APPROVED_8H',
        userId,
        adminEmail
      );
      // If current user, update
      const curr = this.getCurrentUser();
      if (curr && curr.userId === userId) this.setCurrentUser(target);
    }
  },

  extendAccess(userId: string, hours: number, adminEmail: string): void {
    const users = this.getAllUsers();
    const target = users.find(u => u.userId === userId);
    if (target) {
      const now = Date.now();
      const base = target.accessExpiresAt > now ? target.accessExpiresAt : now;
      target.status = 'APPROVED';
      target.accessExpiresAt = base + (hours * 3600 * 1000);
      this.setAllUsers(users);
      this.recordAuditLog(`ACCESS_EXTENDED_${hours}H`, userId, adminEmail);
      const curr = this.getCurrentUser();
      if (curr && curr.userId === userId) this.setCurrentUser(target);
    }
  },

  grantPermanentAccess(userId: string, adminEmail: string): void {
    const users = this.getAllUsers();
    const target = users.find(u => u.userId === userId);
    if (target) {
      target.status = 'APPROVED';
      target.accessExpiresAt = 0;
      this.setAllUsers(users);
      this.recordAuditLog('PERMANENT_ACCESS_GRANTED', userId, adminEmail);
      const curr = this.getCurrentUser();
      if (curr && curr.userId === userId) this.setCurrentUser(target);
    }
  },

  suspendUser(userId: string, adminEmail: string): void {
    const users = this.getAllUsers();
    const target = users.find(u => u.userId === userId);
    if (target) {
      target.status = 'SUSPENDED';
      this.setAllUsers(users);
      this.recordAuditLog('USER_SUSPENDED', userId, adminEmail);
      const curr = this.getCurrentUser();
      if (curr && curr.userId === userId) this.setCurrentUser(target);
    }
  },

  restoreUser(userId: string, adminEmail: string): void {
    const users = this.getAllUsers();
    const target = users.find(u => u.userId === userId);
    if (target) {
      target.status = 'APPROVED';
      target.accessExpiresAt = Date.now() + EIGHT_HOURS_MILLIS;
      this.setAllUsers(users);
      this.recordAuditLog('USER_RESTORED_8H', userId, adminEmail);
      const curr = this.getCurrentUser();
      if (curr && curr.userId === userId) this.setCurrentUser(target);
    }
  },

  terminateUser(userId: string, adminEmail: string): void {
    const users = this.getAllUsers();
    const target = users.find(u => u.userId === userId);
    if (target) {
      target.status = 'TERMINATED';
      this.setAllUsers(users);
      this.recordAuditLog('USER_TERMINATED_AND_WIPED', userId, adminEmail);
      const curr = this.getCurrentUser();
      if (curr && curr.userId === userId) this.setCurrentUser(target);
    }
  },

  rejectUser(userId: string, adminEmail: string): void {
    const users = this.getAllUsers();
    const target = users.find(u => u.userId === userId);
    if (target) {
      target.status = 'REJECTED';
      this.setAllUsers(users);
      this.recordAuditLog('USER_REJECTED', userId, adminEmail);
      const curr = this.getCurrentUser();
      if (curr && curr.userId === userId) this.setCurrentUser(target);
    }
  },

  deleteUser(userId: string, adminEmail: string): void {
    const users = this.getAllUsers().filter(u => u.userId !== userId);
    this.setAllUsers(users);
    this.recordAuditLog('USER_DELETED_FROM_SYSTEM', userId, adminEmail);
    const curr = this.getCurrentUser();
    if (curr && curr.userId === userId) this.setCurrentUser(null);
  },

  updateUserComment(userId: string, comment: string, adminEmail: string): void {
    const users = this.getAllUsers();
    const target = users.find(u => u.userId === userId);
    if (target) {
      target.adminComment = comment.trim();
      target.adminCommentBy = adminEmail;
      target.adminCommentAt = Date.now();
      this.setAllUsers(users);
      this.recordAuditLog('ADMIN_COMMENT_UPDATED', userId, adminEmail);
      const curr = this.getCurrentUser();
      if (curr && curr.userId === userId) this.setCurrentUser(target);
    }
  },

  setServiceMode(mode: 'ACTIVE' | 'MAINTENANCE' | 'DISABLED', adminEmail: string): void {
    const sc = this.getServiceControl();
    sc.serviceEnabled = mode !== 'DISABLED';
    sc.maintenanceMode = mode === 'MAINTENANCE';
    sc.updatedBy = adminEmail;
    sc.updatedAt = Date.now();
    this.setServiceControl(sc);
    this.recordAuditLog(`SERVICE_STATUS_CHANGED_${mode}`, null, adminEmail);
  }
};
