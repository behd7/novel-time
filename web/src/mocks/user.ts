import type { BookshelfItem, User } from '@/api/types';

/** 演示账号：与登录页提示文案保持一致。 */
export const DEMO_CREDENTIALS = { username: 'bookworm', password: 'novel123456' };

export const demoUser: User = {
  id: 1,
  username: 'bookworm',
  nickname: '书虫',
  avatarKey: null,
  bio: '读到哪算哪。偏爱历史与悬疑，睡前读两章。',
  gender: 1,
  birthday: '1998-06-12',
  createdAt: '2026-08-14T10:20:00',
};

/**
 * 书架条目。
 * lastChapterSeq 是「读到第几章」，totalChapters 是「更新到第几章」，
 * hasUpdate = total > last，前端据此打红点。
 */
export const bookshelf: BookshelfItem[] = [
  {
    novelId: 1,
    title: '长安旧事',
    authorName: '沈砚',
    coverKey: null,
    categoryId: 401,
    totalChapters: 128,
    lastChapterSeq: 37,
    hasUpdate: true,
    lastReadAt: '2026-11-05T23:12:00',
    addedAt: '2026-10-20T21:00:00',
  },
  {
    novelId: 2,
    title: '夜行列车',
    authorName: '林晚',
    coverKey: null,
    categoryId: 702,
    totalChapters: 42,
    lastChapterSeq: 12,
    hasUpdate: true,
    lastReadAt: '2026-11-04T00:41:00',
    addedAt: '2026-11-01T22:30:00',
  },
  {
    novelId: 4,
    title: '山月不知心底事',
    authorName: '苏枕书',
    coverKey: null,
    categoryId: 801,
    totalChapters: 61,
    lastChapterSeq: 61,
    hasUpdate: false,
    lastReadAt: '2026-10-28T20:05:00',
    addedAt: '2026-10-12T19:40:00',
  },
];
