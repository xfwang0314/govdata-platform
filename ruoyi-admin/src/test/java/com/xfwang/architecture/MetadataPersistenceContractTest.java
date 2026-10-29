package com.xfwang.architecture;

import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** 元数据追加记录和技术明细实体与精简表结构的持久化契约。 */
@Tags({@Tag("local"), @Tag("dev"), @Tag("prod")})
class MetadataPersistenceContractTest {

    private static final List<String> ENTITIES_WITHOUT_RUOYI_AUDIT_COLUMNS = List.of(
        "com.xfwang.govdata.metadata.domain.entity.MetadataCatalog",
        "com.xfwang.govdata.metadata.domain.entity.MetadataSchema",
        "com.xfwang.govdata.metadata.domain.entity.MetadataTable",
        "com.xfwang.govdata.metadata.domain.entity.MetadataColumn",
        "com.xfwang.govdata.metadata.domain.entity.MetadataTableRevision",
        "com.xfwang.govdata.metadata.domain.entity.MetadataTableRevisionColumn",
        "com.xfwang.govdata.metadata.harvest.domain.entity.HarvestPlanRevision",
        "com.xfwang.govdata.metadata.harvest.domain.entity.HarvestRule",
        "com.xfwang.govdata.metadata.harvest.domain.entity.HarvestRun",
        "com.xfwang.govdata.metadata.harvest.domain.entity.HarvestRunFailure",
        "com.xfwang.govdata.metadata.harvest.domain.entity.HarvestRawResult"
    );

    @Test
    void entitiesBackedByLeanTablesMustNotInheritRuoyiAuditColumns() throws ClassNotFoundException {
        for (String entityName : ENTITIES_WITHOUT_RUOYI_AUDIT_COLUMNS) {
            Class<?> entityType = Class.forName(entityName);
            assertFalse(BaseEntity.class.isAssignableFrom(entityType),
                () -> entityName + " 继承 BaseEntity 会写入表中不存在的 create_dept/create_by/update_by 等字段");
        }
    }
}
