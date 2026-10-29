package com.xfwang.govdata.metadata.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xfwang.govdata.metadata.application.catalog.MetadataCatalogQueryService;
import com.xfwang.govdata.metadata.application.catalog.MetadataGovernanceService;
import com.xfwang.govdata.metadata.application.catalog.command.MetadataGovernanceCommand;
import com.xfwang.govdata.metadata.application.catalog.query.MetadataAssetQuery;
import com.xfwang.govdata.metadata.application.catalog.view.MetadataAssetTreeView;
import com.xfwang.govdata.metadata.application.catalog.view.MetadataAssetView;
import com.xfwang.govdata.metadata.application.impact.MetadataImpactQueryService;
import com.xfwang.govdata.metadata.application.impact.view.MetadataImpactView;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Catalog、Schema、Table、Column 当前目录查询接口。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/govdata/metadata/assets")
public class MetadataAssetController {

    private final MetadataCatalogQueryService queryService;
    private final MetadataGovernanceService governanceService;
    private final MetadataImpactQueryService impactQueryService;

    @SaCheckPermission("govdata:metadata:list")
    @GetMapping
    public R<PageResult<MetadataAssetView>> page(MetadataAssetQuery query, PageQuery pageQuery) {
        return R.ok(queryService.pageAssets(query, pageQuery));
    }

    @SaCheckPermission("govdata:metadata:list")
    @GetMapping("/tree")
    public R<List<MetadataAssetTreeView>> tree(@NotNull @RequestParam Long datasourceId) {
        return R.ok(queryService.tree(datasourceId));
    }

    @SaCheckPermission("govdata:metadata:list")
    @GetMapping("/namespaces")
    public R<List<MetadataAssetTreeView>> namespaces(@NotNull @RequestParam Long datasourceId) {
        return R.ok(queryService.namespaceTree(datasourceId));
    }

    @SaCheckPermission("govdata:metadata:query")
    @GetMapping("/{assetId:\\d+}")
    public R<MetadataAssetView> detail(@NotNull @PathVariable Long assetId) {
        return R.ok(queryService.getAsset(assetId));
    }

    @SaCheckPermission("govdata:metadata:edit")
    @PutMapping("/{assetId:\\d+}/governance")
    public R<MetadataAssetView> updateGovernance(@NotNull @PathVariable Long assetId,
                                                  @Valid @RequestBody MetadataGovernanceCommand command) {
        return R.ok(governanceService.update(assetId, command));
    }

    @SaCheckPermission("govdata:metadata:query")
    @GetMapping("/{assetId:\\d+}/impacts")
    public R<PageResult<MetadataImpactView>> impacts(@NotNull @PathVariable Long assetId, PageQuery pageQuery) {
        return R.ok(impactQueryService.pageByAsset(assetId, pageQuery));
    }
}
