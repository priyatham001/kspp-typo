import { KeyboardMapper } from './keyboardMapper';
import { buildKeyDownReport, buildKeyUpReport, HidReport } from './keyboardDescriptor';
import { TypingState } from '../types';

export class TypingEngine {
  private typingState: TypingState = { status: 'IDLE' };
  private delayMs: number = 25;
  private timerId: number | null = null;
  private fullText: string = '';
  private currentIndex: number = 0;
  private isPaused: boolean = false;
  private onStateChange: ((state: TypingState) => void) | null = null;
  private onReportGenerated: ((report: HidReport, char: string) => void) | null = null;

  constructor(
    initialDelayMs: number = 25,
    onStateChange?: (state: TypingState) => void,
    onReportGenerated?: (report: HidReport, char: string) => void
  ) {
    this.delayMs = initialDelayMs;
    if (onStateChange) this.onStateChange = onStateChange;
    if (onReportGenerated) this.onReportGenerated = onReportGenerated;
  }

  public setCallbacks(
    onStateChange: (state: TypingState) => void,
    onReportGenerated?: (report: HidReport, char: string) => void
  ) {
    this.onStateChange = onStateChange;
    if (onReportGenerated) this.onReportGenerated = onReportGenerated;
  }

  public getState(): TypingState {
    return this.typingState;
  }

  public setDelay(ms: number) {
    this.delayMs = Math.max(5, Math.min(ms, 500));
  }

  public getDelay(): number {
    return this.delayMs;
  }

  private updateState(newState: TypingState) {
    this.typingState = newState;
    if (this.onStateChange) {
      this.onStateChange(newState);
    }
  }

  public startTyping(text: string, customDelay?: number) {
    if (!text || text.length === 0) {
      this.updateState({
        status: 'ERROR',
        message: 'There is no text to type. Please enter or load text first.',
        atIndex: 0
      });
      return;
    }

    if (customDelay) {
      this.setDelay(customDelay);
    }

    this.stopTyping(false);
    this.fullText = text;
    this.currentIndex = 0;
    this.isPaused = false;

    this.launchLoop();
  }

  public pauseTyping() {
    if (this.typingState.status === 'TYPING') {
      this.isPaused = true;
      if (this.timerId !== null) {
        clearTimeout(this.timerId);
        this.timerId = null;
      }
      const keyUp = buildKeyUpReport();
      if (this.onReportGenerated) this.onReportGenerated(keyUp, '');

      const total = this.fullText.length;
      const percent = total > 0 ? (this.currentIndex / total) * 100 : 0;
      this.updateState({
        status: 'PAUSED',
        currentIndex: this.currentIndex,
        totalChars: total,
        percent
      });
    }
  }

  public resumeTyping() {
    if (this.typingState.status === 'PAUSED') {
      this.isPaused = false;
      this.launchLoop();
    }
  }

  public stopTyping(reportState: boolean = true) {
    if (this.timerId !== null) {
      clearTimeout(this.timerId);
      this.timerId = null;
    }
    this.isPaused = false;
    const keyUp = buildKeyUpReport();
    if (this.onReportGenerated) this.onReportGenerated(keyUp, '');

    if (reportState) {
      const total = this.fullText.length;
      this.updateState({
        status: 'STOPPED',
        stoppedAtIndex: this.currentIndex,
        totalChars: total
      });
    }
  }

  public resetToIdle() {
    this.stopTyping(false);
    this.currentIndex = 0;
    this.fullText = '';
    this.updateState({ status: 'IDLE' });
  }

  public sendSingleKey(keyCode: number, modifier: number = 0, charName: string = '') {
    const downReport = buildKeyDownReport(keyCode, modifier);
    if (this.onReportGenerated) this.onReportGenerated(downReport, charName);
    setTimeout(() => {
      const upReport = buildKeyUpReport();
      if (this.onReportGenerated) this.onReportGenerated(upReport, '');
    }, 15);
  }

  private launchLoop() {
    const total = this.fullText.length;

    const step = () => {
      if (this.isPaused || this.currentIndex >= total) {
        if (this.currentIndex >= total && !this.isPaused) {
          const keyUp = buildKeyUpReport();
          if (this.onReportGenerated) this.onReportGenerated(keyUp, '');
          this.updateState({
            status: 'COMPLETED',
            totalChars: total
          });
        }
        return;
      }

      const char = this.fullText[this.currentIndex];

      // Handle CRLF newlines:
      let effectiveChar = char;
      if (char === '\r') {
        if (this.currentIndex + 1 < total && this.fullText[this.currentIndex + 1] === '\n') {
          this.currentIndex++; // skip to \n
          effectiveChar = '\n';
        } else {
          effectiveChar = '\n';
        }
      }

      const stroke = KeyboardMapper.mapChar(effectiveChar);
      if (!stroke) {
        const keyUp = buildKeyUpReport();
        if (this.onReportGenerated) this.onReportGenerated(keyUp, '');
        this.updateState({
          status: 'ERROR',
          message: `Unsupported character: '${effectiveChar}' at index ${this.currentIndex}.`,
          atIndex: this.currentIndex
        });
        return;
      }

      // Generate Key Down report
      const keyDown = buildKeyDownReport(stroke.keyCode, stroke.modifier);
      if (this.onReportGenerated) this.onReportGenerated(keyDown, effectiveChar);

      // Key hold duration (5-10ms)
      const holdTime = Math.min(10, Math.max(3, Math.floor(this.delayMs / 2)));
      setTimeout(() => {
        const keyUp = buildKeyUpReport();
        if (this.onReportGenerated) this.onReportGenerated(keyUp, '');

        this.currentIndex++;
        const percent = (this.currentIndex / total) * 100;
        this.updateState({
          status: 'TYPING',
          currentIndex: this.currentIndex,
          totalChars: total,
          percent
        });

        // Inter-key delay
        const restDelay = Math.max(5, this.delayMs - holdTime);
        this.timerId = window.setTimeout(step, restDelay);
      }, holdTime);
    };

    step();
  }
}
