// 读链路压测脚本（k6）
//
// 用法：
//   docker run --rm -i --network host grafana/k6:0.54.0 run - < deploy/loadtest/read-path.js
//   docker run --rm -i --network host -e BASE_URL=http://127.0.0.1:8080 -e VUS=50 \
//     grafana/k6:0.54.0 run - < deploy/loadtest/read-path.js
//
// 为什么按 40/30/25/5 的比例混合，而不是只压一个接口：
// 单接口压测只能得到"这个接口的上限"，得不到"这套读写混合下的表现"。
// 详情接口每次访问都会让阅读数 +1（走 Redis Hash），把它混进读链路里，
// 才能看到"最热的读接口同时也在写"时的真实排队情况。
//
// 脚本本身不做断言式校验（那是 smoke 的活），只保证请求形态真实、分布稳定。

import http from 'k6/http';
import { check } from 'k6';
import { Rate } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const VUS = Number(__ENV.VUS || 30);
const DURATION = __ENV.DURATION || '60s';

// 只看 2xx/3xx：业务码由响应体的 code 字段表达，HTTP 层永远是 200，
// 所以这里的失败率反映的是连接超时、502、被限流这类"真的没拿到响应"
const failed = new Rate('novel_request_failed');

export const options = {
  scenarios: {
    read_path: {
      executor: 'ramping-vus',
      startVUs: Math.max(1, Math.floor(VUS / 3)),
      stages: [
        { duration: '10s', target: VUS },
        { duration: DURATION, target: VUS },
        { duration: '5s', target: 0 },
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    // 读接口全部是免登录的公开接口，正常情况不该出现失败；
    // 超过 1% 说明后端已经在超时或拒绝连接
    novel_request_failed: ['rate<0.01'],
  },
};

// 开跑前先取一批真实存在的书 id：写死 id 的书可能不在库里，
// 那测出来的就是 404 的速度，而不是读链路的速度
export function setup() {
  const res = http.get(`${BASE}/api/v1/novels?page=1&size=20`);
  const body = res.json();
  const list = body && body.data && body.data.list ? body.data.list : [];
  const ids = list.map((novel) => novel.id);
  return { ids: ids.length > 0 ? ids : [1] };
}

export default function (data) {
  const id = data.ids[Math.floor(Math.random() * data.ids.length)];
  const roll = Math.random();

  if (roll < 0.4) {
    listNovels();
  } else if (roll < 0.7) {
    novelDetail(id);
  } else if (roll < 0.95) {
    chapter(id);
  } else {
    categories();
  }
}

function listNovels() {
  const page = 1 + Math.floor(Math.random() * 5);
  const res = http.get(`${BASE}/api/v1/novels?page=${page}&size=20&sort=latest`, {
    tags: { kind: 'list' },
  });
  failed.add(res.status >= 400);
  check(res, { 'list 200': (r) => r.status === 200 });
}

function novelDetail(id) {
  // 详情接口有副作用：每次访问阅读数 +1，点击数就是写入量
  const res = http.get(`${BASE}/api/v1/novels/${id}`, { tags: { kind: 'detail' } });
  failed.add(res.status >= 400);
  check(res, { 'detail 200': (r) => r.status === 200 });
}

function chapter(id) {
  // 正文按 chapterId 缓存 2h：并发越高越容易撞上"同时未命中"的回源窗口，
  // 命中率曲线就是缓存设计有没有生效的直接证据
  const seq = 1 + Math.floor(Math.random() * 8);
  const res = http.get(`${BASE}/api/v1/novels/${id}/chapters/${seq}`, { tags: { kind: 'chapter' } });
  failed.add(res.status >= 400);
  check(res, { 'chapter 200': (r) => r.status === 200 });
}

function categories() {
  const res = http.get(`${BASE}/api/v1/categories`, { tags: { kind: 'categories' } });
  failed.add(res.status >= 400);
  check(res, { 'categories 200': (r) => r.status === 200 });
}
