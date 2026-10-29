package com.xfwang.govdata.metadata.harvest.application.scheduling;

import com.xfwang.platform.api.auth.model.SubjectContext;

import java.util.function.Supplier;

/** 后台采集执行期间绑定显式主体的内部 Adapter seam。 */
public interface HarvestSubjectRunner {
    <T> T runAs(SubjectContext subject, Supplier<T> action);
}
