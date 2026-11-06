import { useEffect, useMemo, useRef, useState, type CSSProperties } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeft,
  ArrowRight,
  Check,
  ChevronLeft,
  Minus,
  Moon,
  Plus,
  Sun,
  X,
} from 'lucide-react';
import { getChapter, getNovel } from '@/mocks';
import { cn } from '@/lib/cn';
import { saveProgress } from '@/lib/progress';
import { NotFoundPage } from './NotFoundPage';

type Theme = 'paper' | 'sepia' | 'night';

interface ReaderSettings {
  fontSize: number;
  leading: number;
  theme: Theme;
}

const SETTINGS_KEY = 'novel.reader.settings';

const DEFAULT_SETTINGS: ReaderSettings = { fontSize: 19, leading: 1.95, theme: 'paper' };

const THEMES: { value: Theme; label: string; swatch: string }[] = [
  { value: 'paper', label: '纸白', swatch: '#f5f1e8' },
  { value: 'sepia', label: '米黄', swatch: '#f0e3c8' },
  { value: 'night', label: '夜间', swatch: '#14151a' },
];

function readSettings(): ReaderSettings {
  try {
    const raw = window.localStorage.getItem(SETTINGS_KEY);
    if (!raw) return DEFAULT_SETTINGS;
    const parsed = JSON.parse(raw) as Partial<ReaderSettings>;
    return { ...DEFAULT_SETTINGS, ...parsed };
  } catch {
    return DEFAULT_SETTINGS;
  }
}

