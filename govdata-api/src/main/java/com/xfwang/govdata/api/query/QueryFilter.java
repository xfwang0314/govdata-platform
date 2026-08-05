package com.xfwang.govdata.api.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** 使用元数据字段 ID 表达的结构化过滤条件，不接受列名或 SQL。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryFilter implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    private Long fieldId;
    private QueryOperator operator;
    @Builder.Default private List<Object> values = new ArrayList<>();
}
