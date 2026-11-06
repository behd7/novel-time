import type { Category } from '@/api/types';

/**
 * 与 V2__seed_categories.sql 完全一致的两级分类。
 * 演示阶段写死在前端；接后端后换成 GET /api/v1/categories（10 分钟缓存）。
 */
export const categories: Category[] = [
  { id: 1, name: '玄幻', parentId: 0, sortNo: 1 },
  { id: 2, name: '都市', parentId: 0, sortNo: 2 },
  { id: 3, name: '仙侠', parentId: 0, sortNo: 3 },
  { id: 4, name: '历史', parentId: 0, sortNo: 4 },
  { id: 5, name: '科幻', parentId: 0, sortNo: 5 },
  { id: 6, name: '游戏', parentId: 0, sortNo: 6 },
  { id: 7, name: '悬疑', parentId: 0, sortNo: 7 },
  { id: 8, name: '言情', parentId: 0, sortNo: 8 },
  { id: 101, name: '东方玄幻', parentId: 1, sortNo: 1 },
  { id: 102, name: '异世大陆', parentId: 1, sortNo: 2 },
  { id: 103, name: '王朝争霸', parentId: 1, sortNo: 3 },
  { id: 201, name: '都市异能', parentId: 2, sortNo: 1 },
  { id: 202, name: '都市生活', parentId: 2, sortNo: 2 },
  { id: 203, name: '商战职场', parentId: 2, sortNo: 3 },
  { id: 301, name: '修真文明', parentId: 3, sortNo: 1 },
  { id: 302, name: '神话修真', parentId: 3, sortNo: 2 },
  { id: 401, name: '架空历史', parentId: 4, sortNo: 1 },
  { id: 402, name: '两宋元明', parentId: 4, sortNo: 2 },
  { id: 501, name: '星际文明', parentId: 5, sortNo: 1 },
  { id: 502, name: '末世危机', parentId: 5, sortNo: 2 },
  { id: 601, name: '虚拟网游', parentId: 6, sortNo: 1 },
  { id: 602, name: '电子竞技', parentId: 6, sortNo: 2 },
  { id: 701, name: '推理侦探', parentId: 7, sortNo: 1 },
  { id: 702, name: '诡秘惊悚', parentId: 7, sortNo: 2 },
  { id: 801, name: '现代言情', parentId: 8, sortNo: 1 },
  { id: 802, name: '古代言情', parentId: 8, sortNo: 2 },
];

export const topCategories = categories.filter((item) => item.parentId === 0);

export function childrenOf(parentId: number): Category[] {
  return categories.filter((item) => item.parentId === parentId);
}

const byId = new Map(categories.map((item) => [item.id, item]));

export function categoryName(id: number | null | undefined): string {
  if (id == null) return '未分类';
  return byId.get(id)?.name ?? '未分类';
}

/** 顶级分类 → 自身 + 所有子分类，用于筛选时把大类展开成 id 列表。 */
export function categoryIds(id: number): number[] {
  if (!id) return [];
  return [id, ...childrenOf(id).map((item) => item.id)];
}

export function topCategoryOf(id: number): Category | undefined {
  const category = byId.get(id);
  if (!category) return undefined;
  return category.parentId === 0 ? category : byId.get(category.parentId);
}

/** 首页分类入口的文案：大类 + 三个子类，能在 4×2 的格子里说清"这里有什么"。 */
export const categoryBlurb: Record<number, string> = {
  1: '东方玄幻 · 异世大陆 · 王朝争霸',
  2: '都市异能 · 都市生活 · 商战职场',
  3: '修真文明 · 神话修真 · 剑与远方',
  4: '架空历史 · 两宋元明 · 庙堂江湖',
  5: '星际文明 · 末世危机 · 硬核设定',
  6: '虚拟网游 · 电子竞技 · 热血开黑',
  7: '推理侦探 · 诡秘惊悚 · 深夜勿读',
  8: '现代言情 · 古代言情 · 细水长流',
};
