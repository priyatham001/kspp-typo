import React, { useState } from 'react';
import {
  Save,
  Play,
  Pause,
  Square,
  X,
  FileCode,
  Bluetooth,
  Gauge,
  Minus,
  Plus,
  CheckCircle2,
  FolderOpen,
  Wifi,
  Sparkles
} from 'lucide-react';
import { UserProfile, TypingState, getRemainingTimeFormatted } from '../types';

interface EditorScreenProps {
  userProfile: UserProfile | null;
  editorText: string;
  editorTitle: string;
  onTextChange: (text: string) => void;
  typingDelayMs: number;
  onDelayChange: (ms: number) => void;
  typingState: TypingState;
  onStartTyping: () => void;
  onPauseTyping: () => void;
  onResumeTyping: () => void;
  onStopTyping: () => void;
  onSave: (title: string) => void;
  onLoadSample: () => void;
  onNavigateToScripts: () => void;
  isConnected: boolean;
  onToggleConnect: () => void;
  autoConnect: boolean;
  onToggleAutoConnect: (val: boolean) => void;
  connectedDeviceName?: string;
  onLockClick: () => void;
}

export const EditorScreen: React.FC<EditorScreenProps> = ({
  userProfile,
  editorText,
  editorTitle,
  onTextChange,
  typingDelayMs,
  onDelayChange,
  typingState,
  onStartTyping,
  onPauseTyping,
  onResumeTyping,
  onStopTyping,
  onSave,
  onLoadSample,
  onNavigateToScripts,
  isConnected,
  onToggleConnect,
  autoConnect,
  onToggleAutoConnect,
  connectedDeviceName = 'Windows Laptop',
  onLockClick
}) => {
  const [showSaveDialog, setShowSaveDialog] = useState(false);
  const [showClearDialog, setShowClearDialog] = useState(false);
  const [saveTitleInput, setSaveTitleInput] = useState(editorTitle);

  const linesCount = editorText ? editorText.split('\n').length : 0;
  const charsCount = editorText.length;
  const isTyping = typingState.status === 'TYPING';
  const isPaused = typingState.status === 'PAUSED';

  const delayPresets = [15, 25, 35, 50, 75];

  return (
    <div className="flex flex-col gap-4 pb-20">
      {/* 1. ABOVE: BLUETOOTH AUTO CONNECT CARD */}
      <div className="bg-[#111827] border border-[#1f293d] rounded-2xl p-3.5 shadow-md flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div
            className={`w-10 h-10 rounded-full flex items-center justify-center border transition-all ${
              isConnected
                ? 'bg-emerald-950/60 border-emerald-500/50 text-emerald-400'
                : 'bg-[#182338] border-gray-700 text-gray-400'
            }`}
          >
            <Bluetooth size={20} className={isConnected ? 'animate-pulse' : ''} />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="text-xs font-bold text-white tracking-wide">
                Bluetooth Auto Connect
              </span>
              <span
                className={`text-[10px] font-mono px-1.5 py-0.5 rounded ${
                  autoConnect
                    ? 'bg-cyan-950/80 text-cyan-400 border border-cyan-800/60'
                    : 'bg-gray-800 text-gray-400'
                }`}
              >
                {autoConnect ? 'AUTO: ON' : 'AUTO: OFF'}
              </span>
            </div>
            <div className="text-[11px] text-gray-400 flex items-center gap-1.5 mt-0.5">
              {isConnected ? (
                <>
                  <span className="w-2 h-2 rounded-full bg-emerald-400 inline-block animate-ping"></span>
                  <span className="text-emerald-400 font-semibold truncate">
                    Connected: {connectedDeviceName}
                  </span>
                </>
              ) : (
                <span className="text-gray-400">Ready to pair with Windows PC</span>
              )}
            </div>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {/* Auto Connect Toggle Switch */}
          <button
            onClick={() => onToggleAutoConnect(!autoConnect)}
            title="Toggle Bluetooth Auto-Connect"
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

          {/* Quick Connect / Disconnect button */}
          <button
            onClick={onToggleConnect}
            className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all ${
              isConnected
                ? 'bg-red-950/60 hover:bg-red-900 border border-red-800/80 text-red-300'
                : 'bg-cyan-600 hover:bg-cyan-500 text-white shadow-md shadow-cyan-600/30'
            }`}
          >
            {isConnected ? 'DISCONNECT' : 'CONNECT'}
          </button>
        </div>
      </div>

      {/* 2. MAIN TEXT EDITOR CARD */}
      <div className="bg-[#111827] border border-[#1f293d] rounded-2xl p-4 shadow-lg flex flex-col gap-3">
        {/* Document Header & Counters */}
        <div className="flex items-center justify-between border-b border-[#1f293d]/80 pb-2.5">
          <div className="flex items-center gap-2">
            <span className="text-sm font-bold text-white tracking-wide truncate max-w-[180px]">
              {editorTitle}
            </span>
          </div>
          <div className="text-xs font-mono text-gray-400">
            Characters: <span className="text-cyan-400 font-bold">{charsCount}</span> ({linesCount} lines)
          </div>
        </div>

        {/* Text Area */}
        <textarea
          value={editorText}
          onChange={(e) => onTextChange(e.target.value)}
          placeholder="Type, paste, or load text/code to type into Windows Notepad, CodeTantra, or any editor..."
          rows={9}
          className="w-full bg-[#0b0f19] border border-[#1f293d] rounded-xl p-3 text-xs sm:text-sm font-mono text-gray-200 placeholder-gray-600 focus:outline-none focus:border-cyan-500 resize-y leading-relaxed"
          spellCheck={false}
        />

        {/* 4 SCREENSHOT BUTTONS: SAVE | RUN | CLEAR | SAMPLE */}
        <div className="grid grid-cols-4 gap-2 pt-1">
          {/* Button 1: SAVE */}
          <button
            onClick={() => {
              setSaveTitleInput(editorTitle);
              setShowSaveDialog(true);
            }}
            className="flex flex-col items-center justify-center p-2.5 rounded-xl bg-[#0b0f19] hover:bg-[#162035] active:scale-95 border border-[#1f293d] transition-all group"
          >
            <div className="w-8 h-8 rounded-lg bg-cyan-950/60 border border-cyan-500/30 flex items-center justify-center text-cyan-400 mb-1 group-hover:scale-105 transition-transform">
              <Save size={18} />
            </div>
            <span className="text-[10px] font-bold tracking-widest text-cyan-400 font-mono">
              SAVE
            </span>
          </button>

          {/* Button 2: RUN (Start / Pause / Resume Auto-Typing) */}
          <button
            onClick={() => {
              if (isTyping) {
                onPauseTyping();
              } else if (isPaused) {
                onResumeTyping();
              } else {
                onStartTyping();
              }
            }}
            className={`flex flex-col items-center justify-center p-2.5 rounded-xl active:scale-95 border transition-all group ${
              isTyping
                ? 'bg-amber-950/40 border-amber-500/50 shadow-lg shadow-amber-950/40'
                : 'bg-[#0b0f19] hover:bg-[#162035] border-[#1f293d]'
            }`}
          >
            <div
              className={`w-8 h-8 rounded-lg flex items-center justify-center mb-1 group-hover:scale-105 transition-transform ${
                isTyping
                  ? 'bg-amber-900/60 text-amber-300'
                  : 'bg-emerald-950/60 border border-emerald-500/40 text-emerald-400'
              }`}
            >
              {isTyping ? <Pause size={18} /> : <Play size={18} className="fill-emerald-400/20" />}
            </div>
            <span
              className={`text-[10px] font-bold tracking-widest font-mono ${
                isTyping ? 'text-amber-400' : 'text-emerald-400'
              }`}
            >
              {isTyping ? 'PAUSE' : isPaused ? 'RESUME' : 'RUN'}
            </span>
          </button>

          {/* Button 3: CLEAR */}
          <button
            onClick={() => setShowClearDialog(true)}
            className="flex flex-col items-center justify-center p-2.5 rounded-xl bg-[#0b0f19] hover:bg-[#162035] active:scale-95 border border-[#1f293d] transition-all group"
          >
            <div className="w-8 h-8 rounded-lg bg-red-950/40 border border-red-500/30 flex items-center justify-center text-red-400 mb-1 group-hover:scale-105 transition-transform">
              <X size={18} />
            </div>
            <span className="text-[10px] font-bold tracking-widest text-red-400 font-mono">
              CLEAR
            </span>
          </button>

          {/* Button 4: SAMPLE (Loads default sample text) */}
          <button
            onClick={onLoadSample}
            className="flex flex-col items-center justify-center p-2.5 rounded-xl bg-[#0b0f19] hover:bg-[#162035] active:scale-95 border border-[#1f293d] transition-all group"
          >
            <div className="w-8 h-8 rounded-lg bg-blue-950/60 border border-blue-500/30 flex items-center justify-center text-blue-400 mb-1 group-hover:scale-105 transition-transform">
              <Sparkles size={18} />
            </div>
            <span className="text-[10px] font-bold tracking-widest text-blue-400 font-mono">
              SAMPLE
            </span>
          </button>
        </div>

        {/* Live typing progress banner if active */}
        {(isTyping || isPaused) && (
          <div className="mt-2 p-3 rounded-xl bg-[#0b0f19] border border-cyan-800/60">
            <div className="flex items-center justify-between text-xs mb-1.5">
              <span className={`font-bold flex items-center gap-1.5 ${isTyping ? 'text-emerald-400' : 'text-amber-400'}`}>
                <span className={`w-2 h-2 rounded-full ${isTyping ? 'bg-emerald-400 animate-ping' : 'bg-amber-400'}`} />
                {isTyping ? 'Streaming keystrokes...' : 'Paused'}
              </span>
              <span className="font-mono text-cyan-300">
                {'currentIndex' in typingState ? typingState.currentIndex : 0} / {'totalChars' in typingState ? typingState.totalChars : charsCount} ({'percent' in typingState ? typingState.percent.toFixed(1) : 0}%)
              </span>
            </div>
            {/* Progress bar */}
            <div className="w-full h-2 rounded-full bg-gray-800 overflow-hidden mb-2">
              <div
                className={`h-full transition-all ${isTyping ? 'bg-gradient-to-r from-cyan-500 to-emerald-400' : 'bg-amber-400'}`}
                style={{ width: `${'percent' in typingState ? typingState.percent : 0}%` }}
              />
            </div>
            <div className="flex justify-end gap-2">
              <button
                onClick={onStopTyping}
                className="px-3 py-1 rounded-lg bg-red-950/80 hover:bg-red-900 border border-red-800 text-red-300 text-xs font-bold flex items-center gap-1"
              >
                <Square size={12} />
                STOP
              </button>
            </div>
          </div>
        )}

        {typingState.status === 'COMPLETED' && (
          <div className="mt-2 p-2.5 rounded-xl bg-emerald-950/40 border border-emerald-800/80 text-emerald-300 text-xs flex items-center justify-between">
            <span className="flex items-center gap-1.5">
              <CheckCircle2 size={16} className="text-emerald-400" />
              Typing completed successfully! ({typingState.totalChars} chars transmitted)
            </span>
            <button
              onClick={onStopTyping}
              className="text-[11px] underline text-emerald-400 hover:text-emerald-200"
            >
              Dismiss
            </button>
          </div>
        )}
      </div>

      {/* 3. BELOW: KEYSTROKE DELAY / SPEED CARD (EXACTLY MATCHING SCREENSHOT) */}
      <div className="bg-[#111827] border border-[#1f293d] rounded-2xl p-4 shadow-lg flex flex-col gap-3">
        {/* Header: Cyan Checkmark icon + Keystroke Delay + 25 ms / char */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div className="w-7 h-7 rounded-lg bg-cyan-950/60 border border-cyan-500/40 flex items-center justify-center text-cyan-400">
              <CheckCircle2 size={18} />
            </div>
            <span className="text-sm font-bold text-white tracking-wide">Keystroke Delay</span>
          </div>
          <span className="text-sm font-mono font-bold text-cyan-400">
            {typingDelayMs} ms / char
          </span>
        </div>

        {/* Speed Chips Row: 15ms | 25ms | 35ms | 50ms | 75ms */}
        <div className="grid grid-cols-5 gap-2 pt-1">
          {delayPresets.map((ms) => {
            const isSelected = typingDelayMs === ms;
            return (
              <button
                key={ms}
                onClick={() => onDelayChange(ms)}
                className={`py-2 px-1 rounded-xl font-mono text-xs font-semibold text-center transition-all ${
                  isSelected
                    ? 'bg-cyan-600 text-white font-bold shadow-md shadow-cyan-600/30 border border-cyan-400'
                    : 'bg-[#0b0f19] hover:bg-[#162035] text-gray-300 border border-[#1f293d]'
                }`}
              >
                {ms}ms
              </button>
            );
          })}
        </div>

        {/* Fine Adjustment Row: "Fine Adjustment:" with - and + */}
        <div className="flex items-center justify-between pt-2 border-t border-[#1f293d]/80 text-xs">
          <span className="text-gray-400 font-medium">Fine Adjustment:</span>
          <div className="flex items-center gap-2">
            <button
              onClick={() => onDelayChange(Math.max(5, typingDelayMs - 5))}
              className="w-8 h-8 rounded-xl bg-[#0b0f19] hover:bg-[#162035] active:scale-95 border border-[#1f293d] text-gray-200 flex items-center justify-center transition-all"
            >
              <Minus size={15} />
            </button>
            <span className="font-mono text-xs text-gray-300 min-w-8 text-center">
              {typingDelayMs}ms
            </span>
            <button
              onClick={() => onDelayChange(Math.min(500, typingDelayMs + 5))}
              className="w-8 h-8 rounded-xl bg-[#0b0f19] hover:bg-[#162035] active:scale-95 border border-[#1f293d] text-gray-200 flex items-center justify-center transition-all"
            >
              <Plus size={15} />
            </button>
          </div>
        </div>
      </div>

      {/* Access Window Banner */}
      {userProfile && (
        <div className="bg-[#111827] border border-[#1f293d] rounded-2xl p-3 px-4 text-xs flex items-center justify-between text-gray-400">
          <span>
            Access: <strong className="text-emerald-400">{getRemainingTimeFormatted(userProfile)}</strong>
          </span>
          <span className="font-mono text-[11px] text-gray-400">
            Account: {userProfile.displayName}
          </span>
        </div>
      )}

      {/* SAVE DIALOG */}
      {showSaveDialog && (
        <div className="fixed inset-0 z-50 bg-black/80 flex items-center justify-center p-4">
          <div className="w-full max-w-sm bg-[#111827] border border-[#1f293d] rounded-2xl p-5 shadow-2xl flex flex-col gap-4">
            <h3 className="text-base font-bold text-white">Save Document</h3>
            <div>
              <label className="text-xs text-gray-400 block mb-1">Document Title:</label>
              <input
                type="text"
                value={saveTitleInput}
                onChange={(e) => setSaveTitleInput(e.target.value)}
                className="w-full bg-[#0b0f19] border border-[#1f293d] rounded-xl px-3 py-2 text-sm text-white focus:outline-none focus:border-cyan-500 font-mono"
              />
            </div>
            <div className="flex justify-end gap-2">
              <button
                onClick={() => setShowSaveDialog(false)}
                className="px-4 py-2 rounded-xl bg-[#0b0f19] hover:bg-[#162035] text-gray-300 text-xs font-semibold"
              >
                CANCEL
              </button>
              <button
                onClick={() => {
                  onSave(saveTitleInput);
                  setShowSaveDialog(false);
                }}
                className="px-4 py-2 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-bold"
              >
                SAVE DOCUMENT
              </button>
            </div>
          </div>
        </div>
      )}

      {/* CLEAR CONFIRMATION DIALOG */}
      {showClearDialog && (
        <div className="fixed inset-0 z-50 bg-black/80 flex items-center justify-center p-4">
          <div className="w-full max-w-sm bg-[#111827] border border-[#1f293d] rounded-2xl p-5 shadow-2xl flex flex-col gap-4">
            <h3 className="text-base font-bold text-white">Clear Editor?</h3>
            <p className="text-xs text-gray-400 leading-relaxed">
              Are you sure you want to clear the editor text? Any unsaved edits will be lost.
            </p>
            <div className="flex justify-end gap-2">
              <button
                onClick={() => setShowClearDialog(false)}
                className="px-4 py-2 rounded-xl bg-[#0b0f19] hover:bg-[#162035] text-gray-300 text-xs font-semibold"
              >
                CANCEL
              </button>
              <button
                onClick={() => {
                  onTextChange('');
                  setShowClearDialog(false);
                }}
                className="px-4 py-2 rounded-xl bg-red-600 hover:bg-red-500 text-white text-xs font-bold"
              >
                CLEAR
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
