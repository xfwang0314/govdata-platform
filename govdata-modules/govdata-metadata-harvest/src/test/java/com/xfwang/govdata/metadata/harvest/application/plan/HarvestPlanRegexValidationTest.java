package com.xfwang.govdata.metadata.harvest.application.plan;

import com.xfwang.govdata.api.datasource.DataSourceReferenceProvider;
import com.xfwang.govdata.metadata.harvest.application.plan.command.HarvestPlanCommand;
import com.xfwang.govdata.metadata.harvest.application.plan.command.HarvestRuleCommand;
import com.xfwang.govdata.metadata.harvest.infrastructure.persistence.mapper.HarvestPlanMapper;
import com.xfwang.govdata.metadata.harvest.infrastructure.persistence.mapper.HarvestPlanRevisionMapper;
import com.xfwang.govdata.metadata.harvest.infrastructure.persistence.mapper.HarvestRuleMapper;
import com.xfwang.platform.api.auth.service.SubjectProvider;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class HarvestPlanRegexValidationTest {
    @Test
    void rejectsUnsupportedRegexBeforeDatasourceLookupOrPersistence() {
        DataSourceReferenceProvider datasourceProvider = mock(DataSourceReferenceProvider.class);
        HarvestPlanService service = new HarvestPlanService(mock(HarvestPlanMapper.class),
            mock(HarvestPlanRevisionMapper.class), mock(HarvestRuleMapper.class), datasourceProvider,
            mock(SubjectProvider.class), mock(ApplicationEventPublisher.class));
        HarvestRuleCommand rule = new HarvestRuleCommand();
        rule.setRuleScope("TABLE");
        rule.setRuleType("INCLUDE");
        rule.setMatchType("REGEX");
        rule.setRulePattern("(?=secret).*" );
        rule.setSortNo(0);
        HarvestPlanCommand command = new HarvestPlanCommand();
        command.setScheduleType("MANUAL");
        command.setPlanStatus("ACTIVE");
        command.setRules(List.of(rule));

        assertThatThrownBy(() -> service.create(command))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("正则表达式");
        verifyNoInteractions(datasourceProvider);
    }
}
