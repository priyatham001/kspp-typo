import React, { useState } from 'react';
import { UserProfile } from '../types';
import { LogIn, Sparkles, Clock } from 'lucide-react';
import { RotatingDotsSphere } from './RotatingDotsSphere';

interface LoginScreenProps {
  onLogin: (email: string, name: string) => void;
  isLoading?: boolean;
  errorMessage?: string | null;
}

export const LoginScreen: React.FC<LoginScreenProps> = ({
  onLogin,
  isLoading = false,
  errorMessage = null
}) => {
  // Optical illusion tilt & warp state
  const [mousePos, setMousePos] = useState({ x: 0, y: 0 });
  const [isWarping, setIsWarping] = useState(false);

  const handleMouseMove = (e: React.MouseEvent<HTMLDivElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const x = ((e.clientX - rect.left) / rect.width - 0.5) * 28;
    const y = ((e.clientY - rect.top) / rect.height - 0.5) * -28;
    setMousePos({ x, y });
  };

  const handleMouseLeave = () => {
    setMousePos({ x: 0, y: 0 });
  };

  return (
    <div className="min-h-screen bg-black flex items-center justify-center p-4">
      <div className="w-full max-w-md bg-[#111827] border border-[#1f293d] rounded-3xl p-6 sm:p-8 shadow-2xl relative overflow-hidden">
        {/* Holographic background gradient lights */}
        <div className="absolute -top-24 -left-24 w-64 h-64 bg-cyan-500/10 rounded-full blur-3xl pointer-events-none"></div>
        <div className="absolute -bottom-24 -right-24 w-64 h-64 bg-blue-600/10 rounded-full blur-3xl pointer-events-none"></div>

        <div className="flex flex-col items-center text-center">
          {/* OPTICAL ILLUSION SPHERE CONTAINER */}
          <div
            onMouseMove={handleMouseMove}
            onMouseLeave={handleMouseLeave}
            onClick={() => setIsWarping(prev => !prev)}
            className="relative w-44 h-44 mb-6 cursor-pointer flex items-center justify-center select-none active:scale-95 transition-transform"
            title="Click or hover to warp optical illusion"
          >
            {/* Outer pulsating holographic glow aura */}
            <div className="absolute inset-0 rounded-full bg-gradient-to-tr from-cyan-500/30 via-blue-500/20 to-purple-500/30 blur-xl animate-sphere-aura pointer-events-none"></div>

            {/* Cyan radial radar rings */}
            <div className="absolute w-40 h-40 rounded-full border border-cyan-500/30 animate-pulse pointer-events-none"></div>
            <div className="absolute w-48 h-48 rounded-full border border-blue-500/20 animate-radar pointer-events-none"></div>

            {/* 3D illusion sphere with interactive tilt & continuous rotation */}
            <div
              style={{
                transform: `perspective(500px) rotateX(${mousePos.y}deg) rotateY(${mousePos.x}deg)`,
                transition: 'transform 0.15s ease-out'
              }}
              className="relative w-36 h-36 rounded-full overflow-hidden border-2 border-cyan-400/50 shadow-[0_0_35px_rgba(6,182,212,0.45)] bg-black flex items-center justify-center"
            >
              <RotatingDotsSphere
                size={144}
                dotCount={850}
                className={isWarping ? 'scale-110 brightness-125 transition-all duration-300' : 'transition-all duration-300'}
              />
              {/* Refraction shimmer overlay */}
              <div className="absolute inset-0 bg-gradient-to-t from-cyan-900/40 via-transparent to-white/10 pointer-events-none rounded-full"></div>
            </div>
          </div>

          {/* Title & Motto */}
          <h1 className="text-3xl font-extrabold text-white tracking-widest mb-1">
            REPLICA
          </h1>
          <p className="text-sm font-mono text-cyan-400 font-semibold mb-2">
            &quot;I replicate keyboard&quot;
          </p>
          <p className="text-xs text-gray-400 leading-relaxed max-w-xs mb-6">
            Server-Controlled Bluetooth HID Keyboard &amp; Auto-Typer with 8-Hour Access Control
          </p>

          {/* Error Message */}
          {errorMessage && (
            <div className="w-full mb-4 p-3 rounded-xl bg-red-950/50 border border-red-800 text-red-300 text-xs">
              {errorMessage}
            </div>
          )}

          {/* Primary Action: Google Sign-in */}
          <button
            onClick={() => onLogin('user@replica.local', 'REPLICA User')}
            disabled={isLoading}
            className="w-full py-3.5 px-6 rounded-2xl bg-gradient-to-r from-blue-600 to-cyan-600 hover:from-blue-500 hover:to-cyan-500 text-white font-bold text-sm shadow-lg shadow-blue-600/30 transition-all flex items-center justify-center gap-3 disabled:opacity-50"
          >
            {isLoading ? (
              <span className="flex items-center gap-2">
                <span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
                Authenticating...
              </span>
            ) : (
              <>
                <svg className="w-5 h-5" viewBox="0 0 24 24">
                  <path
                    fill="currentColor"
                    d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
                  />
                  <path
                    fill="currentColor"
                    d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
                  />
                  <path
                    fill="currentColor"
                    d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"
                  />
                  <path
                    fill="currentColor"
                    d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"
                  />
                </svg>
                Continue with Google
              </>
            )}
          </button>

          {/* Quick Free Trial shortcut */}
          <div className="w-full mt-4 pt-4 border-t border-[#1f293d]/80 flex flex-col gap-2">
            <button
              onClick={() => onLogin('guest@replica.local', 'Trial User')}
              className="w-full py-2.5 px-3 rounded-xl bg-[#0b0f19] hover:bg-[#151c2e] border border-[#1f293d] text-left text-xs transition-colors flex items-center justify-between text-cyan-300"
            >
              <div className="flex items-center gap-2.5">
                <Clock size={15} className="text-cyan-400 shrink-0" />
                <div>
                  <div className="font-bold text-xs text-white">8h Instant Free Trial</div>
                  <div className="text-[10px] text-gray-400">Launch workspace without credentials</div>
                </div>
              </div>
              <span className="text-[10px] font-mono text-cyan-400 font-semibold bg-cyan-950/60 border border-cyan-800/40 px-2 py-0.5 rounded-md">
                DEMO
              </span>
            </button>

          </div>
        </div>
      </div>
    </div>
  );
};
