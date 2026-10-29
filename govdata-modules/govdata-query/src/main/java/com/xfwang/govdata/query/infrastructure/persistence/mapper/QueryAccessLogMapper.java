package com.xfwang.govdata.query.infrastructure.persistence.mapper;

import com.xfwang.govdata.query.domain.entity.QueryAccessLog;
import org.apache.ibatis.annotations.Param;

/** 仅暴露追加，不提供业务修改、删除能力。 */
public interface QueryAccessLogMapper {
    int insert(@Param("record") QueryAccessLog record);
}
