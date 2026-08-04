package com.xfwang.govdata.query.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** 外部数据源在线查询请求。所有标识均为平台注册的稳定 ID。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorizedQuery implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    private String requestId;
    private Long datasourceId;
    private Long resourceId;
    @Builder.Default private List<Long> fieldIds = new ArrayList<>();
    @Builder.Default private List<QueryFilter> filters = new ArrayList<>();
    @Builder.Default private List<QuerySort> sorts = new ArrayList<>();
    @Builder.Default private Integer offset = 0;
    @Builder.Default private Integer limit = 100;
    @Builder.Default private Integer timeoutMs = 30_000;
    private String purpose;
}
