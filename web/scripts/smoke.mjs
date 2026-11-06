/**
 * 前端冒烟测试。
 *
 * 不起测试框架，只用 Playwright 的浏览器 API：这些断言检查的是「页面能不能用」，
 * 不是组件内部实现，所以没必要为它引入一整套 runner。
 *
 * 运行前：
 *   1. 另一个终端里 pnpm dev
 *   2. 首次运行 pnpm exec playwright install chromium
 *   3. pnpm smoke
 *
 * 环境变量：
 *   BASE_URL  默认 http://127.0.0.1:5173
 *   CHROME_PATH  指定本机 Chromium（可选，CI 里通常不用）
 */
import { chromium } from 'playwright';

const BASE = process.env.BASE_URL ?? 'http://127.0.0.1:5173';
const EXECUTABLE = process.env.CHROME_PATH;

let passed = 0;

function assert(condition, message) {
  if (!condition) throw new Error(`断言失败：${message}`);
  passed += 1;
  console.log(`  ✓ ${message}`);
}

async function main() {
  const health = await fetch(BASE).catch(() => null);
  if (!health?.ok) {
    throw new Error(`开发服务器不可达：${BASE}。请先在 web/ 下运行 pnpm dev`);
  }

  const browser = await chromium.launch({
    executablePath: EXECUTABLE || undefined,
    args: ['--no-sandbox'],
  });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
    locale: 'zh-CN',
  });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', (error) => errors.push(error.message));
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(message.text());
  });

  try {
    console.log('1. 登录流程');
    await page.goto(`${BASE}/login`);
    await page.getByRole('button', { name: '一键填入' }).click();
    assert((await page.getByPlaceholder('bookworm').inputValue()) === 'bookworm', '一键填入演示账号');
    await page.locator('button[type="submit"]').click();
    await page.waitForURL(`${BASE}/`, { timeout: 8000 });
    assert(page.url() === `${BASE}/`, '登录后回到首页');
    await page.getByText('书虫').first().waitFor({ timeout: 5000 });
    assert(await page.getByText('书虫').first().isVisible(), '页头显示昵称');
    assert(await page.getByText('继续阅读').first().isVisible(), '登录后出现继续阅读模块');

    console.log('2. 评分');
    await page.goto(`${BASE}/novel/1`);
    await page.getByRole('button', { name: /你打了 5 星/ }).click();
    await page.getByRole('radio', { name: '4 星' }).click();
    await page.getByText('已评 4 星').waitFor({ timeout: 5000 });
    assert(await page.getByText('你打了 4 星').isVisible(), '评分状态更新为 4 星');

    console.log('3. 书架');
    await page.getByRole('button', { name: '已在书架' }).click();
    await page.getByText('已从书架移除').waitFor({ timeout: 5000 });
    assert(await page.getByRole('button', { name: '加入书架' }).isVisible(), '书架状态可切换');
    await page.getByRole('button', { name: '加入书架' }).click();
    assert(await page.getByRole('button', { name: '已在书架' }).isVisible(), '再次加入书架');

    console.log('4. 阅读器');
    await page.goto(`${BASE}/novel/1/chapters/3`);
    await page.getByRole('button', { name: '阅读设置' }).click();
    await page.getByText('阅读设置').first().waitFor({ timeout: 3000 });
    await page.getByRole('button', { name: '增大字号' }).click();
    await page.getByRole('button', { name: '增大字号' }).click();
    const saved = await page.evaluate(() => localStorage.getItem('novel.reader.settings'));
    assert(JSON.parse(saved).fontSize === 21, '字号持久化到 localStorage');
    await page.getByRole('button', { name: '关闭' }).click();
    await page.keyboard.press('ArrowRight');
    await page.waitForURL(/\/novel\/1\/chapters\/4$/, { timeout: 5000 });
    assert(page.url().endsWith('/chapters/4'), '键盘右方向键翻到下一章');
    await page.waitForFunction(() => localStorage.getItem('novel.reader.progress.1') === '4', null, {
      timeout: 5000,
    });
    assert(true, '阅读进度写回 localStorage');

    console.log('5. 搜索');
    await page.goto(`${BASE}/`);
    const search = page.getByPlaceholder('搜书名、作者、简介').first();
    await search.fill('长安');
    await page.getByRole('option').first().waitFor({ timeout: 3000 });
    assert(await page.getByRole('option').first().isVisible(), '输入时出现搜索建议');
    await search.press('Enter');
    await page.waitForURL(/\/search\?q=/, { timeout: 8000 });
    await page.locator('h1').filter({ hasText: '的搜索结果' }).waitFor({ timeout: 5000 });
    assert(true, '进入搜索结果页');
    await page.getByText('长安旧事').first().waitFor({ timeout: 5000 });
    assert(await page.getByText('长安旧事').first().isVisible(), '命中演示书目');

    console.log('6. 空状态');
    await page.goto(`${BASE}/search?q=不存在的书`);
    await page.getByText('没有找到相关作品').waitFor({ timeout: 5000 });
    assert(await page.getByText('没有找到相关作品').isVisible(), '搜索空态可见');

    console.log('7. 控制台');
    assert(errors.length === 0, `无控制台错误（实际 ${errors.length} 条）`);
    if (errors.length) console.log(errors.slice(0, 5));
  } finally {
    await browser.close();
  }

  console.log(`\n全部 ${passed} 条断言通过`);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
