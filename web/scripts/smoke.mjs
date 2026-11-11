/**
 * 前端冒烟测试（真实后端版）。
 *
 * 不起测试框架，只用 Playwright 的浏览器 API：断言的是「页面能不能用」，
 * 不是组件内部实现，所以没必要为它引入一整套 runner。
 *
 * 运行前：
 *   1. 后端在 127.0.0.1:8080，且已导入演示数据
 *   2. 另一个终端里 pnpm dev
 *   3. pnpm smoke
 *
 * 环境变量：
 *   BASE_URL     默认 http://127.0.0.1:5173
 *   CHROME_PATH  指定本机 Chromium（Playwright 版本与已装浏览器不匹配时用）
 */
import { chromium } from 'playwright';

const BASE = process.env.BASE_URL ?? 'http://127.0.0.1:5173';
const EXECUTABLE = process.env.CHROME_PATH;
const DEMO = { username: 'bookworm', password: 'novel123456' };

let passed = 0;

const API = 'http://127.0.0.1:8080/api/v1';

function assert(condition, message) {
  if (!condition) throw new Error(`断言失败：${message}`);
  passed += 1;
  console.log(`  ✓ ${message}`);
}

/**
 * 清理冒烟产生的帖子与书评。
 *
 * UI 里删帖已经覆盖，但书评没有在 UI 里删；失败中断时也可能留下半成品。
 * 直接登录 API 按前缀清掉，保证脚本可以反复跑。
 */
