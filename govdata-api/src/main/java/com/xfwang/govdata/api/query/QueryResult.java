package com.xfwang.govdata.api.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 已执行授权约束和脱敏后的查询结果。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryResult implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    @Builder.Default private List<QueryColumn> columns = new ArrayList<>();
    @Builder.Default private List<Map<Long, Object>> rows = new ArrayList<>();
    private Integer returnedRows;
    private Boolean hasMore;
}
