package com.xfwang.govdata.datasource.application.catalog;

import com.xfwang.govdata.datasource.infrastructure.connector.ConnectorCatalog;
import com.xfwang.govdata.datasource.domain.connector.ConnectorDescriptor;
import com.xfwang.govdata.datasource.domain.connector.ConnectorAuthProfile;
import com.xfwang.govdata.datasource.domain.enums.DataSourceSecretUsage;
import com.xfwang.platform.api.secret.SecretCatalog;
import com.xfwang.platform.api.secret.SecretReference;
import com.xfwang.platform.api.secret.SecretReferenceQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Connector 目录与可用凭据查询应用服务。
 *
 * <p>负责提供 Connector 元数据描述符查询、认证 Profile 匹配校验以及数据源可用凭据引用列表的检索。</p>
 */
@Service
@RequiredArgsConstructor
public class ConnectorCatalogQueryService {

    private final ConnectorCatalog connectorCatalog;
    private final SecretCatalog secretCatalog;

    /**
     * 查询系统注册的所有 Connector 描述符列表。
     *
     * @return Connector 描述符列表
     */
    public List<ConnectorDescriptor> list() {
        return connectorCatalog.list();
    }

    /**
     * 获取指定 Connector 编码的描述符，若不存在则抛出异常。
     *
     * @param connectorCode Connector 编码
     * @return Connector 描述符
     */
    public ConnectorDescriptor require(String connectorCode) {
        return connectorCatalog.require(connectorCode);
    }

    /**
     * 获取 Connector 指定认证 Profile，若不存在或不支持则抛出异常。
     *
     * @param descriptor  Connector 描述符
     * @param profileCode 认证 Profile 编码
     * @return 认证 Profile 描述符
     */
    public ConnectorAuthProfile requireAuthProfile(ConnectorDescriptor descriptor, String profileCode) {
        return connectorCatalog.requireAuthProfile(descriptor, profileCode);
    }

    /**
     * 返回当前调用模块可用于数据源连接测试的全部凭据元数据。
     *
     * <p>具体认证方式的类型匹配仍在保存时由后端强校验，前端只负责提示和禁用不兼容项。</p>
     *
     * @param connectorCode Connector 编码
     * @return 可用凭据引用列表
     */
    public List<SecretReference> listSecretReferences(String connectorCode) {
        connectorCatalog.require(connectorCode);
        return secretCatalog.listAvailable(SecretReferenceQuery.builder()
            .callerModule(DataSourceSecretUsage.OWNER_MODULE)
            .purpose(DataSourceSecretUsage.CONNECTION_TEST.code())
            .build());
    }
}
