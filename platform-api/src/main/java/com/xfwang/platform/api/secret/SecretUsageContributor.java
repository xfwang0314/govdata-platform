package com.xfwang.platform.api.secret;

import java.util.List;

/**
 * 业务模块向凭据目录贡献所属模块和允许用途的稳定接口。
 *
 * <p>用途编码由业务模块拥有；platform-secret 只负责聚合、展示和运行时校验。</p>
 */
public interface SecretUsageContributor {
    String ownerModule();

    String ownerModuleLabel();

    List<SecretPurposeDefinition> purposes();
}
