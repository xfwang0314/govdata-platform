package com.xfwang.govdata.metadata.application.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xfwang.govdata.api.metadata.MetadataObjectType;
import com.xfwang.govdata.metadata.application.authorization.MetadataAuthorizationScope;
import com.xfwang.govdata.metadata.application.catalog.query.MetadataAssetQuery;
import com.xfwang.govdata.metadata.application.catalog.view.MetadataAssetTreeView;
import com.xfwang.govdata.metadata.application.catalog.view.MetadataAssetView;
import com.xfwang.govdata.metadata.application.catalog.view.MetadataColumnView;
import com.xfwang.govdata.metadata.domain.entity.MetadataAsset;
import com.xfwang.govdata.metadata.domain.entity.MetadataColumn;
import com.xfwang.govdata.metadata.domain.entity.MetadataTable;
import com.xfwang.govdata.metadata.domain.exception.MetadataErrorCode;
import com.xfwang.govdata.metadata.infrastructure.persistence.mapper.MetadataAssetMapper;
import com.xfwang.govdata.metadata.infrastructure.persistence.mapper.MetadataColumnMapper;
import com.xfwang.govdata.metadata.infrastructure.persistence.mapper.MetadataTableMapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 元数据当前目录只读应用服务。 */
@Service
@RequiredArgsConstructor
public class MetadataCatalogQueryService {
    private static final int MAX_PARENT_IDS = 500;
    private final MetadataAssetMapper assetMapper;
    private final MetadataTableMapper tableMapper;
    private final MetadataColumnMapper columnMapper;
    private final MetadataAuthorizationScope authorizationScope;

    public PageResult<MetadataAssetView> pageAssets(MetadataAssetQuery query, PageQuery pageQuery) {
        MetadataObjectType type = requestedType(query.getAssetType());
        var scope = authorizationScope.catalogViewScope(type == MetadataObjectType.COLUMN
            ? MetadataObjectType.TABLE : type);
        if (scope.denied()) return PageResult.build(List.of(), 0L);
        Set<Long> namespaceParents = namespaceParents(query);
        if (query.getNamespaceAssetId() != null && namespaceParents.isEmpty()) {
            return PageResult.build(List.of(), 0L);
        }
        if (type == MetadataObjectType.COLUMN && query.getNamespaceAssetId() != null) {
            LambdaQueryWrapper<MetadataAsset> tables = new LambdaQueryWrapper<MetadataAsset>()
                .select(MetadataAsset::getAssetId)
                .eq(MetadataAsset::getDatasourceId, query.getDatasourceId())
                .eq(MetadataAsset::getAssetType, MetadataObjectType.TABLE.getCode());
            constrainParents(tables, namespaceParents);
            namespaceParents = assetMapper.selectList(tables).stream()
                .map(MetadataAsset::getAssetId).collect(Collectors.toSet());
            if (namespaceParents.isEmpty()) return PageResult.build(List.of(), 0L);
        }
        Set<Long> authorizedParentIds = type == MetadataObjectType.COLUMN && !scope.unrestricted()
            ? tableAssetIds(scope.resourceIds()) : Set.of();
        if (type == MetadataObjectType.COLUMN && !scope.unrestricted() && authorizedParentIds.isEmpty()) {
            return PageResult.build(List.of(), 0L);
        }
        LambdaQueryWrapper<MetadataAsset> wrapper = new LambdaQueryWrapper<MetadataAsset>()
            .in(!scope.unrestricted() && type != MetadataObjectType.COLUMN,
                MetadataAsset::getAuthzResourceId, scope.resourceIds())
            .eq(query.getDatasourceId() != null, MetadataAsset::getDatasourceId, query.getDatasourceId())
            .eq(MetadataAsset::getAssetType, type.getCode())
            .eq(StringUtils.isNotBlank(query.getTechnicalStatus()), MetadataAsset::getTechnicalStatus,
                query.getTechnicalStatus())
            .eq(StringUtils.isNotBlank(query.getGovernanceStatus()), MetadataAsset::getGovernanceStatus,
                query.getGovernanceStatus())
            .and(StringUtils.isNotBlank(query.getKeyword()), value -> value
                .like(MetadataAsset::getOriginalName, query.getKeyword())
                .or().like(MetadataAsset::getDisplayName, query.getKeyword())
                .or().like(MetadataAsset::getBusinessName, query.getKeyword()))
            .orderByDesc(MetadataAsset::getLastSeenTime).orderByDesc(MetadataAsset::getAssetId);
        if (query.getNamespaceAssetId() != null) constrainParents(wrapper, namespaceParents);
        if (type == MetadataObjectType.COLUMN && !scope.unrestricted()) constrainParents(wrapper, authorizedParentIds);
        Page<MetadataAsset> page = assetMapper.selectPage(pageQuery.build(), wrapper);
        return PageResult.build(page.getRecords().stream().map(value -> toAssetView(value, false)).toList(),
            page.getTotal());
    }

