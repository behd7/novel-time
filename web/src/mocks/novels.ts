import type { NovelDetail, NovelSummary } from '@/api/types';

/**
 * 演示用书目。
 *
 * 全部为虚构书名与笔名，避免把真实作品和版权素材带进演示环境。
 * 数据形状与 NovelDetailView 一致：详情页能直接渲染「继续阅读」，列表页用 toSummary 投影。
 */
export const novels: NovelDetail[] = [
  {
    id: 1,
    title: '长安旧事',
    authorName: '沈砚',
    coverKey: null,
    categoryId: 401,
    status: 1,
    intro:
      '天宝年间，长安西市一间旧书坊在一场大火后只剩半面墙。掌柜的孙子在废墟里捡到一册没有封皮的手稿，上面记着三十七个名字，每一个都曾在这座城里活过。他决定把这本书补完——哪怕要用掉自己的一生。',
    wordCount: 1_286_000,
    chapterCount: 128,
    avgRating: 4.31,
    ratingCount: 3842,
    viewCount: 126_000,
    favoriteCount: 23_410,
    latestChapterSeq: 128,
    latestChapterTitle: '长街尽头',
    latestUpdateAt: '2026-11-05T22:10:00',
  },
  {
    id: 2,
    title: '夜行列车',
    authorName: '林晚',
    coverKey: null,
    categoryId: 702,
    status: 0,
    intro:
      '凌晨两点十七分，开往北方的列车上总会出现一节没有编号的车厢。乘务员说那是临时加挂，可它在时刻表上从不存在。直到有一天，一个提着旧皮箱的姑娘走了进去，再没有下来。',
    wordCount: 423_000,
    chapterCount: 42,
    avgRating: 4.15,
    ratingCount: 1206,
    viewCount: 88_000,
    favoriteCount: 12_300,
    latestChapterSeq: 42,
    latestChapterTitle: '终点之前',
    latestUpdateAt: '2026-11-06T00:40:00',
  },
  {
    id: 3,
    title: '青云志',
    authorName: '江照',
    coverKey: null,
    categoryId: 301,
    status: 0,
    intro:
      '他十二岁上山，师父说青云之上没有神，只有不肯下山的人。三十年后他站在山门前，雪落满肩，终于明白这句话的意思——所谓修仙，不过是把人间的事一件一件放下，再一件一件捡起来。',
    wordCount: 968_000,
    chapterCount: 96,
    avgRating: 4.32,
    ratingCount: 2431,
    viewCount: 154_000,
    favoriteCount: 19_800,
    latestChapterSeq: 96,
    latestChapterTitle: '问心',
    latestUpdateAt: '2026-11-05T20:15:00',
  },
  {
    id: 4,
    title: '山月不知心底事',
    authorName: '苏枕书',
    coverKey: null,
    categoryId: 801,
    status: 1,
    intro:
      '她在城南开了十年书店，等一个说会回来的人。槐花开了又落，书架上的书换过三批，只有抽屉最底层那封没寄出的信一直没动。直到某个下雨的傍晚，推门进来的人抖了抖伞上的水，说：我找一本书。',
    wordCount: 612_000,
    chapterCount: 61,
    avgRating: 4.37,
    ratingCount: 2879,
    viewCount: 97_000,
    favoriteCount: 21_200,
    latestChapterSeq: 61,
    latestChapterTitle: '迟到的春天',
    latestUpdateAt: '2026-10-29T18:00:00',
  },
  {
    id: 5,
    title: '星海拾遗',
    authorName: '陆沉',
    coverKey: null,
    categoryId: 501,
    status: 0,
    intro:
      '人类离开地球的第三百一十年，拾荒船在柯伊伯带捡到一只还在播放摇篮曲的黑匣子。它没有编号，语言早已失传，可船上每一个人都听懂了。船长说把它扔回去，没有人动手。',
    wordCount: 735_000,
    chapterCount: 73,
    avgRating: 4.12,
    ratingCount: 964,
    viewCount: 71_000,
    favoriteCount: 9_400,
    latestChapterSeq: 73,
    latestChapterTitle: '归航',
    latestUpdateAt: '2026-11-04T23:20:00',
  },
  {
    id: 6,
    title: '长夜将尽',
    authorName: '顾青',
    coverKey: null,
    categoryId: 701,
    status: 1,
    intro:
      '十年前的连环案在一场暴雨后重新出现，作案手法分毫不差。唯一的目击者却已经失忆，只记得一首没人听过的童谣。刑警队长翻开卷宗的第一页，发现自己的名字被写在嫌疑人一栏。',
    wordCount: 557_000,
    chapterCount: 55,
    avgRating: 4.45,
    ratingCount: 4120,
    viewCount: 143_000,
    favoriteCount: 26_800,
    latestChapterSeq: 55,
    latestChapterTitle: '天亮以前',
    latestUpdateAt: '2026-11-02T21:05:00',
  },
  {
    id: 7,
    title: '江湖夜雨十年灯',
    authorName: '温酒',
    coverKey: null,
    categoryId: 101,
    status: 0,
    intro:
      '他提着一盏不灭的灯走过十二座城，只为找到当年那个替他挡刀的人。有人说那人早已死了，有人说在北方见过他。灯芯烧了十年，火苗始终没有矮下去——欠着的命，得亲手还。',
    wordCount: 1_104_000,
    chapterCount: 110,
    avgRating: 4.14,
    ratingCount: 1873,
    viewCount: 132_000,
    favoriteCount: 17_600,
    latestChapterSeq: 110,
    latestChapterTitle: '灯下故人',
    latestUpdateAt: '2026-11-05T19:40:00',
  },
  {
    id: 8,
    title: '我在大唐开客栈',
    authorName: '白鹿',
    coverKey: null,
    categoryId: 401,
    status: 0,
    intro:
      '一觉醒来，他成了长安西市一间破客栈的掌柜。账上没钱，伙计跑光，只剩一口漏风的灶。他决定先把招牌擦干净——反正来都来了，总得让人知道这里能吃饭。',
    wordCount: 389_000,
    chapterCount: 38,
    avgRating: 3.98,
    ratingCount: 742,
    viewCount: 58_000,
    favoriteCount: 7_900,
    latestChapterSeq: 38,
    latestChapterTitle: '西市灯火',
    latestUpdateAt: '2026-11-06T08:20:00',
  },
  {
    id: 9,
    title: '归墟',
    authorName: '沈砚',
    coverKey: null,
    categoryId: 302,
    status: 0,
    intro:
      '归墟之下没有海，只有无数被时间遗忘的名字。少年在海底捡到一枚刻着自己名字的骨片，可他从未来过这里。为了弄清这件事，他拜入一个只剩三名弟子的门派，学的第一课是：不要相信自己的记忆。',
    wordCount: 841_000,
    chapterCount: 84,
    avgRating: 4.38,
    ratingCount: 3012,
    viewCount: 118_000,
    favoriteCount: 20_400,
    latestChapterSeq: 84,
    latestChapterTitle: '旧誓',
    latestUpdateAt: '2026-11-05T23:50:00',
  },
  {
    id: 10,
    title: '城南旧巷',
    authorName: '陈拾',
    coverKey: null,
    categoryId: 202,
    status: 1,
    intro:
      '老巷要拆的那年夏天，他又闻到了槐花和煤炉的味道。母亲留下的搪瓷缸、邻居家的收音机、墙上褪色的奖状——他一件一件收进纸箱，才发现自己从来没真正离开过这里。',
    wordCount: 468_000,
    chapterCount: 46,
    avgRating: 4.24,
    ratingCount: 1564,
    viewCount: 76_000,
    favoriteCount: 13_800,
    latestChapterSeq: 46,
    latestChapterTitle: '晚风',
    latestUpdateAt: '2026-10-25T16:30:00',
  },
  {
    id: 11,
    title: '深空回响',
    authorName: '程野',
    coverKey: null,
    categoryId: 502,
    status: 0,
    intro:
      '末世第七年，无线电里第一次传来了不是人类的声音。它每天重复同一段坐标，指向一座早已沉没的城市。幸存者分成两派：一派说那是救援，一派说那是诱饵。而信号里夹着的，是他女儿的名字。',
    wordCount: 672_000,
    chapterCount: 67,
    avgRating: 4.11,
    ratingCount: 1098,
    viewCount: 69_000,
    favoriteCount: 8_600,
    latestChapterSeq: 67,
    latestChapterTitle: '第七年',
    latestUpdateAt: '2026-11-06T01:10:00',
  },
  {
    id: 12,
    title: '春山如笑',
    authorName: '苏枕书',
    coverKey: null,
    categoryId: 802,
    status: 1,
    intro:
      '她是被退婚的侯府嫡女，他是驻守边关的年轻将军。一纸婚书把两个陌生人绑在一起，谁也没打算先低头。可边关的雪落下来时，她还是把亲手缝的护膝塞进了他的行囊。',
    wordCount: 524_000,
    chapterCount: 52,
    avgRating: 4.31,
    ratingCount: 2103,
    viewCount: 81_000,
    favoriteCount: 15_700,
    latestChapterSeq: 52,
    latestChapterTitle: '春山',
    latestUpdateAt: '2026-10-30T20:45:00',
  },
  {
    id: 13,
    title: '雾都档案',
    authorName: '顾青',
    coverKey: null,
    categoryId: 701,
    status: 0,
    intro:
      '雾都的档案馆里，每一份失踪案卷的最后一页都是空白。新来的档案员发现，空白页在潮湿的夜里会浮出字迹。她抄下第一个名字的当晚，那个人就出现在了她的门口。',
    wordCount: 316_000,
    chapterCount: 31,
    avgRating: 4.07,
    ratingCount: 612,
    viewCount: 44_000,
    favoriteCount: 6_200,
    latestChapterSeq: 31,
    latestChapterTitle: '空白页',
    latestUpdateAt: '2026-11-03T22:30:00',
  },
  {
    id: 14,
    title: '剑来风雪',
    authorName: '温酒',
    coverKey: null,
    categoryId: 101,
    status: 0,
    intro:
      '北境的风雪下了三十年，山上的剑也等了三十年。少年背着师父的断剑下山，一路上遇见的人都说，那把剑的主人欠他们一条命。他这才知道，自己要还的债，比想象中多得多。',
    wordCount: 927_000,
    chapterCount: 92,
    avgRating: 4.21,
    ratingCount: 1764,
    viewCount: 103_000,
    favoriteCount: 14_900,
    latestChapterSeq: 92,
    latestChapterTitle: '下山',
    latestUpdateAt: '2026-11-05T18:25:00',
  },
];

