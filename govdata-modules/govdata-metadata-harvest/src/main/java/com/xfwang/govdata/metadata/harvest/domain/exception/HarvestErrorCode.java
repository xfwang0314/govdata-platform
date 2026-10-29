package com.xfwang.govdata.metadata.harvest.domain.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.dromara.common.core.exception.ErrorCode;

/** 元数据采集稳定异常编码。 */
@Getter
@AllArgsConstructor
public enum HarvestErrorCode implements ErrorCode {
    PLAN_NOT_FOUND("GOV-HARVEST-0404", "采集计划不存在", 404),
    RUN_NOT_FOUND("GOV-HARVEST-0404-01", "采集运行不存在", 404),
    INVALID_PLAN("GOV-HARVEST-0400", "采集计划配置无效", 400),
    PLAN_DISABLED("GOV-HARVEST-0409", "采集计划未启用", 409),
    RUN_CONFLICT("GOV-HARVEST-0409-01", "采集计划已有运行正在执行", 409),
    MASS_MISSING_SUPPRESSED("GOV-HARVEST-0409-02", "采集结果出现大规模资产消失，已暂停缺失生效", 409),
    SCHEDULER_FAILED("GOV-HARVEST-0500-01", "采集计划调度器操作失败", 500),
    RAW_RESULT_TOO_LARGE("GOV-HARVEST-0413", "采集原始结果超过归档大小限制", 413),
    RAW_ARCHIVE_FAILED("GOV-HARVEST-0502-01", "采集原始结果归档失败", 502),
    DISCOVERY_FAILED("GOV-HARVEST-0502", "数据源元数据发现失败", 502);

    private final String code;
    private final String message;
    private final int httpStatus;
}
