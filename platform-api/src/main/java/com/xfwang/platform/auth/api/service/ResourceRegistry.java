package com.xfwang.platform.auth.api.service;

import com.xfwang.platform.auth.api.model.ResourceDescriptor;

import java.util.Optional;

/**
 * 通用资源注册契约。
 */
public interface ResourceRegistry {

    ResourceDescriptor register(ResourceDescriptor resource);

    Optional<ResourceDescriptor> findById(Long resourceId);

    Optional<ResourceDescriptor> findByCode(String resourceCode);

    /** 修改资源生命周期状态：ACTIVE、DISABLED 或 ARCHIVED。 */
    ResourceDescriptor changeStatus(Long resourceId, String status);
}
