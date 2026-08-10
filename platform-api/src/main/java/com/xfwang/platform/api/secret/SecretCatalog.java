package com.xfwang.platform.api.secret;

import java.util.List;

/** 面向业务模块的可用凭据引用目录。 */
public interface SecretCatalog {
    List<SecretReference> listAvailable(SecretReferenceQuery query);

    void validateReference(SecretReferenceValidation validation);
}
