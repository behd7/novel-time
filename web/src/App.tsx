import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '@/features/auth/AuthProvider';
import { ToastProvider } from '@/components/ui/Toast';
import { SiteLayout } from '@/components/layout/SiteLayout';
import { HomePage } from '@/pages/HomePage';
import { BrowsePage } from '@/pages/BrowsePage';
import { SearchPage } from '@/pages/SearchPage';
import { RankPage } from '@/pages/RankPage';
import { NovelDetailPage } from '@/pages/NovelDetailPage';
import { CatalogPage } from '@/pages/CatalogPage';
import { ReaderPage } from '@/pages/ReaderPage';
import { LoginPage } from '@/pages/LoginPage';
import { StyleguidePage } from '@/pages/StyleguidePage';
import { NotFoundPage } from '@/pages/NotFoundPage';

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <ToastProvider>
          <Routes>
            <Route element={<SiteLayout />}>
              <Route index element={<HomePage />} />
              <Route path="browse" element={<BrowsePage />} />
              <Route path="search" element={<SearchPage />} />
              <Route path="rank" element={<RankPage />} />
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
      </AuthProvider>
    </BrowserRouter>
  );
}
