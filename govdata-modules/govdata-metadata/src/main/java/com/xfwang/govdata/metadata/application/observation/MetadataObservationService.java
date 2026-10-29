package com.xfwang.govdata.metadata.application.observation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xfwang.govdata.api.metadata.CollectedMetadataColumn;
import com.xfwang.govdata.api.metadata.CollectedMetadataNamespace;
import com.xfwang.govdata.api.metadata.CollectedMetadataTable;
import com.xfwang.govdata.api.metadata.MetadataBatchWriteResult;
import com.xfwang.govdata.api.metadata.MetadataObjectType;
import com.xfwang.govdata.api.metadata.MetadataObservationContext;
import com.xfwang.govdata.api.metadata.MetadataObservationWriter;
import com.xfwang.govdata.api.metadata.MetadataRunCompletionResult;
import com.xfwang.govdata.metadata.application.authorization.event.MetadataAssetProjectionEvent;
import com.xfwang.govdata.metadata.domain.entity.MetadataAsset;
import com.xfwang.govdata.metadata.domain.entity.MetadataCatalog;
import com.xfwang.govdata.metadata.domain.entity.MetadataChangeIssue;
import com.xfwang.govdata.metadata.domain.entity.MetadataColumn;
import com.xfwang.govdata.metadata.domain.entity.MetadataSchema;
import com.xfwang.govdata.metadata.domain.entity.MetadataTable;
import com.xfwang.govdata.metadata.domain.entity.MetadataTableRevision;
import com.xfwang.govdata.metadata.domain.entity.MetadataTableRevisionColumn;
import com.xfwang.govdata.metadata.domain.enums.MetadataChangeLevel;
import com.xfwang.govdata.metadata.domain.enums.MetadataChangeIssueType;
import com.xfwang.govdata.metadata.domain.enums.MetadataTechnicalStatus;
import com.xfwang.govdata.metadata.domain.exception.MetadataErrorCode;
import com.xfwang.govdata.metadata.domain.service.MetadataMissingGuard;
import com.xfwang.govdata.metadata.infrastructure.persistence.mapper.MetadataAssetMapper;
import com.xfwang.govdata.metadata.infrastructure.persistence.mapper.MetadataCatalogMapper;
import com.xfwang.govdata.metadata.infrastructure.persistence.mapper.MetadataChangeIssueMapper;
import com.xfwang.govdata.metadata.infrastructure.persistence.mapper.MetadataColumnMapper;
import com.xfwang.govdata.metadata.infrastructure.persistence.mapper.MetadataSchemaMapper;
import com.xfwang.govdata.metadata.infrastructure.persistence.mapper.MetadataTableMapper;
import com.xfwang.govdata.metadata.infrastructure.persistence.mapper.MetadataTableRevisionColumnMapper;
import com.xfwang.govdata.metadata.infrastructure.persistence.mapper.MetadataTableRevisionMapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.tenant.helper.TenantHelper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 可信当前元数据的批量写入和自动修订服务。 */
@Service
@RequiredArgsConstructor
public class MetadataObservationService implements MetadataObservationWriter {

    private static final int MAX_NAMESPACE_BATCH = 500;
    private static final int MAX_TABLE_BATCH = 500;
    private static final int MAX_COLUMNS_PER_TABLE = 2_000;
    private static final int QUERY_CHUNK = 500;

