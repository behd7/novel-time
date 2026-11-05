package com.novel.core.rank.application;

/**
 * 榜单的运行参数。
 *
 * <p>只有一个字段，但单独抽出来是因为它被两个地方同时使用：结算时决定写多少行，
 * 读取时决定从快照里取多少行。两处各写一个常量，改的时候必然只改一处。
 *
 * @param snapshotSize 每个周期快照的最大条数
 */
public record RankSettings(int snapshotSize) {

    public RankSettings {
        if (snapshotSize < 1 || snapshotSize > 1000) {
            throw new IllegalArgumentException("榜单快照条数必须在 1~1000 之间");
        }
    }
}
