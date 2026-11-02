package com.novel.infra.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件。
 *
 * <p>顺序有讲究（官方建议）：分页 → 乐观锁 → 防全表攻击。
 * 分页放前面，是因为后面两个插件都可能改写 SQL，让分页在最终 SQL 上做才准确。
 *
 * <p>{@link BlockAttackInnerInterceptor} 是救命的：它拦截没有 where 条件的
 * update/delete，防止一次手滑把整张表清空。开发环境就该开着，不要等到出事才想起。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 单页上限，和 PageQuery.MAX_SIZE 保持一致，双保险
        pagination.setMaxLimit(50L);
        // 超出最大页后是否回到第一页；false 表示返回空列表，语义更清晰
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);

        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        return interceptor;
    }
}
