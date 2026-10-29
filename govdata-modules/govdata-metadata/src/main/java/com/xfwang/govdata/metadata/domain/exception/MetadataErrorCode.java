package com.xfwang.govdata.metadata.domain.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.dromara.common.core.exception.ErrorCode;

/** 元数据目录稳定异常编码。 */
@Getter
@AllArgsConstructor
public enum MetadataErrorCode implements ErrorCode {
    INVALID_OBSERVATION("GOV-META-0400-02", "元数据观察批次无效", 400),
    OBSERVATION_TOO_LARGE("GOV-META-0400-03", "元数据观察批次超过限制", 400),
    PARENT_ASSET_NOT_FOUND("GOV-META-0404-02", "元数据父资产不存在", 404),
    ASSET_TYPE_INVALID("GOV-META-0400-01", "元数据资产类型无效", 400),
    ASSET_NOT_FOUND("GOV-META-0404", "元数据资产不存在", 404),
    INVALID_GOVERNANCE("GOV-META-0400-04", "元数据治理属性无效", 400),
    GOVERNANCE_CONFLICT("GOV-META-0409", "元数据治理属性已被其他用户修改", 409),
    INVALID_IMPACT_PROJECTION("GOV-META-0400-05", "元数据下游影响投影无效", 400),
    RESOURCE_PROJECTION_FAILED("GOV-META-0501", "元数据授权资源投影失败", 500);

    private final String code;
    private final String message;
    private final int httpStatus;
}
