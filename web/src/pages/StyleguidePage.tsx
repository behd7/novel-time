import { useState } from 'react';
import { Link } from 'react-router-dom';
import { BookOpen, Search, Sparkles } from 'lucide-react';
import { novels } from '@/mocks';
import { designCover } from '@/lib/cover';
import { cn } from '@/lib/cn';
import { Button, ButtonLink } from '@/components/ui/Button';
import { Stars, StarPicker } from '@/components/ui/Stars';
import { Tag, StatusDot } from '@/components/ui/Tag';
import { Avatar } from '@/components/ui/Avatar';
import { EmptyState } from '@/components/ui/EmptyState';
import { NovelCardSkeleton } from '@/components/ui/Skeleton';
import { Pagination } from '@/components/ui/Pagination';
import { Segmented } from '@/components/ui/Segmented';
import { NovelCover } from '@/components/book/NovelCover';
import { useToast } from '@/components/ui/Toast';

const COLORS = [
  { name: 'paper', value: '#f6f4ef', note: '页面底色' },
  { name: 'surface', value: '#fffefb', note: '卡片 / 输入框' },
  { name: 'ink', value: '#1a1815', note: '正文与主按钮' },
  { name: 'ink-soft', value: '#403b34', note: '次级正文' },
  { name: 'muted', value: '#7b756b', note: '辅助信息' },
  { name: 'faint', value: '#a39c90', note: '弱提示' },
  { name: 'line', value: '#e4dfd4', note: '分隔线' },
  { name: 'accent', value: '#2e594e', note: '强调 / 链接' },
  { name: 'gold', value: '#b0873f', note: '评分' },
  { name: 'danger', value: '#a8483d', note: '错误' },
];

const TYPE_SCALE = [
  { label: 'Display', size: 'text-[56px]', sample: '今晚，读点什么？' },
  { label: 'H1', size: 'text-[40px]', sample: '长安旧事' },
  { label: 'H2', size: 'text-[28px]', sample: '按题材找书' },
  { label: 'H3', size: 'text-[20px]', sample: '你可能也会喜欢' },
  { label: 'Body', size: 'text-[15px]', sample: '没有信息流，没有标题党。只有书，和你愿意花掉的那个晚上。' },
  { label: 'Small', size: 'text-[13px]', sample: '更新于 3 小时前 · 沈砚 著' },
  { label: 'Caption', size: 'text-[11px] tracking-[0.2em]', sample: 'EDITOR’S PICK' },
];

