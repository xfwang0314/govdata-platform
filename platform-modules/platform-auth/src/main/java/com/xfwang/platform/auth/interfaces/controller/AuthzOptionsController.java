package com.xfwang.platform.auth.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xfwang.platform.auth.application.options.AuthorizationOptionsQueryService;
import com.xfwang.platform.auth.application.options.view.AuthzOptionsView;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ABAC 授权目录只读入口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/platform/auth/options")
public class AuthzOptionsController {

    private final AuthorizationOptionsQueryService queryService;

    @SaCheckPermission("platform:auth:policy:query")
    @GetMapping
    public R<AuthzOptionsView> getOptions() {
        return R.ok(queryService.options());
    }
}
