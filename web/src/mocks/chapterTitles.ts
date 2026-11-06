/**
 * 章节标题生成。
 *
 * 一章一个标题，同一本书内至少隔 32 章才会撞一次；目录一页 30 章，
 * 所以翻一页看不到重复。主推书的头几章有独立标题，优先返回。
 */

import { featuredChapter } from './prose';

type Mood = 'history' | 'suspense' | 'xianxia' | 'romance' | 'scifi' | 'urban';

const POOLS: Record<Mood, string[]> = {
  history: [
    '雨落长安', '西市灯火', '城门初雪', '旧信一封', '长街尽头', '灯下故人',
    '夜半更声', '坊墙春草', '一纸无名', '客从远方', '酒肆微醺', '曲江池畔',
    '雁塔题名', '雪满朱雀', '故人入梦', '烟火长安', '楼上看山', '春风不度',
    '书坊夜话', '江湖庙堂', '一别经年', '城南旧宅', '灯市如昼', '长安未眠',
    '曲江春晓', '胡姬酒肆', '大明宫外', '驿路梅花', '城南访旧', '雨夜叩门',
    '一灯如豆', '故纸堆中',
  ],
  suspense: [
    '无编号车厢', '凌晨两点十七分', '隧道里的灯', '第七位乘客', '空白档案', '回声',
    '雨夜来客', '终点之前', '第二十四张票', '广播之后', '玻璃上的脸', '无人报站',
    '停电十三分钟', '走廊尽头', '旧案重提', '一首童谣', '雾中灯塔', '失踪的第七天',
    '录音带', '门牌号不存在', '对面的窗', '谁在敲门', '最后一页', '天亮以前',
    '末班车', '无人车厢', '监控盲区', '第十三站', '信号中断', '旧车票',
    '凌晨三点', '隧道尽头',
  ],
  xianxia: [
    '山门初雪', '剑冢', '云海之上', '旧誓', '归墟', '一盏灯',
    '风起青萍', '问心', '断剑', '松间月', '入世', '山雨欲来',
    '碑上无名', '雪夜访客', '十年之约', '剑鸣', '海底骨片', '师门旧事',
    '一线天', '长明灯', '下山', '北境风雪', '灯下故人', '人间',
    '山门夜话', '剑冢初雪', '云海孤舟', '旧约', '归墟之门', '长明不灭',
    '问剑', '人间烟火',
  ],
  romance: [
    '槐花巷', '未寄出的信', '春山', '旧书页', '月光', '等一个人',
    '迟到的春天', '茶凉之前', '下雨的傍晚', '十年', '抽屉最底层', '一张车票',
    '他回来了', '边关雪', '护膝', '婚书', '春山如笑', '梅子黄时雨',
    '长夜', '灯花', '一别两宽', '药香', '归期', '细水长流',
    '槐花落时', '旧信重读', '春山可望', '灯下缝衣', '雨夜同伞', '十年一诺',
    '婚书之后', '岁月长',
  ],
  scifi: [
    '柯伊伯带', '黑匣子', '摇篮曲', '深空', '第七年', '归航',
    '信号', '失传的语言', '零号航道', '冰封的船', '坐标', '静默',
    '星图之外', '回声', '父女', '沉没的城市', '最后一个人类', '低温休眠',
    '破晓', '无应答', '引力井', '出发之前', '光年之外', '回家',
    '深空信号', '黑匣低语', '摇篮曲终', '零号坐标', '冰船苏醒', '星图尽头',
    '归航之前', '地球回声',
  ],
  urban: [
    '老巷', '槐花', '煤炉', '夏天', '旧照片', '搬家',
    '路口', '晚风', '搪瓷缸', '收音机', '奖状', '门锁',
    '巷口', '六月', '纸箱', '邻居', '天井', '灯绳',
    '晾衣绳', '蝉鸣', '拆字', '最后一天', '钥匙', '城南',
    '巷口黄昏', '槐树之下', '旧物清单', '炉火将熄', '搬家那日', '巷深不知',
    '蝉声渐远', '城南旧事',
  ],
};

export function moodOf(categoryId: number): Mood {
  if (categoryId >= 400 && categoryId < 500) return 'history';
  if (categoryId >= 700 && categoryId < 800) return 'suspense';
  if (categoryId >= 300 && categoryId < 400) return 'xianxia';
  if (categoryId >= 800 && categoryId < 900) return 'romance';
  if (categoryId >= 500 && categoryId < 600) return 'scifi';
  if (categoryId >= 100 && categoryId < 200) return 'xianxia';
  return 'urban';
}

const titleCache = new Map<number, string[]>();

/**
 * 一整本书的标题表。
 *
 * 逐章往后取，撞到用过的就顺延；32 条池子用完后开启下一轮。
 * 目录一页 30 章，所以翻一页不会看到重复标题；跨页的重复在演示里可以接受。
 * 最后一章强制使用后端给的「最新章节」标题，详情页与目录页必须说的是同一件事。
 */
export function titlesFor(
  novelId: number,
  categoryId: number,
  chapterCount: number,
  latestTitle: string | null,
): string[] {
  const cached = titleCache.get(novelId);
  if (cached) return cached;

  const pool = POOLS[moodOf(categoryId)];
  const result: string[] = [];
  const used = new Set<string>();
  let cursor = (novelId * 5 + 3) % pool.length;

  for (let seq = 1; seq <= chapterCount; seq += 1) {
    const featured = featuredChapter(novelId, seq);
    if (featured) {
      result.push(featured.title);
      used.add(featured.title);
      continue;
    }

    if (seq === chapterCount) {
      result.push(latestTitle ?? pool[cursor % pool.length]);
      break;
    }

    if (used.size >= pool.length) used.clear();

    let title = pool[cursor % pool.length];
    let guard = 0;
    while (used.has(title) && guard < pool.length) {
      cursor += 1;
      title = pool[cursor % pool.length];
      guard += 1;
    }

    used.add(title);
    result.push(title);
    cursor += 1;
  }

  titleCache.set(novelId, result);
  return result;
}

export function chapterTitleFor(
  novelId: number,
  categoryId: number,
  seq: number,
  chapterCount: number,
  latestTitle: string | null,
): string {
  if (seq < 1 || seq > chapterCount) return '';
  return titlesFor(novelId, categoryId, chapterCount, latestTitle)[seq - 1];
}
