import React from 'react';
import { UserProfile, isUserAdmin, getRemainingTimeFormatted } from '../types';
import {
  User,
  Shield,
  LogOut,
  Bluetooth,
  Laptop,
  RefreshCw,
  Sun,
  Lock,
  Info,
  Clock,
  BookOpen
} from 'lucide-react';

interface SettingsScreenProps {
  userProfile: UserProfile | null;
  onSignOut: () => void;
  onOpenAdmin: () => void;
  isConnected: boolean;
  onToggleConnect: () => void;
  keepAwake: boolean;
  onToggleKeepAwake: (val: boolean) => void;
  autoConnect: boolean;
  onToggleAutoConnect: (val: boolean) => void;
  onLockClick: () => void;
}

export const SettingsScreen: React.FC<SettingsScreenProps> = ({
  userProfile,
  onSignOut,
  onOpenAdmin,
  isConnected,
  onToggleConnect,
  keepAwake,
  onToggleKeepAwake,
  autoConnect,
  onToggleAutoConnect,
  onLockClick
}) => {
  const isAdmin = isUserAdmin(userProfile);

  return (
    <div className="flex flex-col gap-4 pb-20">
      <div>
        <h2 className="text-xl font-bold text-white">Account &amp; Settings</h2>
        <p className="text-xs text-gray-400">Manage server access, Bluetooth HID, and app options</p>
      </div>

      {/* SECTION 0: REPLICA ACCOUNT INFO */}
      {userProfile && (
        <div className="bg-[#111827] border border-[#1f293d] rounded-2xl p-4 shadow-lg flex flex-col gap-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="w-11 h-11 rounded-full bg-cyan-950/60 border border-cyan-500/40 flex items-center justify-center text-cyan-400 font-bold font-mono text-base">
                {userProfile.displayName ? userProfile.displayName.charAt(0).toUpperCase() : 'U'}
              </div>
              <div className="truncate">
                <div className="text-sm font-bold text-white truncate">{userProfile.displayName}</div>
                <div className="text-xs text-gray-400 truncate">{userProfile.email}</div>
              </div>
            </div>

            <span className="px-2.5 py-1 rounded-full text-[10px] font-bold font-mono tracking-wider bg-blue-950/60 text-blue-400 border border-blue-800">
              {userProfile.role}
            </span>
          </div>

          <div className="flex items-center gap-2 text-xs bg-[#0b0f19] p-2.5 rounded-xl border border-[#1f293d]">
            <Clock size={14} className="text-emerald-400" />
            <span className="text-gray-400">Access Period:</span>
            <span className="font-bold text-emerald-400">{getRemainingTimeFormatted(userProfile)}</span>
          </div>

          {userProfile.adminComment && (
            <div className="bg-[#0b0f19] p-2.5 rounded-xl border border-cyan-900/50 text-xs font-mono text-cyan-300">
              Admin Note: &quot;{userProfile.adminComment}&quot;
            </div>
          )}

          <div className="grid grid-cols-2 gap-2 pt-1">
            {isAdmin && (
              <button
                onClick={onOpenAdmin}
                className="py-2.5 px-3 rounded-xl bg-blue-600 hover:bg-blue-500 text-white font-bold text-xs flex items-center justify-center gap-1.5 shadow-md shadow-blue-600/30 transition-all"
              >
                <Shield size={14} />
                ADMIN PANEL
              </button>
            )}

            <button
              onClick={onSignOut}
              className={`py-2.5 px-3 rounded-xl bg-[#0b0f19] hover:bg-[#162035] border border-[#1f293d] text-gray-300 hover:text-white font-semibold text-xs flex items-center justify-center gap-1.5 transition-all ${
                !isAdmin ? 'col-span-2' : ''
              }`}
            >
              <LogOut size={14} />
              SIGN OUT
            </button>
          </div>
        </div>
      )}

      {/* SECTION 1: BLUETOOTH HID DEVICES */}
      <div className="bg-[#111827] border border-[#1f293d] rounded-2xl p-4 shadow-lg flex flex-col gap-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Bluetooth size={18} className="text-cyan-400" />
            <h3 className="text-sm font-bold text-white">Bluetooth HID Connection</h3>
          </div>
          <span
            className={`text-xs font-mono font-bold px-2 py-0.5 rounded-full ${
              isConnected
                ? 'bg-emerald-950/80 text-emerald-400 border border-emerald-800'
                : 'bg-gray-800 text-gray-400 border border-gray-700'
            }`}
          >
            {isConnected ? 'CONNECTED' : 'DISCONNECTED'}
          </span>
        </div>

        {/* Device item */}
        <div className="bg-[#0b0f19] border border-[#1f293d] rounded-xl p-3 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div
              className={`w-9 h-9 rounded-xl flex items-center justify-center border ${
                isConnected
                  ? 'bg-emerald-950/60 border-emerald-500/40 text-emerald-400'
                  : 'bg-gray-900 border-gray-800 text-gray-400'
              }`}
            >
              <Laptop size={18} />
            </div>
            <div>
              <div className="text-xs font-bold text-white">Windows Laptop</div>
              <div className="text-[10px] text-gray-500 font-mono">
                {isConnected ? 'RFCOMM HID Channel Active' : 'Virtual Bluetooth HID Peripheral'}
              </div>
            </div>
          </div>

          <button
            onClick={onToggleConnect}
            className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all ${
              isConnected
                ? 'bg-red-950/60 hover:bg-red-900 border border-red-800 text-red-300'
                : 'bg-cyan-600 hover:bg-cyan-500 text-white shadow-md shadow-cyan-600/30'
            }`}
          >
            {isConnected ? 'DISCONNECT' : 'CONNECT'}
          </button>
        </div>

        <div className="flex items-center justify-between pt-2 border-t border-[#1f293d]/80 text-xs">
          <span className="text-gray-300 font-medium">Auto-connect on launch</span>
          <button
            onClick={() => onToggleAutoConnect(!autoConnect)}
            className={`w-10 h-5 rounded-full transition-colors relative p-0.5 ${
              autoConnect ? 'bg-cyan-600' : 'bg-gray-700'
            }`}
          >
            <div
              className={`w-4 h-4 rounded-full bg-white transition-transform ${
                autoConnect ? 'translate-x-5' : 'translate-x-0'
              }`}
            />
          </button>
        </div>
      </div>

      {/* SECTION 2: PREFERENCES */}
      <div className="bg-[#111827] border border-[#1f293d] rounded-2xl p-4 shadow-lg flex flex-col gap-3">
        <h3 className="text-sm font-bold text-white">Preferences</h3>

        <div className="flex items-center justify-between text-xs">
          <div>
            <div className="text-gray-200 font-medium">Keep screen awake</div>
            <div className="text-[11px] text-gray-500">Prevents display sleep during auto-typing</div>
          </div>
          <button
            onClick={() => onToggleKeepAwake(!keepAwake)}
            className={`w-10 h-5 rounded-full transition-colors relative p-0.5 ${
              keepAwake ? 'bg-cyan-600' : 'bg-gray-700'
            }`}
          >
            <div
              className={`w-4 h-4 rounded-full bg-white transition-transform ${
                keepAwake ? 'translate-x-5' : 'translate-x-0'
              }`}
            />
          </button>
        </div>

        <div className="pt-2 border-t border-[#1f293d]/80">
          <button
            onClick={onLockClick}
            className="w-full py-2.5 px-3 rounded-xl bg-red-950/60 hover:bg-red-900/80 border border-red-800 text-red-300 font-bold text-xs flex items-center justify-center gap-2 transition-all"
          >
            <Lock size={15} />
            LOCK APP NOW (EMERGENCY STOP)
          </button>
        </div>
      </div>

      {/* SECTION 3: UNIVERSAL TEXT & CODETANTRA GUIDE */}
      <div className="bg-[#111827] border border-[#1f293d] rounded-2xl p-4 flex flex-col gap-2.5">
        <div className="flex items-center gap-2 text-cyan-400">
          <BookOpen size={16} />
          <h3 className="text-sm font-bold">Universal Text &amp; CodeTantra Guide</h3>
        </div>
        <ol className="text-xs text-gray-400 space-y-1.5 pl-4 list-decimal leading-relaxed">
          <li>Pair Android phone or web host with your Windows laptop via Bluetooth.</li>
          <li>Open REPLICA and verify &quot;🟢 Connected&quot; status.</li>
          <li>Perform the Notepad First Test in Keyboard Test screen.</li>
          <li>On Windows, click inside target text field (Notepad, CodeTantra editor, form, terminal).</li>
          <li>Paste or load your text/code into the Text Editor.</li>
          <li>Set typing delay (25ms recommended for web editors; 5ms for fast terminal).</li>
          <li>Tap [RUN]. REPLICA streams HID keyboard reports character-by-character.</li>
          <li>Windows receives physical keystrokes without any companion software or clipboard access.</li>
          <li>Press [STOP] or [LOCK NOW] at any moment to immediately halt transmission.</li>
        </ol>
      </div>

      {/* SECTION 4: HID LIMITATIONS & TECHNICAL DISCLOSURE */}
      <div className="bg-[#111827] border border-[#1f293d] rounded-2xl p-4 flex flex-col gap-2 text-xs text-gray-400">
        <div className="flex items-center gap-2 text-gray-300 font-bold">
          <Info size={16} className="text-cyan-400" />
          <span>Android / Web HID Technical Disclosure</span>
        </div>
        <p className="leading-relaxed text-[11px]">
          • Bluetooth HID Device profile operates standard USB HID 1.11 reports.
          <br />
          • No companion software, no scripts, and no clipboard synchronization required on Windows.
          <br />
          • All data transmission stays local over genuine RFCOMM Bluetooth HID profiles.
        </p>
      </div>
    </div>
  );
};
