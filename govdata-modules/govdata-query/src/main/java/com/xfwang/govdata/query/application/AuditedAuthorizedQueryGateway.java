package com.xfwang.govdata.query.application;

import com.xfwang.govdata.api.query.AuthorizedQuery;
import com.xfwang.govdata.api.query.AuthorizedQueryGateway;
import com.xfwang.govdata.api.query.QueryAuthorization;
import com.xfwang.govdata.api.query.QueryResult;
import com.xfwang.govdata.query.application.audit.QueryAccessRecorder;
import com.xfwang.govdata.query.domain.entity.QueryAccessLog;
import com.xfwang.govdata.query.domain.enums.QueryAccessResult;
import com.xfwang.govdata.query.domain.exception.QueryErrorCode;
import com.xfwang.platform.api.auth.model.SubjectContext;
import com.xfwang.platform.api.auth.service.SubjectProvider;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.slf4j.MDC;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/** 对所有在线查询统一审计，再授权；失败时绝不返回部分结果。 */
@Service
@Primary
@RequiredArgsConstructor
public class AuditedAuthorizedQueryGateway implements AuthorizedQueryGateway {
    private final QueryAccessRecorder recorder;
    private final AuthorizedQueryExecutionService execution;
    private final SubjectProvider subjects;

    @Override
    public QueryResult execute(AuthorizedQuery query) {
        SubjectContext subject = subjects.currentSubject();
        if (subject == null || subject.getTenantId() == null || subject.getTenantId().isBlank()
            || TransactionSynchronizationManager.isActualTransactionActive())
            throw new ServiceException(QueryErrorCode.AUTHORIZATION_REQUIRED);
        String queryId = UUID.randomUUID().toString().replace("-", "");
        String trace = MDC.get("traceId");
        String traceId = trace != null && trace.matches("[A-Za-z0-9_-]{1,64}") ? trace : queryId;
        long started = System.nanoTime();
        MDC.put("traceId", traceId);
        try {
            append(query, subject, queryId, traceId, QueryAccessResult.STARTED, null, 0, 0);
            try {
            QueryResult result = execution.execute(query);
            append(query, subject, queryId, traceId, QueryAccessResult.SUCCEEDED, null,
                result.getReturnedRows(), elapsed(started));
            return result;
        } catch (RuntimeException failure) {
            boolean denied = failure instanceof ServiceException error && Integer.valueOf(403).equals(error.getCode());
            String code = failure instanceof ServiceException error ? error.getErrorCode() : QueryErrorCode.EXECUTION_FAILED.getCode();
            append(query, subject, queryId, traceId, denied ? QueryAccessResult.DENIED : QueryAccessResult.FAILED,
                code, 0, elapsed(started));
            if (failure instanceof ServiceException error) throw error;
            throw new ServiceException(QueryErrorCode.EXECUTION_FAILED);
            }
        } finally {
            if (trace == null) MDC.remove("traceId");
            else MDC.put("traceId", trace);
        }
    }

    private void append(AuthorizedQuery query, SubjectContext subject, String queryId, String traceId,
                        QueryAccessResult result, String code, int rows, long duration) {
        var record = new QueryAccessLog();
        record.setTenantId(subject.getTenantId());
        record.setQueryId(queryId);
        record.setTraceId(traceId);
        record.setDatasourceId(query == null ? null : query.getDatasourceId());
        record.setResourceId(query == null ? null : query.getResourceId());
        record.setActionCode(QueryAuthorization.ACTION);
        String purpose = query == null ? null : query.getPurpose();
        record.setPurpose(purpose != null && purpose.matches("[A-Z][A-Z0-9_]{0,63}") ? purpose : null);
        record.setActorType(subject.getPrincipalType() == null ? "HUMAN" : subject.getPrincipalType().name());
        record.setActorId(subject.getPrincipalId() == null ? String.valueOf(subject.getUserId()) : subject.getPrincipalId());
        record.setResult(result.code());
        record.setErrorCode(code);
        record.setFieldCount(query == null || query.getFieldIds() == null ? 0 : query.getFieldIds().size());
        record.setFilterCount(query == null || query.getFilters() == null ? 0 : query.getFilters().size());
        record.setReturnedRowCount(rows);
        record.setDurationMs(duration);
        record.setOccurredTime(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
        try {
            recorder.append(record);
        } catch (RuntimeException ignored) {
            // 包括事务代理提交阶段的异常，统一转换为稳定审计错误。
            throw new ServiceException(QueryErrorCode.AUDIT_UNAVAILABLE);
        }
    }

    private long elapsed(long start) { return Math.max(0, (System.nanoTime() - start) / 1_000_000); }
}
