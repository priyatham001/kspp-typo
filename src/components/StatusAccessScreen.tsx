import React, { useState } from 'react';
import { UserProfile, isUserAdmin } from '../types';
import { Hourglass, Ban, AlertTriangle, Lock, RefreshCw, LogOut, ShieldAlert, Send, CheckCircle2 } from 'lucide-react';

export type AccessDeniedReason =
  | 'PENDING'
  | 'REJECTED'
  | 'SUSPENDED'
  | 'TERMINATED'
  | 'ACCESS_EXPIRED'
  | 'MAINTENANCE'
  | 'SERVICE_DISABLED';

interface StatusAccessScreenProps {
  reason: AccessDeniedReason;
  userProfile: UserProfile | null;
  serviceMessage?: string | null;
  onRefresh: () => void;
  onSignOut: () => void;
  onOpenAdmin?: () => void;
  onRequestAccess?: () => void;
}

export const StatusAccessScreen: React.FC<StatusAccessScreenProps> = ({
  reason,
  userProfile,
  serviceMessage,
  onRefresh,
  onSignOut,
  onOpenAdmin,
  onRequestAccess
}) => {
  const [requestSent, setRequestSent] = useState(false);

  const handleSendRequest = () => {
    setRequestSent(true);
    if (onRequestAccess) {
      onRequestAccess();
    }
  };

  const getDetails = () => {
    switch (reason) {
      case 'ACCESS_EXPIRED':
        return {
          icon: <Hourglass className="w-10 h-10 text-amber-400" />,
          title: 'TRIAL EXPIRED',
          subtitle: 'APPROVAL REQUIRED',
          message:
            'Your 8-hour access window has ended. Send a request to the administrator to approve continued access.',
          bgColor: 'bg-amber-950/20 border-amber-500/30 text-amber-400'
        };
      case 'PENDING':
        return {
          icon: <Hourglass className="w-10 h-10 text-amber-400" />,
          title: 'Approval Required',
          subtitle: 'Verification Pending',
          message: 'Your access request is pending administrator review. Please check back shortly.',
          bgColor: 'bg-amber-950/20 border-amber-500/30 text-amber-400'
        };
      case 'REJECTED':
        return {
          icon: <Ban className="w-10 h-10 text-red-400" />,
          title: 'Access Not Approved',
          subtitle: 'Request Review',
          message: 'Your access request is currently not approved. You can submit a new access request.',
          bgColor: 'bg-red-950/20 border-red-500/30 text-red-400'
        };
      case 'SUSPENDED':
        return {
          icon: <AlertTriangle className="w-10 h-10 text-amber-400" />,
          title: 'Account Suspended',
          subtitle: 'Access Review',
          message: 'Your account is temporarily paused. Send a request to re-enable access.',
          bgColor: 'bg-amber-950/20 border-amber-500/30 text-amber-400'
        };
      case 'TERMINATED':
        return {
          icon: <Lock className="w-10 h-10 text-red-500" />,
          title: 'Access Terminated',
          subtitle: 'Administrator Review',
          message: 'Your access has been terminated. You can submit a request for administrator re-approval.',
          bgColor: 'bg-red-950/30 border-red-500/40 text-red-400'
        };
      case 'MAINTENANCE':
        return {
          icon: <AlertTriangle className="w-10 h-10 text-amber-400" />,
          title: 'REPLICA Under Maintenance',
          subtitle: 'Scheduled Service Update',
          message: serviceMessage || 'REPLICA is currently under maintenance. Please try again later.',
          bgColor: 'bg-amber-950/20 border-amber-500/30 text-amber-400'
        };
      case 'SERVICE_DISABLED':
      default:
        return {
          icon: <AlertTriangle className="w-10 h-10 text-amber-400" />,
          title: 'Service Unavailable',
          subtitle: 'System Offline',
          message: serviceMessage || 'REPLICA service is currently unavailable. Please try again later.',
          bgColor: 'bg-amber-950/20 border-amber-500/30 text-amber-400'
        };
    }
  };

  const details = getDetails();
  const isAdmin = isUserAdmin(userProfile);

  return (
    <div className="min-h-screen bg-[#090d16] flex items-center justify-center p-4">
      <div className="w-full max-w-md bg-[#111827] border border-[#1f293d] rounded-3xl p-6 sm:p-8 shadow-2xl flex flex-col items-center text-center">
        {/* Icon */}
        <div className={`w-20 h-20 rounded-full flex items-center justify-center border mb-4 ${details.bgColor}`}>
          {details.icon}
        </div>

        <h2 className="text-2xl font-extrabold text-white mb-1">{details.title}</h2>
        <h3 className="text-xs font-mono uppercase tracking-widest text-cyan-400 font-bold mb-3">
          {details.subtitle}
        </h3>
        <p className="text-xs text-gray-300 leading-relaxed max-w-xs mb-6">
          {details.message}
        </p>

        {/* User Info card (no admin email exposed!) */}
        {userProfile && (
          <div className="w-full mb-5 p-4 rounded-2xl bg-[#0b0f19] border border-[#1f293d] text-left text-xs">
            <div className="flex justify-between items-center mb-1">
              <span className="font-bold text-white text-sm">{userProfile.displayName}</span>
              <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-gray-800 text-gray-300 border border-gray-700">
                {userProfile.status}
              </span>
            </div>
            <div className="text-gray-400 truncate">{userProfile.email}</div>
          </div>
        )}

        {/* Request feedback badge */}
        {requestSent && (
          <div className="w-full mb-4 p-3 rounded-xl bg-emerald-950/60 border border-emerald-500/50 text-emerald-300 text-xs flex items-center justify-center gap-2">
            <CheckCircle2 size={16} className="text-emerald-400" />
            <span>Access request sent successfully! Waiting for approval.</span>
          </div>
        )}

        {/* Action Buttons */}
        <div className="w-full flex flex-col gap-2.5">
          {/* SEND REQUEST BUTTON (Requested by user) */}
          {reason !== 'MAINTENANCE' && reason !== 'SERVICE_DISABLED' && !requestSent && (
            <button
              onClick={handleSendRequest}
              className="w-full py-3.5 px-4 rounded-xl bg-gradient-to-r from-blue-600 to-cyan-600 hover:from-blue-500 hover:to-cyan-500 text-white font-bold text-xs flex items-center justify-center gap-2 shadow-lg shadow-cyan-600/30 transition-all active:scale-98"
            >
              <Send size={15} />
              SEND ACCESS REQUEST
            </button>
          )}

          {isAdmin && onOpenAdmin && (
            <button
              onClick={onOpenAdmin}
              className="w-full py-3 px-4 rounded-xl bg-blue-600 hover:bg-blue-500 text-white font-bold text-xs flex items-center justify-center gap-2 shadow-lg shadow-blue-600/30 transition-all"
            >
              <ShieldAlert size={16} />
              OPEN ADMIN DASHBOARD
            </button>
          )}

          <button
            onClick={onRefresh}
            className="w-full py-3 px-4 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white font-bold text-xs flex items-center justify-center gap-2 shadow-lg shadow-cyan-600/30 transition-all"
          >
            <RefreshCw size={15} />
            CHECK STATUS AGAIN
          </button>

          <button
            onClick={onSignOut}
            className="w-full py-2.5 px-4 rounded-xl bg-[#0b0f19] hover:bg-[#162033] border border-[#1f293d] text-gray-300 hover:text-white font-semibold text-xs flex items-center justify-center gap-2 transition-all"
          >
            <LogOut size={15} />
            SIGN OUT
          </button>
        </div>
      </div>
    </div>
  );
};
