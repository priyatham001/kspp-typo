import React from 'react';
import { Lock, ShieldAlert, Wifi, WifiOff } from 'lucide-react';
import { UserProfile, isUserAdmin } from '../types';

interface HeaderProps {
  userProfile: UserProfile | null;
  onLockClick: () => void;
  onAdminClick: () => void;
  isAdminActive: boolean;
  isConnected: boolean;
}

export const Header: React.FC<HeaderProps> = ({
  userProfile,
  onLockClick,
  onAdminClick,
  isAdminActive,
  isConnected
}) => {
  const admin = isUserAdmin(userProfile);

  return (
    <header className="sticky top-0 z-40 bg-[#090d16]/95 backdrop-blur-md border-b border-[#1f293d] px-4 py-3 flex items-center justify-between">
      <div>
        <h1 className="text-xl font-extrabold tracking-wider text-white">replica_kspp</h1>
        <p className="text-xs font-mono text-cyan-400 font-semibold tracking-wide">
          &quot;I replicate keyboard&quot;
        </p>
      </div>

      <div className="flex items-center gap-2">
        {/* Connection status mini dot */}
        <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-[#111827] border border-[#1f293d] text-xs">
          {isConnected ? (
            <>
              <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span>
              <span className="text-emerald-400 font-mono text-[11px] hidden sm:inline">HID LINK</span>
            </>
          ) : (
            <>
              <span className="w-2 h-2 rounded-full bg-amber-400"></span>
              <span className="text-gray-400 font-mono text-[11px] hidden sm:inline">OFFLINE</span>
            </>
          )}
        </div>

        {/* Admin Dashboard button */}
        {admin && (
          <button
            onClick={onAdminClick}
            title="Admin Control Center"
            className={`p-2 rounded-xl transition-all ${
              isAdminActive
                ? 'bg-blue-600 text-white shadow-lg shadow-blue-500/30'
                : 'bg-[#111827] text-blue-400 hover:bg-blue-900/30 border border-[#1f293d]'
            }`}
          >
            <ShieldAlert size={19} />
          </button>
        )}

        {/* Emergency Lock Now Button */}
        <button
          onClick={onLockClick}
          title="Lock App Now (Emergency Halt)"
          className="p-2 rounded-xl bg-red-950/40 text-red-400 hover:bg-red-900/60 border border-red-800/60 hover:text-red-300 transition-all"
        >
          <Lock size={19} />
        </button>
      </div>
    </header>
  );
};
