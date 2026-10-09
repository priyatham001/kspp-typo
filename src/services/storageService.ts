import { ScriptEntity } from '../types';

const STORAGE_KEYS = {
  PASSCODE_HASH: 'replica_passcode_hash',
  PASSCODE_SALT: 'replica_passcode_salt',
  SCRIPTS: 'replica_scripts',
  DELAY: 'replica_delay_ms',
  KEEP_AWAKE: 'replica_keep_awake',
  AUTO_CONNECT: 'replica_auto_connect',
  IS_UNLOCKED: 'replica_is_unlocked'
};

const DEFAULT_SCRIPTS: ScriptEntity[] = [
  {
    id: 1,
    title: 'greeting.txt',
    language: 'text',
    createdAt: Date.now() - 3600000,
    updatedAt: Date.now() - 3600000,
    content: `Hello World!

This is my first test using PSK BT Auto.
Everything typed here will appear on your Windows PC exactly as physical keystrokes.`
  },
  {
    id: 2,
    title: 'email_draft.txt',
    language: 'text',
    createdAt: Date.now() - 3000000,
    updatedAt: Date.now() - 3000000,
    content: `Dear Sir,

I would like to submit my lab assignment. Here is my test program:

public class Main {
    public static void main(String[] args) {
        System.out.println("Hello from CodeTantra!");
    }
}

Thank you,
PSK BT Auto User`
  },
  {
    id: 3,
    title: 'commands.txt',
    language: 'shell',
    createdAt: Date.now() - 2400000,
    updatedAt: Date.now() - 2400000,
    content: `git status
cd project
npm install
python3 app.py`
  },
  {
    id: 4,
    title: 'Main.java',
    language: 'java',
    createdAt: Date.now() - 1800000,
    updatedAt: Date.now() - 1800000,
    content: `public class Main {
    public static void main(String[] args) {
        int a = 10;
        int b = 20;

        int sum = a + b;

        System.out.println("Sum = " + sum);
    }
}`
  },
  {
    id: 5,
    title: 'BinarySearch.java',
    language: 'java',
    createdAt: Date.now() - 1200000,
    updatedAt: Date.now() - 1200000,
    content: `public class BinarySearch {
    public static int search(int[] arr, int target) {
        int low = 0;
        int high = arr.length - 1;

        while (low <= high) {
            int mid = low + (high - low) / 2;
            if (arr[mid] == target) return mid;
            if (arr[mid] < target) low = mid + 1;
            else high = mid - 1;
        }
        return -1;
    }

    public static void main(String[] args) {
        int[] numbers = {2, 4, 6, 8, 10, 12, 14};
        int result = search(numbers, 10);
        System.out.println("Index: " + result);
    }
}`
  }
];

export async function hashPasscode(passcode: string, salt: string): Promise<string> {
  const enc = new TextEncoder();
  const data = enc.encode(passcode + salt);
  const hashBuffer = await crypto.subtle.digest('SHA-256', data);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
}

