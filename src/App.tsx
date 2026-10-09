import React, { useState, useEffect, useMemo, useRef } from 'react';
import {
  UserProfile,
  ServiceControl,
  AuditLog,
  ScriptEntity,
  ScreenTab,
  TypingState,
  isUserAdmin,
  isUserSuperAdmin,
  isAccessExpired,
  hasActiveAccess
} from './types';
import { AuthService } from './services/authService';
import { StorageService } from './services/storageService';
import { TypingEngine } from './engine/typingEngine';
import { HidReport } from './engine/keyboardDescriptor';
import { Header } from './components/Header';
import { BottomNav } from './components/BottomNav';
import { LoginScreen } from './components/LoginScreen';
import { LockScreen } from './components/LockScreen';
import { StatusAccessScreen } from './components/StatusAccessScreen';
import { EditorScreen } from './components/EditorScreen';
import { ScriptsScreen } from './components/ScriptsScreen';
import { KeyboardTestScreen } from './components/KeyboardTestScreen';
import { SettingsScreen } from './components/SettingsScreen';
import { AdminDashboard } from './components/AdminDashboard';

const DEFAULT_SAMPLE_TEXT = `REPLICA - I replicate keyboard
Hello World!

This is a test message from REPLICA.
Website: https://example.com
Command: git status

public class Main {
    public static void main(String[] args)
    {
        System.out.println("Hello CodeTantra & Windows!");
    }
}`;

