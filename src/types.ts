export type UserStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED' | 'TERMINATED';
export type UserRole = 'USER' | 'ADMIN' | 'SUPER_ADMIN';

export const SUPER_ADMIN_EMAILS = [
  'nani68629@gmail.com',
  'pskcoll68629@gmail.com'
];

export const EIGHT_HOURS_MILLIS = 8 * 60 * 60 * 1000;

export interface UserProfile {
  userId: string;
  displayName: string;
  email: string;
  photoUrl?: string | null;
  status: UserStatus;
  role: UserRole;
  registrationDate: number;
  lastLogin: number;
  accessExpiresAt: number; // 0 = permanent (admin or granted), otherwise timestamp ms
  adminComment?: string | null;
  adminCommentBy?: string | null;
  adminCommentAt?: number;
}

export function isUserSuperAdmin(email?: string | null, role?: string): boolean {
  if (role === 'SUPER_ADMIN') return true;
  if (!email) return false;
  return SUPER_ADMIN_EMAILS.some(e => e.toLowerCase() === email.toLowerCase());
}

export function isUserAdmin(profile?: UserProfile | null): boolean {
  if (!profile) return false;
  return profile.role === 'ADMIN' || profile.role === 'SUPER_ADMIN' || isUserSuperAdmin(profile.email, profile.role);
}

export function isAccessExpired(profile?: UserProfile | null): boolean {
  if (!profile) return true;
  if (isUserAdmin(profile)) return false;
  return profile.accessExpiresAt > 0 && Date.now() > profile.accessExpiresAt;
}

export function hasActiveAccess(profile?: UserProfile | null): boolean {
  if (!profile) return false;
  return profile.status === 'APPROVED' && !isAccessExpired(profile);
}

export function getRemainingTimeFormatted(profile?: UserProfile | null): string {
  if (!profile) return 'No Account';
  if (isUserAdmin(profile) || profile.accessExpiresAt === 0) return 'Permanent (Admin)';
  const remaining = profile.accessExpiresAt - Date.now();
  if (remaining <= 0) return 'Expired';
  const hours = Math.floor(remaining / (1000 * 60 * 60));
  const minutes = Math.floor((remaining / (1000 * 60)) % 60);
  return hours > 0 ? `${hours}h ${minutes}m left` : `${minutes}m left`;
}

export interface ServiceControl {
  serviceEnabled: boolean;
  maintenanceMode: boolean;
  disabledMessage: string;
  maintenanceMessage: string;
  updatedBy: string;
  updatedAt: number;
}

export interface AuditLog {
  id: string;
  timestamp: number;
  adminId: string;
  adminEmail: string;
  action: string;
  targetUserId?: string | null;
  result: string;
}

export interface ScriptEntity {
  id: number;
  title: string;
  content: string;
  language: string;
  createdAt: number;
  updatedAt: number;
}

export type ScreenTab = 'EDITOR' | 'SCRIPTS' | 'TEST' | 'SETTINGS' | 'ADMIN';

export interface BluetoothDeviceInfo {
  id: string;
  name: string;
  address?: string;
  connected: boolean;
}

export type HidConnectionState =
  | { status: 'DISCONNECTED' }
  | { status: 'CONNECTING'; deviceName?: string }
  | { status: 'CONNECTED'; deviceName: string }
  | { status: 'DISABLED' };

export type TypingState =
  | { status: 'IDLE' }
  | { status: 'TYPING'; currentIndex: number; totalChars: number; percent: number }
  | { status: 'PAUSED'; currentIndex: number; totalChars: number; percent: number }
  | { status: 'COMPLETED'; totalChars: number }
  | { status: 'STOPPED'; stoppedAtIndex: number; totalChars: number }
  | { status: 'ERROR'; message: string; atIndex: number };