export const StorageService = {
  // Passcode operations
  isPasscodeSet(): boolean {
    return !!localStorage.getItem(STORAGE_KEYS.PASSCODE_HASH);
  },

  async setupPasscode(passcode: string): Promise<boolean> {
    if (passcode.length < 4) return false;
    const salt = Array.from(crypto.getRandomValues(new Uint8Array(16)))
      .map(b => b.toString(16).padStart(2, '0'))
      .join('');
    const hash = await hashPasscode(passcode, salt);
    localStorage.setItem(STORAGE_KEYS.PASSCODE_SALT, salt);
    localStorage.setItem(STORAGE_KEYS.PASSCODE_HASH, hash);
    sessionStorage.setItem(STORAGE_KEYS.IS_UNLOCKED, 'true');
    return true;
  },

  async verifyPasscode(enteredPasscode: string): Promise<boolean> {
    const salt = localStorage.getItem(STORAGE_KEYS.PASSCODE_SALT);
    const expectedHash = localStorage.getItem(STORAGE_KEYS.PASSCODE_HASH);
    if (!salt || !expectedHash) return false;
    const computed = await hashPasscode(enteredPasscode, salt);
    const valid = computed === expectedHash;
    if (valid) {
      sessionStorage.setItem(STORAGE_KEYS.IS_UNLOCKED, 'true');
    }
    return valid;
  },

  isUnlocked(): boolean {
    if (!this.isPasscodeSet()) return true;
    return sessionStorage.getItem(STORAGE_KEYS.IS_UNLOCKED) === 'true';
  },

  lock(): void {
    sessionStorage.removeItem(STORAGE_KEYS.IS_UNLOCKED);
  },

  unlock(): void {
    sessionStorage.setItem(STORAGE_KEYS.IS_UNLOCKED, 'true');
  },

  // Scripts CRUD
  getScripts(): ScriptEntity[] {
    const data = localStorage.getItem(STORAGE_KEYS.SCRIPTS);
    if (!data) {
      localStorage.setItem(STORAGE_KEYS.SCRIPTS, JSON.stringify(DEFAULT_SCRIPTS));
      return DEFAULT_SCRIPTS;
    }
    try {
      return JSON.parse(data);
    } catch {
      return DEFAULT_SCRIPTS;
    }
  },

  saveScript(title: string, content: string, language: string = 'text'): ScriptEntity {
    const scripts = this.getScripts();
    const newScript: ScriptEntity = {
      id: Date.now(),
      title: title || `Text_${Math.floor(Date.now() / 1000)}.txt`,
      content,
      language,
      createdAt: Date.now(),
      updatedAt: Date.now()
    };
    scripts.unshift(newScript);
    localStorage.setItem(STORAGE_KEYS.SCRIPTS, JSON.stringify(scripts));
    return newScript;
  },

  updateScript(id: number, title: string, content: string): ScriptEntity | null {
    const scripts = this.getScripts();
    const idx = scripts.findIndex(s => s.id === id);
    if (idx === -1) return null;
    scripts[idx] = {
      ...scripts[idx],
      title: title || scripts[idx].title,
      content,
      updatedAt: Date.now()
    };
    localStorage.setItem(STORAGE_KEYS.SCRIPTS, JSON.stringify(scripts));
    return scripts[idx];
  },

  renameScript(id: number, newTitle: string): void {
    const scripts = this.getScripts();
    const idx = scripts.findIndex(s => s.id === id);
    if (idx !== -1) {
      scripts[idx].title = newTitle;
      scripts[idx].updatedAt = Date.now();
      localStorage.setItem(STORAGE_KEYS.SCRIPTS, JSON.stringify(scripts));
    }
  },

  deleteScript(id: number): void {
    const scripts = this.getScripts().filter(s => s.id !== id);
    localStorage.setItem(STORAGE_KEYS.SCRIPTS, JSON.stringify(scripts));
  },

  clearAllScripts(): void {
    localStorage.setItem(STORAGE_KEYS.SCRIPTS, JSON.stringify([]));
  },

  // Preferences
  getDefaultDelay(): number {
    const val = localStorage.getItem(STORAGE_KEYS.DELAY);
    return val ? parseInt(val, 10) : 25; // 25ms matches user screenshot!
  },

  setDefaultDelay(ms: number): void {
    localStorage.setItem(STORAGE_KEYS.DELAY, ms.toString());
  },

  getKeepAwake(): boolean {
    const val = localStorage.getItem(STORAGE_KEYS.KEEP_AWAKE);
    return val !== null ? val === 'true' : true;
  },

  setKeepAwake(val: boolean): void {
    localStorage.setItem(STORAGE_KEYS.KEEP_AWAKE, String(val));
  },

  getAutoConnect(): boolean {
    const val = localStorage.getItem(STORAGE_KEYS.AUTO_CONNECT);
    return val !== null ? val === 'true' : true;
  },

  setAutoConnect(val: boolean): void {
    localStorage.setItem(STORAGE_KEYS.AUTO_CONNECT, String(val));
  }
};
