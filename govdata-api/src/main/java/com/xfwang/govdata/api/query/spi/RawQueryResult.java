package com.xfwang.govdata.api.query.spi;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Connector Adapter 返回的内部原始结果，离开查询网关前必须完成脱敏。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RawQueryResult {
    @Builder.Default private List<Map<Long, Object>> rows = new ArrayList<>();
    private Boolean hasMore;
}
