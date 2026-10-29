package com.xfwang.platform.mcp.infrastructure.security;

import com.xfwang.platform.api.auth.model.SubjectContext;
import com.xfwang.platform.api.auth.service.SubjectContextOverride;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.function.Supplier;

/** 将 MCP Agent 主体安全地绑定到当前调用线程，供 ABAC/Grant 执行链读取。 */
@Component
public class McpSubjectContextHolder implements SubjectContextOverride {

    private final ThreadLocal<Deque<SubjectContext>> subjects = ThreadLocal.withInitial(ArrayDeque::new);

    @Override
    public Optional<SubjectContext> currentSubject() {
        return Optional.ofNullable(subjects.get().peek());
    }

    public <T> T runAs(SubjectContext subject, Supplier<T> action) {
        subjects.get().push(subject);
        try {
            return action.get();
        } finally {
            Deque<SubjectContext> stack = subjects.get();
            stack.pop();
            if (stack.isEmpty()) {
                subjects.remove();
            }
        }
    }
}
