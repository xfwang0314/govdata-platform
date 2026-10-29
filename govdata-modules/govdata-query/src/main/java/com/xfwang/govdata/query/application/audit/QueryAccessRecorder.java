package com.xfwang.govdata.query.application.audit;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.xfwang.govdata.query.domain.entity.QueryAccessLog;
import com.xfwang.govdata.query.domain.exception.QueryErrorCode;
import com.xfwang.govdata.query.infrastructure.persistence.mapper.QueryAccessLogMapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 与外部查询分离的独立短事务；写入失败关闭数据通路。 */
@Service
@RequiredArgsConstructor
public class QueryAccessRecorder {
    private final QueryAccessLogMapper mapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void append(QueryAccessLog record) {
        record.setAccessLogId(IdWorker.getId());
        try {
            if (mapper.insert(record) != 1) throw new ServiceException(QueryErrorCode.AUDIT_UNAVAILABLE);
        } catch (RuntimeException ignored) {
            throw new ServiceException(QueryErrorCode.AUDIT_UNAVAILABLE);
        }
    }
}
