package com.xfwang.govdata.query;

import com.xfwang.govdata.query.api.AuthorizedQuery;
import com.xfwang.govdata.query.api.QueryResult;
import com.xfwang.govdata.query.spi.AuthorizedExecutionPlan;
import com.xfwang.govdata.query.spi.QueryExecutionAdapter;
import com.xfwang.govdata.query.spi.QueryFieldSchema;
import com.xfwang.govdata.query.spi.QueryResourceSchema;
import com.xfwang.govdata.query.spi.QueryResourceSchemaProvider;
import com.xfwang.govdata.query.spi.RawQueryResult;
import com.xfwang.platform.auth.api.enums.ConditionOperator;
import com.xfwang.platform.auth.api.enums.DecisionType;
import com.xfwang.platform.auth.api.model.AccessDecision;
import com.xfwang.platform.auth.api.model.AccessRequest;
import com.xfwang.platform.auth.api.model.AuthorizationObligation;
import com.xfwang.platform.auth.api.model.EnvironmentContext;
import com.xfwang.platform.auth.api.model.RowFilter;
import com.xfwang.platform.auth.api.model.SubjectContext;
import com.xfwang.platform.auth.api.service.AuthorizationContextAccessor;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class DefaultAuthorizedQueryGatewayTest {

    @Test
    void enforcesAuthorizationFiltersLimitsAndMaskingBeforeReturningRows() {
        AuthorizationObligation obligation = AuthorizationObligation.builder()
            .allowedColumns(Set.of("name", "phone"))
            .rowFilters(List.of(RowFilter.builder()
                .attribute("regionCode")
                .operator(ConditionOperator.EQ)
                .values(List.of("subject.regionCode"))
                .build()))
            .maskingRules(Map.of("phone", "MOBILE"))
            .maxRows(1)
            .exportAllowed(false)
            .build();
        TestAuthorizationContext context = new TestAuthorizationContext(
            AccessRequest.builder()
                .resourceId(101L)
                .subject(SubjectContext.builder()
                    .tenantId("000000")
                    .userId(1L)
                    .attributes(Map.of("regionCode", "110000"))
                    .build())
                .environment(EnvironmentContext.builder().purpose("数据预览").build())
                .build(),
            AccessDecision.builder().decision(DecisionType.PERMIT).obligation(obligation).build());
        QueryResourceSchema schema = schema();
        AtomicReference<AuthorizedExecutionPlan> captured = new AtomicReference<>();
        QueryExecutionAdapter adapter = new QueryExecutionAdapter() {
            @Override
            public boolean supports(String connectorCode) {
                return "mysql".equals(connectorCode);
            }

            @Override
            public RawQueryResult execute(AuthorizedExecutionPlan plan) {
                captured.set(plan);
                Map<Long, Object> first = new LinkedHashMap<>();
                first.put(11L, "张三");
                first.put(12L, "13812345678");
                Map<Long, Object> second = new LinkedHashMap<>();
                second.put(11L, "李四");
                second.put(12L, "13987654321");
                return RawQueryResult.builder().rows(List.of(first, second)).build();
            }
        };
        DefaultAuthorizedQueryGateway gateway = new DefaultAuthorizedQueryGateway(
            context, List.of(provider(schema)), List.of(adapter));

        QueryResult result = gateway.execute(AuthorizedQuery.builder()
            .requestId("query-001")
            .datasourceId(201L)
            .resourceId(101L)
            .fieldIds(List.of(11L, 12L))
            .limit(100)
            .timeoutMs(60_000)
            .purpose("数据预览")
            .build());

        assertThat(captured.get().getLimit()).isEqualTo(1);
        assertThat(captured.get().getTimeoutMs()).isEqualTo(30_000);
        assertThat(captured.get().getFilters()).singleElement().satisfies(filter -> {
            assertThat(filter.getFieldId()).isEqualTo(13L);
            assertThat(filter.getValues()).containsExactly("110000");
        });
        assertThat(result.getReturnedRows()).isEqualTo(1);
        assertThat(result.getHasMore()).isTrue();
        assertThat(result.getRows().get(0).get(12L)).isEqualTo("138****5678");
        assertThat(result.getColumns()).filteredOn(column -> column.getFieldId().equals(12L))
            .singleElement().extracting("masked").isEqualTo(true);
    }

    @Test
    void rejectsCallsWithoutResourceAuthorizationContext() {
        AuthorizationContextAccessor empty = new TestAuthorizationContext(null, null);
        DefaultAuthorizedQueryGateway gateway = new DefaultAuthorizedQueryGateway(
            empty, List.of(provider(schema())), List.of());

        assertThatThrownBy(() -> gateway.execute(AuthorizedQuery.builder()
            .datasourceId(201L).resourceId(101L).fieldIds(List.of(11L)).build()))
            .isInstanceOf(ServiceException.class)
            .extracting("errorCode").isEqualTo("GOV-QUERY-0403");
    }

    @Test
    void emptyAllowedColumnSetDeniesEveryRequestedField() {
        AuthorizationObligation obligation = AuthorizationObligation.builder()
            .allowedColumns(Set.of())
            .build();
        TestAuthorizationContext context = new TestAuthorizationContext(
            AccessRequest.builder().resourceId(101L)
                .environment(EnvironmentContext.builder().purpose("数据预览").build())
                .build(),
            AccessDecision.builder().decision(DecisionType.PERMIT).obligation(obligation).build());
        DefaultAuthorizedQueryGateway gateway = new DefaultAuthorizedQueryGateway(
            context, List.of(provider(schema())), List.of());

        assertThatThrownBy(() -> gateway.execute(AuthorizedQuery.builder()
            .datasourceId(201L)
            .resourceId(101L)
            .fieldIds(List.of(11L))
            .limit(10)
            .timeoutMs(1_000)
            .purpose("数据预览")
            .build()))
            .isInstanceOf(ServiceException.class)
            .extracting("errorCode").isEqualTo("GOV-QUERY-0403-01");
    }

    private QueryResourceSchema schema() {
        return QueryResourceSchema.builder()
            .resourceId(101L)
            .datasourceId(201L)
            .connectorCode("mysql")
            .schemaName("govdata")
            .objectName("population")
            .fields(List.of(
                field(11L, "name"),
                field(12L, "phone"),
                field(13L, "regionCode")))
            .build();
    }

    private QueryFieldSchema field(Long id, String code) {
        return QueryFieldSchema.builder()
            .fieldId(id)
            .fieldCode(code)
            .displayName(code)
            .authorizationAttribute(code)
            .physicalName(code)
            .selectable(true)
            .build();
    }

    private QueryResourceSchemaProvider provider(QueryResourceSchema schema) {
        return resourceId -> Objects.equals(resourceId, schema.getResourceId())
            ? Optional.of(schema) : Optional.empty();
    }

    private record TestAuthorizationContext(AccessRequest request, AccessDecision decision)
        implements AuthorizationContextAccessor {
        @Override
        public Optional<AccessRequest> currentRequest() {
            return Optional.ofNullable(request);
        }

        @Override
        public Optional<AccessDecision> currentDecision() {
            return Optional.ofNullable(decision);
        }
    }
}