    /** 目录范围和授权范围分别取交集，单个IN不超过500个受信任ID。 */
    private void constrainParents(LambdaQueryWrapper<MetadataAsset> wrapper, Set<Long> parentIds) {
        List<Long> ids = parentIds.stream().sorted().toList();
        wrapper.and(scope -> {
            for (int offset = 0; offset < ids.size(); offset += MAX_PARENT_IDS) {
                if (offset > 0) scope.or();
                scope.in(MetadataAsset::getParentAssetId, ids.subList(offset, Math.min(offset + MAX_PARENT_IDS, ids.size())));
            }
        });
    }

    public MetadataAssetView getAsset(Long assetId) {
        MetadataAsset asset = assetMapper.selectById(assetId);
        if (asset == null || !visible(asset)) throw new ServiceException(MetadataErrorCode.ASSET_NOT_FOUND);
        return toAssetView(asset, true);
    }

    /** Catalog 下允许直接挂表或经 Schema 挂表，筛选不改变表级授权范围。 */
    private Set<Long> namespaceParents(MetadataAssetQuery query) {
        if (query.getNamespaceAssetId() == null) return Set.of();
        MetadataAsset namespace = assetMapper.selectById(query.getNamespaceAssetId());
        if (namespace == null || query.getDatasourceId() == null
            || !query.getDatasourceId().equals(namespace.getDatasourceId())) return Set.of();
        if (MetadataObjectType.SCHEMA.getCode().equals(namespace.getAssetType())) {
            return Set.of(namespace.getAssetId());
        }
        if (!MetadataObjectType.CATALOG.getCode().equals(namespace.getAssetType())) return Set.of();
        Set<Long> parents = new HashSet<>();
        parents.add(namespace.getAssetId());
        assetMapper.selectList(new LambdaQueryWrapper<MetadataAsset>()
            .select(MetadataAsset::getAssetId)
            .eq(MetadataAsset::getDatasourceId, query.getDatasourceId())
            .eq(MetadataAsset::getParentAssetId, namespace.getAssetId())
            .eq(MetadataAsset::getAssetType, MetadataObjectType.SCHEMA.getCode()))
            .stream().map(MetadataAsset::getAssetId).forEach(parents::add);
        return parents;
    }

    public void requireVisibleTable(Long tableAssetId) {
        MetadataAsset asset = assetMapper.selectById(tableAssetId);
        if (asset == null || !MetadataObjectType.TABLE.getCode().equals(asset.getAssetType()) || !visible(asset)) {
            throw new ServiceException(MetadataErrorCode.ASSET_NOT_FOUND);
        }
    }

    public List<MetadataAssetTreeView> tree(Long datasourceId) {
        if (datasourceId == null) throw new ServiceException(MetadataErrorCode.INVALID_OBSERVATION, "数据源不能为空");
        List<MetadataAsset> assets = assetMapper.selectList(new LambdaQueryWrapper<MetadataAsset>()
            .eq(MetadataAsset::getDatasourceId, datasourceId)
            .orderByAsc(MetadataAsset::getAssetType).orderByAsc(MetadataAsset::getOriginalName)
            .orderByAsc(MetadataAsset::getAssetId));
        if (assets.isEmpty()) return List.of();
        Map<Long, MetadataAsset> byId = assets.stream().collect(Collectors.toMap(MetadataAsset::getAssetId,
            Function.identity()));
        Set<Long> visibleIds = visibleIds(assets);
        addAncestors(visibleIds, byId);
        Map<Long, List<MetadataAsset>> childrenByParent = assets.stream()
            .filter(value -> visibleIds.contains(value.getAssetId()) && value.getParentAssetId() != null)
            .collect(Collectors.groupingBy(MetadataAsset::getParentAssetId, LinkedHashMap::new, Collectors.toList()));
        return assets.stream().filter(value -> visibleIds.contains(value.getAssetId()))
            .filter(value -> value.getParentAssetId() == null || !visibleIds.contains(value.getParentAssetId()))
            .sorted(assetComparator()).map(value -> toTreeView(value, childrenByParent)).toList();
    }

