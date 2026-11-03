package com.novel.api.importing;

import com.novel.core.novel.importing.NovelImportService;
import com.novel.core.novel.importing.ParsedNovel;
import com.novel.core.novel.importing.TxtNovelParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * 批量导入入口（仅 {@code import} profile 生效）。
 *
 * <p>用法：
 * <pre>
 * java -jar novel-api.jar --spring.profiles.active=import \
 *      --novel.import.dir=deploy/samples
 * </pre>
 * 启动后不起 Web 服务，导完即退出。
 *
 * <p>单个文件失败**不中断整批**：一本格式有问题的 TXT 不该让其余 99 本都导不进去。
 *
 * <p>同名小说会被跳过而不是重复插入，所以这个命令可以直接重跑，不必先清库。
 */
@Slf4j
@Component
@Profile("import")
@RequiredArgsConstructor
public class NovelImportRunner implements ApplicationRunner {

    private final NovelImportService importService;
    private final TxtNovelParser parser;

    @Value("${novel.import.dir:deploy/samples}")
    private String directory;

    @Override
    public void run(ApplicationArguments args) {
        int exitCode;
        try {
            exitCode = importAll() ? 0 : 1;
        } catch (Exception e) {
            log.error("导入过程异常", e);
            exitCode = 1;
        }
        // 必须显式退出：非 Web 应用不会自动关停，
        // Lettuce 的连接事件线程与 @Scheduled 的调度线程都是非守护线程，
        // 不调这一句，JVM 会在导完之后一直挂着（本次就是这么踩到的）。
        System.exit(exitCode);
    }

    /** @return 是否全部成功 */
    private boolean importAll() throws Exception {
        Path root = Path.of(directory);
        if (!Files.isDirectory(root)) {
            log.error("导入目录不存在或不是目录：{}", root.toAbsolutePath());
            return false;
        }

        List<Path> files;
        try (Stream<Path> stream = Files.list(root)) {
            files = stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".txt"))
                    .sorted()
                    .toList();
        }
        if (files.isEmpty()) {
            log.warn("目录 {} 下没有 .txt 文件", root.toAbsolutePath());
            return true;
        }

        int success = 0;
        int failed = 0;
        for (Path file : files) {
            String fallbackTitle = file.getFileName().toString().replaceAll("(?i)\\.txt$", "");
            try {
                ParsedNovel parsed = parser.parse(Files.readString(file, StandardCharsets.UTF_8), fallbackTitle);
                long novelId = importService.importNovel(parsed, parsed.categoryName());
                log.info("✓ [{}] {}（{} 章 / {} 字）→ novelId={}",
                        file.getFileName(), parsed.title(), parsed.chapters().size(), parsed.totalWords(), novelId);
                success++;
            } catch (Exception e) {
                log.warn("✗ [{}] 导入失败：{}", file.getFileName(), e.getMessage());
                failed++;
            }
        }
        log.info("导入结束：成功 {} 本，失败 {} 本", success, failed);
        return failed == 0;
    }
}
