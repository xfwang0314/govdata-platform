package com.xfwang.govdata.datasource.application.catalog;

import com.xfwang.govdata.datasource.infrastructure.connector.ConnectorCatalog;
import com.xfwang.govdata.datasource.domain.connector.ConnectorDescriptor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConnectorCatalogQueryService {

    private final ConnectorCatalog connectorCatalog;

    public List<ConnectorDescriptor> list() {
        return connectorCatalog.list();
    }
}
