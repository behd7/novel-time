import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

/** 合并 className：后写的 Tailwind 类覆盖先写的，避免组件默认样式和调用方样式打架。 */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
