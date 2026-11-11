package com.novel.api.importing.demo;

import java.util.List;

/**
 * 演示书库。
 *
 * <p>只放虚构书名与笔名，避免把真实作品和版权素材带进演示环境。
 * 分类必须与 {@code V2__seed_categories.sql} 的顶级分类逐字一致，
 * 否则导入时会因为 {@code findCategoryByName} 找不到而失败。
 *
 * <p>28 本覆盖 8 个顶级分类；章节数控制在 8–12 章，保证单本文件不大、
 * 目录一页（30 章）能完整放下，也够前端展示"最近更新 / 目录 / 阅读"三种页面。
 */
public final class DemoNovelLibrary {

    private static final List<DemoNovel> BOOKS = List.of(
            new DemoNovel("剑来风雪", "温酒", "玄幻",
                    "北境的风雪下了三十年，山上的剑也等了三十年。少年背着师父的断剑下山，才发现要还的债比想象中多。",
                    10, DemoNovel.DemoMood.XIANXIA),
            new DemoNovel("归墟", "沈砚", "玄幻",
                    "归墟之下没有海，只有被时间遗忘的名字。少年在海底捡到刻着自己名字的骨片，可他从未到过这里。",
                    9, DemoNovel.DemoMood.XIANXIA),
            new DemoNovel("山河剑心", "江照", "玄幻",
                    "王朝更替的第三年，铸剑师把最后一把剑沉进江里。捞起它的人，将决定这片山河下一个百年。",
                    10, DemoNovel.DemoMood.XIANXIA),
            new DemoNovel("万古长夜", "迟白", "玄幻",
                    "天上的星一颗一颗熄灭，人间开始点灯。守灯人世代相传一句话：灯灭之前，别抬头。",
                    8, DemoNovel.DemoMood.XIANXIA),

            new DemoNovel("城南旧巷", "陈拾", "都市",
                    "老巷要拆的那年夏天，他又闻到槐花和煤炉的味道。纸箱装得下旧物，装不下一个人在这里的二十年。",
                    8, DemoNovel.DemoMood.URBAN),
            new DemoNovel("深夜食堂的第七张桌子", "白鹿", "都市",
                    "凌晨两点还开着的食堂，只留第七张桌子给今天不想回家的人。老板从不问名字，只问一句：吃什么。",
                    9, DemoNovel.DemoMood.URBAN),
            new DemoNovel("三十岁的第一天", "青禾", "都市",
                    "生日那天他丢了工作，退了租房，坐在便利店门口吃完一块打折蛋糕。然后接到一个电话，改变了一切。",
                    10, DemoNovel.DemoMood.URBAN),

            new DemoNovel("青云志", "江照", "仙侠",
                    "他十二岁上山，师父说青云之上没有神，只有不肯下山的人。三十年后他站在山门前，终于明白这句话。",
                    10, DemoNovel.DemoMood.XIANXIA),
            new DemoNovel("一盏灯", "温酒", "仙侠",
                    "提灯的人走过十二座城，只为找到一个替他挡刀的人。灯芯烧了十年，火苗始终没有矮下去。",
                    9, DemoNovel.DemoMood.XIANXIA),
            new DemoNovel("云海孤舟", "南枝", "仙侠",
                    "云海之上有一叶孤舟，专渡将死之人的执念。摆渡人收的船资不是钱，是客人最不愿忘掉的一段记忆。",
                    8, DemoNovel.DemoMood.XIANXIA),
            new DemoNovel("长明", "顾青", "仙侠",
                    "长明灯灭了，整座山就塌了。守灯的小道士每天添一次油，直到有一天，他发现灯里没有油，只有一滴血。",
                    10, DemoNovel.DemoMood.XIANXIA),

            new DemoNovel("长安旧事", "沈砚", "历史",
                    "天宝年间，西市旧书坊在一场大火后只剩半面墙。掌柜的孙子捡到一册没有封皮的手稿，上面记着三十七个名字。",
                    10, DemoNovel.DemoMood.HISTORY),
            new DemoNovel("我在大唐开客栈", "白鹿", "历史",
                    "一觉醒来成了长安西市破客栈的掌柜。账上没钱，伙计跑光，只剩一口漏风的灶。他决定先把招牌擦干净。",
                    8, DemoNovel.DemoMood.HISTORY),
            new DemoNovel("大明小吏", "陈拾", "历史",
                    "万历十五年，他在县衙做一名不入流的书吏。别人忙着站队，他忙着把每一笔账记清楚——直到账本被人偷走。",
                    9, DemoNovel.DemoMood.HISTORY),
            new DemoNovel("北境无雪", "温酒", "历史",
                    "北境十年无雪，庄稼枯死，边军断粮。朝廷派来的钦差却说，这是祥瑞。守将把刀按在桌上，问：谁的信。",
                    10, DemoNovel.DemoMood.HISTORY),

            new DemoNovel("星海拾遗", "陆沉", "科幻",
                    "人类离开地球的第三百一十年，拾荒船在柯伊伯带捡到一只还在播放摇篮曲的黑匣子。没有人听得懂，可所有人都哭了。",
                    10, DemoNovel.DemoMood.SCIFI),
            new DemoNovel("深空回响", "程野", "科幻",
                    "末世第七年，无线电里第一次传来不是人类的声音。它每天重复同一段坐标，坐标里夹着他女儿的名字。",
                    9, DemoNovel.DemoMood.SCIFI),
            new DemoNovel("零号航道", "陆沉", "科幻",
                    "星图上没有这条航道。所有驶入的船都回来了，船员却坚持说自己从未离开过港口。",
                    8, DemoNovel.DemoMood.SCIFI),

            new DemoNovel("第七区服务器", "周野", "游戏",
                    "全息网游《山海》的第七区已经停服三年。一个没有ID的玩家每天照常上线，在空城里等一个不会来的队友。",
                    9, DemoNovel.DemoMood.SCIFI),
            new DemoNovel("开局一把木剑", "迟白", "游戏",
                    "他抽到了全服最废的开局装备，一把会说话的木剑。木剑说：别慌，我上辈子是神器。",
                    10, DemoNovel.DemoMood.SCIFI),

            new DemoNovel("夜行列车", "林晚", "悬疑",
                    "凌晨两点十七分，开往北方的列车总会出现一节没有编号的车厢。乘务员说那是临时加挂，可它在时刻表上从不存在。",
                    10, DemoNovel.DemoMood.SUSPENSE),
            new DemoNovel("长夜将尽", "顾青", "悬疑",
                    "十年前的连环案在一场暴雨后重演，唯一的目击者却失忆了。刑警翻开卷宗，发现自己的名字写在嫌疑人一栏。",
                    9, DemoNovel.DemoMood.SUSPENSE),
            new DemoNovel("雾都档案", "林晚", "悬疑",
                    "档案馆里每一份失踪案卷的最后一页都是空白。新来的档案员发现，空白页在潮湿的夜里会浮出字迹。",
                    8, DemoNovel.DemoMood.SUSPENSE),
            new DemoNovel("第十三位乘客", "迟白", "悬疑",
                    "摆渡车核载十二人，可每次到站都会多出一位乘客。司机从不回头，只在下车前说一句：到站了，该下的下。",
                    10, DemoNovel.DemoMood.SUSPENSE),

            new DemoNovel("山月不知心底事", "苏枕书", "言情",
                    "她在城南开了十年书店，等一个说会回来的人。槐花开了又落，只有抽屉最底层那封没寄出的信一直没动。",
                    10, DemoNovel.DemoMood.ROMANCE),
            new DemoNovel("春山如笑", "苏枕书", "言情",
                    "被退婚的侯府嫡女嫁给驻守边关的年轻将军。一纸婚书绑住两个陌生人，谁也没打算先低头。",
                    9, DemoNovel.DemoMood.ROMANCE),
            new DemoNovel("槐花巷", "南枝", "言情",
                    "巷子里的槐花一年只开七天。她每年那七天都坐在门口，从十六岁坐到二十六岁。",
                    8, DemoNovel.DemoMood.ROMANCE),
            new DemoNovel("迟到的春天", "青禾", "言情",
                    "他答应等她毕业就回来。她等了三年，等到一封从南极寄来的信，邮戳日期是两年前。",
                    10, DemoNovel.DemoMood.ROMANCE)
    );

    private DemoNovelLibrary() {
    }

    public static List<DemoNovel> all() {
        return BOOKS;
    }
}
