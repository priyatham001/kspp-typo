import React, { useState } from 'react';
import { Lock, Shield, Delete, Check } from 'lucide-react';
import { StorageService } from '../services/storageService';

interface LockScreenProps {
  onUnlockSuccess: () => void;
}

export const LockScreen: React.FC<LockScreenProps> = ({ onUnlockSuccess }) => {
  const isSetup = StorageService.isPasscodeSet();

  const [enteredPin, setEnteredPin] = useState('');
  const [confirmPin, setConfirmPin] = useState('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const handleDigit = (digit: string) => {
    setErrorMessage(null);
    if (enteredPin.length < 8) {
      setEnteredPin(prev => prev + digit);
    }
  };

  const handleBackspace = () => {
    setErrorMessage(null);
    setEnteredPin(prev => prev.slice(0, -1));
  };

  const handleUnlock = async () => {
    if (!enteredPin) {
      setErrorMessage('Please enter your passcode.');
      return;
    }
    const ok = await StorageService.verifyPasscode(enteredPin);
    if (ok) {
      setEnteredPin('');
      setErrorMessage(null);
      onUnlockSuccess();
    } else {
      setErrorMessage('Incorrect passcode. Access denied.');
      setEnteredPin('');
    }
  };

  const handleCreatePasscode = async () => {
    if (enteredPin.length < 4) {
      setErrorMessage('Passcode must be at least 4 digits.');
      return;
    }
    if (enteredPin !== confirmPin) {
      setErrorMessage('Passcodes do not match.');
      return;
    }
    const ok = await StorageService.setupPasscode(enteredPin);
    if (ok) {
      onUnlockSuccess();
    } else {
      setErrorMessage('Failed to set passcode.');
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-[#090d16] flex items-center justify-center p-4">
      <div className="w-full max-w-sm bg-[#111827] border border-[#1f293d] rounded-3xl p-6 shadow-2xl flex flex-col items-center">
        {/* Lock Icon */}
        <div className="w-16 h-16 rounded-full bg-cyan-950/60 border border-cyan-500/40 flex items-center justify-center text-cyan-400 mb-4 shadow-lg shadow-cyan-950/50">
          {isSetup ? <Lock size={28} /> : <Shield size={28} />}
        </div>

        <h2 className="text-xl font-bold text-white mb-1">
          {isSetup ? 'Enter Owner Passcode' : 'Create Owner Passcode'}
        </h2>
        <p className="text-xs text-gray-400 text-center mb-5">
          {isSetup
            ? 'Authentication required to transmit keyboard events.'
            : 'Protect keyboard transmission with an owner passcode (min 4 digits).'}
        </p>

        {/* Pin Dots */}
        <div className="flex gap-3 mb-4">
          {[0, 1, 2, 3].map((idx) => (
            <div
              key={idx}
              className={`w-3.5 h-3.5 rounded-full border transition-all ${
                idx < enteredPin.length
                  ? 'bg-cyan-400 border-cyan-300 shadow-[0_0_8px_rgba(6,182,212,0.8)]'
                  : 'bg-gray-800 border-gray-600'
              }`}
            ></div>
          ))}
        </div>

        {errorMessage && (
          <div className="w-full mb-4 p-2 rounded-xl bg-red-950/60 border border-red-800 text-red-300 text-xs text-center font-medium">
            {errorMessage}
          </div>
        )}

        {isSetup ? (
          <>
            {/* Numeric Keypad */}
            <div className="grid grid-cols-3 gap-3 w-full max-w-[260px] mb-5">
              {['1', '2', '3', '4', '5', '6', '7', '8', '9', 'C', '0', 'DEL'].map((val) => (
                <button
                  key={val}
                  onClick={() => {
                    if (val === 'C') setEnteredPin('');
                    else if (val === 'DEL') handleBackspace();
                    else handleDigit(val);
                  }}
                  className="w-16 h-14 mx-auto rounded-2xl bg-[#0e1626] hover:bg-[#1a263d] active:scale-95 border border-[#1f293d] text-white font-mono text-xl font-semibold flex items-center justify-center transition-all shadow-sm"
                >
                  {val === 'DEL' ? <Delete size={20} className="text-gray-300" /> : val}
                </button>
              ))}
            </div>

            <button
              onClick={handleUnlock}
              className="w-full py-3.5 rounded-2xl bg-cyan-600 hover:bg-cyan-500 text-white font-bold text-sm shadow-lg shadow-cyan-600/30 transition-all flex items-center justify-center gap-2"
            >
              <Check size={18} />
              UNLOCK
            </button>
          </>
        ) : (
          <div className="w-full flex flex-col gap-3">
            <div>
              <label className="text-[11px] text-gray-400 block mb-1">New Passcode (4+ digits):</label>
              <input
                type="password"
                maxLength={8}
                value={enteredPin}
                onChange={(e) => setEnteredPin(e.target.value.replace(/\D/g, ''))}
                placeholder="••••"
                className="w-full bg-[#0b0f19] border border-[#1f293d] rounded-xl px-4 py-2.5 text-center font-mono text-lg text-white focus:outline-none focus:border-cyan-500 tracking-widest"
              />
            </div>

            <div>
              <label className="text-[11px] text-gray-400 block mb-1">Confirm Passcode:</label>
              <input
                type="password"
                maxLength={8}
                value={confirmPin}
                onChange={(e) => setConfirmPin(e.target.value.replace(/\D/g, ''))}
                placeholder="••••"
                className="w-full bg-[#0b0f19] border border-[#1f293d] rounded-xl px-4 py-2.5 text-center font-mono text-lg text-white focus:outline-none focus:border-cyan-500 tracking-widest"
              />
            </div>

            <button
              onClick={handleCreatePasscode}
              className="w-full mt-2 py-3.5 rounded-2xl bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-sm shadow-lg shadow-emerald-600/30 transition-all"
            >
              SET OWNER PASSCODE
            </button>
          </div>
        )}
      </div>
    </div>
  );
};
