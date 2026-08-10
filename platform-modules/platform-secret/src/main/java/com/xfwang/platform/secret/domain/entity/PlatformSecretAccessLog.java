package com.xfwang.platform.secret.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 平台凭据解析访问审计日志实体类。
 * <p>
 * 对应数据库表 {@code platform_secret_access_log}。采用只读追加写的方式，记录所有凭据解析尝试
 * （包含成功与越权失败），仅记录凭据句柄、调用模块、主体 ID、解析用途及结果原因代码，绝对不包含凭据明文。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("platform_secret_access_log")
public class PlatformSecretAccessLog extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 访问日志主键雪花 ID
     */
    @TableId
    private Long accessLogId;

    /**
     * 关联的凭据主键 ID
     */
    private Long secretId;

    /**
     * 解析尝试时的凭据句柄快照（如 "secret:sec_xxx"）
     */
    private String secretRefSnapshot;

    /**
     * 发起解析请求的调用业务模块编码（如 "govdata-datasource"）
     */
    private String callerModule;

    /**
     * 发起解析的主体类型（如 USER、SYSTEM_JOB、AGENT）
     */
    private String actorType;

    /**
     * 发起解析的主体稳定 ID（如用户 ID 或系统任务编码）
     */
    private String actorId;

    /**
     * 解析请求申明的用途编码（如 "DATASOURCE_CONNECTION_TEST"）
     */
    private String purpose;

    /**
     * 解析结果：SUCCESS（成功）、FAILED（失败）
     */
    private String result;

    /**
     * 稳定原因代码（如 RESOLVED、SECRET_DISABLED、PURPOSE_NOT_ALLOWED 等）
     */
    private String reasonCode;

    /**
     * 全局链路追踪 ID（TraceId），便于分布式日志关联排查
     */
    private String traceId;

    /**
     * 访问事件发生时间
     */
    private LocalDateTime occurredTime;
}
