import type { RankItem } from '@/api/types';
import { novelById, toSummary } from './novels';

export type RankPeriod = 'daily' | 'weekly' | 'monthly' | 'all';

export const RANK_TABS: { value: RankPeriod; label: string; hint: string }[] = [
  { value: 'daily', label: '日榜', hint: '过去 24 小时' },
  { value: 'weekly', label: '周榜', hint: '本周（ISO 周）' },
  { value: 'monthly', label: '月榜', hint: '本月' },
  { value: 'all', label: '总榜', hint: '累计热度' },
];

/** 各周期的排序：名次是演示数据，接后端后整块替换成 GET /api/v1/ranks。 */
const ORDERS: Record<RankPeriod, number[]> = {
  daily: [2, 6, 1, 11, 9, 3, 7, 4, 14, 5],
  weekly: [6, 1, 9, 3, 2, 10, 7, 4, 12, 11],
  monthly: [1, 6, 3, 9, 4, 7, 10, 2, 12, 5],
  all: [1, 6, 3, 4, 9, 7, 10, 12, 2, 5],
};

/** 榜首分数：日榜接近千级，总榜到万级，让"分数只在同一周期可比"这件事从页面就能看出来。 */
const TOP_SCORE: Record<RankPeriod, number> = {
  daily: 986.4,
  weekly: 4820.7,
  monthly: 12_840.2,
  all: 48_260.9,
};

export function rankItems(period: RankPeriod, limit = 10): RankItem[] {
  const ids = ORDERS[period];
  const top = TOP_SCORE[period];
  return ids.slice(0, limit).map((id, index) => {
    const novel = novelById(id);
    return {
      rankNo: index + 1,
      score: Number((top * Math.pow(0.86, index) + index * 0.7).toFixed(1)),
      novel: toSummary(novel!),
    };
  });
}
