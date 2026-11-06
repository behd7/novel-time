/** 阅读进度：按书存在 localStorage，刷新后仍能接着上次读。 */
const KEY_PREFIX = 'novel.reader.progress.';

export function loadProgress(novelId: number): number | null {
  try {
    const raw = window.localStorage.getItem(`${KEY_PREFIX}${novelId}`);
    if (!raw) return null;
    const seq = Number(raw);
    return Number.isInteger(seq) && seq > 0 ? seq : null;
  } catch {
    return null;
  }
}

export function saveProgress(novelId: number, seq: number): void {
  try {
    window.localStorage.setItem(`${KEY_PREFIX}${novelId}`, String(seq));
  } catch {
    // 隐私模式下 localStorage 可能不可写，阅读本身不该因此中断
  }
}
