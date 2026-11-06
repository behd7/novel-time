/** 数字与时间的展示格式。全站只从这里取，避免出现"12.3万"和"123,000"并存的页面。 */

const WAN = 10_000;
const YI = 100_000_000;

/** 12400 → 1.2万；124000000 → 1.2亿。小于一万保留原数。 */
export function formatCount(value: number | null | undefined): string {
  if (value == null) return '—';
  if (value >= YI) return `${trim(value / YI)}亿`;
  if (value >= WAN) return `${trim(value / WAN)}万`;
  return String(value);
}

/** 1286000 → 128.6 万字 */
export function formatWordCount(value: number | null | undefined): string {
  if (value == null) return '—';
  if (value >= WAN) return `${trim(value / WAN)} 万字`;
  return `${value} 字`;
}

function trim(value: number): string {
  return value.toFixed(1).replace(/\.0$/, '');
}

export function formatRating(value: number | null | undefined): string {
  if (value == null || value === 0) return '暂无评分';
  return value.toFixed(1);
}

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

/** 最近一周用相对时间，再往前用日期：榜单与新书列表看的是"新不新"，不是精确时间点。 */
export function formatRelativeTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  const time = new Date(iso).getTime();
  if (Number.isNaN(time)) return '—';
  const diff = Date.now() - time;
  if (diff < MINUTE) return '刚刚';
  if (diff < HOUR) return `${Math.floor(diff / MINUTE)} 分钟前`;
  if (diff < DAY) return `${Math.floor(diff / HOUR)} 小时前`;
  if (diff < 7 * DAY) return `${Math.floor(diff / DAY)} 天前`;
  return formatDate(iso);
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '—';
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function pad(value: number): string {
  return value < 10 ? `0${value}` : String(value);
}

export function formatNumber(value: number | null | undefined): string {
  if (value == null) return '—';
  return value.toLocaleString('zh-CN');
}

/** 阅读进度百分比，封顶 99：最后一章也要留一点"还没读完"的余地。 */
export function readingPercent(seq: number, total: number): number {
  if (!total || total <= 0) return 0;
  return Math.min(99, Math.round((seq / total) * 100));
}

export function chapterLabel(seq: number): string {
  return `第 ${seq} 章`;
}
