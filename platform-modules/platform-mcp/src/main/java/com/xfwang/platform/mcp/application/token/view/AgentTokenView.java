package com.xfwang.platform.mcp.application.token.view;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** Agent 凭据列表视图，不包含可恢复的秘密。 */
@Data
@Builder
public class AgentTokenView {
    private Long tokenId;
    private String agentName;
    private String agentCode;
    private String tokenPrefix;
    private Long userId;
    private String userName;
    private Long deptId;
    private String deptName;
    private List<String> allowedCategories;
    private String status;
    private LocalDateTime expireTime;
    private Integer maxDailyRequests;
    private Integer usedRequestsToday;
    private LocalDateTime lastUsedTime;
    private LocalDateTime lastRotatedTime;
    private Integer rotationCount;
    private String remark;
    private LocalDateTime createTime;
}
