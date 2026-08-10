package com.xfwang.govdata.datasource.infrastructure.connector.sftp;

import com.xfwang.govdata.datasource.domain.connector.ConnectorAuthProfile;
import com.xfwang.govdata.datasource.domain.connector.ConnectorDescriptor;
import com.xfwang.govdata.datasource.domain.connector.ConnectorPreviewDescriptor;
import com.xfwang.govdata.datasource.domain.enums.ConnectionTestReason;
import com.xfwang.govdata.datasource.infrastructure.connector.AbstractConnectionTester;
import com.xfwang.govdata.datasource.infrastructure.connector.ConnectorDescriptorProvider;
import com.xfwang.govdata.datasource.infrastructure.connector.model.ConnectionTestRequest;
import com.xfwang.govdata.datasource.infrastructure.connector.model.ConnectionTestResult;
import com.xfwang.govdata.datasource.infrastructure.connector.support.ConnectionFailureClassifier;
import com.xfwang.platform.api.secret.SecretProvider;
import com.xfwang.platform.api.secret.SecretType;
import com.xfwang.platform.api.secret.SecretValue;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.auth.password.PasswordIdentityProvider;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.keyprovider.KeyIdentityProvider;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.xfwang.govdata.datasource.infrastructure.connector.ConnectorDescriptorSupport.choiceField;
import static com.xfwang.govdata.datasource.infrastructure.connector.ConnectorDescriptorSupport.integerField;
import static com.xfwang.govdata.datasource.infrastructure.connector.ConnectorDescriptorSupport.textField;

/**
 * SFTP 密码或 SSH 私钥认证连通测试 Connector。
 *
 * <p>继承自 {@link AbstractConnectionTester}，使用 Apache MINA SSHD 客户端建立 SSH 连接并验证远程根目录可访问性。</p>
 */
@Component
public class SftpConnectionTester extends AbstractConnectionTester implements ConnectorDescriptorProvider {

    /**
     * 构造函数，注入凭据解析服务。
     *
     * @param secretProviders 凭据解析提供者列表
     */
    public SftpConnectionTester(List<SecretProvider> secretProviders) {
        super(secretProviders);
    }

    @Override
    protected String connectorCode() {
        return "sftp";
    }

    @Override
    protected String connectorVersion() {
        return "1.0";
    }

    /**
     * 执行 SFTP 连通与目录访问测试。
     *
     * @param request 连通测试请求
     * @return 连通测试结果
     */
    @Override
    public ConnectionTestResult test(ConnectionTestRequest request) {
        long started = System.nanoTime();
        boolean privateKey = "PRIVATE_KEY".equals(request.getAuthProfileCode());
        SecretType type = privateKey ? SecretType.SSH_PRIVATE_KEY : SecretType.USERNAME_PASSWORD;
        Optional<SecretValue> resolved = resolveSecret(request, type, type.requiredKeys());
        if (resolved.isEmpty()) {
            return failed(ConnectionTestReason.SECRET_UNAVAILABLE, null, elapsed(started));
        }
        AtomicBoolean hostKeyMismatch = new AtomicBoolean(false);
        try (SecretValue secret = resolved.orElseThrow(); SshClient client = SshClient.setUpDefaultClient()) {
            client.setKeyIdentityProvider(KeyIdentityProvider.EMPTY_KEYS_PROVIDER);
            client.setPasswordIdentityProvider(PasswordIdentityProvider.EMPTY_PASSWORDS_PROVIDER);
            String expectedFingerprint = text(request, "hostKeyFingerprint");
            client.setServerKeyVerifier((session, remoteAddress, serverKey) -> {
                boolean matches = KeyUtils.checkFingerPrint(expectedFingerprint, serverKey).getKey();
                hostKeyMismatch.set(!matches);
                return matches;
            });
            client.start();
            char[] username = required(secret, "username");
            try (ClientSession session = client.connect(new String(username), text(request, "host"),
                integer(request, "port")).verify(Duration.ofSeconds(integer(request, "connectTimeoutSeconds")))
                .getSession()) {
                if (privateKey) {
                    addPrivateKeys(session, secret);
                } else {
                    char[] password = required(secret, "password");
                    try {
                        session.addPasswordIdentity(new String(password));
                    } finally {
                        Arrays.fill(password, '\0');
                    }
                }
                session.auth().verify(Duration.ofSeconds(integer(request, "connectTimeoutSeconds")));
                try (SftpClient sftp = SftpClientFactory.instance().createSftpClient(session)) {
                    sftp.setNameDecodingCharset(Charset.forName(text(request, "charset")));
                    String rootPath = text(request, "rootPath");
                    try (SftpClient.CloseableHandle handle = sftp.openDir(rootPath)) {
                        sftp.readDir(handle);
                    } catch (Exception exception) {
                        return failed(ConnectionTestReason.REMOTE_PATH_UNAVAILABLE, null, elapsed(started));
                    }
                    return ConnectionTestResult.builder()
                        .success(true)
                        .reasonCode(ConnectionTestReason.CONNECTED.code())
                        .message(ConnectionTestReason.CONNECTED.message())
                        .latencyMs(elapsed(started))
                        .serverInfo(Map.of("protocol", "SFTP", "sftpVersion", sftp.getVersion()))
                        .build();
                }
            } finally {
                Arrays.fill(username, '\0');
            }
        } catch (Exception exception) {
            ConnectionTestReason reason = hostKeyMismatch.get() ? ConnectionTestReason.SSH_HOST_KEY_MISMATCH
                : ConnectionFailureClassifier.classify(exception);
            return failed(reason, null, elapsed(started));
        }
    }

