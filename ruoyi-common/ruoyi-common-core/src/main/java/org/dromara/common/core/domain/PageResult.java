package org.dromara.common.core.domain;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;

/**
 * 通用分页结果。
 *
 * @param <T> 数据类型
 */
@Data
@NoArgsConstructor
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 总记录数。
     */
    private long total;

    /**
     * 当前页数据。
     */
    private Collection<T> rows;

    public PageResult(Collection<T> rows, long total) {
        this.rows = emptyIfNull(rows);
        this.total = total;
    }

    public static <T> PageResult<T> build(Collection<T> rows, long total) {
        return new PageResult<>(rows, total);
    }

    public static <T> PageResult<T> build(Collection<T> rows) {
        Collection<T> safeRows = emptyIfNull(rows);
        return new PageResult<>(safeRows, safeRows.size());
    }

    public static <T> PageResult<T> build() {
        return new PageResult<>(Collections.emptyList(), 0);
    }

    private static <T> Collection<T> emptyIfNull(Collection<T> rows) {
        return rows == null ? Collections.emptyList() : rows;
    }
}
