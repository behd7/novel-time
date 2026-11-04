# 带 IK 中文分词的 Elasticsearch。
#
# 为什么把插件烤进镜像，而不是在 compose 的 command 里装：
# 官方镜像的 plugins 目录是容器内的可写层，容器重建就没了——每次 `up -d` 都要重装一遍，
# 装失败还会让 ES 起不来，排查时看到的是"ES 崩了"而不是"插件没装上"。
# 烤进镜像后版本号是写死的，一次构建、终身受用。
#
# 两个版本必须对齐：
#   1. base image 的 ES 版本 = pom 里由 Boot BOM 管理的 elasticsearch-java 版本（8.13.x）；
#   2. IK 插件版本 = ES 版本，差一个小版本都可能加载失败。
#
# 用法：docker compose -f deploy/docker-compose.yml build elasticsearch && ... up -d
# 没有网络装插件时，把 novel.search.analyzer / search-analyzer 配成 standard 也能跑，
# 只是中文会被按单字切分，搜索质量明显下降。
FROM elasticsearch:8.13.0

RUN bin/elasticsearch-plugin install --batch \
    https://get.infini.cloud/elasticsearch/analysis-ik/8.13.0
