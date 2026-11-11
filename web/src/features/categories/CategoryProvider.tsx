import { createContext, useContext, useMemo, type ReactNode } from 'react';
import type { Category } from '@/api/types';
import { useCategories } from '@/api/queries';

interface CategoryContextValue {
  categories: Category[];
  topCategories: Category[];
  nameOf: (id?: number | null) => string;
  topOf: (id?: number | null) => Category | undefined;
  childrenOf: (id: number) => Category[];
  isLoading: boolean;
  isError: boolean;
}

const CategoryContext = createContext<CategoryContextValue | null>(null);

/**
 * 分类字典。
 *
 * <p>后端只返回扁平数组，树由前端拼；分类是读多写零的数据，缓存 10 分钟。
 * 封面的题材标签、卡片的分类名、面包屑都从这里取，避免每个组件各查一次。
 */
export function CategoryProvider({ children }: { children: ReactNode }) {
  const { data, isLoading, isError } = useCategories();

  const value = useMemo<CategoryContextValue>(() => {
    const categories = data ?? [];
    const byId = new Map(categories.map((category) => [category.id, category]));
    const childrenOf = (id: number) =>
      categories
        .filter((category) => category.parentId === id)
        .sort((a, b) => a.sortNo - b.sortNo);

    return {
      categories,
      topCategories: childrenOf(0),
      nameOf: (id) => {
        if (id == null) return '未分类';
        return byId.get(id)?.name ?? '未分类';
      },
      topOf: (id) => {
        if (id == null) return undefined;
        const category = byId.get(id);
        if (!category) return undefined;
        return category.parentId === 0 ? category : byId.get(category.parentId);
      },
      childrenOf,
      isLoading,
      isError,
    };
  }, [data, isLoading, isError]);

  return <CategoryContext.Provider value={value}>{children}</CategoryContext.Provider>;
}

export function useCategoriesContext(): CategoryContextValue {
  const context = useContext(CategoryContext);
  if (!context) {
    throw new Error('useCategoriesContext 必须在 CategoryProvider 内使用');
  }
  return context;
}
