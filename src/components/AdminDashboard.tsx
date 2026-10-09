import React, { useState } from 'react';
import {
  UserProfile,
  ServiceControl,
  AuditLog,
  isUserSuperAdmin,
  getRemainingTimeFormatted
} from '../types';
import {
  ArrowLeft,
  Users,
  Shield,
  Clock,
  AlertTriangle,
  CheckCircle,
  XCircle,
  FileText,
  Power,
  Trash2,
  Edit3,
  Search,
  MessageSquare,
  Lock,
  Plus,
  UserPlus,
  CheckCircle2
} from 'lucide-react';

interface AdminDashboardProps {
  currentProfile: UserProfile | null;
  allUsers: UserProfile[];
  serviceControl: ServiceControl;
  auditLogs: AuditLog[];
  onBack: () => void;
  onApproveUser: (userId: string) => void;
  onRejectUser: (userId: string) => void;
  onExtendAccess: (userId: string, hours?: number) => void;
  onGrantPermanent: (userId: string) => void;
  onSuspendUser: (userId: string) => void;
  onRestoreUser: (userId: string) => void;
  onTerminateUser: (userId: string) => void;
  onDeleteUser: (userId: string) => void;
  onUpdateComment: (userId: string, comment: string) => void;
  onSetServiceMode: (mode: 'ACTIVE' | 'MAINTENANCE' | 'DISABLED') => void;
  onAddUserAndGrantAccess?: (email: string, name: string, hours: number) => void;
}

