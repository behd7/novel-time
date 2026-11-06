/**
 * 程序化封面。
 *
 * 演示库里没有封面图（cover_key 为空），但小说站的第一眼就是封面。
 * 这里按「书名 + 作者」做确定性哈希，从 10 组纸墨色里挑一组、再挑 3 种版式之一：
 * 同一本书永远得到同一张封面，刷新不会变；不同书之间又足够有差别。
 * 真接入 MinIO 之后，有 coverKey 的书优先用真图，这个组件退化成兜底。
 */

export type CoverPalette = {
  id: string;
  from: string;
  to: string;
  ink: string;
  /** 详情页背景的极浅染色，用来让页面跟着封面走 */
  wash: string;
};

const PALETTES: CoverPalette[] = [
  { id: 'pine', from: '#2a4d43', to: '#152b26', ink: '#e9e4d6', wash: '#e9eeea' },
  { id: 'oxblood', from: '#5b2c28', to: '#311514', ink: '#efe1d5', wash: '#f1e7e4' },
  { id: 'midnight', from: '#273650', to: '#131b2b', ink: '#e2e6ec', wash: '#e8ebf1' },
  { id: 'ochre', from: '#8a6534', to: '#4d371a', ink: '#f3e7d0', wash: '#f2ebdc' },
  { id: 'plum', from: '#4b2f45', to: '#271724', ink: '#ebe0e7', wash: '#efe7ed' },
  { id: 'rust', from: '#7b3b2a', to: '#401c13', ink: '#f1dfd2', wash: '#f3e8e3' },
  { id: 'slate', from: '#343a3c', to: '#191d1f', ink: '#dee3e2', wash: '#e9ebeb' },
  { id: 'olive', from: '#4e4b2d', to: '#292717', ink: '#eae6d3', wash: '#eeecdf' },
  { id: 'indigo', from: '#303b5d', to: '#181e32', ink: '#e0e2ec', wash: '#e8eaf1' },
  { id: 'teal', from: '#204b4f', to: '#0e292c', ink: '#dfeae9', wash: '#e4eeed' },
];

/** FNV-1a：短字符串上分布够均匀，也不用引第三方依赖。 */
function hashString(input: string): number {
  let hash = 2166136261;
  for (let i = 0; i < input.length; i += 1) {
    hash ^= input.charCodeAt(i);
    hash = Math.imul(hash, 16777619);
  }
  return hash >>> 0;
}

export type CoverDesign = {
  palette: CoverPalette;
  variant: 0 | 1 | 2;
  seal: string;
  /** 详情页背景的染色，直接用内联 style */
  wash: string;
};

export function designCover(title: string, author: string): CoverDesign {
  const hash = hashString(`${title}|${author}`);
  const palette = PALETTES[hash % PALETTES.length];
  const variant = (((hash >>> 7) % 3) as 0 | 1 | 2);
  return {
    palette,
    variant,
    seal: title.slice(0, 1) || '书',
    wash: palette.wash,
  };
}

/** 头像兜底：昵称首字 + 固定色，避免出现一排灰色默认头像。 */
const AVATAR_COLORS = ['#2e594e', '#7b5a34', '#4b3f63', '#6b3a34', '#33505f', '#5a4a2e'];

export function avatarColor(name: string): string {
  return AVATAR_COLORS[hashString(name) % AVATAR_COLORS.length];
}
