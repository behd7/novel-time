import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { Check } from 'lucide-react';
import { cn } from '@/lib/cn';

type ToastTone = 'default' | 'error';

interface ToastItem {
  id: number;
  message: string;
  tone: ToastTone;
}

interface ToastContextValue {
  toast: (message: string, tone?: ToastTone) => void;
}

const ToastContext = createContext<ToastContextValue>({ toast: () => undefined });

export function useToast() {
  return useContext(ToastContext);
}

/** 底部居中的轻提示：只做"已完成/失败了"的确认，不抢焦点、不阻挡点击。 */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([]);
  const nextId = useRef(1);
  const timers = useRef<number[]>([]);

  const toast = useCallback((message: string, tone: ToastTone = 'default') => {
    const id = nextId.current++;
    setItems((current) => [...current, { id, message, tone }]);
    const timer = window.setTimeout(() => {
      setItems((current) => current.filter((item) => item.id !== id));
    }, 2400);
    timers.current.push(timer);
  }, []);

  useEffect(() => {
    return () => timers.current.forEach((timer) => window.clearTimeout(timer));
  }, []);

  const value = useMemo(() => ({ toast }), [toast]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div
        className="pointer-events-none fixed inset-x-0 bottom-8 z-[80] flex flex-col items-center gap-2"
        role="status"
        aria-live="polite"
      >
        {items.map((item) => (
          <div
            key={item.id}
            className={cn(
              'flex items-center gap-2 rounded-full px-4 py-2 text-[13px] shadow-pop',
              item.tone === 'error' ? 'bg-danger text-paper' : 'bg-ink text-paper',
            )}
          >
            {item.tone === 'default' ? <Check size={14} /> : null}
            {item.message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
