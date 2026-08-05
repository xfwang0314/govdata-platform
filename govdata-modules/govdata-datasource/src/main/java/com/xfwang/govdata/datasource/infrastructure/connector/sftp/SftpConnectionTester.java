package com.xfwang.govdata.datasource.infrastructure.connector.sftp;

import com.xfwang.govdata.datasource.infrastructure.connector.ConnectionTestRequest;
import com.xfwang.govdata.datasource.infrastructure.connector.ConnectionTestResult;
import com.xfwang.govdata.datasource.infrastructure.connector.ConnectionTester;
import com.xfwang.govdata.datasource.domain.connector.ConnectorDescriptor;
import com.xfwang.govdata.datasource.infrastructure.connector.ConnectorDescriptorProvider;
import com.xfwang.platform.api.secret.SecretProvider;
import com.xfwang.platform.api.secret.SecretValue;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.springframework.stereotype.Component;

import java.net.SocketAddress;
import java.security.PublicKey;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static com.xfwang.govdata.datasource.infrastructure.connector.ConnectorDescriptorSupport.*;

/** SFTP 密码认证与主机密钥指纹校验 Connector。 */
@Component
public class SftpConnectionTester implements ConnectionTester, ConnectorDescriptorProvider {

    private static final Pattern HOST = Pattern.compile("^[a-zA-Z0-9._-]{1,253}$");
    private final List<SecretProvider> secretProviders;

    public SftpConnectionTester(List<SecretProvider> secretProviders) { this.secretProviders = secretProviders; }
    @Override public boolean supports(String code, String version) {
        return "sftp".equalsIgnoreCase(code) && "1.0".equals(version);
    }

    @Override public ConnectionTestResult test(ConnectionTestRequest request) {
        String host = value(request, "host");
        int port = integer(request, "port");
        String basePath = value(request, "basePath");
        String expectedFingerprint = value(request, "hostKeySha256");
        if (!HOST.matcher(host).matches() || port < 1 || port > 65535 || !basePath.startsWith("/")
            || !expectedFingerprint.startsWith("SHA256:")) {
            return failed("ENDPOINT_INVALID", "host、port、basePath 或主机密钥指纹配置无效", 0);
        }
        Optional<SecretValue> resolved = secretProviders.stream()
            .map(provider -> provider.resolve(request.secretRequest(Set.of("username", "password"))))
            .flatMap(Optional::stream).findFirst();
        if (resolved.isEmpty()) {
            return failed("SECRET_UNAVAILABLE", "SecretRef 无法解析", 0);
        }
        long started = System.nanoTime();
        SshClient client = SshClient.setUpDefaultClient();
        client.setServerKeyVerifier((ClientSession session, SocketAddress address, PublicKey key) ->
            expectedFingerprint.equals(KeyUtils.getFingerPrint(key)));
        client.start();
        try (SecretValue secret = resolved.orElseThrow()) {
            char[] username = secret.get("username");
            char[] password = secret.get("password");
            if (username == null || password == null) {
                return failed("SECRET_FIELDS_MISSING", "凭据缺少 username 或 password", elapsed(started));
            }
            try (ClientSession session = client.connect(new String(username), host, port)
                    .verify(Duration.ofSeconds(5)).getSession()) {
                session.addPasswordIdentity(new String(password));
                session.auth().verify(Duration.ofSeconds(5));
                try (SftpClient sftp = SftpClientFactory.instance().createSftpClient(session)) {
                    SftpClient.Attributes attributes = sftp.stat(basePath);
                    return ConnectionTestResult.builder().success(true).reasonCode("CONNECTED").message("连接成功")
                        .latencyMs(elapsed(started)).serverInfo(Map.of(
                            "protocol", "SFTP", "basePathExists", true, "directory", attributes.isDirectory()))
                        .build();
                }
            }
        } catch (Exception ignored) {
            return failed("CONNECTION_FAILED", "连接失败，请检查端点、主机指纹、凭据和目录", elapsed(started));
        } finally {
            client.stop();
        }
    }

    @Override public ConnectorDescriptor descriptor() {
        return ConnectorDescriptor.builder().connectorCode("sftp").connectorName("SFTP")
            .connectorVersion("1.0").category("FILE").secretType("USERNAME_PASSWORD").capabilities(Set.of("CONNECTION_TEST"))
            .endpointFields(List.of(textField("host", "主机", true, "SFTP 服务地址"),
                integerField("port", "端口", true, 22), textField("basePath", "根目录", true, "必须使用绝对路径"),
                textField("hostKeySha256", "主机密钥 SHA256 指纹", true, "例如 SHA256:xxxx，禁止跳过校验")))
            .secretKeys(List.of("username", "password")).build();
    }

    private String value(ConnectionTestRequest request, String key) {
        Object value = request.getEndpointConfig().get(key);
        return value == null ? "" : value.toString().trim();
    }
    private int integer(ConnectionTestRequest request, String key) {
        try { return Integer.parseInt(value(request, key)); } catch (NumberFormatException ignored) { return -1; }
    }
    private long elapsed(long started) { return (System.nanoTime() - started) / 1_000_000; }
    private ConnectionTestResult failed(String code, String message, long latency) {
        return ConnectionTestResult.builder().success(false).reasonCode(code).message(message).latencyMs(latency).build();
    }
}
