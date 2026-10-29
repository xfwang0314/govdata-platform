package com.xfwang.govdata.metadata.application.catalog;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xfwang.govdata.api.metadata.MetadataObjectType;
import com.xfwang.govdata.metadata.application.authorization.MetadataAuthorizationScope;
import com.xfwang.govdata.metadata.application.catalog.query.MetadataAssetQuery;
import com.xfwang.govdata.metadata.domain.entity.MetadataAsset;
import com.xfwang.govdata.metadata.infrastructure.persistence.mapper.MetadataAssetMapper;
import com.xfwang.govdata.metadata.infrastructure.persistence.mapper.MetadataColumnMapper;
import com.xfwang.govdata.metadata.infrastructure.persistence.mapper.MetadataTableMapper;
import com.xfwang.platform.api.auth.model.AuthorizedResourceScope;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.LongStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("dev")
class MetadataColumnScopeTest {
    private final MetadataAssetMapper mapper = mock(MetadataAssetMapper.class);
    private final MetadataAuthorizationScope authorization = mock(MetadataAuthorizationScope.class);
    private final MetadataCatalogQueryService service = new MetadataCatalogQueryService(mapper,
        mock(MetadataTableMapper.class), mock(MetadataColumnMapper.class), authorization);
    private final AtomicReference<LambdaQueryWrapper<MetadataAsset>> paged = new AtomicReference<>();

    @BeforeEach
    void prepare() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), MetadataAsset.class);
        when(authorization.catalogViewScope(MetadataObjectType.TABLE)).thenReturn(AuthorizedResourceScope.unrestrictedScope());
        when(mapper.selectById(11L)).thenReturn(asset(11L, "SCHEMA"));
        when(mapper.selectPage(any(), any())).thenAnswer(call -> {
            paged.set(call.getArgument(1));
            return new Page<MetadataAsset>(1, 15).setRecords(List.of());
        });
    }

    @Test
    void namespaceIsConvertedToTableParentsBeforeColumnPagination() {
        when(mapper.selectList(any())).thenReturn(List.of(asset(12L, "TABLE"), asset(13L, "TABLE")));
        service.pageAssets(query(), new PageQuery(15, 1));
        String sql = paged.get().getSqlSegment();
        assertThat(sql).contains("parent_asset_id IN");
        assertThat(paged.get().getParamNameValuePairs().values()).contains(12L, 13L, "COLUMN").doesNotContain(11L);
    }

    @Test
    void authorizedTableParentsAreIntersectedWithNamespaceParents() {
        when(authorization.catalogViewScope(MetadataObjectType.TABLE)).thenReturn(AuthorizedResourceScope.restricted(Set.of(900L)));
        when(mapper.selectList(any())).thenAnswer(call -> {
            LambdaQueryWrapper<MetadataAsset> wrapper = call.getArgument(0);
            return wrapper.getSqlSegment().contains("authz_resource_id") ? List.of(asset(13L, "TABLE"))
                : List.of(asset(12L, "TABLE"), asset(13L, "TABLE"));
        });
        service.pageAssets(query(), new PageQuery(15, 1));
        assertThat(paged.get().getSqlSegment().split("parent_asset_id IN", -1)).hasSize(3);
        assertThat(paged.get().getParamNameValuePairs().values()).contains(12L, 13L);
    }

    @Test
    void namespaceWithoutTablesDoesNotIssueUnscopedPageQuery() {
        when(mapper.selectList(any())).thenReturn(List.of());
        assertThat(service.pageAssets(query(), new PageQuery(15, 1)).getTotal()).isZero();
        verify(mapper, never()).selectPage(any(), any());
    }

    @Test
    void largeNamespacesSplitParentIdsIntoBoundedInGroups() {
        when(mapper.selectList(any())).thenReturn(LongStream.range(1000, 2200).mapToObj(id -> asset(id, "TABLE")).toList());
        service.pageAssets(query(), new PageQuery(15, 1));
        assertThat(paged.get().getSqlSegment().split("parent_asset_id IN", -1)).hasSize(4);
    }

    @Test
    void foreignDatasourceNamespaceReturnsNoColumns() {
        MetadataAsset foreign = asset(11L, "SCHEMA"); foreign.setDatasourceId(2L);
        when(mapper.selectById(11L)).thenReturn(foreign);
        assertThat(service.pageAssets(query(), new PageQuery(15, 1)).getTotal()).isZero();
        verify(mapper, never()).selectPage(any(), any());
    }

    @Test
    void missingAuthorizedParentProjectionDoesNotFallBackToUnrestrictedColumns() {
        when(authorization.catalogViewScope(MetadataObjectType.TABLE)).thenReturn(AuthorizedResourceScope.restricted(Set.of(900L)));
        when(mapper.selectList(any())).thenAnswer(call -> {
            LambdaQueryWrapper<MetadataAsset> wrapper = call.getArgument(0);
            return wrapper.getSqlSegment().contains("authz_resource_id") ? List.of() : List.of(asset(12L, "TABLE"));
        });
        assertThat(service.pageAssets(query(), new PageQuery(15, 1)).getTotal()).isZero();
        verify(mapper, never()).selectPage(any(), any());
    }

    private MetadataAssetQuery query() {
        MetadataAssetQuery query = new MetadataAssetQuery(); query.setDatasourceId(1L);
        query.setNamespaceAssetId(11L); query.setAssetType("COLUMN"); return query;
    }
    private MetadataAsset asset(Long id, String type) {
        MetadataAsset value = new MetadataAsset(); value.setAssetId(id); value.setAssetType(type); value.setDatasourceId(1L); return value;
    }
}
