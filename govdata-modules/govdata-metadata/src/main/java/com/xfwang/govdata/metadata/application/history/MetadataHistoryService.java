package com.xfwang.govdata.metadata.application.history;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xfwang.govdata.metadata.application.catalog.MetadataCatalogQueryService;
import com.xfwang.govdata.metadata.application.history.command.MetadataChangeIssueStatusCommand;
import com.xfwang.govdata.metadata.application.history.view.MetadataChangeIssueView;
import com.xfwang.govdata.metadata.application.history.view.MetadataRevisionColumnView;
import com.xfwang.govdata.metadata.application.history.view.MetadataTableRevisionView;
import com.xfwang.govdata.metadata.domain.entity.MetadataChangeIssue;
import com.xfwang.govdata.metadata.domain.entity.MetadataTableRevision;
import com.xfwang.govdata.metadata.domain.entity.MetadataTableRevisionColumn;
import com.xfwang.govdata.metadata.domain.enums.MetadataChangeIssueStatus;
import com.xfwang.govdata.metadata.domain.exception.MetadataErrorCode;
import com.xfwang.govdata.metadata.infrastructure.persistence.mapper.MetadataChangeIssueMapper;
import com.xfwang.govdata.metadata.infrastructure.persistence.mapper.MetadataTableRevisionColumnMapper;
import com.xfwang.govdata.metadata.infrastructure.persistence.mapper.MetadataTableRevisionMapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/** 单表修订历史与高风险变化处置应用服务。 */
@Service
@RequiredArgsConstructor
public class MetadataHistoryService {
    private final MetadataTableRevisionMapper revisionMapper;
    private final MetadataTableRevisionColumnMapper revisionColumnMapper;
    private final MetadataChangeIssueMapper issueMapper;
    private final MetadataCatalogQueryService catalogQueryService;

    public PageResult<MetadataTableRevisionView> pageRevisions(Long tableAssetId, PageQuery pageQuery) {
        catalogQueryService.requireVisibleTable(tableAssetId);
        Page<MetadataTableRevision> page = revisionMapper.selectPage(pageQuery.build(),
            new LambdaQueryWrapper<MetadataTableRevision>()
                .eq(MetadataTableRevision::getTableAssetId, tableAssetId)
                .orderByDesc(MetadataTableRevision::getRevisionNo)
                .orderByDesc(MetadataTableRevision::getTableRevisionId));
        return PageResult.build(page.getRecords().stream().map(value -> toRevision(value, false)).toList(),
            page.getTotal());
    }

    public MetadataTableRevisionView getRevision(Long tableAssetId, Long revisionId) {
        catalogQueryService.requireVisibleTable(tableAssetId);
        MetadataTableRevision value = revisionMapper.selectById(revisionId);
        if (value == null || !tableAssetId.equals(value.getTableAssetId())) {
            throw new ServiceException(MetadataErrorCode.ASSET_NOT_FOUND, "元数据表修订不存在");
        }
        return toRevision(value, true);
    }

    public PageResult<MetadataChangeIssueView> pageIssues(Long tableAssetId, PageQuery pageQuery) {
        catalogQueryService.requireVisibleTable(tableAssetId);
        Page<MetadataChangeIssue> page = issueMapper.selectPage(pageQuery.build(),
            new LambdaQueryWrapper<MetadataChangeIssue>()
                .eq(MetadataChangeIssue::getTableAssetId, tableAssetId)
                .orderByDesc(MetadataChangeIssue::getDetectedTime)
                .orderByDesc(MetadataChangeIssue::getChangeIssueId));
        return PageResult.build(page.getRecords().stream().map(this::toIssue).toList(), page.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public MetadataChangeIssueView updateIssueStatus(Long issueId, MetadataChangeIssueStatusCommand command) {
        MetadataChangeIssue issue = issueMapper.selectById(issueId);
        if (issue == null) throw new ServiceException(MetadataErrorCode.ASSET_NOT_FOUND, "元数据变更问题不存在");
        catalogQueryService.requireVisibleTable(issue.getTableAssetId());
        MetadataChangeIssueStatus target = Arrays.stream(MetadataChangeIssueStatus.values())
            .filter(value -> value.getCode().equals(command.getIssueStatus())).findFirst()
            .orElseThrow(() -> new ServiceException(MetadataErrorCode.INVALID_OBSERVATION, "变更问题状态无效"));
        issue.setIssueStatus(target.getCode());
        if (target == MetadataChangeIssueStatus.RESOLVED || target == MetadataChangeIssueStatus.DISMISSED) {
            issue.setResolvedTime(LocalDateTime.now());
            issue.setActiveDedupeKey(null);
        } else {
            issue.setResolvedTime(null);
        }
        issueMapper.updateById(issue);
        return toIssue(issueMapper.selectById(issueId));
    }

    private MetadataTableRevisionView toRevision(MetadataTableRevision value, boolean detail) {
        List<MetadataRevisionColumnView> columns = detail ? revisionColumnMapper.selectList(
            new LambdaQueryWrapper<MetadataTableRevisionColumn>()
                .eq(MetadataTableRevisionColumn::getTableRevisionId, value.getTableRevisionId())
                .orderByAsc(MetadataTableRevisionColumn::getOrdinalPosition)
                .orderByAsc(MetadataTableRevisionColumn::getRevisionColumnId)).stream()
            .map(column -> MetadataRevisionColumnView.builder().columnAssetId(column.getColumnAssetId())
                .columnRevisionNo(column.getColumnRevisionNo()).originalName(column.getOriginalName())
                .ordinalPosition(column.getOrdinalPosition()).dataType(column.getDataType())
                .nativeType(column.getNativeType()).lengthValue(column.getLengthValue())
                .precisionValue(column.getPrecisionValue()).scaleValue(column.getScaleValue())
                .nullableFlag(column.getNullableFlag()).primaryKeyFlag(column.getPrimaryKeyFlag())
                .defaultExpression(column.getDefaultExpression()).commentText(column.getCommentText()).build())
            .toList() : List.of();
        return MetadataTableRevisionView.builder().tableRevisionId(value.getTableRevisionId())
            .tableAssetId(value.getTableAssetId()).revisionNo(value.getRevisionNo())
            .previousRevisionNo(value.getPreviousRevisionNo()).harvestRunId(value.getHarvestRunId())
            .changeLevel(value.getChangeLevel()).structureHash(value.getStructureHash())
            .contentHash(value.getContentHash()).changeSummaryJson(value.getChangeSummaryJson())
            .observedTime(value.getObservedTime()).actorType(value.getActorType()).actorId(value.getActorId())
            .traceId(value.getTraceId()).columns(columns).build();
    }

    private MetadataChangeIssueView toIssue(MetadataChangeIssue value) {
        return MetadataChangeIssueView.builder().changeIssueId(value.getChangeIssueId())
            .issueCode(value.getIssueCode()).issueType(value.getIssueType()).tableAssetId(value.getTableAssetId())
            .fromRevisionNo(value.getFromRevisionNo()).toRevisionNo(value.getToRevisionNo())
            .changeLevel(value.getChangeLevel()).issueStatus(value.getIssueStatus())
            .changeSummaryJson(value.getChangeSummaryJson()).detectedTime(value.getDetectedTime())
            .assigneeType(value.getAssigneeType()).assigneeId(value.getAssigneeId())
            .resolvedTime(value.getResolvedTime()).traceId(value.getTraceId()).build();
    }
}
