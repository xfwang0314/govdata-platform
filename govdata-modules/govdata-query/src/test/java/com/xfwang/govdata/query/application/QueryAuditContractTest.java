package com.xfwang.govdata.query.application;

import com.xfwang.govdata.api.query.AuthorizedQuery;
import com.xfwang.govdata.query.application.audit.QueryAccessRecorder;
import com.xfwang.govdata.query.infrastructure.persistence.mapper.QueryAccessLogMapper;
import com.xfwang.platform.api.auth.model.SubjectContext;
import com.xfwang.platform.api.auth.service.SubjectProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** 通过查询公开入口验证审计故障必须关闭数据通路。 */
@Tag("dev")
class QueryAuditContractTest {
    @Test
    void refusesQueryWhenAuditStoreIsUnavailable() {
        var mapper = mock(QueryAccessLogMapper.class);
        when(mapper.insert(any(com.xfwang.govdata.query.domain.entity.QueryAccessLog.class)))
            .thenThrow(new IllegalStateException("database unavailable"));
        var subjects = mock(SubjectProvider.class);
        when(subjects.currentSubject()).thenReturn(SubjectContext.builder().tenantId("000000").userId(1L).build());
        var gateway = new AuditedAuthorizedQueryGateway(new QueryAccessRecorder(mapper),
            new AuthorizedQueryExecutionService(new DefaultAuthorizedQueryGateway(
                mock(com.xfwang.platform.api.auth.service.AuthorizationContextAccessor.class), java.util.List.of(), java.util.List.of())), subjects);
        assertThatThrownBy(() -> gateway.execute(AuthorizedQuery.builder().datasourceId(1L).resourceId(2L)
            .fieldIds(java.util.List.of(3L)).purpose("DATA_QUERY").build()))
            .hasFieldOrPropertyWithValue("errorCode", "GOV-QUERY-0503-AUDIT");
    }
}
