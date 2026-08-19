package com.xfwang.govdata.datasource.application.authorization;

import com.xfwang.govdata.datasource.domain.authorization.DataSourceAuthorizationCodes;
import com.xfwang.platform.api.auth.model.AuthorizedResourceQuery;
import com.xfwang.platform.api.auth.model.AuthorizedResourceScope;
import com.xfwang.platform.api.auth.service.AuthorizedResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 将平台通用授权决策转换为数据源用例可直接应用的资源范围。 */
@Service
@RequiredArgsConstructor
public class DataSourceAuthorizationScope {

    private final AuthorizedResourceService authorizedResourceService;

    /** 当前主体在数据源管理场景中的批量可见范围。 */
    public AuthorizedResourceScope administrationViewScope() {
        return authorizedResourceService.resolveScope(
            AuthorizedResourceQuery.builder()
                .resourceType(DataSourceAuthorizationCodes.RESOURCE_TYPE)
                .action(DataSourceAuthorizationCodes.Action.VIEW)
                .purpose(DataSourceAuthorizationCodes.Purpose.ADMINISTRATION)
                .build());
    }
}
