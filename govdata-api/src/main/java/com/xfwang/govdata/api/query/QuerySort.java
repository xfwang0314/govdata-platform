package com.xfwang.govdata.api.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/** 使用元数据字段 ID 表达的排序。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuerySort implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    private Long fieldId;
    private QueryDirection direction;
}
