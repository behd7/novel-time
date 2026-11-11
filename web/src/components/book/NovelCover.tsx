import { designCover, type CoverDesign } from '@/lib/cover';
import { useCategoriesContext } from '@/features/categories/CategoryProvider';
import { cn } from '@/lib/cn';

const ASSET_BASE = import.meta.env.VITE_ASSET_BASE ?? 'http://127.0.0.1:9000/novel';

export interface CoverNovel {
  title: string;
  authorName: string;
  coverKey?: string | null;
  categoryId: number;
}

interface NovelCoverProps {
  novel: CoverNovel;
  className?: string;
  /** 封面上叠加序号（榜单、书架里的角标） */
  badge?: string | number;
}

/**
 * 书封。
 *
 * 有 coverKey 就用 MinIO 的真图；没有就按「书名 + 作者」生成一张确定性的封面。
 * 三种版式（居中 / 顶对齐 / 竖排）由哈希决定，让书库网格看起来像被编辑排过版，
 * 而不是十张一模一样的色块。
 */
export function NovelCover({ novel, className, badge }: NovelCoverProps) {
  const { topOf } = useCategoriesContext();
  const design = designCover(novel.title, novel.authorName);
  const src = novel.coverKey ? `${ASSET_BASE}/${novel.coverKey}` : null;
  const topCategory = topOf(novel.categoryId)?.name ?? '未分类';

  return (
    <div
      className={cn(
        '@container relative aspect-[3/4] w-full select-none overflow-hidden rounded-[3px] shadow-cover',
        className,
      )}
      style={{
        background: `linear-gradient(152deg, ${design.palette.from} 0%, ${design.palette.to} 100%)`,
      }}
      role="img"
      aria-label={`《${novel.title}》封面，${novel.authorName} 著`}
    >
      {src ? (
        <img
          src={src}
          alt=""
          loading="lazy"
          className="h-full w-full object-cover"
          onError={(event) => {
            event.currentTarget.style.display = 'none';
          }}
        />
      ) : (
        <ProceduralCover novel={novel} design={design} topCategory={topCategory} />
      )}

      {/* 书脊与整体光影：两笔就能让平面色块有"厚度" */}
      <span className="pointer-events-none absolute inset-y-0 left-0 w-[3cqw] bg-gradient-to-r from-black/30 to-transparent" />
      <span className="pointer-events-none absolute inset-0 bg-gradient-to-br from-white/[0.08] via-transparent to-black/25" />

      {badge != null ? (
        <span className="absolute left-2 top-2 rounded-[2px] bg-ink/80 px-1.5 py-0.5 text-[10px] font-medium tracking-wide text-paper backdrop-blur">
          {badge}
        </span>
      ) : null}
    </div>
  );
}

function ProceduralCover({
  novel,
  design,
  topCategory,
}: {
  novel: CoverNovel;
  design: CoverDesign;
  topCategory: string;
}) {
  const { palette, variant, seal } = design;
  const ink = palette.ink;
  const top = topCategory;

  return (
    <div className="absolute inset-0" aria-hidden>
      <span className="grain opacity-40" />
      <span className="absolute inset-[5cqw] border border-white/15" />

      {variant === 0 ? (
        <>
          <span
            className="absolute left-[9cqw] top-[7cqw] text-[3.4cqw] tracking-[0.32em]"
            style={{ color: ink, opacity: 0.6 }}
          >
            {top}
          </span>
          <div className="absolute inset-x-0 top-1/2 -translate-y-1/2 px-[11cqw]">
            <p
              className="text-center font-serif text-[12.5cqw] font-medium leading-[1.34] tracking-[0.1em]"
              style={{ color: ink, textWrap: 'balance' }}
            >
              {novel.title}
            </p>
            <span
              className="mx-auto mt-[4cqw] block h-px w-[18cqw]"
              style={{ backgroundColor: ink, opacity: 0.35 }}
            />
          </div>
        </>
      ) : null}

      {variant === 1 ? (
        <>
          <span
            className="absolute left-[9cqw] top-[7cqw] text-[3.4cqw] tracking-[0.32em]"
            style={{ color: ink, opacity: 0.6 }}
          >
            {top}
          </span>
          <p
            className="absolute inset-x-0 top-[19%] px-[11cqw] font-serif text-[11.5cqw] font-medium leading-[1.32] tracking-[0.1em]"
            style={{ color: ink, textWrap: 'balance' }}
          >
            {novel.title}
          </p>
          <span
            className="absolute left-[11cqw] top-[47%] block h-px w-[22cqw]"
            style={{ backgroundColor: ink, opacity: 0.32 }}
          />
          <span
            className="cover-mark pointer-events-none absolute -bottom-[8cqw] -right-[3cqw] select-none font-serif text-[42cqw] leading-none"
            style={{ color: ink, opacity: 0.05 }}
            aria-hidden
          >
            {seal}
          </span>
        </>
      ) : null}

      {variant === 2 ? (
        <>
          <span
            className="absolute left-[9cqw] top-[7cqw] text-[3.4cqw] tracking-[0.32em]"
            style={{ color: ink, opacity: 0.6 }}
          >
            {top}
          </span>
          <p
            className="absolute right-[13cqw] top-[17cqw] max-h-[62%] font-serif text-[6.8cqw] font-medium leading-[1.5] tracking-[0.24em]"
            style={{ color: ink, writingMode: 'vertical-rl' }}
          >
            {novel.title}
          </p>
          <span
            className="absolute bottom-[18cqw] right-[9.5cqw] top-[17cqw] w-px"
            style={{ backgroundColor: ink, opacity: 0.22 }}
          />
        </>
      ) : null}

      <span
        className="absolute bottom-[7cqw] left-[9cqw] text-[4cqw] tracking-[0.18em]"
        style={{ color: ink, opacity: 0.62 }}
      >
        {novel.authorName} 著
      </span>

      {variant === 1 ? (
        <span
          className="absolute right-[7cqw] top-[6.5cqw] flex h-[10cqw] w-[10cqw] rotate-2 items-center justify-center border font-serif text-[5.2cqw]"
          style={{ borderColor: ink, color: ink, opacity: 0.45 }}
          aria-hidden
        >
          {seal}
        </span>
      ) : variant === 0 ? (
        <span
          className="absolute bottom-[6.5cqw] right-[7cqw] flex h-[10cqw] w-[10cqw] rotate-2 items-center justify-center border font-serif text-[5.2cqw]"
          style={{ borderColor: ink, color: ink, opacity: 0.55 }}
          aria-hidden
        >
          {seal}
        </span>
      ) : null}
    </div>
  );
}