const byId = new Map(novels.map((novel) => [novel.id, novel]));

export function novelById(id: number): NovelDetail | undefined {
  return byId.get(id);
}

/** 列表/榜单场景拿摘要：找不到时直接抛，演示数据里不该出现悬空 id。 */
export function summaryById(id: number): NovelSummary {
  const novel = byId.get(id);
  if (!novel) throw new Error(`演示数据缺少小说 ${id}`);
  return toSummary(novel);
}

/** 详情投影成列表摘要：与后端 NovelSummaryView.from 的职责一致。 */
export function toSummary(novel: NovelDetail): NovelSummary {
  const { latestChapterSeq: _seq, latestChapterTitle: _title, ...summary } = novel;
  return summary;
}

export const summaries: NovelSummary[] = novels.map(toSummary);

export const NOVEL_TAGS: Record<number, string[]> = {
  1: ['群像', '硬核', '催泪'],
  2: ['悬疑', '诡秘', '单女主'],
  3: ['修真', '热血', '群像'],
  4: ['治愈', '都市', '细水长流'],
  5: ['硬核', '星际', '无女主'],
  6: ['推理', '反转', '完结'],
  7: ['热血', '江湖', '爽文'],
  8: ['轻松', '种田', '沙雕'],
  9: ['神话', '悬疑', '群像'],
  10: ['催泪', '现实', '完结'],
  11: ['末世', '硬核', '父女'],
  12: ['古言', '先婚后爱', '完结'],
  13: ['推理', '灵异', '短篇'],
  14: ['热血', '剑修', '复仇'],
};

export function tagsOf(id: number): string[] {
  return NOVEL_TAGS[id] ?? [];
}

/** 首页/空态用的推荐位：编辑挑的六本，顺序写死，不从算法来。 */
export const editorPicks = [1, 6, 4, 9, 5, 10].map((id) => byId.get(id)!);

/** 首页「最近更新」：按更新时间倒序取六本。 */
export const recentlyUpdated = [...novels]
  .sort((a, b) => b.latestUpdateAt.localeCompare(a.latestUpdateAt))
  .slice(0, 6)
  .map(toSummary);
