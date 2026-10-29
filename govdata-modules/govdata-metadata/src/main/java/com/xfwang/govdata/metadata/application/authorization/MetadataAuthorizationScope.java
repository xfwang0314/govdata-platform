package com.xfwang.govdata.metadata.application.authorization;

import com.xfwang.govdata.api.metadata.MetadataObjectType;
import com.xfwang.govdata.metadata.domain.authorization.MetadataAuthorizationCodes;
import com.xfwang.platform.api.auth.model.AuthorizedResourceQuery;
import com.xfwang.platform.api.auth.model.AuthorizedResourceScope;
import com.xfwang.platform.api.auth.service.AuthorizedResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 将统一授权决策转换为元数据目录批量可见范围。 */
@Service
@RequiredArgsConstructor
public class MetadataAuthorizationScope {
    private final AuthorizedResourceService authorizedResourceService;

    public AuthorizedResourceScope catalogViewScope() {
        return catalogViewScope(MetadataObjectType.TABLE);
    }

    /** 解析指定元数据层级的目录浏览范围。 */
    public AuthorizedResourceScope catalogViewScope(MetadataObjectType type) {
        return authorizedResourceService.resolveScope(AuthorizedResourceQuery.builder()
            .resourceType(MetadataAuthorizationCodes.resourceType(type))
            .action(MetadataAuthorizationCodes.Action.VIEW)
            .purpose(MetadataAuthorizationCodes.Purpose.CATALOG).build());
    }
}
