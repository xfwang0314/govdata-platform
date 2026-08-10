package com.xfwang.govdata.datasource.infrastructure.connector.http;

import com.xfwang.govdata.datasource.domain.enums.ConnectionTestReason;
import com.xfwang.govdata.datasource.infrastructure.connector.support.ConnectionFailureException;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

/** HTTP 出站目标安全策略；允许政务内网地址，但拒绝本机、链路本地和非 HTTP 协议。 */
@Component
public class HttpTargetPolicy {

    private static final Set<String> FORBIDDEN_HOSTS = Set.of("localhost", "localhost.localdomain");

    public URI validate(String value, boolean resolveHost) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!("http".equals(scheme) || "https".equals(scheme)) || host.isBlank()
                || uri.getUserInfo() != null || uri.getFragment() != null || FORBIDDEN_HOSTS.contains(host)) {
                throw invalid();
            }
            if (resolveHost) {
                for (InetAddress address : InetAddress.getAllByName(host)) {
                    if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                        || address.isLinkLocalAddress() || address.isMulticastAddress()) {
                        throw invalid();
                    }
                }
            }
            return uri;
        } catch (ConnectionFailureException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid();
        }
    }

    private ConnectionFailureException invalid() {
        return new ConnectionFailureException(ConnectionTestReason.ENDPOINT_INVALID);
    }
}
