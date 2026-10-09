import React from 'react';
import { FileText, Keyboard, Settings } from 'lucide-react';
import { ScreenTab } from '../types';

interface BottomNavProps {
  currentTab: ScreenTab;
  onTabChange: (tab: ScreenTab) => void;
}

export const BottomNav: React.FC<BottomNavProps> = ({ currentTab, onTabChange }) => {
  return (
    <nav className="fixed bottom-0 left-0 right-0 z-40 bg-[#090d16]/95 backdrop-blur-md border-t border-[#1f293d] px-4 py-2">
      <div className="max-w-md mx-auto grid grid-cols-4 gap-1">
        {/* Tab 1: Editor */}
        <button
          onClick={() => onTabChange('EDITOR')}
          className={`flex flex-col items-center justify-center py-1.5 px-2 rounded-xl transition-all ${
            currentTab === 'EDITOR'
              ? 'text-emerald-400 font-semibold'
              : 'text-gray-400 hover:text-gray-200'
          }`}
        >
          <div
            className={`w-10 h-7 rounded-full flex items-center justify-center transition-all ${
              currentTab === 'EDITOR' ? 'bg-emerald-950/60 text-emerald-400 border border-emerald-500/40' : ''
            }`}
          >
            <span className="font-extrabold text-base tracking-tighter font-mono">T<span className="text-xs">T</span></span>
          </div>
          <span className="text-[11px] mt-0.5">Editor</span>
        </button>

        {/* Tab 2: Texts */}
        <button
          onClick={() => onTabChange('SCRIPTS')}
          className={`flex flex-col items-center justify-center py-1.5 px-2 rounded-xl transition-all ${
            currentTab === 'SCRIPTS'
              ? 'text-emerald-400 font-semibold'
              : 'text-gray-400 hover:text-gray-200'
          }`}
        >
          <div
            className={`w-10 h-7 rounded-full flex items-center justify-center transition-all ${
              currentTab === 'SCRIPTS' ? 'bg-emerald-950/60 text-emerald-400 border border-emerald-500/40' : ''
            }`}
          >
            <FileText size={18} />
          </div>
          <span className="text-[11px] mt-0.5">Texts</span>
        </button>

        {/* Tab 3: Test */}
        <button
          onClick={() => onTabChange('TEST')}
          className={`flex flex-col items-center justify-center py-1.5 px-2 rounded-xl transition-all ${
            currentTab === 'TEST'
              ? 'text-emerald-400 font-semibold'
              : 'text-gray-400 hover:text-gray-200'
          }`}
        >
          <div
            className={`w-10 h-7 rounded-full flex items-center justify-center transition-all ${
              currentTab === 'TEST' ? 'bg-emerald-950/60 text-emerald-400 border border-emerald-500/40' : ''
            }`}
          >
            <Keyboard size={18} />
          </div>
          <span className="text-[11px] mt-0.5">Test</span>
        </button>

        {/* Tab 4: Settings */}
        <button
          onClick={() => onTabChange('SETTINGS')}
          className={`flex flex-col items-center justify-center py-1.5 px-2 rounded-xl transition-all ${
            currentTab === 'SETTINGS'
              ? 'text-emerald-400 font-semibold'
              : 'text-gray-400 hover:text-gray-200'
          }`}
        >
          <div
            className={`w-10 h-7 rounded-full flex items-center justify-center transition-all ${
              currentTab === 'SETTINGS' ? 'bg-emerald-950/60 text-emerald-400 border border-emerald-500/40' : ''
            }`}
          >
            <Settings size={18} />
          </div>
          <span className="text-[11px] mt-0.5">Settings</span>
        </button>
      </div>
    </nav>
  );
};
