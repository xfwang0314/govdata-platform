package com.xfwang.platform.mcp.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * Agent 访问凭据实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("authz_agent_token")
public class AuthzAgentToken extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 凭据雪花ID */
    @TableId
    private Long tokenId;

    /** Agent名称 (如: 张三的Cursor数据助手) */
    private String agentName;

    /** Agent稳定业务编码 */
    private String agentCode;

    /** Token哈希摘要 (SHA-256防明文泄露) */
    private String tokenHash;

    /** Token脱敏前缀 (如 mcp_sk_live_1234) */
    private String tokenPrefix;

    /** 归属用户ID */
    private Long userId;

    /** 归属部门ID快照 */
    private Long deptId;

    /** 允许调用的 MCP 模块分类 (英文逗号分隔，如: DATASOURCE,STANDARD) */
    private String allowedCategories;

    /** 状态 (ACTIVE: 启用, DISABLED: 禁用, REVOKED: 已吊销) */
    private String status;

    /** 过期时间 (null 表示长期有效) */
    private LocalDateTime expireTime;

    /** 每日最大请求配额 */
    private Integer maxDailyRequests;

    /** 今日已用请求数 */
    private Integer usedRequestsToday;

    /** 最后活跃时间 */
    private LocalDateTime lastUsedTime;

    /** 最后轮换时间 */
    private LocalDateTime lastRotatedTime;

    /** 累计轮换次数 */
    private Integer rotationCount;

    /** 用途备注 */
    private String remark;

    /** 逻辑删除标记 */
    @TableLogic
    private String delFlag;
}
