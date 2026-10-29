package com.xfwang.govdata.metadata.harvest.application.scheduling;

import com.xfwang.govdata.metadata.harvest.application.run.HarvestRunService;
import com.xfwang.platform.api.auth.model.SubjectContext;
import com.xfwang.platform.api.auth.service.SubjectProvider;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HarvestSchedulingServiceTest {
    @Test
    void resolvesCreatorBeforeBindingRunContext() {
        HarvestRunService runService = mock(HarvestRunService.class);
        SubjectProvider subjectProvider = mock(SubjectProvider.class);
        HarvestSubjectRunner subjectRunner = mock(HarvestSubjectRunner.class);
        SubjectContext subject = mock(SubjectContext.class);
        when(subjectProvider.subjectForUser("000000", 1L)).thenReturn(subject);
        when(subjectRunner.runAs(eq(subject), any())).thenAnswer(invocation ->
            ((Supplier<?>) invocation.getArgument(1)).get());

        new HarvestSchedulingService(runService, subjectProvider, subjectRunner)
            .executeScheduled("000000", 10L, 1L, "fire-1");

        InOrder calls = inOrder(subjectProvider, subjectRunner, runService);
        calls.verify(subjectProvider).subjectForUser("000000", 1L);
        calls.verify(subjectRunner).runAs(eq(subject), any());
        calls.verify(runService).executeScheduled(10L, "fire-1");
    }
}