export function StyleguidePage() {
  const { toast } = useToast();
  const [page, setPage] = useState(1);
  const [rating, setRating] = useState<number | null>(4);
  const [seg, setSeg] = useState<'daily' | 'weekly'>('weekly');

  const coverSamples = [0, 1, 2].map((variant) => {
    const novel = novels.find((item) => designCover(item.title, item.authorName).variant === variant);
    return { variant, novel: novel ?? novels[variant] };
  });

  return (
    <div className="mx-auto max-w-[1080px] px-6 pb-24 pt-12 lg:px-10 lg:pt-16">
      <p className="text-[10.5px] tracking-[0.28em] text-accent">DESIGN SYSTEM</p>
      <h1 className="mt-3 font-serif text-[38px] font-semibold text-ink">夜读的设计语言</h1>
      <p className="mt-3 max-w-2xl text-[13px] leading-[1.9] text-muted">
        这套页面只用了两种字体、一个强调色和一张纸的质感。所有颜色、字号与间距都来自
        Tailwind 的 theme token，页面里不应该出现写死的十六进制色值。
      </p>

      <Section title="色彩" eyebrow="COLOR">
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-5">
          {COLORS.map((color) => (
            <div key={color.name} className="overflow-hidden rounded-[6px] border border-line">
              <div className="h-16" style={{ backgroundColor: color.value }} />
              <div className="bg-surface px-2.5 py-2">
                <p className="text-[11px] text-ink">{color.name}</p>
                <p className="mt-0.5 font-mono text-[9.5px] uppercase text-faint">{color.value}</p>
                <p className="mt-0.5 text-[9.5px] text-faint">{color.note}</p>
              </div>
            </div>
          ))}
        </div>
      </Section>

      <Section title="字体" eyebrow="TYPOGRAPHY">
        <div className="grid gap-8 sm:grid-cols-2">
          <div className="rounded-[8px] border border-line bg-surface/50 p-6">
            <p className="text-[10.5px] tracking-[0.2em] text-faint">SERIF · 标题与正文</p>
            <p className="mt-3 font-serif text-[32px] leading-tight text-ink">在字里行间</p>
            <p className="mt-1 font-serif text-[13px] text-muted">Noto Serif SC Variable · 200–900</p>
            <p className="mt-4 font-serif text-[15px] leading-[1.95] text-ink-soft">
              长安的雨总在黄昏时落下。先是朱雀大街的尘土被压住，接着是坊墙上的青苔慢慢亮起来。
            </p>
          </div>
          <div className="rounded-[8px] border border-line bg-surface/50 p-6">
            <p className="text-[10.5px] tracking-[0.2em] text-faint">SANS · 界面与信息</p>
            <p className="mt-3 text-[32px] font-medium leading-tight text-ink">在字里行间</p>
            <p className="mt-1 text-[13px] text-muted">Inter + 系统中文黑体</p>
            <p className="mt-4 text-[14px] leading-[1.8] text-ink-soft">
              按钮、标签、分页与表单用无衬线体；标题和正文用衬线体。两者交替出现，
              页面才有“印刷品”的层次。
            </p>
          </div>
        </div>

        <div className="mt-8 divide-y divide-line/70 border-y border-line/70">
          {TYPE_SCALE.map((item) => (
            <div key={item.label} className="flex items-baseline gap-6 py-3.5">
              <span className="w-20 shrink-0 text-[10.5px] tracking-[0.16em] text-faint">
                {item.label}
              </span>
              <span className={cn('font-serif text-ink', item.size)}>{item.sample}</span>
            </div>
          ))}
        </div>
      </Section>

      <Section title="按钮与控件" eyebrow="COMPONENTS">
        <div className="flex flex-wrap items-center gap-3">
          <Button>主要按钮</Button>
          <Button variant="secondary">次要按钮</Button>
          <Button variant="soft">柔和按钮</Button>
          <Button variant="ghost">幽灵按钮</Button>
          <Button variant="accent">强调按钮</Button>
          <Button disabled>禁用</Button>
        </div>
        <div className="mt-4 flex flex-wrap items-center gap-3">
          <Button size="sm">小号</Button>
          <Button size="md">中号</Button>
          <Button size="lg">大号</Button>
          <ButtonLink to="/browse" variant="secondary" size="md">
            链接按钮
          </ButtonLink>
        </div>

        <div className="mt-8 flex flex-wrap items-center gap-6">
          <Segmented
            options={[
              { value: 'daily', label: '日榜' },
              { value: 'weekly', label: '周榜' },
            ]}
            value={seg}
            onChange={setSeg}
          />
          <div className="flex items-center gap-2">
            <Tag>标签</Tag>
            <Tag tone="accent">强调标签</Tag>
            <Tag tone="gold">评分标签</Tag>
            <StatusDot status={0} />
            <StatusDot status={1} />
          </div>
        </div>

        <div className="mt-8 grid gap-6 sm:grid-cols-3">
          <div className="rounded-[8px] border border-line bg-surface/50 p-5">
            <p className="text-[10.5px] tracking-[0.18em] text-faint">星级展示</p>
            <div className="mt-3 space-y-2">
              <Stars value={4.6} size={16} />
              <p className="text-[12px] text-muted">4.6 / 5 · 3,842 人评分</p>
            </div>
          </div>
          <div className="rounded-[8px] border border-line bg-surface/50 p-5">
            <p className="text-[10.5px] tracking-[0.18em] text-faint">星级输入</p>
            <StarPicker value={rating} onChange={setRating} size={20} className="mt-2" />
            <p className="mt-1 text-[11px] text-muted">{rating ? `已选 ${rating} 星` : '未选择'}</p>
          </div>
          <div className="rounded-[8px] border border-line bg-surface/50 p-5">
            <p className="text-[10.5px] tracking-[0.18em] text-faint">头像与提示</p>
            <div className="mt-2 flex items-center gap-2">
              <Avatar name="青杉" size={28} />
              <Avatar name="林晚" size={28} />
              <Avatar name="顾青" size={28} />
              <button
                type="button"
                onClick={() => toast('已加入书架')}
                className="ml-auto text-[11.5px] text-accent hover:text-accent-deep"
              >
                触发提示
              </button>
            </div>
          </div>
        </div>

        <div className="mt-6 flex flex-wrap items-center justify-between gap-4 rounded-[8px] border border-line bg-surface/50 px-4 py-2">
          <p className="text-[11.5px] text-muted">分页器</p>
          <Pagination page={page} total={96} size={12} onChange={setPage} />
        </div>
      </Section>

      <Section title="封面" eyebrow="COVER SYSTEM">
        <p className="mb-6 max-w-2xl text-[12.5px] leading-[1.9] text-muted">
          没有真实封面图时，按书名与作者做确定性哈希，从 10 组纸墨色里取一组，再挑三种版式之一。
          同一本书永远得到同一张封面；有 MinIO 真图时自动让位。
        </p>
        <div className="grid grid-cols-2 gap-6 sm:grid-cols-4 lg:grid-cols-5">
          {coverSamples.map((sample) => (
            <div key={sample.variant}>
              <NovelCover novel={sample.novel} />
              <p className="mt-2 text-[11px] text-muted">版式 {sample.variant + 1}</p>
              <p className="text-[10.5px] text-faint">{sample.novel.title}</p>
            </div>
          ))}
          <div className="col-span-1">
            <NovelCover novel={novels[6]} badge="07" />
            <p className="mt-2 text-[11px] text-muted">带序号角标</p>
            <p className="text-[10.5px] text-faint">{novels[6].title}</p>
          </div>
        </div>
      </Section>

      <Section title="状态" eyebrow="STATES">
        <div className="grid gap-6 md:grid-cols-2">
          <div>
            <p className="mb-3 text-[10.5px] tracking-[0.18em] text-faint">骨架屏</p>
            <div className="grid grid-cols-3 gap-4">
              <NovelCardSkeleton />
              <NovelCardSkeleton />
              <NovelCardSkeleton />
            </div>
          </div>
          <div>
            <p className="mb-3 text-[10.5px] tracking-[0.18em] text-faint">空状态</p>
            <EmptyState
              icon={<Search size={24} strokeWidth={1.3} />}
              title="没有找到相关作品"
              description="试试更短的关键词，或去掉筛选条件。"
            />
          </div>
        </div>
        <div className="mt-6">
          <p className="mb-3 text-[10.5px] tracking-[0.18em] text-faint">加载 / 错误提示</p>
          <div className="flex flex-wrap gap-3">
            <div className="rounded-[6px] border border-danger/25 bg-danger-soft px-3 py-2 text-[12px] text-danger">
              搜索服务暂不可用（10601）
            </div>
            <div className="rounded-[6px] border border-accent/25 bg-accent-soft px-3 py-2 text-[12px] text-accent">
              已加入书架
            </div>
            <div className="rounded-[6px] border border-line bg-surface px-3 py-2 text-[12px] text-muted">
              加载中…
            </div>
          </div>
        </div>
      </Section>

      <Section title="阅读主题" eyebrow="READING">
        <div className="grid gap-4 sm:grid-cols-3">
          {[
            { theme: 'paper', bg: '#f5f1e8', fg: '#2a251d', label: '纸白' },
            { theme: 'sepia', bg: '#f0e3c8', fg: '#3a2d1d', label: '米黄' },
            { theme: 'night', bg: '#14151a', fg: '#c6c2ba', label: '夜间' },
          ].map((item) => (
            <div
              key={item.theme}
              className="reader-shell overflow-hidden rounded-[8px] border border-line"
              data-theme={item.theme}
            >
              <div className="px-5 py-6">
                <p className="text-[10px] tracking-[0.2em] opacity-50">第 3 章</p>
                <p className="mt-1 font-serif text-[17px]">雨落长安</p>
                <p className="mt-3 font-serif text-[12.5px] leading-[1.9] opacity-85">
                  长安的雨总在黄昏时落下。先是朱雀大街的尘土被压住，接着是坊墙上的青苔慢慢亮起来。
                </p>
                <p className="mt-3 text-[10px] opacity-40">{item.label} · {item.bg}</p>
              </div>
            </div>
          ))}
        </div>
      </Section>

      <Section title="交互与可访问性" eyebrow="PRINCIPLES">
        <ul className="grid gap-3 text-[12.5px] leading-relaxed text-muted sm:grid-cols-2">
          <li className="flex gap-2">
            <Sparkles size={14} className="mt-0.5 shrink-0 text-accent" />
            动效只用于状态变化，时长 150–300ms，遵循 prefers-reduced-motion。
          </li>
          <li className="flex gap-2">
            <Sparkles size={14} className="mt-0.5 shrink-0 text-accent" />
            焦点环统一为 2px 强调色，键盘可以完成找书、翻页与设置。
          </li>
          <li className="flex gap-2">
            <BookOpen size={14} className="mt-0.5 shrink-0 text-accent" />
            正文行宽控制在 42rem（约 28 个汉字），行高 1.95，首行缩进两字。
          </li>
          <li className="flex gap-2">
            <Sparkles size={14} className="mt-0.5 shrink-0 text-accent" />
            所有图标仅为装饰时标记 aria-hidden，可点击的图标按钮必须有 aria-label。
          </li>
        </ul>
      </Section>

      <div className="mt-16 border-t border-line pt-8">
        <p className="text-[11.5px] text-muted">
          回到{' '}
          <Link to="/" className="text-accent hover:text-accent-deep">
            首页
          </Link>{' '}
          或{' '}
          <Link to="/browse" className="text-accent hover:text-accent-deep">
            书库
          </Link>
          看看真实页面。
        </p>
      </div>
    </div>
  );
}

function Section({
  title,
  eyebrow,
  children,
}: {
  title: string;
  eyebrow: string;
  children: React.ReactNode;
}) {
  return (
    <section className="mt-16 border-t border-line/70 pt-10">
      <p className="text-[10.5px] tracking-[0.26em] text-accent">{eyebrow}</p>
      <h2 className="mt-2 font-serif text-[24px] text-ink">{title}</h2>
      <div className="mt-6">{children}</div>
    </section>
  );
}
