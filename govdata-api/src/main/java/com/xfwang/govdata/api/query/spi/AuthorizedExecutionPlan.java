package com.xfwang.govdata.api.query.spi;

import com.xfwang.govdata.api.query.QueryFilter;
import com.xfwang.govdata.api.query.QuerySort;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 已完成授权和元数据解析的执行计划；Adapter 只能参数化编译，不得拼接请求 SQL。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorizedExecutionPlan {
    private String requestId;
    private QueryResourceSchema resource;
    @Builder.Default
    private List<QueryFieldSchema> selectedFields = new ArrayList<>();
    @Builder.Default
    private List<QueryFilter> filters = new ArrayList<>();
    @Builder.Default
    private List<QuerySort> sorts = new ArrayList<>();
    private Integer offset;
    private Integer limit;
    private Integer timeoutMs;
}
