package com.xfwang.platform.api.secret;

import lombok.Builder;
import lombok.Value;

/** 业务模块查询可选择凭据时使用的受控筛选条件。 */
@Value
@Builder
public class SecretReferenceQuery {
    String callerModule;
    SecretType secretType;
    String purpose;
}