    /** 为数据库元数据筛选提供轻量 Catalog/Schema 层级，不加载字段。 */
    public List<MetadataAssetTreeView> namespaceTree(Long datasourceId) {
        if (datasourceId == null) throw new ServiceException(MetadataErrorCode.INVALID_OBSERVATION, "数据源不能为空");
        List<MetadataAsset> assets = assetMapper.selectList(new LambdaQueryWrapper<MetadataAsset>()
            .eq(MetadataAsset::getDatasourceId, datasourceId)
            .in(MetadataAsset::getAssetType, List.of(MetadataObjectType.CATALOG.getCode(),
                MetadataObjectType.SCHEMA.getCode(), MetadataObjectType.TABLE.getCode()))
            .orderByAsc(MetadataAsset::getAssetType).orderByAsc(MetadataAsset::getOriginalName)
            .orderByAsc(MetadataAsset::getAssetId));
        if (assets.isEmpty()) return List.of();
        Map<Long, MetadataAsset> byId = assets.stream().collect(Collectors.toMap(MetadataAsset::getAssetId,
            Function.identity()));
        Set<Long> visibleIds = visibleIds(assets);
        addAncestors(visibleIds, byId);
        List<MetadataAsset> namespaces = assets.stream()
            .filter(value -> visibleIds.contains(value.getAssetId()))
            .filter(value -> !MetadataObjectType.TABLE.getCode().equals(value.getAssetType()))
            .toList();
        Set<Long> namespaceIds = namespaces.stream().map(MetadataAsset::getAssetId).collect(Collectors.toSet());
        Map<Long, List<MetadataAsset>> childrenByParent = namespaces.stream()
            .filter(value -> value.getParentAssetId() != null && namespaceIds.contains(value.getParentAssetId()))
            .collect(Collectors.groupingBy(MetadataAsset::getParentAssetId, LinkedHashMap::new, Collectors.toList()));
        return namespaces.stream()
            .filter(value -> value.getParentAssetId() == null || !namespaceIds.contains(value.getParentAssetId()))
            .sorted(assetComparator()).map(value -> toTreeView(value, childrenByParent)).toList();
    }

    private Set<Long> visibleIds(List<MetadataAsset> assets) {
        Set<Long> result = new HashSet<>();
        for (MetadataObjectType type : List.of(MetadataObjectType.CATALOG, MetadataObjectType.SCHEMA,
            MetadataObjectType.TABLE)) {
            var scope = authorizationScope.catalogViewScope(type);
            if (scope.denied()) continue;
            assets.stream().filter(value -> type.getCode().equals(value.getAssetType()))
                .filter(value -> scope.unrestricted() || value.getAuthzResourceId() != null
                    && scope.resourceIds().contains(value.getAuthzResourceId()))
                .map(MetadataAsset::getAssetId).forEach(result::add);
        }
        assets.stream().filter(value -> MetadataObjectType.COLUMN.getCode().equals(value.getAssetType()))
            .filter(value -> result.contains(value.getParentAssetId()))
            .map(MetadataAsset::getAssetId).forEach(result::add);
        return result;
    }

    private boolean visible(MetadataAsset asset) {
        MetadataObjectType type = requestedType(asset.getAssetType());
        if (type == MetadataObjectType.COLUMN) {
            MetadataAsset table = asset.getParentAssetId() == null ? null : assetMapper.selectById(asset.getParentAssetId());
            return table != null && visible(table);
        }
        var scope = authorizationScope.catalogViewScope(type);
        return !scope.denied() && (scope.unrestricted() || asset.getAuthzResourceId() != null
            && scope.resourceIds().contains(asset.getAuthzResourceId()));
    }

    private MetadataAssetView toAssetView(MetadataAsset asset, boolean detail) {
        MetadataTable table = MetadataObjectType.TABLE.getCode().equals(asset.getAssetType())
            ? tableMapper.selectById(asset.getAssetId()) : null;
        List<MetadataColumnView> columns = detail && table != null ? loadColumns(asset.getAssetId()) : List.of();
        return MetadataAssetView.builder().assetId(asset.getAssetId()).authzResourceId(asset.getAuthzResourceId())
            .assetCode(asset.getAssetCode()).assetType(asset.getAssetType()).parentAssetId(asset.getParentAssetId())
            .datasourceId(asset.getDatasourceId()).canonicalKey(asset.getCanonicalKey())
            .nativeObjectId(asset.getNativeObjectId()).originalName(asset.getOriginalName())
            .displayName(asset.getDisplayName()).businessName(asset.getBusinessName()).description(asset.getDescription())
            .ownerDeptId(asset.getOwnerDeptId()).stewardUserId(asset.getStewardUserId())
            .classificationCode(asset.getClassificationCode()).securityLevel(asset.getSecurityLevel())
            .technicalStatus(asset.getTechnicalStatus()).governanceStatus(asset.getGovernanceStatus())
            .firstSeenTime(asset.getFirstSeenTime()).lastSeenTime(asset.getLastSeenTime())
            .lastHarvestRunId(asset.getLastHarvestRunId()).structureHash(asset.getStructureHash())
            .contentHash(asset.getContentHash()).currentRevisionNo(asset.getCurrentRevisionNo())
            .lockVersion(asset.getLockVersion())
            .tableType(table == null ? null : table.getTableType())
            .commentText(table == null ? null : table.getCommentText()).columns(columns).build();
    }

