package com.xfwang.platform.auth.application.decision;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xfwang.platform.auth.application.decision.query.DecisionLogQuery;
import com.xfwang.platform.auth.application.decision.view.DecisionLogView;
import com.xfwang.platform.auth.domain.entity.AuthzDecisionLog;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzDecisionLogMapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.stereotype.Service;

/** 授权审计只读查询服务。 */
@Service
@RequiredArgsConstructor
public class DecisionLogQueryService {

    private final AuthzDecisionLogMapper logMapper;

    public PageResult<DecisionLogView> page(DecisionLogQuery query, PageQuery pageQuery) {
        DecisionLogQuery filter = query == null ? new DecisionLogQuery() : query;
        LambdaQueryWrapper<AuthzDecisionLog> wrapper = new LambdaQueryWrapper<AuthzDecisionLog>()
            .like(StringUtils.isNotBlank(filter.getTraceId()), AuthzDecisionLog::getTraceId, filter.getTraceId())
            .eq(StringUtils.isNotBlank(filter.getSubjectType()), AuthzDecisionLog::getSubjectType, filter.getSubjectType())
            .like(StringUtils.isNotBlank(filter.getSubjectId()), AuthzDecisionLog::getSubjectId, filter.getSubjectId())
            .like(StringUtils.isNotBlank(filter.getResourceCode()), AuthzDecisionLog::getResourceCodeSnapshot,
                filter.getResourceCode())
            .eq(StringUtils.isNotBlank(filter.getDecision()), AuthzDecisionLog::getDecision, filter.getDecision())
            .eq(StringUtils.isNotBlank(filter.getActionCode()), AuthzDecisionLog::getActionCode,
                filter.getActionCode())
            .orderByDesc(AuthzDecisionLog::getOccurredTime)
            .orderByDesc(AuthzDecisionLog::getDecisionId);
        Page<AuthzDecisionLog> page = logMapper.selectPage(pageQuery.build(), wrapper);
        return PageResult.build(page.getRecords().stream().map(this::toView).toList(), page.getTotal());
    }

    private DecisionLogView toView(AuthzDecisionLog log) {
        return DecisionLogView.builder()
            .decisionId(log.getDecisionId()).traceId(log.getTraceId())
            .subjectType(log.getSubjectType()).subjectId(log.getSubjectId())
            .resourceId(log.getResourceId()).resourceType(log.getResourceTypeSnapshot())
            .resourceCode(log.getResourceCodeSnapshot()).actionCode(log.getActionCode())
            .decision(log.getDecision()).reasonCode(log.getReasonCode()).purpose(log.getPurpose())
            .policyVersionIdsJson(log.getPolicyVersionIdsJson()).grantIdsJson(log.getGrantIdsJson())
            .obligationSummaryJson(log.getObligationSummaryJson()).durationMs(log.getDurationMs())
            .occurredTime(log.getOccurredTime()).build();
    }
}
