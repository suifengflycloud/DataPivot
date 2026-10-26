package com.datapivot.common;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * 统一分页返回体
 *
 * @param <T> 记录类型
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<T> records;
    private long total;
    private long current;
    private long size;

    public static <T> PageResult<T> empty(long current, long size) {
        PageResult<T> page = new PageResult<>();
        page.setRecords(Collections.emptyList());
        page.setTotal(0L);
        page.setCurrent(current);
        page.setSize(size);
        return page;
    }

    public static <S, T> PageResult<T> of(List<S> records, long total, long current, long size,
                                          Function<S, T> converter) {
        PageResult<T> page = new PageResult<>();
        page.setRecords(records.stream().map(converter).toList());
        page.setTotal(total);
        page.setCurrent(current);
        page.setSize(size);
        return page;
    }
}
