package com.xfwang.platform.secret.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;
import java.io.Serial;
import java.time.LocalDateTime;

@Data @EqualsAndHashCode(callSuper = true) @TableName("platform_secret_access_log")
public class PlatformSecretAccessLog extends TenantEntity {
    @Serial private static final long serialVersionUID = 1L;
    @TableId private Long accessLogId;
    private Long secretId; private String secretRefSnapshot; private String callerModule; private String actorType;
    private String actorId; private String purpose; private String result; private String reasonCode;
    private String traceId; private LocalDateTime occurredTime;
}
