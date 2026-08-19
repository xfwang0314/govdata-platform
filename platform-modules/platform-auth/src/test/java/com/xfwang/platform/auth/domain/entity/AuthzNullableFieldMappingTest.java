package com.xfwang.platform.auth.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class AuthzNullableFieldMappingTest {

    @Test
    void lifecycleFieldsThatMustBeClearedAreAlwaysIncludedInUpdates() throws Exception {
        assertAlwaysUpdated(AuthzPolicyGroup.class,
            "currentVersionId", "draftVersionId", "deletedBy", "deletedTime", "deleteReason");
    }

    @Test
    void mutableNullableSnapshotsAndResourceAttributesCanBeCleared() throws Exception {
        assertAlwaysUpdated(AuthzPolicyVersion.class, "descriptionSnapshot");
        assertAlwaysUpdated(AuthzResource.class,
            "parentId", "ownerDeptId", "ownerUserId", "regionCode", "classification", "securityLevel");
    }

    private void assertAlwaysUpdated(Class<?> entityType, String... fieldNames) throws Exception {
        for (String fieldName : fieldNames) {
            Field field = entityType.getDeclaredField(fieldName);
            TableField mapping = field.getAnnotation(TableField.class);
            assertThat(mapping)
                .as("%s.%s 必须允许显式写入 NULL", entityType.getSimpleName(), fieldName)
                .isNotNull();
            assertThat(mapping.updateStrategy())
                .as("%s.%s updateStrategy", entityType.getSimpleName(), fieldName)
                .isEqualTo(FieldStrategy.ALWAYS);
        }
    }
}