export function ReaderPage() {
  const { id, seq } = useParams();
  const novelId = Number(id);
  const currentSeq = Number(seq);
  const novel = getNovel(novelId);
  const chapter = getChapter(novelId, currentSeq);

  const navigate = useNavigate();
  const [settings, setSettings] = useState<ReaderSettings>(readSettings);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [toolbarVisible, setToolbarVisible] = useState(true);
  const [progress, setProgress] = useState(0);
  const lastY = useRef(0);
  const panelRef = useRef<HTMLDivElement>(null);

  const paragraphs = useMemo(
    () => (chapter ? chapter.content.split('\n\n').filter(Boolean) : []),
    [chapter],
  );

  useEffect(() => {
    window.localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings));
  }, [settings]);

  useEffect(() => {
    if (novel && chapter) {
      saveProgress(novel.id, chapter.seq);
      document.title = `${chapter.title} · ${novel.title} · 夜读`;
    }
    return () => {
      document.title = '夜读 · 在字里行间遇见另一个世界';
    };
  }, [novel, chapter]);

  useEffect(() => {
    window.scrollTo({ top: 0, behavior: 'instant' as ScrollBehavior });
    lastY.current = 0;
  }, [currentSeq]);

  useEffect(() => {
    const onScroll = () => {
      const y = window.scrollY;
      const height = document.documentElement.scrollHeight - window.innerHeight;
      setProgress(height > 0 ? Math.min(1, Math.max(0, y / height)) : 0);
      if (!settingsOpen) {
        setToolbarVisible(y < 96 || y < lastY.current);
      }
      lastY.current = y;
    };
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, [settingsOpen]);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      if (target && ['INPUT', 'TEXTAREA'].includes(target.tagName)) return;
      if (event.key === 'ArrowLeft' && chapter?.prevSeq) {
        navigate(`/novel/${novelId}/chapters/${chapter.prevSeq}`);
      } else if (event.key === 'ArrowRight' && chapter?.nextSeq) {
        navigate(`/novel/${novelId}/chapters/${chapter.nextSeq}`);
      } else if (event.key === 'Escape') {
        navigate(`/novel/${novelId}/chapters`);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [chapter, navigate, novelId]);

  useEffect(() => {
    if (!settingsOpen) return;
    const onClick = (event: MouseEvent) => {
      if (!panelRef.current?.contains(event.target as Node)) setSettingsOpen(false);
    };
    document.addEventListener('mousedown', onClick);
    return () => document.removeEventListener('mousedown', onClick);
  }, [settingsOpen]);

  if (!novel || !chapter) {
    return <NotFoundPage />;
  }

  const style = {
    '--reader-size': `${settings.fontSize}px`,
    '--reader-leading': String(settings.leading),
  } as CSSProperties;

  return (
    <div className="reader-shell min-h-screen" data-theme={settings.theme} style={style}>
      {/* 顶部进度细线 */}
      <div className="fixed inset-x-0 top-0 z-50 h-[2px]" style={{ backgroundColor: 'var(--reader-line)' }}>
        <div
          className="h-full transition-[width] duration-150"
          style={{ width: `${progress * 100}%`, backgroundColor: 'var(--reader-accent)' }}
        />
      </div>

      {/* 工具栏：向下滚动收起，向上滚动露出 */}
      <header
        className={cn(
          'fixed inset-x-0 top-0 z-40 border-b transition-transform duration-300',
          toolbarVisible ? 'translate-y-0' : '-translate-y-full',
        )}
        style={{
          backgroundColor: 'var(--reader-bg)',
          borderColor: 'var(--reader-line)',
          color: 'var(--reader-fg)',
        }}
      >
        <div className="mx-auto flex h-14 max-w-[1080px] items-center gap-4 px-4 sm:px-6">
          <button
            type="button"
            onClick={() => navigate(`/novel/${novel.id}/chapters`)}
            className="inline-flex h-8 items-center gap-1.5 rounded-full px-2 text-[12px] opacity-70 transition-opacity hover:opacity-100"
            aria-label="返回目录"
          >
            <ArrowLeft size={15} />
            <span className="hidden sm:inline">目录</span>
          </button>

          <div className="min-w-0 flex-1 text-center">
            <Link to={`/novel/${novel.id}`} className="inline-block max-w-full">
              <p className="truncate font-serif text-[13.5px]">{novel.title}</p>
              <p className="mt-0.5 truncate text-[10.5px] opacity-50">
                第 {chapter.seq} / {novel.chapterCount} 章 · {chapter.title}
              </p>
            </Link>
          </div>

          <div className="flex items-center gap-1">
            <button
              type="button"
              onClick={() => setSettings({ ...settings, theme: nextTheme(settings.theme) })}
              className="inline-flex h-8 w-8 items-center justify-center rounded-full opacity-70 transition-opacity hover:opacity-100"
              aria-label="切换阅读主题"
            >
              {settings.theme === 'night' ? <Moon size={16} /> : <Sun size={16} />}
            </button>
            <div className="relative" ref={panelRef}>
              <button
                type="button"
                onClick={() => setSettingsOpen((open) => !open)}
                className="inline-flex h-8 items-center gap-1 rounded-full px-2 text-[12px] opacity-70 transition-opacity hover:opacity-100"
                aria-expanded={settingsOpen}
                aria-label="阅读设置"
              >
                <span className="font-serif text-[15px] leading-none tracking-tight">Aa</span>
              </button>

              {settingsOpen ? (
                <div
                  className="absolute right-0 top-full mt-2 w-[268px] rounded-[8px] border p-5 shadow-pop"
                  style={{
                    backgroundColor: 'var(--reader-bg)',
                    borderColor: 'var(--reader-line)',
                    color: 'var(--reader-fg)',
                  }}
                >
                  <div className="flex items-center justify-between">
                    <p className="text-[11px] tracking-[0.2em] opacity-50">阅读设置</p>
                    <button
                      type="button"
                      onClick={() => setSettingsOpen(false)}
                      aria-label="关闭"
                      className="opacity-50 hover:opacity-100"
                    >
                      <X size={14} />
                    </button>
                  </div>

                  <div className="mt-5 flex items-center justify-between">
                    <span className="text-[12px]">字号</span>
                    <div className="flex items-center gap-3">
                      <button
                        type="button"
                        onClick={() =>
                          setSettings({ ...settings, fontSize: Math.max(15, settings.fontSize - 1) })
                        }
                        className="inline-flex h-7 w-7 items-center justify-center rounded-full border"
                        style={{ borderColor: 'var(--reader-line)' }}
                        aria-label="减小字号"
                      >
                        <Minus size={13} />
                      </button>
                      <span className="w-9 text-center text-[12px] tabular-nums">
                        {settings.fontSize}
                      </span>
                      <button
                        type="button"
                        onClick={() =>
                          setSettings({ ...settings, fontSize: Math.min(26, settings.fontSize + 1) })
                        }
                        className="inline-flex h-7 w-7 items-center justify-center rounded-full border"
                        style={{ borderColor: 'var(--reader-line)' }}
                        aria-label="增大字号"
                      >
                        <Plus size={13} />
                      </button>
                    </div>
                  </div>

                  <div className="mt-4 flex items-center justify-between">
                    <span className="text-[12px]">行距</span>
                    <div className="flex gap-1">
                      {[
                        { value: 1.75, label: '紧凑' },
                        { value: 1.95, label: '标准' },
                        { value: 2.15, label: '宽松' },
                      ].map((option) => (
                        <button
                          key={option.value}
                          type="button"
                          onClick={() => setSettings({ ...settings, leading: option.value })}
                          className={cn(
                            'h-7 rounded-full px-2.5 text-[11px] transition-colors',
                            settings.leading === option.value
                              ? 'opacity-100'
                              : 'opacity-50 hover:opacity-80',
                          )}
                          style={
                            settings.leading === option.value
                              ? { backgroundColor: 'var(--reader-accent)', color: 'var(--reader-bg)' }
                              : { border: '1px solid var(--reader-line)' }
                          }
                        >
                          {option.label}
                        </button>
                      ))}
                    </div>
                  </div>

                  <div className="mt-4 flex items-center justify-between">
                    <span className="text-[12px]">主题</span>
                    <div className="flex gap-2">
                      {THEMES.map((theme) => (
                        <button
                          key={theme.value}
                          type="button"
                          onClick={() => setSettings({ ...settings, theme: theme.value })}
                          className="relative inline-flex h-7 w-7 items-center justify-center rounded-full border"
                          style={{
                            backgroundColor: theme.swatch,
                            borderColor:
                              settings.theme === theme.value
                                ? 'var(--reader-accent)'
                                : 'var(--reader-line)',
                          }}
                          aria-label={theme.label}
                          aria-pressed={settings.theme === theme.value}
                        >
                          {settings.theme === theme.value ? (
                            <Check
                              size={12}
                              style={{
                                color: theme.value === 'night' ? '#c6c2ba' : '#2e594e',
                              }}
                            />
                          ) : null}
                        </button>
                      ))}
                    </div>
                  </div>

                  <p className="mt-5 border-t pt-3 text-[10.5px] leading-relaxed opacity-40" style={{ borderColor: 'var(--reader-line)' }}>
                    设置会记住；键盘 ← → 可翻章，Esc 回目录。
                  </p>
                </div>
              ) : null}
            </div>
          </div>
        </div>
      </header>

      {/* 正文 */}
      <main className="mx-auto max-w-[42rem] px-6 pb-24 pt-24 sm:px-8 sm:pt-28">
        <article>
          <header className="mb-12 text-center">
            <p className="text-[11px] tracking-[0.3em] opacity-45">第 {chapter.seq} 章</p>
            <h1 className="mt-3 font-serif text-[25px] font-semibold tracking-[0.02em] sm:text-[28px]">
              {chapter.title}
            </h1>
            <span
              className="mx-auto mt-6 block h-px w-10"
              style={{ backgroundColor: 'var(--reader-line)' }}
            />
          </header>

          <div className="reader-body">
            {paragraphs.map((paragraph, index) => (
              <p key={index}>{paragraph}</p>
            ))}
          </div>

          <div className="mt-16 text-center">
            <span className="text-[11px] tracking-[0.3em] opacity-35">本章完</span>
            <p className="mt-2 text-[10.5px] opacity-30">
              约 {Math.max(1, Math.round(chapter.wordCount / 400))} 分钟读完
            </p>
          </div>
        </article>

        <nav className="mt-12 flex items-center justify-between gap-3 border-t pt-8" style={{ borderColor: 'var(--reader-line)' }} aria-label="章节导航">
          {chapter.prevSeq ? (
            <Link
              to={`/novel/${novel.id}/chapters/${chapter.prevSeq}`}
              className="group inline-flex max-w-[42%] items-center gap-2 text-[12.5px] opacity-70 transition-opacity hover:opacity-100"
            >
              <ChevronLeft size={15} className="shrink-0 transition-transform group-hover:-translate-x-0.5" />
              <span className="truncate">上一章</span>
            </Link>
          ) : (
            <span className="text-[12px] opacity-25">已是第一章</span>
          )}

          <Link
            to={`/novel/${novel.id}/chapters`}
            className="text-[12px] opacity-50 transition-opacity hover:opacity-90"
          >
            目录
          </Link>

          {chapter.nextSeq ? (
            <Link
              to={`/novel/${novel.id}/chapters/${chapter.nextSeq}`}
              className="group inline-flex max-w-[42%] items-center gap-2 text-[12.5px] opacity-70 transition-opacity hover:opacity-100"
            >
              <span className="truncate">下一章</span>
              <ArrowRight size={15} className="shrink-0 transition-transform group-hover:translate-x-0.5" />
            </Link>
          ) : (
            <span className="text-[12px] opacity-25">已是最后一章</span>
          )}
        </nav>
      </main>
    </div>
  );
}

function nextTheme(theme: Theme): Theme {
  if (theme === 'paper') return 'sepia';
  if (theme === 'sepia') return 'night';
  return 'paper';
}