    /**
     * 将 SSH 私钥及可选的 Passphrase 注入 SSH 会话。
     */
    private void addPrivateKeys(ClientSession session, SecretValue secret) throws Exception {
        char[] privateKey = required(secret, "privateKey");
        char[] passphrase = secret.get("passphrase");
        try (ByteArrayInputStream input = new ByteArrayInputStream(new String(privateKey)
            .getBytes(StandardCharsets.UTF_8))) {
            FilePasswordProvider passwords = passphrase == null
                ? FilePasswordProvider.EMPTY : FilePasswordProvider.of(new String(passphrase));
            Iterable<KeyPair> identities = SecurityUtils.loadKeyPairIdentities(null,
                NamedResource.ofName("secret-private-key"), input, passwords);
            int count = 0;
            for (KeyPair identity : identities) {
                session.addPublicKeyIdentity(identity);
                count++;
            }
            if (count == 0) {
                throw new IllegalArgumentException("private key unavailable");
            }
        } finally {
            Arrays.fill(privateKey, '\0');
            if (passphrase != null) {
                Arrays.fill(passphrase, '\0');
            }
        }
    }

    /**
     * 校验必填字段是否存在。
     */
    private char[] required(SecretValue secret, String key) {
        char[] value = secret.get(key);
        if (value == null || value.length == 0) {
            throw new IllegalArgumentException("secret field unavailable");
        }
        return value;
    }

    /**
     * 提供 SFTP Connector 元数据描述符。
     *
     * @return Connector 描述符
     */
    @Override
    public ConnectorDescriptor descriptor() {
        return ConnectorDescriptor.builder()
            .connectorCode("sftp")
            .connectorName("SFTP")
            .connectorVersion("1.0")
            .category("FILE_SYSTEM")
            .capabilities(Set.of("CONNECTION_TEST"))
            .connectionPreview(ConnectorPreviewDescriptor.builder()
                .label("SFTP 地址")
                .template("sftp://{host}:{port}{rootPath}")
                .build())
            .endpointFields(List.of(
                textField("host", "主机地址", true, "SFTP 服务器域名或 IP"),
                integerField("port", "端口", true, 22),
                textField("rootPath", "根目录", true, "允许访问的远程绝对路径"),
                choiceField("charset", "文件名字符集", "UTF-8", List.of("UTF-8", "GBK")),
                integerField("connectTimeoutSeconds", "连接超时（秒）", true, 10, 1, 60),
                textField("hostKeyFingerprint", "主机指纹", true, "服务端 SSH Host Key 的 SHA256 指纹")))
            .authProfiles(List.of(
                ConnectorAuthProfile.builder()
                    .profileCode("PASSWORD")
                    .profileName("用户名和密码")
                    .secretType(SecretType.USERNAME_PASSWORD)
                    .build(),
                ConnectorAuthProfile.builder()
                    .profileCode("PRIVATE_KEY")
                    .profileName("SSH 私钥")
                    .secretType(SecretType.SSH_PRIVATE_KEY)
                    .build()))
            .build();
    }
}