export const App: React.FC = () => {
  // Authentication & User State
  const [userProfile, setUserProfile] = useState<UserProfile | null>(() => AuthService.getCurrentUser());
  const [allUsers, setAllUsers] = useState<UserProfile[]>(() => AuthService.getAllUsers());
  const [serviceControl, setServiceControl] = useState<ServiceControl>(() => AuthService.getServiceControl());
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>(() => AuthService.getAuditLogs());

  // Passcode Lock State
  const [isUnlocked, setIsUnlocked] = useState<boolean>(() => StorageService.isUnlocked());

  // Navigation State
  const [currentTab, setCurrentTab] = useState<ScreenTab>('EDITOR');

  // Editor State
  const [editorText, setEditorText] = useState<string>(DEFAULT_SAMPLE_TEXT);
  const [editorTitle, setEditorTitle] = useState<string>('greeting.txt');
  const [currentScriptId, setCurrentScriptId] = useState<number | null>(null);

  // Scripts State
  const [scripts, setScripts] = useState<ScriptEntity[]>(() => StorageService.getScripts());

  // Settings & Preferences
  const [typingDelayMs, setTypingDelayMs] = useState<number>(() => StorageService.getDefaultDelay());
  const [keepAwake, setKeepAwake] = useState<boolean>(() => StorageService.getKeepAwake());
  const [autoConnect, setAutoConnect] = useState<boolean>(() => StorageService.getAutoConnect());

  // Bluetooth State
  const [isConnected, setIsConnected] = useState<boolean>(() => StorageService.getAutoConnect());
  const [connectedDeviceName] = useState<string>('Windows Laptop');

  // Typing Engine State
  const [typingState, setTypingState] = useState<TypingState>({ status: 'IDLE' });
  const [lastReport, setLastReport] = useState<HidReport | null>(null);
  const [lastChar, setLastChar] = useState<string>('');
  const [isTestingAllKeys, setIsTestingAllKeys] = useState<boolean>(false);

  // Toast Notification
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  const showToast = (msg: string) => {
    setToastMessage(msg);
  };

  useEffect(() => {
    if (toastMessage) {
      const t = setTimeout(() => setToastMessage(null), 4000);
      return () => clearTimeout(t);
    }
  }, [toastMessage]);

  // Typing Engine Instance Ref
  const typingEngineRef = useRef<TypingEngine | null>(null);
  if (!typingEngineRef.current) {
    typingEngineRef.current = new TypingEngine(
      typingDelayMs,
      (state) => setTypingState(state),
      (report, char) => {
        setLastReport(report);
        if (char) setLastChar(char);
      }
    );
  }

  // Synchronize typing delay to engine
  useEffect(() => {
    if (typingEngineRef.current) {
      typingEngineRef.current.setDelay(typingDelayMs);
      StorageService.setDefaultDelay(typingDelayMs);
    }
  }, [typingDelayMs]);

  // Screen Wake Lock API
  useEffect(() => {
    let wakeLock: any = null;
    if (keepAwake && 'wakeLock' in navigator) {
      (navigator as any).wakeLock?.request('screen').then((lock: any) => {
        wakeLock = lock;
      }).catch(() => {});
    }
    return () => {
      if (wakeLock) wakeLock.release().catch(() => {});
    };
  }, [keepAwake]);

  // Handlers for Auth
  const handleLogin = (email: string, name: string) => {
    const profile = AuthService.loginWithGoogle(email, name);
    setUserProfile(profile);
    setAllUsers(AuthService.getAllUsers());
    showToast(`Signed in as ${profile.displayName}`);
  };

  const handleSignOut = () => {
    if (typingEngineRef.current) typingEngineRef.current.stopTyping();
    AuthService.signOut();
    setUserProfile(null);
    setCurrentTab('EDITOR');
    showToast('Signed out successfully.');
  };

  const handleRefreshStatus = () => {
    const fresh = AuthService.getCurrentUser();
    setUserProfile(fresh);
    setServiceControl(AuthService.getServiceControl());
    setAllUsers(AuthService.getAllUsers());
    showToast('Account status refreshed.');
  };

  const handleRequestAccess = () => {
    if (userProfile) {
      AuthService.requestAccess(userProfile.userId);
      setUserProfile(AuthService.getCurrentUser());
      setAllUsers(AuthService.getAllUsers());
      setAuditLogs(AuthService.getAuditLogs());
      showToast('Access request sent to administrator.');
    }
  };

  // Passcode Lock / Unlock
  const handleLockNow = () => {
    if (typingEngineRef.current) typingEngineRef.current.stopTyping();
    StorageService.lock();
    setIsUnlocked(false);
    showToast('Locked! Keystroke transmission halted.');
  };

  const handleUnlockSuccess = () => {
    setIsUnlocked(true);
    showToast('Owner passcode verified.');
  };

  // Bluetooth Connection Handlers
  const handleToggleConnect = () => {
    if (isConnected) {
      if (typingEngineRef.current) typingEngineRef.current.stopTyping();
      setIsConnected(false);
      showToast('Bluetooth device disconnected.');
    } else {
      setIsConnected(true);
      showToast(`Connected to ${connectedDeviceName}`);
    }
  };

  const handleToggleAutoConnect = (val: boolean) => {
    setAutoConnect(val);
    StorageService.setAutoConnect(val);
    if (val && !isConnected) {
      setIsConnected(true);
    }
    showToast(`Bluetooth Auto-Connect: ${val ? 'ON' : 'OFF'}`);
  };

  // Typing Controls
  const handleStartTyping = () => {
    if (!userProfile || !hasActiveAccess(userProfile)) {
      showToast('Active approved account required to auto-type.');
      return;
    }
    if (!serviceControl.serviceEnabled && !isUserSuperAdmin(userProfile.email, userProfile.role)) {
      showToast(serviceControl.disabledMessage);
      return;
    }
    if (serviceControl.maintenanceMode && !isUserSuperAdmin(userProfile.email, userProfile.role)) {
      showToast(serviceControl.maintenanceMessage);
      return;
    }
    if (!isConnected) {
      showToast('Bluetooth connection required. Connecting...');
      setIsConnected(true);
    }
    if (!editorText.trim()) {
      showToast('There is no text to type. Please enter text first.');
      return;
    }

    typingEngineRef.current?.startTyping(editorText, typingDelayMs);
  };

  const handlePauseTyping = () => {
    typingEngineRef.current?.pauseTyping();
  };

  const handleResumeTyping = () => {
    typingEngineRef.current?.resumeTyping();
  };

  const handleStopTyping = () => {
    typingEngineRef.current?.stopTyping();
  };

  // Editor Actions
  const handleSaveText = (title: string) => {
    const finalTitle = title.trim() || `Text_${Math.floor(Date.now() / 1000)}.txt`;
    if (currentScriptId) {
      StorageService.updateScript(currentScriptId, finalTitle, editorText);
    } else {
      const created = StorageService.saveScript(finalTitle, editorText);
      setCurrentScriptId(created.id);
    }
    setEditorTitle(finalTitle);
    setScripts(StorageService.getScripts());
    showToast(`Saved '${finalTitle}' locally.`);
  };

  const handleLoadSample = () => {
    if (typingEngineRef.current) typingEngineRef.current.stopTyping();
    setEditorText(DEFAULT_SAMPLE_TEXT);
    setEditorTitle('greeting.txt');
    setCurrentScriptId(null);
    showToast('Loaded sample text.');
  };

  // Scripts Actions
  const handleLoadScript = (script: ScriptEntity) => {
    if (typingEngineRef.current) typingEngineRef.current.stopTyping();
    setEditorText(script.content);
    setEditorTitle(script.title);
    setCurrentScriptId(script.id);
    setCurrentTab('EDITOR');
    showToast(`Loaded '${script.title}' into editor.`);
  };

  const handleRenameScript = (id: number, newTitle: string) => {
    StorageService.renameScript(id, newTitle);
    setScripts(StorageService.getScripts());
    if (currentScriptId === id) {
      setEditorTitle(newTitle);
    }
    showToast('Document renamed.');
  };

  const handleDeleteScript = (id: number) => {
    StorageService.deleteScript(id);
    setScripts(StorageService.getScripts());
    if (currentScriptId === id) {
      setCurrentScriptId(null);
    }
    showToast('Document deleted.');
  };

  const handleNewScript = () => {
    setEditorText('');
    setEditorTitle('Untitled.txt');
    setCurrentScriptId(null);
    setCurrentTab('EDITOR');
  };

  // Test Mode Actions
  const handleSendDirectKey = (keyCode: number, modifier: number = 0, charName: string = '') => {
    typingEngineRef.current?.sendSingleKey(keyCode, modifier, charName);
  };

  const handleRunTestAllKeys = () => {
    if (!isConnected) {
      setIsConnected(true);
    }
    setIsTestingAllKeys(true);
    const testString = `abcdefghijklmnopqrstuvwxyz
ABCDEFGHIJKLMNOPQRSTUVWXYZ
0123456789
!@#$%^&*()-_=+
[]{};:'",.<>/?\`~
`;
    typingEngineRef.current?.startTyping(testString, 20);
    const checkTimer = setInterval(() => {
      const state = typingEngineRef.current?.getState();
      if (state && (state.status === 'COMPLETED' || state.status === 'STOPPED' || state.status === 'ERROR' || state.status === 'IDLE')) {
        clearInterval(checkTimer);
        setIsTestingAllKeys(false);
        showToast('Test key sequence completed!');
      }
    }, 100);
  };

  // Admin Dashboard Handlers
  const handleAdminApproveUser = (userId: string) => {
    const adminEmail = userProfile?.email || 'nani68629@gmail.com';
    AuthService.approveUser(userId, adminEmail);
    setAllUsers(AuthService.getAllUsers());
    setAuditLogs(AuthService.getAuditLogs());
    showToast('User approved with 8 hours access.');
  };

  const handleAdminRejectUser = (userId: string) => {
    const adminEmail = userProfile?.email || 'nani68629@gmail.com';
    AuthService.rejectUser(userId, adminEmail);
    setAllUsers(AuthService.getAllUsers());
    setAuditLogs(AuthService.getAuditLogs());
    showToast('User request rejected.');
  };

  const handleAdminExtendAccess = (userId: string, hours: number = 8) => {
    const adminEmail = userProfile?.email || 'nani68629@gmail.com';
    AuthService.extendAccess(userId, hours, adminEmail);
    setAllUsers(AuthService.getAllUsers());
    setAuditLogs(AuthService.getAuditLogs());
    showToast(`Access extended by ${hours} hours.`);
  };

  const handleAdminGrantPermanent = (userId: string) => {
    const adminEmail = userProfile?.email || 'nani68629@gmail.com';
    AuthService.grantPermanentAccess(userId, adminEmail);
    setAllUsers(AuthService.getAllUsers());
    setAuditLogs(AuthService.getAuditLogs());
    showToast('Permanent access granted.');
  };

  const handleAdminSuspendUser = (userId: string) => {
    const adminEmail = userProfile?.email || 'nani68629@gmail.com';
    AuthService.suspendUser(userId, adminEmail);
    setAllUsers(AuthService.getAllUsers());
    setAuditLogs(AuthService.getAuditLogs());
    showToast('User suspended.');
  };

  const handleAdminRestoreUser = (userId: string) => {
    const adminEmail = userProfile?.email || 'nani68629@gmail.com';
    AuthService.restoreUser(userId, adminEmail);
    setAllUsers(AuthService.getAllUsers());
    setAuditLogs(AuthService.getAuditLogs());
    showToast('User restored to Approved with 8 hours access.');
  };

  const handleAdminTerminateUser = (userId: string) => {
    const adminEmail = userProfile?.email || 'nani68629@gmail.com';
    AuthService.terminateUser(userId, adminEmail);
    setAllUsers(AuthService.getAllUsers());
    setAuditLogs(AuthService.getAuditLogs());
    showToast('User access terminated. Admin can re-approve later.');
  };

  const handleAdminDeleteUser = (userId: string) => {
    const adminEmail = userProfile?.email || 'nani68629@gmail.com';
    AuthService.deleteUser(userId, adminEmail);
    setAllUsers(AuthService.getAllUsers());
    setAuditLogs(AuthService.getAuditLogs());
    showToast('User permanently removed from REPLICA.');
  };

  const handleAdminUpdateComment = (userId: string, comment: string) => {
    const adminEmail = userProfile?.email || 'nani68629@gmail.com';
    AuthService.updateUserComment(userId, comment, adminEmail);
    setAllUsers(AuthService.getAllUsers());
    setAuditLogs(AuthService.getAuditLogs());
    showToast('Admin comment updated.');
  };

  const handleAdminAddUserAndGrantAccess = (email: string, name: string, hours: number) => {
    const adminEmail = userProfile?.email || 'nani68629@gmail.com';
    AuthService.addUserAndGrantAccess(email, name, hours, adminEmail);
    setAllUsers(AuthService.getAllUsers());
    setAuditLogs(AuthService.getAuditLogs());
    showToast(`Access granted to ${email} (${hours === 0 ? 'Permanent' : hours + 'h'}).`);
  };

  const handleAdminSetServiceMode = (mode: 'ACTIVE' | 'MAINTENANCE' | 'DISABLED') => {
    const adminEmail = userProfile?.email || 'nani68629@gmail.com';
    AuthService.setServiceMode(mode, adminEmail);
    setServiceControl(AuthService.getServiceControl());
    setAuditLogs(AuthService.getAuditLogs());
    showToast(`Global service status updated to ${mode}.`);
  };

  // ============================================
  // CONDITIONAL GATING FLOW (Matches Compose Lifecycle)
  // ============================================

  // 1. Unauthenticated -> LoginScreen with Sphere Illusion
  if (!userProfile) {
    return <LoginScreen onLogin={handleLogin} />;
  }

  const isSuperAdminUser = isUserSuperAdmin(userProfile.email, userProfile.role);
  const isAdminUser = isUserAdmin(userProfile);

  // 2. Access Control Gate
  if (userProfile.status === 'PENDING') {
    return (
      <StatusAccessScreen
        reason="PENDING"
        userProfile={userProfile}
        onRefresh={handleRefreshStatus}
        onSignOut={handleSignOut}
        onRequestAccess={handleRequestAccess}
      />
    );
  }

  if (userProfile.status === 'REJECTED') {
    return (
      <StatusAccessScreen
        reason="REJECTED"
        userProfile={userProfile}
        onRefresh={handleRefreshStatus}
        onSignOut={handleSignOut}
        onRequestAccess={handleRequestAccess}
      />
    );
  }

  if (userProfile.status === 'SUSPENDED') {
    return (
      <StatusAccessScreen
        reason="SUSPENDED"
        userProfile={userProfile}
        onRefresh={handleRefreshStatus}
        onSignOut={handleSignOut}
        onRequestAccess={handleRequestAccess}
      />
    );
  }

  if (userProfile.status === 'TERMINATED') {
    return (
      <StatusAccessScreen
        reason="TERMINATED"
        userProfile={userProfile}
        onRefresh={handleRefreshStatus}
        onSignOut={handleSignOut}
        onRequestAccess={handleRequestAccess}
      />
    );
  }

  if (isAccessExpired(userProfile)) {
    return (
      <StatusAccessScreen
        reason="ACCESS_EXPIRED"
        userProfile={userProfile}
        onRefresh={handleRefreshStatus}
        onSignOut={handleSignOut}
        onRequestAccess={handleRequestAccess}
      />
    );
  }

  // 3. Service Control Gate (maintenance / disabled)
  if (serviceControl.maintenanceMode && !isSuperAdminUser) {
    return (
      <StatusAccessScreen
        reason="MAINTENANCE"
        userProfile={userProfile}
        serviceMessage={serviceControl.maintenanceMessage}
        onRefresh={handleRefreshStatus}
        onSignOut={handleSignOut}
        onOpenAdmin={isAdminUser ? () => setCurrentTab('ADMIN') : undefined}
      />
    );
  }

  if (!serviceControl.serviceEnabled && !isSuperAdminUser) {
    return (
      <StatusAccessScreen
        reason="SERVICE_DISABLED"
        userProfile={userProfile}
        serviceMessage={serviceControl.disabledMessage}
        onRefresh={handleRefreshStatus}
        onSignOut={handleSignOut}
        onOpenAdmin={isAdminUser ? () => setCurrentTab('ADMIN') : undefined}
      />
    );
  }

  // 4. Owner Passcode Lock Overlay
  if (!isUnlocked) {
    return <LockScreen onUnlockSuccess={handleUnlockSuccess} />;
  }

  // 5. Approved Application Views
  return (
    <div className="min-h-screen bg-[#090d16] text-gray-100 flex flex-col">
      {/* Toast popup */}
      {toastMessage && (
        <div className="fixed top-16 left-1/2 -translate-x-1/2 z-50 px-4 py-2 rounded-xl bg-gray-900 border border-cyan-500/50 text-cyan-300 text-xs font-semibold shadow-2xl backdrop-blur-md animate-fade-in flex items-center gap-2 max-w-sm text-center">
          <span>{toastMessage}</span>
        </div>
      )}

      {/* Top Header */}
      <Header
        userProfile={userProfile}
        onLockClick={handleLockNow}
        onAdminClick={() => setCurrentTab(currentTab === 'ADMIN' ? 'EDITOR' : 'ADMIN')}
        isAdminActive={currentTab === 'ADMIN'}
        isConnected={isConnected}
      />

      {/* Main Container */}
      <main className="flex-1 max-w-lg w-full mx-auto p-4">
        {currentTab === 'ADMIN' ? (
          <AdminDashboard
            currentProfile={userProfile}
            allUsers={allUsers}
            serviceControl={serviceControl}
            auditLogs={auditLogs}
            onBack={() => setCurrentTab('EDITOR')}
            onApproveUser={handleAdminApproveUser}
            onRejectUser={handleAdminRejectUser}
            onExtendAccess={handleAdminExtendAccess}
            onGrantPermanent={handleAdminGrantPermanent}
            onSuspendUser={handleAdminSuspendUser}
            onRestoreUser={handleAdminRestoreUser}
            onTerminateUser={handleAdminTerminateUser}
            onDeleteUser={handleAdminDeleteUser}
            onUpdateComment={handleAdminUpdateComment}
            onSetServiceMode={handleAdminSetServiceMode}
            onAddUserAndGrantAccess={handleAdminAddUserAndGrantAccess}
          />
        ) : currentTab === 'EDITOR' ? (
          <EditorScreen
            userProfile={userProfile}
            editorText={editorText}
            editorTitle={editorTitle}
            onTextChange={setEditorText}
            typingDelayMs={typingDelayMs}
            onDelayChange={setTypingDelayMs}
            typingState={typingState}
            onStartTyping={handleStartTyping}
            onPauseTyping={handlePauseTyping}
            onResumeTyping={handleResumeTyping}
            onStopTyping={handleStopTyping}
            onSave={handleSaveText}
            onLoadSample={handleLoadSample}
            onNavigateToScripts={() => setCurrentTab('SCRIPTS')}
            isConnected={isConnected}
            onToggleConnect={handleToggleConnect}
            autoConnect={autoConnect}
            onToggleAutoConnect={handleToggleAutoConnect}
            connectedDeviceName={connectedDeviceName}
            onLockClick={handleLockNow}
          />
        ) : currentTab === 'SCRIPTS' ? (
          <ScriptsScreen
            scripts={scripts}
            onLoadScript={handleLoadScript}
            onRenameScript={handleRenameScript}
            onDeleteScript={handleDeleteScript}
            onNewScript={handleNewScript}
          />
        ) : currentTab === 'TEST' ? (
          <KeyboardTestScreen
            onSendDirectKey={handleSendDirectKey}
            onRunTestAllKeys={handleRunTestAllKeys}
            isTestingAllKeys={isTestingAllKeys}
            lastReport={lastReport}
            lastChar={lastChar}
            isConnected={isConnected}
          />
        ) : (
          <SettingsScreen
            userProfile={userProfile}
            onSignOut={handleSignOut}
            onOpenAdmin={() => setCurrentTab('ADMIN')}
            isConnected={isConnected}
            onToggleConnect={handleToggleConnect}
            keepAwake={keepAwake}
            onToggleKeepAwake={setKeepAwake}
            autoConnect={autoConnect}
            onToggleAutoConnect={handleToggleAutoConnect}
            onLockClick={handleLockNow}
          />
        )}
      </main>

      {/* Bottom Navigation */}
      {currentTab !== 'ADMIN' && (
        <BottomNav currentTab={currentTab} onTabChange={setCurrentTab} />
      )}
    </div>
  );
};
