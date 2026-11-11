import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '@/features/auth/AuthProvider';
import { CategoryProvider } from '@/features/categories/CategoryProvider';
import { ToastProvider } from '@/components/ui/Toast';
import { SiteLayout } from '@/components/layout/SiteLayout';
import { HomePage } from '@/pages/HomePage';
import { BrowsePage } from '@/pages/BrowsePage';
import { SearchPage } from '@/pages/SearchPage';
import { RankPage } from '@/pages/RankPage';
import { BookshelfPage } from '@/pages/BookshelfPage';
import { ProfilePage } from '@/pages/ProfilePage';
import { CommunityPage } from '@/pages/CommunityPage';
import { PostDetailPage } from '@/pages/PostDetailPage';
import { PostCreatePage } from '@/pages/PostCreatePage';
import { UserProfilePage } from '@/pages/UserProfilePage';
import { NovelDetailPage } from '@/pages/NovelDetailPage';
import { CatalogPage } from '@/pages/CatalogPage';
import { ReaderPage } from '@/pages/ReaderPage';
import { LoginPage } from '@/pages/LoginPage';
import { StyleguidePage } from '@/pages/StyleguidePage';
import { NotFoundPage } from '@/pages/NotFoundPage';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
      // 详情接口有阅读数副作用；全站统一关掉窗口聚焦重取，避免"切回来就多个阅读"
      refetchOnWindowFocus: false,
    },
  },
});

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <CategoryProvider>
            <ToastProvider>
              <Routes>
                <Route element={<SiteLayout />}>
                  <Route index element={<HomePage />} />
                  <Route path="browse" element={<BrowsePage />} />
                  <Route path="search" element={<SearchPage />} />
                  <Route path="rank" element={<RankPage />} />
                  <Route path="bookshelf" element={<BookshelfPage />} />
                  <Route path="me" element={<ProfilePage />} />
                  <Route path="community" element={<CommunityPage />} />
                  <Route path="posts/new" element={<PostCreatePage />} />
                  <Route path="posts/:id" element={<PostDetailPage />} />
                  <Route path="users/:id" element={<UserProfilePage />} />
                  <Route path="novel/:id" element={<NovelDetailPage />} />
                  <Route path="novel/:id/chapters" element={<CatalogPage />} />
                  <Route path="styleguide" element={<StyleguidePage />} />
                  <Route path="*" element={<NotFoundPage />} />
                </Route>

                {/* 阅读器与登录页不套站点壳：一个要沉浸，一个要聚焦 */}
                <Route path="novel/:id/chapters/:seq" element={<ReaderPage />} />
                <Route path="login" element={<LoginPage />} />
              </Routes>
            </ToastProvider>
          </CategoryProvider>
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
