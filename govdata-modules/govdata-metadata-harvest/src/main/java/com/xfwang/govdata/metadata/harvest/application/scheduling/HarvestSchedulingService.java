package com.xfwang.govdata.metadata.harvest.application.scheduling;

import com.xfwang.govdata.metadata.harvest.application.run.HarvestRunService;
import com.xfwang.govdata.metadata.harvest.domain.exception.HarvestErrorCode;
import com.xfwang.platform.api.auth.model.SubjectContext;
import com.xfwang.platform.api.auth.service.SubjectProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.tenant.helper.TenantHelper;
import org.springframework.stereotype.Service;

/** 以计划创建者当前权限和计划所属租户执行调度触发。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HarvestSchedulingService {
    private final HarvestRunService runService;
    private final SubjectProvider subjectProvider;
    private final HarvestSubjectRunner subjectRunner;

    public void executeScheduled(String tenantId, Long planId, Long creatorUserId, String fireRef) {
        if (tenantId == null || planId == null || creatorUserId == null) return;
        try {
            SubjectContext subject = subjectProvider.subjectForUser(tenantId, creatorUserId);
            TenantHelper.dynamic(tenantId, () -> {
                return subjectRunner.runAs(subject, () -> runService.executeScheduled(planId, fireRef));
            });
        } catch (ServiceException exception) {
            if (!HarvestErrorCode.RUN_CONFLICT.getCode().equals(exception.getErrorCode())) {
                log.warn("元数据采集计划调度失败 planId={}, errorCode={}", planId, exception.getErrorCode(),
                    exception);
            }
        } catch (Exception exception) {
            log.warn("元数据采集计划调度异常 planId={}", planId, exception);
        }
    }
}
