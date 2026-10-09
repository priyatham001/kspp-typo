import React, { useState } from 'react';
import { ScriptEntity } from '../types';
import { FileText, FolderOpen, Edit3, Trash2, Play, Plus } from 'lucide-react';

interface ScriptsScreenProps {
  scripts: ScriptEntity[];
  onLoadScript: (script: ScriptEntity) => void;
  onRenameScript: (id: number, newTitle: string) => void;
  onDeleteScript: (id: number) => void;
  onNewScript: () => void;
}

export const ScriptsScreen: React.FC<ScriptsScreenProps> = ({
  scripts,
  onLoadScript,
  onRenameScript,
  onDeleteScript,
  onNewScript
}) => {
  const [renamingScript, setRenamingScript] = useState<ScriptEntity | null>(null);
  const [renameInput, setRenameInput] = useState('');
  const [deletingScript, setDeletingScript] = useState<ScriptEntity | null>(null);

  const formatDate = (ts: number) => {
    return new Date(ts).toLocaleDateString(undefined, {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  return (
    <div className="flex flex-col gap-4 pb-20">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-white">Saved Texts</h2>
          <p className="text-xs text-gray-400">Stored 100% locally on your device (no cloud/server)</p>
        </div>
        <button
          onClick={onNewScript}
          className="px-3 py-1.5 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-bold flex items-center gap-1.5 shadow-md shadow-cyan-600/30"
        >
          <Plus size={14} />
          NEW TEXT
        </button>
      </div>

      {scripts.length === 0 ? (
        <div className="bg-[#111827] border border-[#1f293d] rounded-2xl p-10 flex flex-col items-center justify-center text-center">
          <FolderOpen size={48} className="text-gray-600 mb-3" />
          <h3 className="text-base font-bold text-gray-300 mb-1">No saved texts yet</h3>
          <p className="text-xs text-gray-500 max-w-xs mb-4">
            Type or paste any text in the Editor, then tap [SAVE] to store it locally.
          </p>
          <button
            onClick={onNewScript}
            className="px-4 py-2 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-bold"
          >
            Create New Document
          </button>
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          {scripts.map((script) => (
            <div
              key={script.id}
              className="bg-[#111827] border border-[#1f293d] hover:border-gray-700 rounded-2xl p-4 shadow-md transition-all flex flex-col gap-2.5"
            >
              {/* Header */}
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2.5 truncate">
                  <div className="w-8 h-8 rounded-lg bg-cyan-950/60 border border-cyan-500/30 flex items-center justify-center text-cyan-400 shrink-0">
                    <FileText size={16} />
                  </div>
                  <span className="text-sm font-bold text-white truncate">{script.title}</span>
                </div>

                <div className="flex items-center gap-1">
                  <button
                    onClick={() => {
                      setRenamingScript(script);
                      setRenameInput(script.title);
                    }}
                    title="Rename"
                    className="p-1.5 rounded-lg hover:bg-[#1f293d] text-gray-400 hover:text-white transition-colors"
                  >
                    <Edit3 size={15} />
                  </button>
                  <button
                    onClick={() => setDeletingScript(script)}
                    title="Delete"
                    className="p-1.5 rounded-lg hover:bg-red-950/40 text-red-400 hover:text-red-300 transition-colors"
                  >
                    <Trash2 size={15} />
                  </button>
                </div>
              </div>

              {/* Text Preview */}
              <p className="text-xs font-mono text-gray-400 line-clamp-2 bg-[#0b0f19] p-2.5 rounded-xl border border-[#1f293d]/60 leading-relaxed">
                {script.content.slice(0, 160).replace(/\n/g, ' ')}
              </p>

              {/* Footer */}
              <div className="flex items-center justify-between pt-1">
                <span className="text-[11px] text-gray-500 font-mono">
                  {script.content.length} chars • {formatDate(script.updatedAt)}
                </span>
                <button
                  onClick={() => onLoadScript(script)}
                  className="px-3 py-1.5 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-bold flex items-center gap-1.5 shadow-sm shadow-cyan-600/30 transition-all"
                >
                  <Play size={13} className="fill-white" />
                  Load in Editor
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* RENAME DIALOG */}
      {renamingScript && (
        <div className="fixed inset-0 z-50 bg-black/80 flex items-center justify-center p-4">
          <div className="w-full max-w-sm bg-[#111827] border border-[#1f293d] rounded-2xl p-5 shadow-2xl flex flex-col gap-4">
            <h3 className="text-base font-bold text-white">Rename Document</h3>
            <div>
              <label className="text-xs text-gray-400 block mb-1">New Document Name:</label>
              <input
                type="text"
                value={renameInput}
                onChange={(e) => setRenameInput(e.target.value)}
                className="w-full bg-[#0b0f19] border border-[#1f293d] rounded-xl px-3 py-2 text-sm text-white focus:outline-none focus:border-cyan-500 font-mono"
              />
            </div>
            <div className="flex justify-end gap-2">
              <button
                onClick={() => setRenamingScript(null)}
                className="px-4 py-2 rounded-xl bg-[#0b0f19] hover:bg-[#162035] text-gray-300 text-xs font-semibold"
              >
                CANCEL
              </button>
              <button
                onClick={() => {
                  if (renameInput.trim()) {
                    onRenameScript(renamingScript.id, renameInput.trim());
                  }
                  setRenamingScript(null);
                }}
                className="px-4 py-2 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white text-xs font-bold"
              >
                RENAME
              </button>
            </div>
          </div>
        </div>
      )}

      {/* DELETE DIALOG */}
      {deletingScript && (
        <div className="fixed inset-0 z-50 bg-black/80 flex items-center justify-center p-4">
          <div className="w-full max-w-sm bg-[#111827] border border-[#1f293d] rounded-2xl p-5 shadow-2xl flex flex-col gap-4">
            <h3 className="text-base font-bold text-white">Delete Document?</h3>
            <p className="text-xs text-gray-400 leading-relaxed">
              Are you sure you want to delete &quot;{deletingScript.title}&quot;? This cannot be undone.
            </p>
            <div className="flex justify-end gap-2">
              <button
                onClick={() => setDeletingScript(null)}
                className="px-4 py-2 rounded-xl bg-[#0b0f19] hover:bg-[#162035] text-gray-300 text-xs font-semibold"
              >
                CANCEL
              </button>
              <button
                onClick={() => {
                  onDeleteScript(deletingScript.id);
                  setDeletingScript(null);
                }}
                className="px-4 py-2 rounded-xl bg-red-600 hover:bg-red-500 text-white text-xs font-bold"
              >
                DELETE
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
