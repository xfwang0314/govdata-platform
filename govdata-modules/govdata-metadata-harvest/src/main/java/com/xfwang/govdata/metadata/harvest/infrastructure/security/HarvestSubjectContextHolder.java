package com.xfwang.govdata.metadata.harvest.infrastructure.security;

import com.xfwang.platform.api.auth.model.SubjectContext;
import com.xfwang.platform.api.auth.service.SubjectContextOverride;
import com.xfwang.govdata.metadata.harvest.application.scheduling.HarvestSubjectRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 为后台 Cron 采集提供显式、线程隔离的运行主体上下文。
 */
@Component
public class HarvestSubjectContextHolder implements SubjectContextOverride, HarvestSubjectRunner {
    private final ThreadLocal<Deque<SubjectContext>> subjects = ThreadLocal.withInitial(ArrayDeque::new);

    @Override
    public Optional<SubjectContext> currentSubject() {
        return Optional.ofNullable(subjects.get().peek());
    }

    @Override
    public <T> T runAs(SubjectContext subject, Supplier<T> action) {
        Deque<SubjectContext> stack = subjects.get();
        stack.push(subject);
        try {
            return action.get();
        } finally {
            stack.pop();
            if (stack.isEmpty()) subjects.remove();
        }
    }
}
