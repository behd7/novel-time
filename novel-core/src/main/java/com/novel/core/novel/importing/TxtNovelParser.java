package com.novel.core.novel.importing;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TXT 小说解析器。
 *
 * <p>定了一个明确的文件格式，而不是去猜各种野生排版：
 *
 * <pre>
 * 书名: 青云志
 * 作者: 佚名
 * 分类: 玄幻
 * 简介: 一句话简介
 * ---
 * 第一章 少年
 * 正文……
 * 第二章 下山
 * 正文……
 * </pre>
 *
 * <p>头部是可选的，但 {@code ---} 分隔符必须有（没有分隔符时整篇都当正文解析）。
 * 章节标题按 {@code 第N章} 识别，N 支持中文数字与阿拉伯数字。
 * 章序号以**出现顺序**重新编号，不信文件里写的数字——盗版站转码后
 * "第103章"重复出现三次是常态，按原数字存会撞 {@code uk_novel_seq} 唯一键。
 */
@Component
public final class TxtNovelParser {

    private static final Pattern HEADER_LINE = Pattern.compile("^(书名|作者|分类|简介)\\s*[:：]\\s*(.*)$");    private static final Pattern CHAPTER_LINE =
            Pattern.compile("^第\\s*[0-9零一二三四五六七八九十百千两]+\\s*章[\\s　]*(.*)$");
    private static final String SEPARATOR = "---";

    /** 单章字数上限，防止有人把一整本书塞进一个"章"里撑爆 MEDIUMTEXT 与 Redis。 */
    private static final int MAX_CHAPTER_CHARS = 50_000;

    /**
     * @param raw           文件全文
     * @param fallbackTitle 头部没写书名时用文件名兜底
     */
    public ParsedNovel parse(String raw, String fallbackTitle) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("文件内容为空");
        }
        String text = raw.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = text.split("\n", -1);

        Map<String, String> header = new LinkedHashMap<>();
        int bodyStart = 0;
        boolean hasSeparator = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (SEPARATOR.equals(line)) {
                bodyStart = i + 1;
                hasSeparator = true;
                break;
            }
            Matcher matcher = HEADER_LINE.matcher(line);
            if (matcher.matches()) {
                header.put(matcher.group(1), matcher.group(2).trim());
            } else if (!line.isEmpty()) {
                // 头部区域出现了无法识别的行，说明没有头部，整篇都是正文
                break;
            }
        }
        if (!hasSeparator) {
            bodyStart = 0;
            header.clear();
        }

        List<ParsedChapter> chapters = new ArrayList<>();
        List<String> preamble = new ArrayList<>();
        String currentTitle = null;
        StringBuilder buffer = new StringBuilder();

        for (int i = bodyStart; i < lines.length; i++) {
            String line = lines[i];
            Matcher chapterMatcher = CHAPTER_LINE.matcher(line.trim());
            if (chapterMatcher.matches()) {
                if (currentTitle != null) {
                    chapters.add(build(chapters.size() + 1, currentTitle, buffer.toString()));
                } else if (!buffer.isEmpty()) {
                    preamble.add(buffer.toString());
                }
                String title = chapterMatcher.group(1).trim();
                currentTitle = title.isEmpty() ? "第" + (chapters.size() + 1) + "章" : title;
                buffer.setLength(0);
            } else {
                buffer.append(line).append('\n');
            }
        }
        if (currentTitle != null) {
            chapters.add(build(chapters.size() + 1, currentTitle, buffer.toString()));
        } else {
            // 一个章节标记都没有：整篇当一章，总比导不进去强，但要明确告诉调用方
            String body = String.join("\n", preamble);
            if (body.isBlank()) {
                body = buffer.toString();
            }
            if (body.isBlank()) {
                throw new IllegalArgumentException("未识别到任何正文内容");
            }
            chapters.add(build(1, "正文", body));
        }

        String title = firstNonBlank(header.get("书名"), fallbackTitle, "未命名");
        String intro = header.get("简介");
        if (intro == null && !preamble.isEmpty()) {
            intro = String.join("", preamble).trim();
        }

        return new ParsedNovel(title, firstNonBlank(header.get("作者"), "佚名"),
                header.get("分类"), truncate(intro, 500), chapters);
    }

    private ParsedChapter build(int seq, String title, String content) {
        String body = content.strip();
        if (body.length() > MAX_CHAPTER_CHARS) {
            body = body.substring(0, MAX_CHAPTER_CHARS);
        }
        return new ParsedChapter(seq, truncate(title, 200), body, ParsedChapter.countWords(body));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
