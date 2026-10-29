package com.xfwang.govdata.metadata.harvest.infrastructure.archive;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xfwang.govdata.metadata.harvest.application.archive.HarvestRawArchivePort;
import com.xfwang.govdata.metadata.harvest.domain.entity.HarvestRawResult;
import com.xfwang.govdata.metadata.harvest.domain.exception.HarvestErrorCode;
import com.xfwang.govdata.metadata.harvest.infrastructure.persistence.mapper.HarvestRawResultMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.oss.entity.UploadResult;
import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.factory.OssFactory;
import org.dromara.common.tenant.helper.TenantHelper;
import org.slf4j.MDC;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

/**
 * 将受控采集响应归档到对象存储，控制库只保存对象引用与校验信息。
 */
@Component
@Slf4j
@RequiredArgsConstructor
@EnableConfigurationProperties(HarvestRawArchiveProperties.class)
public class HarvestRawResultArchiver implements HarvestRawArchivePort {
    private final ObjectMapper objectMapper;
    private final HarvestRawResultMapper rawResultMapper;
    private final HarvestRawArchiveProperties properties;

    @Override
    public void archive(Long harvestRunId, int batchNo, Object batch) {
        if (!properties.isEnabled()) return;
        byte[] content = serialize(batch);
        if (content.length > properties.getMaxBytes()) {
            throw new ServiceException(HarvestErrorCode.RAW_RESULT_TOO_LARGE);
        }
        OssClient storage;
        UploadResult uploaded;
        try {
            storage = OssFactory.instance();
            uploaded = storage.uploadSuffix(content, ".json", "application/json");
        } catch (Exception exception) {
            throw new ServiceException(HarvestErrorCode.RAW_ARCHIVE_FAILED);
        }
        if (uploaded == null || StringUtils.isBlank(uploaded.getFilename())) {
            throw new ServiceException(HarvestErrorCode.RAW_ARCHIVE_FAILED);
        }
        String archiveRef = storage.getConfigKey() + "::" + uploaded.getFilename();
        if (archiveRef.length() > 500) {
            compensateUploadedObject(storage, uploaded.getFilename());
            throw new ServiceException(HarvestErrorCode.RAW_ARCHIVE_FAILED);
        }
        HarvestRawResult result = new HarvestRawResult();
        result.setTenantId(TenantHelper.getTenantId());
        result.setRawResultId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
        result.setHarvestRunId(harvestRunId);
        result.setBatchNo(batchNo);
        result.setObjectStorageRef(archiveRef);
        result.setContentHash(sha256(content));
        result.setContentSize((long) content.length);
        result.setContentFormat("JSON");
        result.setRetentionExpireTime(LocalDateTime.now().plusDays(properties.getRetentionDays()));
        result.setTraceId(MDC.get("traceId"));
        try {
            rawResultMapper.insert(result);
        } catch (Exception exception) {
            compensateUploadedObject(storage, uploaded.getFilename());
            throw new ServiceException(HarvestErrorCode.RAW_ARCHIVE_FAILED);
        }
    }

    /**
     * 分批清理过期对象；先删除对象，再在所属租户下删除控制库引用。
     */
    @Override
    public int cleanupExpired() {
        int batchSize = Math.max(1, Math.min(properties.getCleanupBatchSize(), 500));
        List<HarvestRawResult> expired = TenantHelper.ignore(() -> rawResultMapper.selectList(
            new LambdaQueryWrapper<HarvestRawResult>()
                .le(HarvestRawResult::getRetentionExpireTime, LocalDateTime.now())
                .orderByAsc(HarvestRawResult::getRetentionExpireTime)
                .orderByAsc(HarvestRawResult::getRawResultId)
                .last("LIMIT " + batchSize)));
        int deleted = 0;
        for (HarvestRawResult result : expired) {
            try {
                deleteArchivedObject(result.getObjectStorageRef());
                TenantHelper.dynamic(result.getTenantId(), () -> rawResultMapper.deleteById(result.getRawResultId()));
                deleted++;
            } catch (Exception exception) {
                log.warn("元数据采集原始归档清理失败 rawResultId={}", result.getRawResultId());
            }
        }
        return deleted;
    }

    private byte[] serialize(Object batch) {
        try {
            return objectMapper.writeValueAsString(batch).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException exception) {
            throw new ServiceException(HarvestErrorCode.RAW_ARCHIVE_FAILED);
        }
    }

    private void compensateUploadedObject(OssClient storage, String objectRef) {
        try {
            storage.delete(objectRef);
        } catch (Exception exception) {
            log.warn("元数据采集原始归档引用落库失败且对象补偿删除失败");
        }
    }

    private void deleteArchivedObject(String archiveRef) {
        int separator = archiveRef.indexOf("::");
        if (separator < 1) {
            OssFactory.instance().delete(archiveRef);
            return;
        }
        String configKey = archiveRef.substring(0, separator);
        String objectKey = archiveRef.substring(separator + 2);
        OssFactory.instance(configKey).delete(objectKey);
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256 实现", exception);
        }
    }
}
