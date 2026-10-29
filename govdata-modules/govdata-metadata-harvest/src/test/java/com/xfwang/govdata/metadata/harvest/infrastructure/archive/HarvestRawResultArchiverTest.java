package com.xfwang.govdata.metadata.harvest.infrastructure.archive;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.xfwang.govdata.api.metadata.CollectedMetadataTable;
import com.xfwang.govdata.metadata.harvest.domain.exception.HarvestErrorCode;
import com.xfwang.govdata.metadata.harvest.infrastructure.persistence.mapper.HarvestRawResultMapper;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class HarvestRawResultArchiverTest {

    @Test
    void shouldSkipArchiveWhenDisabled() {
        HarvestRawArchiveProperties properties = new HarvestRawArchiveProperties();
        HarvestRawResultMapper mapper = mock(HarvestRawResultMapper.class);
        HarvestRawResultArchiver archiver = new HarvestRawResultArchiver(JsonMapper.builder().build(), mapper, properties);

        archiver.archive(1L, 1, new CollectedMetadataTable());

        verifyNoInteractions(mapper);
    }

    @Test
    void shouldRejectOversizedContentBeforeObjectStorageCall() {
        HarvestRawArchiveProperties properties = new HarvestRawArchiveProperties();
        properties.setEnabled(true);
        properties.setMaxBytes(1);
        HarvestRawResultArchiver archiver = new HarvestRawResultArchiver(JsonMapper.builder().build(),
            mock(HarvestRawResultMapper.class), properties);

        assertThatThrownBy(() -> archiver.archive(1L, 1, new CollectedMetadataTable()))
            .isInstanceOf(ServiceException.class)
            .extracting("errorCode").isEqualTo(HarvestErrorCode.RAW_RESULT_TOO_LARGE.getCode());
    }
}