    private final MetadataAssetMapper assetMapper;
    private final MetadataCatalogMapper catalogMapper;
    private final MetadataSchemaMapper schemaMapper;
    private final MetadataTableMapper tableMapper;
    private final MetadataColumnMapper columnMapper;
    private final MetadataTableRevisionMapper revisionMapper;
    private final MetadataTableRevisionColumnMapper revisionColumnMapper;
    private final MetadataChangeIssueMapper changeIssueMapper;
    private final MetadataMissingGuardProperties missingGuardProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MetadataBatchWriteResult writeNamespaces(MetadataObservationContext context,
                                                     List<CollectedMetadataNamespace> namespaces) {
        validateContext(context);
        List<CollectedMetadataNamespace> batch = namespaces == null ? List.of() : List.copyOf(namespaces);
        if (batch.size() > MAX_NAMESPACE_BATCH) throw new ServiceException(MetadataErrorCode.OBSERVATION_TOO_LARGE);
        validateNamespaces(batch);

        Set<String> lookupKeys = new HashSet<>();
        batch.forEach(value -> {
            lookupKeys.add(value.getCanonicalKey());
            if (value.getParentCanonicalKey() != null) lookupKeys.add(value.getParentCanonicalKey());
        });
        Set<String> nativeIds = batch.stream().map(CollectedMetadataNamespace::getNativeObjectId)
            .filter(StringUtils::isNotBlank).collect(Collectors.toSet());
        List<MetadataAsset> foundAssets = findAssets(context.getDatasourceId(), lookupKeys, nativeIds);
        Map<String, MetadataAsset> assetsByKey = foundAssets.stream()
            .collect(Collectors.toMap(MetadataAsset::getCanonicalKey, Function.identity()));
        Map<String, MetadataAsset> assetsByNative = nativeAssetMap(foundAssets);
        List<MetadataAsset> assets = new ArrayList<>();
        List<MetadataCatalog> catalogs = new ArrayList<>();
        List<MetadataSchema> schemas = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        batch.stream().sorted(Comparator.comparingInt(value -> value.getNamespaceType() == MetadataObjectType.CATALOG ? 0 : 1))
            .forEach(value -> {
                MetadataAsset parent = parent(assetsByKey, value.getParentCanonicalKey());
                MetadataAsset existing = lookupAsset(assetsByKey, assetsByNative, value.getNamespaceType(),
                    value.getCanonicalKey(), value.getNativeObjectId());
                MetadataAsset asset = currentAsset(context, existing,
                    value.getNamespaceType(), value.getCanonicalKey(), value.getNativeObjectId(), value.getNamespaceName(),
                    parent == null ? null : parent.getAssetId(), hash(namespaceStructure(value)),
                    hash(namespaceContent(value)), now);
                assetsByKey.put(asset.getCanonicalKey(), asset);
                putNative(assetsByNative, asset);
                assets.add(asset);
                if (value.getNamespaceType() == MetadataObjectType.CATALOG) {
                    MetadataCatalog detail = new MetadataCatalog();
                    detail.setAssetId(asset.getAssetId());
                    detail.setTenantId(context.getTenantId());
                    detail.setCatalogName(value.getNamespaceName().trim());
                    catalogs.add(detail);
                } else {
                    MetadataSchema detail = new MetadataSchema();
                    detail.setAssetId(asset.getAssetId());
                    detail.setTenantId(context.getTenantId());
                    detail.setSchemaName(value.getNamespaceName().trim());
                    detail.setSchemaOwner(StringUtils.trimToNull(value.getNamespaceOwner()));
                    schemas.add(detail);
                }
            });
        saveAssets(assets);
        if (!catalogs.isEmpty()) catalogMapper.insertOrUpdateBatch(catalogs, MAX_NAMESPACE_BATCH);
        if (!schemas.isEmpty()) schemaMapper.insertOrUpdateBatch(schemas, MAX_NAMESPACE_BATCH);
        assets.forEach(value -> publishProjection(value, context));
        return MetadataBatchWriteResult.builder().observedCount(batch.size()).changedTableCount(0)
            .breakingChangeCount(0).build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MetadataBatchWriteResult writeTables(MetadataObservationContext context,
                                                 List<CollectedMetadataTable> tables) {
        validateContext(context);
        List<CollectedMetadataTable> batch = tables == null ? List.of() : List.copyOf(tables);
        if (batch.size() > MAX_TABLE_BATCH) throw new ServiceException(MetadataErrorCode.OBSERVATION_TOO_LARGE);
        validateTables(batch);

        Set<String> lookupKeys = new HashSet<>();
        batch.forEach(table -> {
            lookupKeys.add(table.getCanonicalKey());
            if (table.getParentCanonicalKey() != null) lookupKeys.add(table.getParentCanonicalKey());
            table.getColumns().forEach(column -> lookupKeys.add(column.getCanonicalKey()));
        });
        Set<String> nativeIds = new HashSet<>();
        batch.forEach(table -> {
            if (StringUtils.isNotBlank(table.getNativeObjectId())) nativeIds.add(table.getNativeObjectId());
            table.getColumns().stream().map(CollectedMetadataColumn::getNativeObjectId)
                .filter(StringUtils::isNotBlank).forEach(nativeIds::add);
        });
        List<MetadataAsset> foundAssets = findAssets(context.getDatasourceId(), lookupKeys, nativeIds);
        Map<String, MetadataAsset> assetsByKey = foundAssets.stream()
            .collect(Collectors.toMap(MetadataAsset::getCanonicalKey, Function.identity()));
        Map<String, MetadataAsset> assetsByNative = nativeAssetMap(foundAssets);
        List<Long> existingTableIds = batch.stream()
            .map(value -> lookupAsset(assetsByKey, assetsByNative, MetadataObjectType.TABLE,
                value.getCanonicalKey(), value.getNativeObjectId()))
            .filter(Objects::nonNull).map(MetadataAsset::getAssetId).distinct().toList();
        List<MetadataAsset> existingColumnAssets = findChildAssets(existingTableIds);
        existingColumnAssets.forEach(value -> assetsByKey.put(value.getCanonicalKey(), value));
        existingColumnAssets.forEach(value -> putNative(assetsByNative, value));
        Map<Long, MetadataColumn> currentColumns = findColumns(existingTableIds).stream()
            .collect(Collectors.toMap(MetadataColumn::getAssetId, Function.identity()));

        List<MetadataAsset> assets = new ArrayList<>();
        List<MetadataTable> tableDetails = new ArrayList<>();
        List<MetadataColumn> columnDetails = new ArrayList<>();
        List<MetadataTableRevision> revisions = new ArrayList<>();
        List<MetadataTableRevisionColumn> revisionColumns = new ArrayList<>();
        List<MetadataChangeIssue> issues = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        int breakingCount = 0;

        for (CollectedMetadataTable observed : batch) {
            MetadataAsset parent = parent(assetsByKey, observed.getParentCanonicalKey());
            List<ColumnState> columnStates = observed.getColumns().stream()
                .sorted(Comparator.comparing(CollectedMetadataColumn::getOrdinalPosition))
                .map(value -> columnState(value, lookupAsset(assetsByKey, assetsByNative, MetadataObjectType.COLUMN,
                    value.getCanonicalKey(), value.getNativeObjectId()), currentColumns)).toList();
            String structureHash = hash(tableStructure(observed, columnStates));
            String contentHash = hash(tableContent(observed, columnStates));
            MetadataAsset previousTable = lookupAsset(assetsByKey, assetsByNative, MetadataObjectType.TABLE,
                observed.getCanonicalKey(), observed.getNativeObjectId());
            Integer previousRevisionNo = previousTable == null ? null : previousTable.getCurrentRevisionNo();
            boolean revised = previousTable == null
                || !MetadataTechnicalStatus.ACTIVE.getCode().equals(previousTable.getTechnicalStatus())
                || !Objects.equals(previousTable.getContentHash(), contentHash);
            Map<Long, MetadataColumn> previousTableColumns = previousColumns(previousTable, assetsByKey, currentColumns);
            MetadataChangeLevel level = classify(previousTable, observed, structureHash, contentHash, columnStates,
                previousTableColumns);
            MetadataAsset tableAsset = currentAsset(context, previousTable, MetadataObjectType.TABLE,
                observed.getCanonicalKey(), observed.getNativeObjectId(), observed.getTableName(),
                parent == null ? null : parent.getAssetId(), structureHash, contentHash, now);
            int revisionNo = previousRevisionNo == null ? 1 : previousRevisionNo + (revised ? 1 : 0);
            tableAsset.setCurrentRevisionNo(revisionNo);
            assetsByKey.put(tableAsset.getCanonicalKey(), tableAsset);
            putNative(assetsByNative, tableAsset);
            assets.add(tableAsset);
            tableDetails.add(tableDetail(context.getTenantId(), tableAsset.getAssetId(), observed));

            Set<Long> observedColumnIds = new HashSet<>();
            for (ColumnState state : columnStates) {
                MetadataAsset columnAsset = currentAsset(context, state.asset(), MetadataObjectType.COLUMN,
                    state.source().getCanonicalKey(), state.source().getNativeObjectId(), state.source().getColumnName(),
                    tableAsset.getAssetId(), state.structureHash(), state.contentHash(), now);
                assetsByKey.put(columnAsset.getCanonicalKey(), columnAsset);
                putNative(assetsByNative, columnAsset);
                assets.add(columnAsset);
                observedColumnIds.add(columnAsset.getAssetId());
                MetadataColumn previous = currentColumns.get(columnAsset.getAssetId());
                MetadataColumn current = columnDetail(context.getTenantId(), columnAsset.getAssetId(),
                    tableAsset.getAssetId(), state, previous, context.getHarvestRunId(), now);
                currentColumns.put(current.getAssetId(), current);
                columnDetails.add(current);
            }
            markMissingColumns(context, previousTableColumns, observedColumnIds, assetsByKey, assets, columnDetails, now);

            if (revised) {
                MetadataTableRevision revision = revision(context, tableAsset, previousRevisionNo, level, now,
                    differenceSummary(previousTable, columnStates, previousTableColumns));
                revisions.add(revision);
                for (ColumnState state : columnStates) {
                    MetadataAsset columnAsset = assetsByKey.get(state.source().getCanonicalKey());
                    revisionColumns.add(revisionColumn(context.getTenantId(), revision.getTableRevisionId(),
                        columnAsset, currentColumns.get(columnAsset.getAssetId()), state));
                }
                if (level == MetadataChangeLevel.BREAKING) {
                    breakingCount++;
                    issues.add(issue(context, tableAsset, previousRevisionNo, revision, now));
                }
            }
        }

        saveAssets(assets);
        if (!tableDetails.isEmpty()) tableMapper.insertOrUpdateBatch(tableDetails, MAX_TABLE_BATCH);
        if (!columnDetails.isEmpty()) columnMapper.insertOrUpdateBatch(columnDetails, MAX_TABLE_BATCH);
        if (!revisions.isEmpty()) revisionMapper.insertBatch(revisions, MAX_TABLE_BATCH);
        if (!revisionColumns.isEmpty()) revisionColumnMapper.insertBatch(revisionColumns, MAX_TABLE_BATCH);
        if (!issues.isEmpty()) insertIssuesIgnoringActiveDuplicates(issues);
        assets.stream().filter(value -> MetadataObjectType.TABLE.getCode().equals(value.getAssetType()))
            .forEach(value -> publishProjection(value, context));
        return MetadataBatchWriteResult.builder().observedCount(batch.size()).changedTableCount(revisions.size())
            .breakingChangeCount(breakingCount).build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MetadataRunCompletionResult completeRun(MetadataObservationContext context, boolean complete,
                                                     boolean authoritativeFullScope) {
        validateContext(context);
        if (!complete || !authoritativeFullScope) return MetadataRunCompletionResult.skipped();
        int activeTableCount = Math.toIntExact(assetMapper.selectCount(new LambdaQueryWrapper<MetadataAsset>()
            .eq(MetadataAsset::getDatasourceId, context.getDatasourceId())
            .eq(MetadataAsset::getAssetType, MetadataObjectType.TABLE.getCode())
            .eq(MetadataAsset::getTechnicalStatus, MetadataTechnicalStatus.ACTIVE.getCode())));
        int missingCandidateCount = Math.toIntExact(assetMapper.selectCount(new LambdaQueryWrapper<MetadataAsset>()
            .eq(MetadataAsset::getDatasourceId, context.getDatasourceId())
            .eq(MetadataAsset::getAssetType, MetadataObjectType.TABLE.getCode())
            .ne(MetadataAsset::getLastHarvestRunId, context.getHarvestRunId())
            .eq(MetadataAsset::getTechnicalStatus, MetadataTechnicalStatus.ACTIVE.getCode())));
        if (missingGuardProperties.isEnabled() && MetadataMissingGuard.shouldSuppress(activeTableCount,
            missingCandidateCount, missingGuardProperties.getMinimumBaselineCount(),
            missingGuardProperties.getMinimumMissingCount(), missingGuardProperties.getMaximumMissingRate())) {
            return completion(activeTableCount, missingCandidateCount, 0, true);
        }
        createMissingIssues(context);
        long lastAssetId = 0L;
        while (true) {
            List<Long> missingColumnIds = assetMapper.selectList(new LambdaQueryWrapper<MetadataAsset>()
                    .select(MetadataAsset::getAssetId)
                    .eq(MetadataAsset::getDatasourceId, context.getDatasourceId())
                    .eq(MetadataAsset::getAssetType, MetadataObjectType.COLUMN.getCode())
                    .ne(MetadataAsset::getLastHarvestRunId, context.getHarvestRunId())
                    .eq(MetadataAsset::getTechnicalStatus, MetadataTechnicalStatus.ACTIVE.getCode())
                    .gt(MetadataAsset::getAssetId, lastAssetId)
                    .orderByAsc(MetadataAsset::getAssetId)
                    .last("LIMIT " + QUERY_CHUNK))
                .stream().map(MetadataAsset::getAssetId).toList();
            if (missingColumnIds.isEmpty()) break;
            columnMapper.update(null, new LambdaUpdateWrapper<MetadataColumn>()
                .in(MetadataColumn::getAssetId, missingColumnIds)
                .set(MetadataColumn::getTechnicalStatus, MetadataTechnicalStatus.MISSING.getCode())
                .set(MetadataColumn::getUpdateTime, LocalDateTime.now()));
            lastAssetId = missingColumnIds.get(missingColumnIds.size() - 1);
        }
        assetMapper.update(null, new LambdaUpdateWrapper<MetadataAsset>()
            .eq(MetadataAsset::getDatasourceId, context.getDatasourceId())
            .ne(MetadataAsset::getLastHarvestRunId, context.getHarvestRunId())
            .eq(MetadataAsset::getTechnicalStatus, MetadataTechnicalStatus.ACTIVE.getCode())
            .set(MetadataAsset::getTechnicalStatus, MetadataTechnicalStatus.MISSING.getCode())
            .set(MetadataAsset::getUpdateTime, LocalDateTime.now()));
        return completion(activeTableCount, missingCandidateCount, missingCandidateCount, false);
    }

    private MetadataRunCompletionResult completion(int activeTableCount, int candidateCount, int markedCount,
                                                    boolean suppressed) {
        return MetadataRunCompletionResult.builder()
            .activeAssetCount(activeTableCount)
            .missingCandidateCount(candidateCount)
            .missingMarkedCount(markedCount)
            .missingSuppressed(suppressed)
            .build();
    }

    private void validateContext(MetadataObservationContext context) {
        if (context == null || context.getHarvestRunId() == null || context.getDatasourceId() == null
            || context.getDatasourceResourceId() == null || context.getOwnerDeptId() == null
            || StringUtils.isBlank(context.getTenantId()) || StringUtils.isBlank(context.getConnectorCode())
            || StringUtils.isBlank(context.getConnectorVersion())
            || !Objects.equals(TenantHelper.getTenantId(), context.getTenantId())) {
            throw new ServiceException(MetadataErrorCode.INVALID_OBSERVATION);
        }
    }

    private void validateNamespaces(List<CollectedMetadataNamespace> values) {
        Set<String> keys = new HashSet<>();
        for (CollectedMetadataNamespace value : values) {
            if (value == null || value.getNamespaceType() == null || !value.getNamespaceType().isNamespace()
                || StringUtils.isBlank(value.getNamespaceName()) || StringUtils.isBlank(value.getCanonicalKey())
                || !keys.add(value.getCanonicalKey())
                || (value.getNamespaceType() == MetadataObjectType.CATALOG && value.getParentCanonicalKey() != null)) {
                throw new ServiceException(MetadataErrorCode.INVALID_OBSERVATION);
            }
        }
    }

    private void validateTables(List<CollectedMetadataTable> values) {
        Set<String> tableKeys = new HashSet<>();
        for (CollectedMetadataTable table : values) {
            if (table == null || StringUtils.isBlank(table.getCanonicalKey()) || StringUtils.isBlank(table.getTableName())
                || StringUtils.isBlank(table.getTableType()) || !tableKeys.add(table.getCanonicalKey())
                || table.getColumns() == null || table.getColumns().size() > MAX_COLUMNS_PER_TABLE) {
                throw new ServiceException(MetadataErrorCode.INVALID_OBSERVATION);
            }
            Set<String> columnKeys = new HashSet<>();
            for (CollectedMetadataColumn column : table.getColumns()) {
                if (column == null || StringUtils.isBlank(column.getCanonicalKey())
                    || StringUtils.isBlank(column.getColumnName()) || StringUtils.isBlank(column.getDataType())
                    || column.getOrdinalPosition() == null || column.getOrdinalPosition() <= 0
                    || !columnKeys.add(column.getCanonicalKey())) {
                    throw new ServiceException(MetadataErrorCode.INVALID_OBSERVATION);
                }
            }
        }
    }

    private MetadataAsset currentAsset(MetadataObservationContext context, MetadataAsset existing,
                                       MetadataObjectType type, String key, String nativeId, String name,
                                       Long parentId, String structureHash, String contentHash, LocalDateTime now) {
        MetadataAsset value = existing == null ? new MetadataAsset() : existing;
        value.setTenantId(context.getTenantId());
        if (existing == null) {
            long id = IdWorker.getId();
            value.setAssetId(id);
            value.setAssetCode("mda_" + Long.toUnsignedString(id, 36));
            value.setAssetType(type.getCode());
            value.setDatasourceId(context.getDatasourceId());
            value.setDisplayName(null);
            value.setBusinessName(null);
            value.setDescription(null);
            value.setOwnerDeptId(context.getOwnerDeptId());
            value.setClassificationCode("INTERNAL");
            value.setSecurityLevel(1);
            value.setGovernanceStatus("UNMANAGED");
            value.setFirstSeenTime(now);
            value.setLockVersion(0);
            value.setDelFlag("0");
        }
        value.setCanonicalKey(key);
        value.setNativeObjectId(StringUtils.trimToNull(nativeId));
        value.setParentAssetId(parentId);
        value.setConnectorCode(context.getConnectorCode());
        value.setConnectorVersion(context.getConnectorVersion());
        value.setOriginalName(name.trim());
        value.setTechnicalStatus(MetadataTechnicalStatus.ACTIVE.getCode());
        value.setLastSeenTime(now);
        value.setLastHarvestRunId(context.getHarvestRunId());
        value.setStructureHash(structureHash);
        value.setContentHash(contentHash);
        return value;
    }

    private MetadataAsset parent(Map<String, MetadataAsset> assets, String parentKey) {
        if (parentKey == null) return null;
        MetadataAsset parent = assets.get(parentKey);
        if (parent == null) throw new ServiceException(MetadataErrorCode.PARENT_ASSET_NOT_FOUND);
        return parent;
    }

    private List<MetadataAsset> findAssets(Long datasourceId, Set<String> keys, Set<String> nativeIds) {
        if (keys.isEmpty() && nativeIds.isEmpty()) return List.of();
        List<String> values = new ArrayList<>(keys);
        Map<Long, MetadataAsset> result = new LinkedHashMap<>();
        for (int from = 0; from < values.size(); from += QUERY_CHUNK) {
            assetMapper.selectList(new LambdaQueryWrapper<MetadataAsset>()
                .eq(MetadataAsset::getDatasourceId, datasourceId)
                .in(MetadataAsset::getCanonicalKey, values.subList(from, Math.min(from + QUERY_CHUNK, values.size()))))
                .forEach(value -> result.put(value.getAssetId(), value));
        }
        List<String> nativeValues = new ArrayList<>(nativeIds);
        for (int from = 0; from < nativeValues.size(); from += QUERY_CHUNK) {
            assetMapper.selectList(new LambdaQueryWrapper<MetadataAsset>()
                .eq(MetadataAsset::getDatasourceId, datasourceId)
                .in(MetadataAsset::getNativeObjectId,
                    nativeValues.subList(from, Math.min(from + QUERY_CHUNK, nativeValues.size()))))
                .forEach(value -> result.put(value.getAssetId(), value));
        }
        return List.copyOf(result.values());
    }

    private List<MetadataAsset> findChildAssets(List<Long> tableIds) {
        if (tableIds.isEmpty()) return List.of();
        List<MetadataAsset> result = new ArrayList<>();
        for (int from = 0; from < tableIds.size(); from += QUERY_CHUNK) {
            result.addAll(assetMapper.selectList(new LambdaQueryWrapper<MetadataAsset>()
                .eq(MetadataAsset::getAssetType, MetadataObjectType.COLUMN.getCode())
                .in(MetadataAsset::getParentAssetId,
                    tableIds.subList(from, Math.min(from + QUERY_CHUNK, tableIds.size())))));
        }
        return result;
    }

    private List<MetadataColumn> findColumns(List<Long> tableIds) {
        if (tableIds.isEmpty()) return List.of();
        List<MetadataColumn> result = new ArrayList<>();
        for (int from = 0; from < tableIds.size(); from += QUERY_CHUNK) {
            result.addAll(columnMapper.selectList(new LambdaQueryWrapper<MetadataColumn>()
                .in(MetadataColumn::getTableAssetId,
                    tableIds.subList(from, Math.min(from + QUERY_CHUNK, tableIds.size())))));
        }
        return result;
    }

    private void saveAssets(List<MetadataAsset> assets) {
        if (!assets.isEmpty()) assetMapper.insertOrUpdateBatch(distinctAssets(assets), MAX_TABLE_BATCH);
    }

    private List<MetadataAsset> distinctAssets(List<MetadataAsset> assets) {
        Map<Long, MetadataAsset> values = new LinkedHashMap<>();
        assets.forEach(value -> values.put(value.getAssetId(), value));
        return List.copyOf(values.values());
    }

    private ColumnState columnState(CollectedMetadataColumn source, MetadataAsset asset,
                                    Map<Long, MetadataColumn> currentColumns) {
        String structure = hash(String.join("|", upper(source.getDataType()), text(source.getLengthValue()),
            text(source.getPrecisionValue()), text(source.getScaleValue()), text(source.getNullableFlag()),
            text(source.getPrimaryKeyFlag()), text(source.getDefaultExpression()), text(source.getOrdinalPosition())));
        String content = hash(structure + '|' + text(source.getComment()));
        return new ColumnState(source, asset, asset == null ? null : currentColumns.get(asset.getAssetId()),
            structure, content);
    }

    private MetadataTable tableDetail(String tenantId, Long assetId, CollectedMetadataTable source) {
        MetadataTable value = new MetadataTable();
        value.setAssetId(assetId);
        value.setTenantId(tenantId);
        value.setTableType(upper(source.getTableType()));
        value.setCommentText(StringUtils.trimToNull(source.getComment()));
        value.setPartitionedFlag(false);
        return value;
    }

    private MetadataColumn columnDetail(String tenantId, Long assetId, Long tableId, ColumnState state,
                                        MetadataColumn previous, Long runId, LocalDateTime now) {
        CollectedMetadataColumn source = state.source();
        MetadataColumn value = previous == null ? new MetadataColumn() : previous;
        value.setAssetId(assetId);
        value.setTenantId(tenantId);
        value.setTableAssetId(tableId);
        value.setRevisionNo(previous == null ? 1 : previous.getRevisionNo()
            + (Objects.equals(previous.getContentHash(), state.contentHash())
            && MetadataTechnicalStatus.ACTIVE.getCode().equals(previous.getTechnicalStatus()) ? 0 : 1));
        value.setOrdinalPosition(source.getOrdinalPosition());
        value.setDataType(upper(source.getDataType()));
        value.setNativeType(source.getDataType().trim());
        value.setLengthValue(source.getLengthValue());
        value.setPrecisionValue(source.getPrecisionValue());
        value.setScaleValue(source.getScaleValue());
        value.setNullableFlag(Boolean.TRUE.equals(source.getNullableFlag()));
        value.setPrimaryKeyFlag(Boolean.TRUE.equals(source.getPrimaryKeyFlag()));
        value.setUniqueFlag(false);
        value.setAutoIncrementFlag(false);
        value.setDefaultExpression(StringUtils.trimToNull(source.getDefaultExpression()));
        value.setCommentText(StringUtils.trimToNull(source.getComment()));
        value.setTechnicalStatus(MetadataTechnicalStatus.ACTIVE.getCode());
        value.setStructureHash(state.structureHash());
        value.setContentHash(state.contentHash());
        if (previous == null) value.setFirstSeenTime(now);
        value.setLastSeenTime(now);
        value.setLastHarvestRunId(runId);
        return value;
    }

    private Map<Long, MetadataColumn> previousColumns(MetadataAsset previousTable,
                                                       Map<String, MetadataAsset> assetsByKey,
                                                       Map<Long, MetadataColumn> columns) {
        if (previousTable == null) return Map.of();
        return assetsByKey.values().stream()
            .filter(value -> Objects.equals(value.getParentAssetId(), previousTable.getAssetId()))
            .map(MetadataAsset::getAssetId).map(columns::get).filter(Objects::nonNull)
            .collect(Collectors.toMap(MetadataColumn::getAssetId, Function.identity()));
    }

    private void markMissingColumns(MetadataObservationContext context, Map<Long, MetadataColumn> previous,
                                    Set<Long> observedIds, Map<String, MetadataAsset> assetsByKey,
                                    List<MetadataAsset> assets, List<MetadataColumn> columns, LocalDateTime now) {
        Map<Long, MetadataAsset> assetsById = assetsByKey.values().stream()
            .collect(Collectors.toMap(MetadataAsset::getAssetId, Function.identity(), (left, right) -> left));
        previous.values().stream().filter(value -> !observedIds.contains(value.getAssetId()))
            .forEach(value -> {
                MetadataAsset asset = assetsById.get(value.getAssetId());
                if (asset != null && !MetadataTechnicalStatus.MISSING.getCode().equals(asset.getTechnicalStatus())) {
                    value.setTechnicalStatus(MetadataTechnicalStatus.MISSING.getCode());
                    value.setRevisionNo(value.getRevisionNo() + 1);
                    value.setLastHarvestRunId(context.getHarvestRunId());
                    columns.add(value);
                    asset.setTechnicalStatus(MetadataTechnicalStatus.MISSING.getCode());
                    asset.setLastHarvestRunId(context.getHarvestRunId());
                    assets.add(asset);
                }
            });
    }

    private MetadataChangeLevel classify(MetadataAsset previousTable, CollectedMetadataTable observed,
                                         String structureHash, String contentHash,
                                         List<ColumnState> current, Map<Long, MetadataColumn> previous) {
        if (previousTable == null) return MetadataChangeLevel.NON_BREAKING;
        if (!MetadataTechnicalStatus.ACTIVE.getCode().equals(previousTable.getTechnicalStatus())) {
            return MetadataChangeLevel.NON_BREAKING;
        }
        if (Objects.equals(previousTable.getContentHash(), contentHash)) return MetadataChangeLevel.DOCUMENTATION_ONLY;
        if (Objects.equals(previousTable.getStructureHash(), structureHash)) return MetadataChangeLevel.DOCUMENTATION_ONLY;
        if (!Objects.equals(previousTable.getOriginalName(), observed.getTableName().trim())) {
            return MetadataChangeLevel.BREAKING;
        }
        Set<Long> currentIds = current.stream().map(ColumnState::asset).filter(Objects::nonNull)
            .map(MetadataAsset::getAssetId).collect(Collectors.toSet());
        if (previous.keySet().stream().anyMatch(id -> !currentIds.contains(id))) return MetadataChangeLevel.BREAKING;
        for (ColumnState state : current) {
            MetadataColumn before = state.previous();
            CollectedMetadataColumn after = state.source();
            if (before != null && (!Objects.equals(state.asset().getOriginalName(), after.getColumnName().trim())
                || !Objects.equals(upper(before.getDataType()), upper(after.getDataType()))
                || decreased(before.getLengthValue(), after.getLengthValue())
                || decreased(before.getPrecisionValue(), after.getPrecisionValue())
                || !Objects.equals(before.getScaleValue(), after.getScaleValue())
                || (Boolean.TRUE.equals(before.getNullableFlag()) && !Boolean.TRUE.equals(after.getNullableFlag()))
                || !Objects.equals(before.getPrimaryKeyFlag(), Boolean.TRUE.equals(after.getPrimaryKeyFlag())))) {
                return MetadataChangeLevel.BREAKING;
            }
        }
        return MetadataChangeLevel.NON_BREAKING;
    }

    private boolean decreased(Integer before, Integer after) {
        return before != null && after != null && after < before;
    }

    private MetadataTableRevision revision(MetadataObservationContext context, MetadataAsset table,
                                           Integer previousRevisionNo, MetadataChangeLevel level, LocalDateTime now,
                                           Map<String, Object> summary) {
        MetadataTableRevision value = new MetadataTableRevision();
        value.setTableRevisionId(IdWorker.getId());
        value.setTenantId(context.getTenantId());
        value.setTableAssetId(table.getAssetId());
        value.setRevisionNo(table.getCurrentRevisionNo());
        value.setPreviousRevisionNo(previousRevisionNo);
        value.setHarvestRunId(context.getHarvestRunId());
        value.setChangeLevel(level.getCode());
        value.setStructureHash(table.getStructureHash());
        value.setContentHash(table.getContentHash());
        value.setChangeSummaryJson(json(summary));
        value.setObservedTime(now);
        value.setActorType(defaultText(context.getActorType(), "SYSTEM"));
        value.setActorId(defaultText(context.getActorId(), "metadata-harvest"));
        value.setTraceId(context.getTraceId());
        return value;
    }

    private MetadataTableRevisionColumn revisionColumn(String tenantId, Long revisionId, MetadataAsset asset,
                                                       MetadataColumn column, ColumnState state) {
        MetadataTableRevisionColumn value = new MetadataTableRevisionColumn();
        value.setRevisionColumnId(IdWorker.getId());
        value.setTenantId(tenantId);
        value.setTableRevisionId(revisionId);
        value.setColumnAssetId(asset.getAssetId());
        value.setColumnRevisionNo(column.getRevisionNo());
        value.setOriginalName(asset.getOriginalName());
        value.setOrdinalPosition(column.getOrdinalPosition());
        value.setDataType(column.getDataType());
        value.setNativeType(column.getNativeType());
        value.setLengthValue(column.getLengthValue());
        value.setPrecisionValue(column.getPrecisionValue());
        value.setScaleValue(column.getScaleValue());
        value.setNullableFlag(column.getNullableFlag());
        value.setPrimaryKeyFlag(column.getPrimaryKeyFlag());
        value.setDefaultExpression(column.getDefaultExpression());
        value.setCommentText(column.getCommentText());
        value.setStructureHash(state.structureHash());
        value.setContentHash(state.contentHash());
        return value;
    }

    private MetadataChangeIssue issue(MetadataObservationContext context, MetadataAsset table,
                                      Integer previousRevisionNo, MetadataTableRevision revision, LocalDateTime now) {
        long id = IdWorker.getId();
        MetadataChangeIssue value = new MetadataChangeIssue();
        value.setChangeIssueId(id);
        value.setTenantId(context.getTenantId());
        value.setIssueCode("mdi_" + Long.toUnsignedString(id, 36));
        value.setIssueType(MetadataChangeIssueType.STRUCTURE_BREAKING.getCode());
        value.setTableAssetId(table.getAssetId());
        value.setFromRevisionNo(previousRevisionNo);
        value.setToRevisionNo(revision.getRevisionNo());
        value.setChangeLevel(MetadataChangeLevel.BREAKING.getCode());
        value.setIssueStatus("OPEN");
        value.setChangeFingerprint(hash(table.getAssetId() + "|" + revision.getStructureHash()));
        value.setActiveDedupeKey(table.getAssetId() + ":" + value.getChangeFingerprint());
        value.setChangeSummaryJson(revision.getChangeSummaryJson());
        value.setDetectedTime(now);
        value.setTraceId(context.getTraceId());
        return value;
    }

    private void createMissingIssues(MetadataObservationContext context) {
        long lastAssetId = 0L;
        while (true) {
            List<MetadataAsset> missingTables = assetMapper.selectList(new LambdaQueryWrapper<MetadataAsset>()
                .eq(MetadataAsset::getDatasourceId, context.getDatasourceId())
                .eq(MetadataAsset::getAssetType, MetadataObjectType.TABLE.getCode())
                .ne(MetadataAsset::getLastHarvestRunId, context.getHarvestRunId())
                .eq(MetadataAsset::getTechnicalStatus, MetadataTechnicalStatus.ACTIVE.getCode())
                .gt(MetadataAsset::getAssetId, lastAssetId)
                .orderByAsc(MetadataAsset::getAssetId)
                .last("LIMIT " + QUERY_CHUNK));
            if (missingTables.isEmpty()) return;
            insertIssuesIgnoringActiveDuplicates(missingTables.stream()
                .map(value -> missingIssue(context, value)).toList());
            lastAssetId = missingTables.get(missingTables.size() - 1).getAssetId();
        }
    }

    private MetadataChangeIssue missingIssue(MetadataObservationContext context, MetadataAsset table) {
        long id = IdWorker.getId();
        int revisionNo = table.getCurrentRevisionNo() == null ? 1 : table.getCurrentRevisionNo();
        String fingerprint = hash(table.getAssetId() + "|TABLE_MISSING|" + revisionNo);
        MetadataChangeIssue value = new MetadataChangeIssue();
        value.setChangeIssueId(id);
        value.setTenantId(context.getTenantId());
        value.setIssueCode("mdi_" + Long.toUnsignedString(id, 36));
        value.setIssueType(MetadataChangeIssueType.TABLE_MISSING.getCode());
        value.setTableAssetId(table.getAssetId());
        value.setFromRevisionNo(revisionNo);
        value.setToRevisionNo(revisionNo);
        value.setChangeLevel(MetadataChangeLevel.BREAKING.getCode());
        value.setIssueStatus("OPEN");
        value.setChangeFingerprint(fingerprint);
        value.setActiveDedupeKey(table.getAssetId() + ":" + fingerprint);
        value.setChangeSummaryJson(json(Map.of("changeType", "TABLE_MISSING", "tableName",
            table.getOriginalName(), "harvestRunId", context.getHarvestRunId())));
        value.setDetectedTime(LocalDateTime.now());
        value.setTraceId(context.getTraceId());
        return value;
    }

    private void insertIssuesIgnoringActiveDuplicates(List<MetadataChangeIssue> issues) {
        Set<String> keys = issues.stream().map(MetadataChangeIssue::getActiveDedupeKey).collect(Collectors.toSet());
        Set<String> existing = changeIssueMapper.selectList(new LambdaQueryWrapper<MetadataChangeIssue>()
                .in(MetadataChangeIssue::getActiveDedupeKey, keys))
            .stream().map(MetadataChangeIssue::getActiveDedupeKey).collect(Collectors.toSet());
        List<MetadataChangeIssue> pending = issues.stream()
            .filter(value -> !existing.contains(value.getActiveDedupeKey())).toList();
        if (!pending.isEmpty()) changeIssueMapper.insertBatch(pending, MAX_TABLE_BATCH);
    }

    private Map<String, MetadataAsset> nativeAssetMap(List<MetadataAsset> assets) {
        Map<String, MetadataAsset> values = new HashMap<>();
        assets.forEach(value -> putNative(values, value));
        return values;
    }

    private void putNative(Map<String, MetadataAsset> values, MetadataAsset asset) {
        if (StringUtils.isNotBlank(asset.getNativeObjectId())) {
            values.put(nativeKey(asset.getAssetType(), asset.getNativeObjectId()), asset);
        }
    }

    private MetadataAsset lookupAsset(Map<String, MetadataAsset> byKey, Map<String, MetadataAsset> byNative,
                                      MetadataObjectType type, String canonicalKey, String nativeObjectId) {
        if (StringUtils.isNotBlank(nativeObjectId)) {
            MetadataAsset nativeMatch = byNative.get(nativeKey(type.getCode(), nativeObjectId));
            if (nativeMatch != null) return nativeMatch;
        }
        return byKey.get(canonicalKey);
    }

    private String nativeKey(String type, String nativeObjectId) {
        return type + '|' + nativeObjectId;
    }

    private void publishProjection(MetadataAsset asset, MetadataObservationContext context) {
        if (!MetadataObjectType.COLUMN.getCode().equals(asset.getAssetType())) {
            eventPublisher.publishEvent(new MetadataAssetProjectionEvent(asset.getAssetId(), asset.getAssetType(),
                asset.getParentAssetId(), context.getDatasourceResourceId(), asset.getOriginalName(),
                asset.getOwnerDeptId(), asset.getClassificationCode(), asset.getSecurityLevel(),
                asset.getDatasourceId(), asset.getCurrentRevisionNo(), asset.getTechnicalStatus()));
        }
    }

    private String namespaceStructure(CollectedMetadataNamespace value) {
        return value.getNamespaceType().getCode() + '|' + value.getCanonicalKey() + '|'
            + text(value.getNativeObjectId());
    }

    private String namespaceContent(CollectedMetadataNamespace value) {
        return namespaceStructure(value) + '|' + text(value.getNamespaceOwner());
    }

    private String tableStructure(CollectedMetadataTable table, List<ColumnState> columns) {
        return upper(table.getTableType()) + '|' + columns.stream()
            .map(value -> value.source().getCanonicalKey() + ':' + value.structureHash())
            .collect(Collectors.joining("|"));
    }

    private String tableContent(CollectedMetadataTable table, List<ColumnState> columns) {
        return tableStructure(table, columns) + '|' + text(table.getComment()) + '|'
            + columns.stream().map(ColumnState::contentHash).collect(Collectors.joining("|"));
    }

    private Map<String, Object> differenceSummary(MetadataAsset previous, List<ColumnState> current,
                                                  Map<Long, MetadataColumn> previousColumns) {
        int added = (int) current.stream().filter(value -> value.previous() == null).count();
        Set<Long> currentIds = current.stream().map(ColumnState::asset).filter(Objects::nonNull)
            .map(MetadataAsset::getAssetId).collect(Collectors.toSet());
        int removed = (int) previousColumns.keySet().stream().filter(id -> !currentIds.contains(id)).count();
        int changed = (int) current.stream().filter(value -> value.previous() != null
            && !Objects.equals(value.previous().getContentHash(), value.contentHash())).count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("initial", previous == null);
        result.put("addedColumns", added);
        result.put("removedColumns", removed);
        result.put("changedColumns", changed);
        return result;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ServiceException(MetadataErrorCode.INVALID_OBSERVATION, "元数据差异序列化失败");
        }
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK缺少SHA-256", exception);
        }
    }

    private String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.isBlank(value) ? fallback : value;
    }

    private record ColumnState(CollectedMetadataColumn source, MetadataAsset asset, MetadataColumn previous,
                               String structureHash, String contentHash) { }
}
