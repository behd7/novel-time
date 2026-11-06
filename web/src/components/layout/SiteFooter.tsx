import { Link } from 'react-router-dom';

export function SiteFooter() {
  return (
    <footer className="mt-24 border-t border-line">
      <div className="mx-auto max-w-[1240px] px-6 py-14 lg:px-10">
        <div className="grid gap-10 md:grid-cols-[1.6fr_1fr_1fr]">
          <div>
            <Link to="/" className="inline-flex items-baseline gap-2">
              <span className="font-serif text-[22px] font-semibold tracking-[0.14em] text-ink">
                夜读
              </span>
              <span className="text-[10px] tracking-[0.3em] text-faint">NOVEL</span>
            </Link>
            <p className="mt-4 max-w-xs text-[13px] leading-relaxed text-muted">
              没有信息流，没有标题党。只有书，和你愿意花掉的那个晚上。
            </p>
            <p className="mt-6 text-[11.5px] leading-relaxed text-faint">
              演示项目，书目、作者与书评均为虚构，仅用于展示阅读体验与前后端协作方式。
            </p>
          </div>

          <div>
            <p className="text-[10.5px] tracking-[0.24em] text-faint">浏览</p>
            <ul className="mt-4 space-y-2.5 text-[13px] text-muted">
              <li>
                <Link className="transition-colors hover:text-ink" to="/browse">
                  全部书库
                </Link>
              </li>
              <li>
                <Link className="transition-colors hover:text-ink" to="/rank">
                  热门榜单
                </Link>
              </li>
              <li>
                <Link className="transition-colors hover:text-ink" to="/search">
                  搜索
                </Link>
              </li>
              <li>
                <Link className="transition-colors hover:text-ink" to="/login">
                  登录 / 注册
                </Link>
              </li>
            </ul>
          </div>

          <div>
            <p className="text-[10.5px] tracking-[0.24em] text-faint">关于</p>
            <ul className="mt-4 space-y-2.5 text-[13px] text-muted">
              <li>
                <Link className="transition-colors hover:text-ink" to="/styleguide">
                  设计系统
                </Link>
              </li>
              <li>
                <a className="transition-colors hover:text-ink" href="http://127.0.0.1:8080/swagger-ui.html" target="_blank" rel="noreferrer">
                  后端 API 文档
                </a>
              </li>
              <li>
                <a className="transition-colors hover:text-ink" href="http://127.0.0.1:8080/actuator/health" target="_blank" rel="noreferrer">
                  服务健康检查
                </a>
              </li>
            </ul>
          </div>
        </div>

        <div className="mt-12 flex flex-col gap-2 border-t border-line pt-6 text-[11.5px] text-faint md:flex-row md:items-center md:justify-between">
          <p>© 2026 夜读 · 一个前端练习作品</p>
          <p className="tracking-[0.04em]">
            React 19 · TypeScript · Tailwind 4 · Spring Boot 3 · MySQL · Redis · RabbitMQ ·
            Elasticsearch · MinIO
          </p>
        </div>
      </div>
    </footer>
  );
}
