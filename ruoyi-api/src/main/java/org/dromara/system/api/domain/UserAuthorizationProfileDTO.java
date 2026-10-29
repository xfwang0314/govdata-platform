package org.dromara.system.api.domain;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

/** 用户当前授权属性快照，用于非登录态受控调用构建授权主体。 */
@Data
@NoArgsConstructor
public class UserAuthorizationProfileDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String tenantId;
    private Long userId;
    private Long deptId;
    private Set<Long> deptPathIds;
    private String deptCategory;
    private String userType;
    private Set<Long> roleIds;
    private Set<String> roleKeys;
    private String status;
}
