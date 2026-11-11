/**
 * 确保演示账号存在。
 *
 * 新库导入演示书之后，bookworm 并不会自动出现；登录页的「一键填入」需要一个真实账号。
 * 这个脚本幂等：账号已存在就登录一次确认密码没变，不存在就注册。
 *
 *   pnpm seed:demo
 *   API_BASE=http://127.0.0.1:8080/api/v1 pnpm seed:demo
 */
const BASE = process.env.API_BASE ?? 'http://127.0.0.1:8080/api/v1';
const DEMO = { username: 'bookworm', password: 'novel123456', nickname: '书虫' };

async function post(path, body) {
  const response = await fetch(`${BASE}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  const payload = await response.json().catch(() => null);
  return { status: response.status, payload };
}

async function main() {
  const registered = await post('/auth/register', DEMO);
  if (registered.payload?.code === 0) {
    console.log(`已创建演示账号 ${DEMO.username} / ${DEMO.password}`);
    return;
  }
  if (registered.payload?.code === 10101) {
    const loggedIn = await post('/auth/login', {
      username: DEMO.username,
      password: DEMO.password,
    });
    if (loggedIn.payload?.code === 0) {
      console.log(`演示账号已存在，密码校验通过：${DEMO.username} / ${DEMO.password}`);
      return;
    }
    throw new Error(
      `账号 ${DEMO.username} 已存在，但密码不是预期的 ${DEMO.password}。` +
        '请换一个用户名，或先在库里改掉该账号的密码。',
    );
  }
  throw new Error(
    `创建演示账号失败（HTTP ${registered.status}）：${registered.payload?.message ?? '无响应'}`,
  );
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
