package com.xfwang.govdata.metadata.application.history;

import com.xfwang.govdata.metadata.application.catalog.MetadataCatalogQueryService;
import com.xfwang.govdata.metadata.infrastructure.persistence.mapper.MetadataChangeIssueMapper;
import com.xfwang.govdata.metadata.infrastructure.persistence.mapper.MetadataTableRevisionColumnMapper;
import com.xfwang.govdata.metadata.infrastructure.persistence.mapper.MetadataTableRevisionMapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@Tag("dev")
class MetadataHistoryServiceTest {
    @Test
    void invisibleTableCannotReadChangeIssues() {
        MetadataCatalogQueryService catalog = mock(MetadataCatalogQueryService.class);
        MetadataChangeIssueMapper issues = mock(MetadataChangeIssueMapper.class);
        MetadataHistoryService service = new MetadataHistoryService(mock(MetadataTableRevisionMapper.class),
            mock(MetadataTableRevisionColumnMapper.class), issues, catalog);
        doThrow(new ServiceException("不可见表")).when(catalog).requireVisibleTable(42L);

        assertThatThrownBy(() -> service.pageIssues(42L, new PageQuery(20, 1))).isInstanceOf(ServiceException.class);

        verifyNoInteractions(issues);
    }
}