export const AdminDashboard: React.FC<AdminDashboardProps> = ({
  currentProfile,
  allUsers,
  serviceControl,
  auditLogs,
  onBack,
  onApproveUser,
  onRejectUser,
  onExtendAccess,
  onGrantPermanent,
  onSuspendUser,
  onRestoreUser,
  onTerminateUser,
  onDeleteUser,
  onUpdateComment,
  onSetServiceMode,
  onAddUserAndGrantAccess
}) => {
  const [activeTab, setActiveTab] = useState<'overview' | 'pending' | 'users' | 'service' | 'logs'>('overview');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [searchQuery, setSearchQuery] = useState('');

  // Dialog states
  const [commentTarget, setCommentTarget] = useState<UserProfile | null>(null);
  const [commentInput, setCommentInput] = useState('');
  const [confirmAction, setConfirmAction] = useState<{ user: UserProfile; action: string } | null>(null);
  const [serviceConfirmMode, setServiceConfirmMode] = useState<'ACTIVE' | 'MAINTENANCE' | 'DISABLED' | null>(null);

  // Add User modal state
  const [showAddUserModal, setShowAddUserModal] = useState(false);
  const [newUserEmail, setNewUserEmail] = useState('');
  const [newUserName, setNewUserName] = useState('');
  const [newUserHours, setNewUserHours] = useState<number>(8);

  const isSuperAdmin = isUserSuperAdmin(currentProfile?.email, currentProfile?.role);

  const pendingUsers = allUsers.filter(u => u.status === 'PENDING');
  const pendingCount = pendingUsers.length;
  const approvedCount = allUsers.filter(u => u.status === 'APPROVED').length;
  const suspendedCount = allUsers.filter(u => u.status === 'SUSPENDED').length;
  const terminatedCount = allUsers.filter(u => u.status === 'TERMINATED').length;

  const filteredUsers = allUsers.filter(u => {
    const matchesStatus = statusFilter === 'ALL' || u.status === statusFilter;
    const matchesSearch =
      u.displayName.toLowerCase().includes(searchQuery.toLowerCase()) ||
      u.email.toLowerCase().includes(searchQuery.toLowerCase());
    return matchesStatus && matchesSearch;
  });

  const handleCreateUser = () => {
    if (!newUserEmail.trim()) return;
    if (onAddUserAndGrantAccess) {
      onAddUserAndGrantAccess(newUserEmail.trim(), newUserName.trim() || newUserEmail.split('@')[0], newUserHours);
    }
    setNewUserEmail('');
    setNewUserName('');
    setShowAddUserModal(false);
  };

  return (
    <div className="flex flex-col gap-4 pb-20">
      {/* Top Header */}
      <div className="flex items-center justify-between pb-3 border-b border-[#1f293d]">
        <div className="flex items-center gap-2.5">
          <button
            onClick={onBack}
            className="p-2 rounded-xl bg-[#0b0f19] hover:bg-[#162035] border border-[#1f293d] text-gray-300 hover:text-white transition-all"
          >
            <ArrowLeft size={18} />
          </button>
          <div>
            <h2 className="text-lg font-bold text-white tracking-wide">ADMIN CONTROL CENTER</h2>
            <div className="text-xs text-cyan-400 font-mono">
              Admin: {currentProfile?.email || 'nani68629@gmail.com'} • {isSuperAdmin ? 'SUPER_ADMIN' : 'ADMIN'}
            </div>
          </div>
        </div>

        {/* Quick Add User & Grant Access button */}
        <button
          onClick={() => setShowAddUserModal(true)}
          className="px-3 py-1.5 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white font-bold text-xs flex items-center gap-1.5 shadow-md shadow-cyan-600/30 transition-all"
        >
          <UserPlus size={15} />
          <span className="hidden sm:inline">Grant Access to User</span>
          <span className="sm:hidden">+ Grant</span>
        </button>
      </div>

      {/* Tabs */}
      <div className="grid grid-cols-5 gap-1 bg-[#0b0f19] p-1 rounded-2xl border border-[#1f293d]">
        {[
          { id: 'overview', label: 'Overview' },
          { id: 'pending', label: `New / Pending (${pendingCount})` },
          { id: 'users', label: 'Users' },
          { id: 'service', label: 'Service' },
          { id: 'logs', label: 'Logs' }
        ].map(t => (
          <button
            key={t.id}
            onClick={() => setActiveTab(t.id as any)}
            className={`py-2 px-1 text-xs font-bold rounded-xl transition-all truncate text-center ${
              activeTab === t.id
                ? 'bg-cyan-600 text-white shadow-md shadow-cyan-600/30'
                : 'text-gray-400 hover:text-gray-200'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {/* TAB 1: OVERVIEW */}
      {activeTab === 'overview' && (
        <div className="flex flex-col gap-4">
          {/* Service Status Overview */}
          <div
            className={`border rounded-2xl p-4 flex items-center justify-between shadow-md ${
              serviceControl.serviceEnabled
                ? 'bg-emerald-950/20 border-emerald-500/40'
                : 'bg-red-950/20 border-red-500/40'
            }`}
          >
            <div>
              <div className="text-xs font-bold text-gray-400 uppercase tracking-wider">
                REPLICA Global Service Status
              </div>
              <div
                className={`text-base font-extrabold flex items-center gap-2 mt-0.5 ${
                  serviceControl.serviceEnabled ? 'text-emerald-400' : 'text-red-400'
                }`}
              >
                <span
                  className={`w-2.5 h-2.5 rounded-full ${
                    serviceControl.serviceEnabled ? 'bg-emerald-400 animate-pulse' : 'bg-red-400'
                  }`}
                />
                {serviceControl.serviceEnabled
                  ? serviceControl.maintenanceMode
                    ? 'MAINTENANCE MODE'
                    : 'ACTIVE & OPERATIONAL'
                  : 'DISABLED GLOBALLY'}
              </div>
            </div>

            <button
              onClick={() => setActiveTab('service')}
              className="px-3 py-1.5 rounded-xl bg-[#0b0f19] border border-[#1f293d] hover:border-gray-600 text-xs text-white font-bold"
            >
              Manage
            </button>
          </div>

          {/* User Stat Cards */}
          <div className="grid grid-cols-2 gap-3">
            <div
              onClick={() => setActiveTab('pending')}
              className="bg-[#111827] border border-[#1f293d] hover:border-amber-500/50 rounded-2xl p-4 cursor-pointer transition-all shadow-md"
            >
              <div className="text-xs font-bold text-amber-400 uppercase">NEW / PENDING APPROVAL</div>
              <div className="text-3xl font-extrabold text-white mt-1">{pendingCount}</div>
            </div>

            <div
              onClick={() => {
                setStatusFilter('APPROVED');
                setActiveTab('users');
              }}
              className="bg-[#111827] border border-[#1f293d] hover:border-emerald-500/50 rounded-2xl p-4 cursor-pointer transition-all shadow-md"
            >
              <div className="text-xs font-bold text-emerald-400 uppercase">APPROVED ACTIVE</div>
              <div className="text-3xl font-extrabold text-white mt-1">{approvedCount}</div>
            </div>

            <div
              onClick={() => {
                setStatusFilter('SUSPENDED');
                setActiveTab('users');
              }}
              className="bg-[#111827] border border-[#1f293d] hover:border-amber-500/50 rounded-2xl p-4 cursor-pointer transition-all shadow-md"
            >
              <div className="text-xs font-bold text-amber-400 uppercase">SUSPENDED</div>
              <div className="text-3xl font-extrabold text-white mt-1">{suspendedCount}</div>
            </div>

            <div
              onClick={() => {
                setStatusFilter('TERMINATED');
                setActiveTab('users');
              }}
              className="bg-[#111827] border border-[#1f293d] hover:border-red-500/50 rounded-2xl p-4 cursor-pointer transition-all shadow-md"
            >
              <div className="text-xs font-bold text-red-400 uppercase">TERMINATED (RE-APPROVABLE)</div>
              <div className="text-3xl font-extrabold text-white mt-1">{terminatedCount}</div>
            </div>
          </div>

          {/* Quick Pending List if any */}
          {pendingCount > 0 && (
            <div className="bg-[#111827] border border-amber-500/30 rounded-2xl p-4 flex flex-col gap-3">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-amber-400 uppercase tracking-wide flex items-center gap-1.5">
                  <Clock size={14} />
                  New Users Awaiting Access Approval ({pendingCount})
                </span>
                <button
                  onClick={() => setActiveTab('pending')}
                  className="text-xs text-cyan-400 hover:underline font-bold"
                >
                  View All
                </button>
              </div>

              {pendingUsers.slice(0, 3).map(user => (
                <div
                  key={user.userId}
                  className="bg-[#0b0f19] p-3 rounded-xl border border-[#1f293d] flex items-center justify-between"
                >
                  <div className="truncate">
                    <div className="text-xs font-bold text-white truncate">{user.displayName}</div>
                    <div className="text-[11px] text-gray-400 truncate">{user.email}</div>
                  </div>
                  <button
                    onClick={() => onApproveUser(user.userId)}
                    className="px-3 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-bold shrink-0 shadow-sm"
                  >
                    GRANT ACCESS (8h)
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* TAB 2: PENDING & NEW ACCESS REQUESTS */}
      {activeTab === 'pending' && (
        <div className="flex flex-col gap-3">
          <div className="flex justify-between items-center bg-[#111827] border border-[#1f293d] p-3 rounded-2xl">
            <span className="text-xs font-bold text-gray-300">New Users &amp; Access Requests</span>
            <button
              onClick={() => setShowAddUserModal(true)}
              className="px-3 py-1.5 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-bold flex items-center gap-1"
            >
              <UserPlus size={14} />
              + Add &amp; Grant Access
            </button>
          </div>

          {pendingCount === 0 ? (
            <div className="bg-[#111827] border border-[#1f293d] rounded-2xl p-8 text-center text-gray-400 text-xs">
              No pending access requests. You can manually grant access to any user with the button above.
            </div>
          ) : (
            pendingUsers.map(user => (
              <div
                key={user.userId}
                className="bg-[#111827] border border-[#1f293d] rounded-2xl p-4 shadow-md flex flex-col gap-3"
              >
                <div className="flex justify-between items-start">
                  <div>
                    <div className="text-sm font-bold text-white">{user.displayName}</div>
                    <div className="text-xs text-gray-400">{user.email}</div>
                    <div className="text-[10px] text-gray-500 mt-1 font-mono">
                      Registered: {new Date(user.registrationDate).toLocaleString()}
                    </div>
                  </div>
                  <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-950/60 text-amber-400 border border-amber-800">
                    PENDING
                  </span>
                </div>

                <div className="flex flex-wrap justify-end gap-2 pt-2 border-t border-[#1f293d]">
                  <button
                    onClick={() => setConfirmAction({ user, action: 'REJECT' })}
                    className="px-3 py-1.5 rounded-xl bg-[#0b0f19] hover:bg-red-950/50 border border-red-800 text-red-300 text-xs font-bold"
                  >
                    REJECT
                  </button>
                  <button
                    onClick={() => onGrantPermanent(user.userId)}
                    className="px-3 py-1.5 rounded-xl bg-[#0b0f19] hover:bg-[#162035] border border-[#1f293d] text-gray-300 text-xs font-bold"
                  >
                    GRANT PERMANENT
                  </button>
                  <button
                    onClick={() => onApproveUser(user.userId)}
                    className="px-4 py-1.5 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-bold shadow-md shadow-emerald-600/30"
                  >
                    GRANT ACCESS (8h)
                  </button>
                </div>
              </div>
            ))
          )}
        </div>
      )}

      {/* TAB 3: ALL USERS MANAGEMENT */}
      {activeTab === 'users' && (
        <div className="flex flex-col gap-3">
          {/* Filters & Search */}
          <div className="flex flex-col sm:flex-row gap-2">
            <div className="relative flex-1">
              <Search size={14} className="absolute left-3 top-3 text-gray-400" />
              <input
                type="text"
                placeholder="Search user name or email..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full bg-[#0b0f19] border border-[#1f293d] rounded-xl pl-9 pr-3 py-2 text-xs text-white focus:outline-none focus:border-cyan-500"
              />
            </div>

            <div className="flex gap-1 overflow-x-auto pb-1">
              {['ALL', 'APPROVED', 'SUSPENDED', 'TERMINATED'].map(status => (
                <button
                  key={status}
                  onClick={() => setStatusFilter(status)}
                  className={`px-3 py-1.5 rounded-xl text-[11px] font-bold font-mono transition-all shrink-0 ${
                    statusFilter === status
                      ? 'bg-cyan-600 text-white shadow-sm'
                      : 'bg-[#0b0f19] text-gray-400 hover:text-gray-200 border border-[#1f293d]'
                  }`}
                >
                  {status}
                </button>
              ))}
            </div>
          </div>

          {/* Users List */}
          {filteredUsers.length === 0 ? (
            <div className="bg-[#111827] border border-[#1f293d] rounded-2xl p-6 text-center text-gray-400 text-xs">
              No users found matching filters.
            </div>
          ) : (
            filteredUsers.map(user => (
              <div
                key={user.userId}
                className="bg-[#111827] border border-[#1f293d] rounded-2xl p-4 shadow-md flex flex-col gap-2.5"
              >
                <div className="flex justify-between items-start">
                  <div>
                    <div className="text-sm font-bold text-white flex items-center gap-2">
                      {user.displayName}
                      <span className="text-[10px] font-mono px-2 py-0.5 rounded-full bg-blue-950/80 text-blue-400 border border-blue-900">
                        {user.role}
                      </span>
                    </div>
                    <div className="text-xs text-gray-400">{user.email}</div>
                  </div>

                  <span
                    className={`px-2.5 py-0.5 rounded-full text-[10px] font-bold border ${
                      user.status === 'APPROVED'
                        ? 'bg-emerald-950/60 text-emerald-400 border-emerald-800'
                        : user.status === 'PENDING'
                        ? 'bg-amber-950/60 text-amber-400 border-amber-800'
                        : 'bg-red-950/60 text-red-400 border-red-800'
                    }`}
                  >
                    {user.status}
                  </span>
                </div>

                <div className="flex items-center gap-2 text-[11px] bg-[#0b0f19] p-2 rounded-xl border border-[#1f293d]">
                  <Clock size={13} className="text-emerald-400" />
                  <span className="text-gray-400">Access:</span>
                  <span className="font-bold text-emerald-400">{getRemainingTimeFormatted(user)}</span>
                </div>

                {user.adminComment && (
                  <div className="text-xs font-mono text-cyan-300 bg-[#0b0f19] p-2 rounded-xl border border-cyan-950">
                    Note: &quot;{user.adminComment}&quot;
                  </div>
                )}

                {/* Action buttons */}
                <div className="flex flex-wrap justify-end gap-1.5 pt-2 border-t border-[#1f293d]">
                  <button
                    onClick={() => {
                      setCommentTarget(user);
                      setCommentInput(user.adminComment || '');
                    }}
                    className="px-2.5 py-1 rounded-lg bg-[#0b0f19] hover:bg-[#162035] border border-[#1f293d] text-cyan-400 text-[11px] font-semibold flex items-center gap-1"
                  >
                    <MessageSquare size={12} />
                    {user.adminComment ? 'Edit Note' : 'Add Note'}
                  </button>

                  {user.status === 'APPROVED' && user.role !== 'SUPER_ADMIN' && (
                    <>
                      <button
                        onClick={() => onExtendAccess(user.userId, 8)}
                        className="px-2.5 py-1 rounded-lg bg-blue-600/30 hover:bg-blue-600/50 border border-blue-500/50 text-blue-300 text-[11px] font-bold"
                      >
                        CONTINUE (+8h)
                      </button>
                      <button
                        onClick={() => onGrantPermanent(user.userId)}
                        className="px-2.5 py-1 rounded-lg bg-[#0b0f19] hover:bg-[#162035] border border-[#1f293d] text-gray-300 text-[11px]"
                      >
                        PERMANENT
                      </button>
                      <button
                        onClick={() => setConfirmAction({ user, action: 'SUSPEND' })}
                        className="px-2.5 py-1 rounded-lg bg-amber-950/40 hover:bg-amber-900/60 border border-amber-800 text-amber-300 text-[11px]"
                      >
                        SUSPEND
                      </button>
                    </>
                  )}

                  {user.status === 'SUSPENDED' && (
                    <button
                      onClick={() => onRestoreUser(user.userId)}
                      className="px-2.5 py-1 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-[11px] font-bold"
                    >
                      RESTORE (8h)
                    </button>
                  )}

                  {/* RE-APPROVE BUTTON FOR TERMINATED USERS (Requested by user) */}
                  {user.status === 'TERMINATED' && (
                    <button
                      onClick={() => onApproveUser(user.userId)}
                      className="px-2.5 py-1 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-[11px] font-bold shadow-sm shadow-emerald-600/30 flex items-center gap-1"
                    >
                      <CheckCircle2 size={13} />
                      RE-APPROVE (8h)
                    </button>
                  )}

                  {user.status !== 'TERMINATED' && user.role !== 'SUPER_ADMIN' && (
                    <button
                      onClick={() => setConfirmAction({ user, action: 'TERMINATE' })}
                      className="px-2.5 py-1 rounded-lg bg-red-950/60 hover:bg-red-900 border border-red-800 text-red-300 text-[11px] font-bold"
                    >
                      TERMINATE &amp; WIPE
                    </button>
                  )}

                  {user.role !== 'SUPER_ADMIN' && (
                    <button
                      onClick={() => setConfirmAction({ user, action: 'DELETE' })}
                      className="px-2 py-1 rounded-lg hover:bg-red-950/40 text-red-400 text-[11px]"
                    >
                      <Trash2 size={13} />
                    </button>
                  )}
                </div>
              </div>
            ))
          )}
        </div>
      )}

      {/* TAB 4: SERVICE CONTROL */}
      {activeTab === 'service' && (
        <div className="bg-[#111827] border border-[#1f293d] rounded-2xl p-5 shadow-lg flex flex-col gap-4">
          <h3 className="text-base font-bold text-white">GLOBAL REPLICA SERVICE CONTROL</h3>
          <p className="text-xs text-gray-400 leading-relaxed">
            Controls whether REPLICA auto-typing and Bluetooth services are accessible to all approved users across the system.
          </p>

          <div className="bg-[#0b0f19] p-3 rounded-xl border border-[#1f293d] text-xs">
            <span className="text-gray-400">Current Mode: </span>
            <span
              className={`font-mono font-bold ${
                serviceControl.serviceEnabled
                  ? serviceControl.maintenanceMode
                    ? 'text-amber-400'
                    : 'text-emerald-400'
                  : 'text-red-400'
              }`}
            >
              {serviceControl.serviceEnabled
                ? serviceControl.maintenanceMode
                  ? 'MAINTENANCE'
                  : 'ACTIVE'
                : 'DISABLED'}
            </span>
          </div>

          <div className="text-xs text-gray-300 font-semibold">Change Service Mode:</div>

          <div className="grid grid-cols-3 gap-2">
            <button
              onClick={() => setServiceConfirmMode('ACTIVE')}
              className={`py-3 px-2 rounded-xl text-xs font-bold transition-all ${
                serviceControl.serviceEnabled && !serviceControl.maintenanceMode
                  ? 'bg-emerald-600 text-white shadow-lg shadow-emerald-600/30'
                  : 'bg-[#0b0f19] hover:bg-[#162035] text-gray-300 border border-[#1f293d]'
              }`}
            >
              ACTIVE
            </button>

            <button
              onClick={() => setServiceConfirmMode('MAINTENANCE')}
              className={`py-3 px-2 rounded-xl text-xs font-bold transition-all ${
                serviceControl.maintenanceMode
                  ? 'bg-amber-600 text-white shadow-lg shadow-amber-600/30'
                  : 'bg-[#0b0f19] hover:bg-[#162035] text-gray-300 border border-[#1f293d]'
              }`}
            >
              MAINTENANCE
            </button>

            <button
              onClick={() => setServiceConfirmMode('DISABLED')}
              className={`py-3 px-2 rounded-xl text-xs font-bold transition-all ${
                !serviceControl.serviceEnabled
                  ? 'bg-red-600 text-white shadow-lg shadow-red-600/30'
                  : 'bg-[#0b0f19] hover:bg-[#162035] text-gray-300 border border-[#1f293d]'
              }`}
            >
              DISABLED
            </button>
          </div>

          {!isSuperAdmin && (
            <div className="p-3 bg-red-950/30 border border-red-800 rounded-xl text-red-300 text-xs">
              Notice: Only SUPER_ADMIN accounts can modify global service mode.
            </div>
          )}
        </div>
      )}

      {/* TAB 5: AUDIT LOGS */}
      {activeTab === 'logs' && (
        <div className="flex flex-col gap-2">
          {auditLogs.length === 0 ? (
            <div className="bg-[#111827] border border-[#1f293d] rounded-2xl p-6 text-center text-gray-400 text-xs">
              No audit logs recorded yet.
            </div>
          ) : (
            auditLogs.map(log => (
              <div
                key={log.id}
                className="bg-[#111827] border border-[#1f293d] rounded-xl p-3 flex flex-col gap-1 text-xs"
              >
                <div className="flex justify-between items-center font-mono">
                  <span className="font-bold text-cyan-400">{log.action}</span>
                  <span className="text-[10px] text-gray-500">
                    {new Date(log.timestamp).toLocaleTimeString()}
                  </span>
                </div>
                <div className="text-[11px] text-gray-400">Admin: {log.adminEmail}</div>
                {log.targetUserId && (
                  <div className="text-[10px] text-gray-500 font-mono">Target: {log.targetUserId}</div>
                )}
              </div>
            ))
          )}
        </div>
      )}

      {/* MODAL: ADD NEW USER & GRANT ACCESS */}
      {showAddUserModal && (
        <div className="fixed inset-0 z-50 bg-black/80 flex items-center justify-center p-4">
          <div className="w-full max-w-sm bg-[#111827] border border-[#1f293d] rounded-2xl p-5 shadow-2xl flex flex-col gap-3">
            <h3 className="text-base font-bold text-white flex items-center gap-2">
              <UserPlus size={18} className="text-cyan-400" />
              Add User &amp; Grant Access
            </h3>
            <p className="text-xs text-gray-400">
              Directly grant REPLICA access to a user email.
            </p>

            <div>
              <label className="text-[11px] text-gray-400 block mb-1">User Email:</label>
              <input
                type="email"
                placeholder="user@example.com"
                value={newUserEmail}
                onChange={(e) => setNewUserEmail(e.target.value)}
                className="w-full bg-[#0b0f19] border border-[#1f293d] rounded-xl px-3 py-2 text-xs text-white focus:outline-none focus:border-cyan-500 font-mono"
              />
            </div>

            <div>
              <label className="text-[11px] text-gray-400 block mb-1">Display Name (Optional):</label>
              <input
                type="text"
                placeholder="e.g. John Doe"
                value={newUserName}
                onChange={(e) => setNewUserName(e.target.value)}
                className="w-full bg-[#0b0f19] border border-[#1f293d] rounded-xl px-3 py-2 text-xs text-white focus:outline-none focus:border-cyan-500"
              />
            </div>

            <div>
              <label className="text-[11px] text-gray-400 block mb-1">Access Duration:</label>
              <div className="grid grid-cols-2 gap-2">
                <button
                  type="button"
                  onClick={() => setNewUserHours(8)}
                  className={`py-2 px-3 rounded-xl text-xs font-bold border transition-all ${
                    newUserHours === 8
                      ? 'bg-cyan-600 text-white border-cyan-400'
                      : 'bg-[#0b0f19] text-gray-300 border-[#1f293d]'
                  }`}
                >
                  8 Hours Access
                </button>
                <button
                  type="button"
                  onClick={() => setNewUserHours(0)}
                  className={`py-2 px-3 rounded-xl text-xs font-bold border transition-all ${
                    newUserHours === 0
                      ? 'bg-cyan-600 text-white border-cyan-400'
                      : 'bg-[#0b0f19] text-gray-300 border-[#1f293d]'
                  }`}
                >
                  Permanent Access
                </button>
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-2 border-t border-[#1f293d]">
              <button
                type="button"
                onClick={() => setShowAddUserModal(false)}
                className="px-4 py-2 rounded-xl bg-[#0b0f19] hover:bg-[#162035] text-gray-300 text-xs font-semibold"
              >
                CANCEL
              </button>
              <button
                type="button"
                onClick={handleCreateUser}
                disabled={!newUserEmail.trim()}
                className="px-4 py-2 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-bold disabled:opacity-50"
              >
                GRANT ACCESS
              </button>
            </div>
          </div>
        </div>
      )}

      {/* COMMENT MODAL */}
      {commentTarget && (
        <div className="fixed inset-0 z-50 bg-black/80 flex items-center justify-center p-4">
          <div className="w-full max-w-sm bg-[#111827] border border-[#1f293d] rounded-2xl p-5 shadow-2xl flex flex-col gap-3">
            <h3 className="text-base font-bold text-white">Admin Comment / Note</h3>
            <p className="text-xs text-gray-400">
              User: {commentTarget.displayName} ({commentTarget.email})
            </p>
            <textarea
              rows={3}
              value={commentInput}
              onChange={(e) => setCommentInput(e.target.value)}
              placeholder="e.g. Approved for project testing until further notice."
              className="w-full bg-[#0b0f19] border border-[#1f293d] rounded-xl p-2.5 text-xs text-white focus:outline-none focus:border-cyan-500 font-mono"
            />
            <div className="flex justify-end gap-2 pt-1">
              <button
                onClick={() => setCommentTarget(null)}
                className="px-4 py-2 rounded-xl bg-[#0b0f19] hover:bg-[#162035] text-gray-300 text-xs font-semibold"
              >
                CANCEL
              </button>
              <button
                onClick={() => {
                  onUpdateComment(commentTarget.userId, commentInput);
                  setCommentTarget(null);
                }}
                className="px-4 py-2 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-bold"
              >
                SAVE NOTE
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ACTION CONFIRMATION MODAL */}
      {confirmAction && (
        <div className="fixed inset-0 z-50 bg-black/80 flex items-center justify-center p-4">
          <div className="w-full max-w-sm bg-[#111827] border border-[#1f293d] rounded-2xl p-5 shadow-2xl flex flex-col gap-3">
            <h3 className="text-base font-bold text-white">
              Confirm Action: {confirmAction.action}
            </h3>
            <p className="text-xs text-gray-400 leading-relaxed">
              Target: <strong className="text-white">{confirmAction.user.displayName}</strong> (
              {confirmAction.user.email})
            </p>
            <div className="flex justify-end gap-2 pt-2">
              <button
                onClick={() => setConfirmAction(null)}
                className="px-4 py-2 rounded-xl bg-[#0b0f19] hover:bg-[#162035] text-gray-300 text-xs font-semibold"
              >
                CANCEL
              </button>
              <button
                onClick={() => {
                  const { user, action } = confirmAction;
                  if (action === 'APPROVE') onApproveUser(user.userId);
                  else if (action === 'REJECT') onRejectUser(user.userId);
                  else if (action === 'EXTEND_8H') onExtendAccess(user.userId, 8);
                  else if (action === 'PERMANENT') onGrantPermanent(user.userId);
                  else if (action === 'SUSPEND') onSuspendUser(user.userId);
                  else if (action === 'RESTORE') onRestoreUser(user.userId);
                  else if (action === 'TERMINATE') onTerminateUser(user.userId);
                  else if (action === 'DELETE') onDeleteUser(user.userId);
                  setConfirmAction(null);
                }}
                className="px-4 py-2 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-bold"
              >
                CONFIRM
              </button>
            </div>
          </div>
        </div>
      )}

      {/* SERVICE MODE CONFIRM MODAL */}
      {serviceConfirmMode && (
        <div className="fixed inset-0 z-50 bg-black/80 flex items-center justify-center p-4">
          <div className="w-full max-w-sm bg-[#111827] border border-[#1f293d] rounded-2xl p-5 shadow-2xl flex flex-col gap-3">
            <h3 className="text-base font-bold text-white">Set Service Mode: {serviceConfirmMode}</h3>
            <p className="text-xs text-gray-400 leading-relaxed">
              {serviceConfirmMode === 'ACTIVE'
                ? 'Restore normal application operation for all approved users?'
                : serviceConfirmMode === 'MAINTENANCE'
                ? 'Place REPLICA under maintenance? Non-admins will see maintenance screen.'
                : 'Disable REPLICA service globally? Active sessions will be stopped immediately.'}
            </p>
            <div className="flex justify-end gap-2 pt-2">
              <button
                onClick={() => setServiceConfirmMode(null)}
                className="px-4 py-2 rounded-xl bg-[#0b0f19] hover:bg-[#162035] text-gray-300 text-xs font-semibold"
              >
                CANCEL
              </button>
              <button
                onClick={() => {
                  onSetServiceMode(serviceConfirmMode);
                  setServiceConfirmMode(null);
                }}
                className="px-4 py-2 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-bold"
              >
                CONFIRM CHANGE
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
