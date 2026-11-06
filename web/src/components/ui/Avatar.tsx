import { avatarColor } from '@/lib/cover';
import { cn } from '@/lib/cn';

interface AvatarProps {
  name: string;
  avatarKey?: string | null;
  size?: number;
  className?: string;
}

const ASSET_BASE = import.meta.env.VITE_ASSET_BASE ?? 'http://127.0.0.1:9000/novel';

/** 没有头像图时用昵称首字 + 固定色，列表里不会出现一排灰色默认头像。 */
export function Avatar({ name, avatarKey, size = 32, className }: AvatarProps) {
  const label = name.trim().slice(0, 1) || '读';
  const src = avatarKey ? `${ASSET_BASE}/${avatarKey}` : null;
  return (
    <span
      className={cn(
        'inline-flex shrink-0 select-none items-center justify-center overflow-hidden rounded-full',
        className,
      )}
      style={{
        width: size,
        height: size,
        backgroundColor: avatarColor(name),
        fontSize: size * 0.4,
      }}
      aria-hidden
    >
      {src ? (
        <img src={src} alt="" className="h-full w-full object-cover" />
      ) : (
        <span className="font-serif text-paper">{label}</span>
      )}
    </span>
  );
}