async function cleanup() {
  const login = await fetch(`${API}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'bookworm', password: 'novel123456' }),
  }).then((response) => response.json());
  const token = login?.data?.accessToken;
  if (!token) return;

  const headers = { Authorization: `Bearer ${token}` };
  const posts = await fetch(`${API}/posts?sort=latest&page=1&size=50`, { headers })
    .then((response) => response.json())
    .catch(() => null);
  for (const post of posts?.data?.list ?? []) {
    if (post.title.startsWith('冒烟帖子')) {
      await fetch(`${API}/posts/${post.id}`, { method: 'DELETE', headers }).catch(() => {});
    }
  }

  const reviews = await fetch(`${API}/novels/10/reviews?sort=latest&page=1&size=50`, { headers })
    .then((response) => response.json())
    .catch(() => null);
  for (const review of reviews?.data?.list ?? []) {
    if (review.content.includes('冒烟书评')) {
      await fetch(`${API}/reviews/${review.id}`, { method: 'DELETE', headers }).catch(() => {});
    }
  }
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
  let postUrl = '';
  const errors = [];
  page.on('pageerror', (error) => errors.push(error.message));
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(message.text());
  });

  try {
    console.log('0. 未登录访问书架');
    await page.goto(`${BASE}/bookshelf`);
    await page.getByText('登录后才能使用书架').waitFor({ timeout: 8_000 });
    assert(true, '未登录访问书架显示登录提示');

    console.log('1. 登录与刷新页面');
    await page.goto(`${BASE}/login`);
    await page.getByRole('button', { name: '一键填入' }).click();
    assert(
      (await page.getByPlaceholder('bookworm').inputValue()) === DEMO.username,
      '一键填入演示账号',
    );
    await page.locator('button[type="submit"]').click();
    await page.waitForURL(`${BASE}/`, { timeout: 10_000 });
    await page.getByText('书虫').first().waitFor({ timeout: 8_000 });
    assert(await page.getByText('书虫').first().isVisible(), '登录后页头显示真实昵称');

    await page.reload();
    await page.getByText('书虫').first().waitFor({ timeout: 10_000 });
    assert(await page.getByText('书虫').first().isVisible(), '刷新后靠 refresh 轮换恢复登录态');

    console.log('2. 首页真实数据');
    await page.getByText('本周热读').waitFor({ timeout: 10_000 });
    await page.getByRole('link', { name: /剑来风雪/ }).first().waitFor({ timeout: 8_000 });
    assert(true, '首页榜单读到真实作品');
    await page.getByText('最近更新').waitFor();
    const homeLinks = await page.locator('a[href^="/novel/"]').count();
    assert(homeLinks > 5, `首页出现多本真实书的链接（${homeLinks} 个）`);

    console.log('3. 书库与搜索');
    await page.goto(`${BASE}/browse`);
    await page.waitForFunction(
      () => {
        const node = [...document.querySelectorAll('p')].find((item) =>
          /共 \d+ 本/.test(item.textContent ?? ''),
        );
        return node != null && Number((node.textContent ?? '').match(/\d+/)?.[0] ?? 0) >= 28;
      },
      null,
      { timeout: 10_000 },
    );
    const totalText = await page.getByText(/共 \d+ 本/).first().innerText();
    const total = Number(totalText.match(/\d+/)?.[0] ?? 0);
    assert(total >= 28, `书库总数来自后端（${total} 本，不再是 14 本 mock）`);

    await page.goto(`${BASE}/`);
    const search = page.getByPlaceholder('搜书名、作者、简介').first();
    await search.fill('长安');
    await page.getByRole('option').first().waitFor({ timeout: 6_000 });
    assert(
      (await page.getByRole('option').first().innerText()).includes('长安旧事'),
      '搜索建议来自真实接口',
    );
    await search.press('Enter');
    await page.waitForURL(/\/search\?q=/, { timeout: 10_000 });
    await page.getByText('找到 2 本相关作品').waitFor({ timeout: 10_000 });
    await page.getByText('长安旧事').first().waitFor({ timeout: 8_000 });
    assert(true, '搜索结果数与后端一致（长安 = 2 本）');

    await page.goto(`${BASE}/search?q=zzzznotfound`);
    await page.getByText('没有找到相关作品').waitFor({ timeout: 8_000 });
    assert(true, '搜索空态可见');

    console.log('4. 详情、评分与书架');
    await page.goto(`${BASE}/novel/10`);
    await page.getByRole('heading', { name: '剑来风雪' }).waitFor({ timeout: 10_000 });
    await page.getByText('山门初雪').first().waitFor();
    assert(true, '详情与目录来自真实章节');

    const rateButton = page
      .getByRole('button', { name: /你打了 \d 星|给它打分/ })
      .first();
    await rateButton.click();
    const currentRating = await rateButton.innerText();
    const targetScore = currentRating.includes('5 星') ? 4 : 5;
    await page.getByRole('radio', { name: `${targetScore} 星` }).click();
    await page.getByText(new RegExp(`已评 ${targetScore} 星`)).waitFor({ timeout: 8_000 });
    await page
      .getByRole('button', { name: new RegExp(`你打了 ${targetScore} 星`) })
      .waitFor();
    assert(true, `评分真实落库并回显（${targetScore} 星）`);

    const shelfButton = page.getByRole('button', { name: /已在书架|加入书架/ }).first();
    if ((await shelfButton.innerText()).includes('加入书架')) {
      await shelfButton.click();
      await page.getByText('已加入书架').waitFor({ timeout: 8_000 });
    }
    await page.getByRole('button', { name: '已在书架' }).waitFor();
    assert(true, '加入书架成功');

    console.log('5. 阅读器与进度');
    await page.goto(`${BASE}/novel/10/chapters/1`);
    await page.getByRole('heading', { name: '山门初雪' }).waitFor({ timeout: 10_000 });
    await page.getByRole('button', { name: '阅读设置' }).click();
    await page.getByText('阅读设置').first().waitFor({ timeout: 4_000 });
    await page.getByRole('button', { name: '增大字号' }).click();
    await page.getByRole('button', { name: '增大字号' }).click();
    const saved = await page.evaluate(() => localStorage.getItem('novel.reader.settings'));
    assert(JSON.parse(saved).fontSize === 21, '字号持久化到 localStorage');
    await page.getByRole('button', { name: '关闭' }).click();
    await page.keyboard.press('ArrowRight');
    await page.waitForURL(/\/novel\/10\/chapters\/2$/, { timeout: 8_000 });
    await page.waitForFunction(() => localStorage.getItem('novel.reader.progress.10') === '2', null, {
      timeout: 5_000,
    });
    assert(true, '键盘右方向键翻章并写回本地进度');
    await page.waitForTimeout(800);

    console.log('6. 目录与继续阅读');
    await page.goto(`${BASE}/novel/10/chapters`);
    await page.getByText('剑冢').first().waitFor({ timeout: 8_000 });
    assert(true, '目录页读到真实章节');

    await page.goto(`${BASE}/`);
    await page.getByText('继续阅读').first().waitFor({ timeout: 10_000 });
    await page.getByText('上次停在第 2 章').waitFor({ timeout: 8_000 });
    assert(true, '继续阅读读到服务端书架进度');

    console.log('7. 书架页');
    await page.goto(`${BASE}/bookshelf`);
    await page.getByRole('heading', { name: '我的书架' }).waitFor({ timeout: 10_000 });
    await page.getByText('剑来风雪').first().waitFor({ timeout: 8_000 });
    assert(
      (await page.locator('body').innerText()).includes('读到第 2 章'),
      '书架页显示真实阅读进度',
    );

    await page.goto(`${BASE}/novel/11`);
    const addEleven = page.getByRole('button', { name: /已在书架|加入书架/ }).first();
    if ((await addEleven.innerText()).includes('加入书架')) {
      await addEleven.click();
      // 等按钮状态而不是等 toast：提示条是装饰，状态才是结果
      await page.getByRole('button', { name: '已在书架' }).waitFor({ timeout: 8_000 });
    }
    await page.goto(`${BASE}/bookshelf`);
    await page.getByText('北境无雪').first().waitFor({ timeout: 8_000 });
    assert(true, '书架页出现新加入的书');

    const elevenCard = page.locator('article').filter({ hasText: '北境无雪' });
    await elevenCard.getByRole('button', { name: '移出' }).click();
    await elevenCard.getByRole('button', { name: '确认移出' }).click();
    await page.waitForFunction(() => !document.body.innerText.includes('北境无雪'), null, {
      timeout: 8_000,
    });
    assert(true, '书架可移出书籍');

    console.log('8. 个人主页');
    await page.goto(`${BASE}/me`);
    await page.getByRole('heading', { name: '书虫' }).waitFor({ timeout: 10_000 });
    await page.getByText('@bookworm').first().waitFor({ timeout: 8_000 });
    assert(true, '个人主页显示账号信息');
    await page.getByText('获赞').waitFor({ timeout: 8_000 });
    assert(true, '个人主页显示后端统计');
    await page.getByText('正在读').waitFor({ timeout: 8_000 });
    await page.getByText('剑来风雪').first().waitFor({ timeout: 8_000 });
    assert(true, '个人主页显示正在读的书');

    await page.getByRole('button', { name: '编辑资料' }).click();
    await page.getByLabel('个性签名').fill('读书，写字，偶尔走神。');
    await page.getByLabel('性别').selectOption('1');
    await page.getByLabel('生日').fill('2000-01-01');
    await page.getByRole('button', { name: '保存' }).click();
    await page.getByText('资料已更新').waitFor({ timeout: 8_000 });
    await page.getByText('读书，写字，偶尔走神。').waitFor({ timeout: 8_000 });
    await page.getByText('男').first().waitFor({ timeout: 8_000 });
    await page.getByText('2000-01-01').first().waitFor({ timeout: 8_000 });
    assert(true, '个人资料、性别与生日可编辑并回显');

    console.log('9. 社区发帖、评论与回复');
    await page.goto(`${BASE}/community`);
    await page.getByRole('heading', { name: '社区' }).waitFor({ timeout: 10_000 });
    assert(true, '社区页可打开');
    await page.getByRole('link', { name: '写帖子', exact: true }).click();
    await page.waitForURL(/\/posts\/new$/, { timeout: 8_000 });
    await page.getByPlaceholder('一句话说清你想聊什么').fill('冒烟帖子：剑来风雪');
    await page.getByPlaceholder(/写下你的阅读感受/).fill('这本书的风雪写得很好。');
    await page.getByLabel('选择引用的小说').selectOption('10');
    await page.waitForTimeout(200);
    await page.getByRole('button', { name: '发布帖子' }).click();
    await page.waitForURL(/\/posts\/\d+$/, { timeout: 10_000 });
    await page.getByRole('heading', { name: '冒烟帖子：剑来风雪' }).waitFor({ timeout: 8_000 });
    postUrl = page.url();
    assert(
      (await page.getByText('[[ref:0]]').count()) === 0,
      '帖子引用占位符已替换成书卡',
    );

    await page.getByPlaceholder('写下你的想法…').fill('冒烟评论内容');
    await page.getByRole('button', { name: '发表评论' }).click();
    await page.getByText('冒烟评论内容').waitFor({ timeout: 8_000 });
    assert(true, '帖子评论可发表');

    await page.getByRole('button', { name: '回复' }).first().click();
    const replyForm = page.locator('form').filter({ hasText: '回复 @书虫' }).first();
    await replyForm.getByPlaceholder(/回复 @书虫/).fill('冒烟回复内容');
    await replyForm.getByRole('button', { name: '回复' }).click();
    await page.getByText('冒烟回复内容').waitFor({ timeout: 8_000 });
    assert(true, '楼中楼回复可发表');


    console.log('10. 写书评与回复');
    await page.goto(`${BASE}/novel/10`);
    await page.getByRole('tab', { name: /书评/ }).click();
    await page.getByRole('button', { name: '写书评' }).click();
    await page.getByPlaceholder(/说说这本书哪里好/).fill('冒烟书评：风雪与断剑。');
    await page.getByRole('button', { name: '发布书评' }).click();
    await page.getByText('冒烟书评：风雪与断剑。').waitFor({ timeout: 8_000 });
    assert(true, '书评可发表');

    await page.getByRole('button', { name: /条回复/ }).first().click();
    await page.getByPlaceholder('写一条回复…').fill('冒烟回复书评');
    const reviewReplyForm = page.locator('form').filter({ has: page.getByPlaceholder('写一条回复…') });
    await reviewReplyForm.getByRole('button', { name: '回复' }).click();
    await page.getByText('冒烟回复书评').waitFor({ timeout: 8_000 });
    assert(true, '书评回复可发表');

    console.log('11. 我的书评/帖子列表');
    await page.goto(`${BASE}/me`);
    await page.getByText('我的内容').waitFor({ timeout: 8_000 });
    await page.getByText('冒烟书评：风雪与断剑。').waitFor({ timeout: 8_000 });
    assert(true, '我的书评列表显示刚发布的书评');
    await page.getByRole('tab', { name: /我的帖子/ }).click();
    await page.getByText('冒烟帖子：剑来风雪').waitFor({ timeout: 8_000 });
    assert(true, '我的帖子列表显示刚发布的帖子');

    await page.goto(postUrl);
    await page.getByRole('button', { name: '删除' }).first().click();
    await page.getByRole('button', { name: '确认删除' }).first().click();
    await page.waitForURL(/\/community$/, { timeout: 8_000 });
    await page.waitForFunction(() => !document.body.innerText.includes('冒烟帖子：剑来风雪'), null, {
      timeout: 8_000,
    });
    assert(true, '帖子可删除');

    console.log('12. 他人主页');
    await page.goto(`${BASE}/users/18`);
    await page.getByRole('heading', { name: '联调读者' }).waitFor({ timeout: 8_000 });
    await page.getByText('公开范围').waitFor();
    assert(true, '他人主页显示公开资料与统计');

    console.log('13. 退出登录');
    await page.getByText('书虫').first().click();
    await page.getByRole('menuitem', { name: '退出登录' }).click();
    await page.locator('header').getByText('登录', { exact: true }).waitFor({ timeout: 8_000 });
    assert(true, '退出后页头恢复登录入口');

    console.log('14. 控制台');
    if (errors.length) console.log(errors.slice(0, 8));
    assert(errors.length === 0, `无控制台错误（实际 ${errors.length} 条）`);
  } finally {
    await cleanup().catch(() => {});
    await browser.close();
  }

  console.log(`\n全部 ${passed} 条断言通过`);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