    private List<MetadataColumnView> loadColumns(Long tableAssetId) {
        List<MetadataAsset> assets = assetMapper.selectList(new LambdaQueryWrapper<MetadataAsset>()
            .eq(MetadataAsset::getParentAssetId, tableAssetId)
            .eq(MetadataAsset::getAssetType, MetadataObjectType.COLUMN.getCode())
            .orderByAsc(MetadataAsset::getAssetId));
        List<Long> ids = assets.stream().map(MetadataAsset::getAssetId).toList();
        Map<Long, MetadataColumn> details = ids.isEmpty() ? Map.of() : columnMapper.selectByIds(ids).stream()
            .collect(Collectors.toMap(MetadataColumn::getAssetId, Function.identity()));
        return assets.stream().map(asset -> {
            MetadataColumn column = details.get(asset.getAssetId());
            return MetadataColumnView.builder().assetId(asset.getAssetId()).assetCode(asset.getAssetCode())
                .originalName(asset.getOriginalName()).displayName(asset.getDisplayName())
                .ordinalPosition(column == null ? null : column.getOrdinalPosition())
                .dataType(column == null ? null : column.getDataType())
                .lengthValue(column == null ? null : column.getLengthValue())
                .precisionValue(column == null ? null : column.getPrecisionValue())
                .scaleValue(column == null ? null : column.getScaleValue())
                .nullableFlag(column == null ? null : column.getNullableFlag())
                .primaryKeyFlag(column == null ? null : column.getPrimaryKeyFlag())
                .defaultExpression(column == null ? null : column.getDefaultExpression())
                .commentText(column == null ? null : column.getCommentText())
                .revisionNo(column == null ? null : column.getRevisionNo())
                .technicalStatus(column == null ? null : column.getTechnicalStatus()).build();
        }).toList();
    }

    private Set<Long> tableAssetIds(Set<Long> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) return Set.of();
        return assetMapper.selectList(new LambdaQueryWrapper<MetadataAsset>()
            .eq(MetadataAsset::getAssetType, MetadataObjectType.TABLE.getCode())
            .in(MetadataAsset::getAuthzResourceId, resourceIds)).stream()
            .map(MetadataAsset::getAssetId).collect(Collectors.toSet());
    }

    private void addAncestors(Set<Long> visibleIds, Map<Long, MetadataAsset> byId) {
        List<Long> pending = new ArrayList<>(visibleIds);
        for (int index = 0; index < pending.size(); index++) {
            MetadataAsset asset = byId.get(pending.get(index));
            Long parentId = asset == null ? null : asset.getParentAssetId();
            if (parentId != null && visibleIds.add(parentId)) pending.add(parentId);
        }
    }

    private MetadataAssetTreeView toTreeView(MetadataAsset asset, Map<Long, List<MetadataAsset>> childrenByParent) {
        List<MetadataAssetTreeView> children = childrenByParent.getOrDefault(asset.getAssetId(), List.of()).stream()
            .sorted(assetComparator()).map(value -> toTreeView(value, childrenByParent)).toList();
        return MetadataAssetTreeView.builder().assetId(asset.getAssetId()).parentAssetId(asset.getParentAssetId())
            .assetCode(asset.getAssetCode()).assetType(asset.getAssetType()).originalName(asset.getOriginalName())
            .displayName(asset.getDisplayName()).technicalStatus(asset.getTechnicalStatus())
            .governanceStatus(asset.getGovernanceStatus()).children(children).build();
    }

    private Comparator<MetadataAsset> assetComparator() {
        return Comparator.comparingInt((MetadataAsset value) -> assetOrder(requestedType(value.getAssetType())))
            .thenComparing(MetadataAsset::getOriginalName, Comparator.nullsLast(String::compareToIgnoreCase))
            .thenComparing(MetadataAsset::getAssetId);
    }

    private int assetOrder(MetadataObjectType type) {
        return switch (type) {
            case CATALOG -> 0;
            case SCHEMA -> 1;
            case TABLE -> 2;
            case COLUMN -> 3;
            default -> 4;
        };
    }

    private MetadataObjectType requestedType(String code) {
        String value = StringUtils.isBlank(code) ? MetadataObjectType.TABLE.getCode() : code;
        for (MetadataObjectType type : MetadataObjectType.values()) {
            if (type != MetadataObjectType.FILE && type.getCode().equals(value)) return type;
        }
        throw new ServiceException(MetadataErrorCode.ASSET_TYPE_INVALID);
    }
}
