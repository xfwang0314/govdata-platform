package com.xfwang.govdata.datasource.infrastructure.connector;

import com.xfwang.govdata.datasource.infrastructure.connector.model.MetadataInspectRequest;
import com.xfwang.govdata.datasource.infrastructure.connector.model.MetadataInspectResult;

import java.util.List;

/**
 * 连接器元数据探测器接口。
 */
public interface ConnectorMetadataInspector {
    /**
     * 是否支持指定的连接器代码和版本。
     *
     * @param connectorCode    连接器代码
     * @param connectorVersion 连接器版本
     * @return 若支持则返回 true，否则返回 false
     */
    boolean supports(String connectorCode, String connectorVersion);

    /**
     * 列出可用的数据库 / Schema 清单。
     *
     * @param request 探测请求参数
     * @return 探测结果，包含数据库元数据列表
     */
    MetadataInspectResult listDatabases(MetadataInspectRequest request);

    /**
     * 列出表元数据，支持分页和模式匹配。
     *
     * @param request 探测请求参数
     * @return 探测结果，包含表元数据列表和总数
     */
    MetadataInspectResult listTables(MetadataInspectRequest request);

    /**
     * 批量获取指定表的列元数据。
     * 一次最多允许 500 个表。
     *
     * @param request    探测请求参数
     * @param tableNames 表名列表
     * @return 探测结果，包含表结构列表
     */
    MetadataInspectResult inspectColumns(MetadataInspectRequest request, List<String> tableNames);
}
