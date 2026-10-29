package com.xfwang.govdata.metadata.harvest.infrastructure.authorization;

import com.xfwang.govdata.metadata.harvest.domain.entity.HarvestRun;
import com.xfwang.govdata.metadata.harvest.domain.exception.HarvestErrorCode;
import com.xfwang.govdata.metadata.harvest.infrastructure.persistence.mapper.HarvestRunMapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

/** 为运行详情鉴权解析其所属采集计划稳定业务 ID。 */
@Component("harvestRunResourceResolver")
@RequiredArgsConstructor
public class HarvestRunResourceResolver {
    private final HarvestRunMapper runMapper;
    public String resolvePlanId(Long runId) {
        HarvestRun run = runId == null ? null : runMapper.selectById(runId);
        if (run == null) throw new ServiceException(HarvestErrorCode.RUN_NOT_FOUND);
        return String.valueOf(run.getHarvestPlanId());
    }
}
