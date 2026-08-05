package com.xfwang.platform.auth.infrastructure.enforcement;

import com.xfwang.platform.api.auth.model.AccessDecision;
import com.xfwang.platform.api.auth.model.AccessRequest;
import com.xfwang.platform.api.auth.service.AuthorizationContextAccessor;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * 支持嵌套服务调用的当前线程授权上下文。
 */
@Component
public class ThreadLocalAuthorizationContextAccessor implements AuthorizationContextAccessor {

    private final ThreadLocal<Deque<Entry>> contexts = ThreadLocal.withInitial(ArrayDeque::new);

    public void push(AccessRequest request, AccessDecision decision) {
        contexts.get().push(new Entry(request, decision));
    }

    public void pop() {
        Deque<Entry> values = contexts.get();
        if (!values.isEmpty()) {
            values.pop();
        }
        if (values.isEmpty()) {
            contexts.remove();
        }
    }

    @Override
    public Optional<AccessRequest> currentRequest() {
        return current().map(Entry::request);
    }

    @Override
    public Optional<AccessDecision> currentDecision() {
        return current().map(Entry::decision);
    }

    private Optional<Entry> current() {
        return Optional.ofNullable(contexts.get().peek());
    }

    private record Entry(AccessRequest request, AccessDecision decision) {
    }
}
