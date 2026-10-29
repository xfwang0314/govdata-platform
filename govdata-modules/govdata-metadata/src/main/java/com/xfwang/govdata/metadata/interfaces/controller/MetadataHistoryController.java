package com.xfwang.govdata.metadata.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xfwang.govdata.metadata.application.history.MetadataHistoryService;
import com.xfwang.govdata.metadata.application.history.command.MetadataChangeIssueStatusCommand;
import com.xfwang.govdata.metadata.application.history.view.MetadataChangeIssueView;
import com.xfwang.govdata.metadata.application.history.view.MetadataTableRevisionView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 表修订历史和高风险变更问题接口。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/govdata/metadata")
public class MetadataHistoryController {
    private final MetadataHistoryService historyService;

    @SaCheckPermission("govdata:metadata:revision:list")
    @GetMapping("/tables/{tableAssetId}/revisions")
    public R<PageResult<MetadataTableRevisionView>> revisions(@NotNull @PathVariable Long tableAssetId,
                                                              PageQuery pageQuery) {
        return R.ok(historyService.pageRevisions(tableAssetId, pageQuery));
    }

    @SaCheckPermission("govdata:metadata:revision:query")
    @GetMapping("/tables/{tableAssetId}/revisions/{revisionId}")
    public R<MetadataTableRevisionView> revision(@NotNull @PathVariable Long tableAssetId,
                                                 @NotNull @PathVariable Long revisionId) {
        return R.ok(historyService.getRevision(tableAssetId, revisionId));
    }

    @SaCheckPermission("govdata:metadata:change-issue:list")
    @GetMapping("/tables/{tableAssetId}/change-issues")
    public R<PageResult<MetadataChangeIssueView>> issues(@NotNull @PathVariable Long tableAssetId, PageQuery pageQuery) {
        return R.ok(historyService.pageIssues(tableAssetId, pageQuery));
    }

    @SaCheckPermission("govdata:metadata:change-issue:manage")
    @PutMapping("/change-issues/{issueId}/status")
    public R<MetadataChangeIssueView> updateIssue(@NotNull @PathVariable Long issueId,
                                                  @Valid @RequestBody MetadataChangeIssueStatusCommand command) {
        return R.ok(historyService.updateIssueStatus(issueId, command));
    }
}
