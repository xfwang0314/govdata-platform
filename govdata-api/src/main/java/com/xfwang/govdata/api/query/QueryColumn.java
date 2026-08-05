package com.xfwang.govdata.api.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/** 查询结果列描述，不暴露外部数据库物理列名。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryColumn implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    private Long fieldId;
    private String fieldCode;
    private String displayName;
    private Boolean masked;
}
