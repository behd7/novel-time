# 演示书库

28 本虚构书，覆盖 8 个顶级分类，每本 8–12 章。

- 由 `com.novel.api.importing.demo.DemoNovelWriter` 确定性生成，**不要手改**：重新生成会覆盖。
- 重新生成：
  `java -cp novel-api/target/classes com.novel.api.importing.demo.DemoNovelWriter deploy/samples/demo`
- 导入：
  `java -jar novel-api/target/novel-api-0.1.0-SNAPSHOT.jar --spring.profiles.active=import --novel.import.dir=deploy/samples/demo`
- 本文件不会被导入：导入器只扫同目录下的 `.txt`。
