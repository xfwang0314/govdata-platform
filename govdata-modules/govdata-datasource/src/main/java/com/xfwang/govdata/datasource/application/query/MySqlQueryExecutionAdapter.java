package com.xfwang.govdata.datasource.application.query;

import com.xfwang.govdata.api.query.spi.AuthorizedExecutionPlan;
import com.xfwang.govdata.api.query.spi.QueryExecutionAdapter;
import com.xfwang.govdata.api.query.spi.RawQueryResult;
import com.xfwang.govdata.datasource.domain.exception.DataSourceErrorCode;
import com.xfwang.govdata.datasource.infrastructure.persistence.mapper.DataSourceInstanceMapper;
import com.xfwang.platform.api.auth.service.AuthorizationContextAccessor;
import com.xfwang.platform.api.secret.SecretProvider;
import com.xfwang.platform.api.secret.SecretResolveRequest;
import com.xfwang.platform.api.secret.SecretType;
import com.xfwang.platform.api.secret.SecretValue;
import com.xfwang.govdata.datasource.domain.enums.DataSourceEnums;
import com.xfwang.govdata.datasource.domain.enums.DataSourceSecretUsage;
import com.xfwang.govdata.datasource.infrastructure.connector.mysql.MySqlReadOnlyQueryExecutor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xfwang.platform.api.auth.model.AccessDecision;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** MySQL 受控查询适配器。 */
@RequiredArgsConstructor
@org.springframework.stereotype.Component
public class MySqlQueryExecutionAdapter implements QueryExecutionAdapter {
    private final DataSourceInstanceMapper instanceMapper;
    private final List<SecretProvider> secretProviders;
    private final AuthorizationContextAccessor authorizationContextAccessor;

    @Override
    public boolean supports(String connectorCode) {
        return "mysql".equals(connectorCode);
    }

    @Override
    public RawQueryResult execute(AuthorizedExecutionPlan plan) {
        var request = authorizationContextAccessor.currentRequest()
            .orElseThrow(() -> new ServiceException(DataSourceErrorCode.QUERY_FORBIDDEN));
        if (authorizationContextAccessor.currentDecision().filter(AccessDecision::isAllowed).isEmpty()
            || plan == null || plan.getResource() == null || request.getSubject() == null
            || request.getSubject().getTenantId() == null || !com.xfwang.govdata.api.query.QueryAuthorization.ACTION.equals(request.getAction())
            || !Objects.equals(request.getResourceId(), plan.getResource().getResourceId())) {
            throw new ServiceException(DataSourceErrorCode.QUERY_FORBIDDEN);
        }
        var source = instanceMapper.selectById(plan.getResource().getDatasourceId());
        if (source == null || !Objects.equals(source.getTenantId(), request.getSubject().getTenantId())
            || !supports(source.getConnectorCode()) || !"1.0".equals(source.getConnectorVersion())
            || !DataSourceEnums.Status.ACTIVE.code().equals(source.getStatus())
            || !DataSourceEnums.AccessMode.READ_ONLY.code().equals(source.getAccessMode())
            || !DataSourceEnums.TestStatus.SUCCESS.code().equals(source.getLastTestStatus())
            || source.getLastTestTime() == null) {
            throw new ServiceException(DataSourceErrorCode.QUERY_FORBIDDEN);
        }
        Map<String, Object> endpoint;
        try {
            endpoint = new ObjectMapper().readValue(source.getEndpointConfigJson(), new TypeReference<>() { });
        } catch (Exception ignored) {
            throw new ServiceException(DataSourceErrorCode.QUERY_INVALID);
        }
        var subject = request.getSubject();
        var resolve = SecretResolveRequest.builder().tenantId(source.getTenantId()).secretRef(source.getSecretRef())
            .expectedSecretType(SecretType.USERNAME_PASSWORD).requiredKeys(SecretType.USERNAME_PASSWORD.requiredKeys())
            .callerModule(DataSourceSecretUsage.OWNER_MODULE).purpose(DataSourceSecretUsage.QUERY.code())
            .actorType(subject.getPrincipalType() == null ? "HUMAN" : subject.getPrincipalType().name())
            .actorId(subject.getPrincipalId() == null ? String.valueOf(subject.getUserId()) : subject.getPrincipalId())
            .traceId(org.slf4j.MDC.get("traceId")).build();
        try (var permit = com.xfwang.govdata.datasource.infrastructure.connector.mysql.QueryConcurrencyLimiter.acquire(
                 source.getTenantId(), source.getDatasourceId());
             SecretValue secret = secretProviders.stream().map(provider -> provider.resolve(resolve))
            .flatMap(java.util.Optional::stream).findFirst()
            .orElseThrow(() -> new ServiceException(DataSourceErrorCode.SECRET_UNAVAILABLE))) {
            return MySqlReadOnlyQueryExecutor.execute(plan, endpoint, secret, source.getEnvironment());
        }
    }
}
