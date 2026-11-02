package com.novel.common.result;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/**
 * 通用分页入参。
 *
 * <p>{@code size} 硬上限 50：是为了防止 {@code size=100000} 把数据库和 JVM 一起打爆，
 * 这也是很多线上事故的起因。需要全量的场景走专门的导出接口，不走分页接口。
 */
@Getter
@Setter
public class PageQuery {

    public static final int MAX_SIZE = 50;

    @Min(value = 1, message = "页码从 1 开始")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数至少为 1")
    @Max(value = MAX_SIZE, message = "每页条数不能超过 " + MAX_SIZE)
    private Integer size = 20;

    public int offset() {
        return (page - 1) * size;
    }
}
