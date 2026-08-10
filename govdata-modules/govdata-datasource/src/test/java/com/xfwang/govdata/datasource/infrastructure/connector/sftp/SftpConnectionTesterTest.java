package com.xfwang.govdata.datasource.infrastructure.connector.sftp;

import com.xfwang.govdata.datasource.domain.enums.ConnectionTestReason;
import com.xfwang.govdata.datasource.infrastructure.connector.model.ConnectionTestRequest;
import com.xfwang.govdata.datasource.infrastructure.connector.model.ConnectionTestResult;
import com.xfwang.platform.api.secret.SecretProvider;
import com.xfwang.platform.api.secret.SecretValue;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SftpConnectionTesterTest {

    @TempDir
    Path tempDirectory;

    @Test
    void authenticatesAndChecksReadableRootDirectory() throws Exception {
        Path root = Files.createDirectories(tempDirectory.resolve("root"));
        Files.createDirectories(root.resolve("exchange"));
        SshServer server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        SimpleGeneratorHostKeyProvider hostKeys = new SimpleGeneratorHostKeyProvider(
            tempDirectory.resolve("host-key.ser"));
        server.setKeyPairProvider(hostKeys);
        server.setPasswordAuthenticator((username, password, session) ->
            "tester".equals(username) && "password".equals(password));
        server.setSubsystemFactories(List.of(new SftpSubsystemFactory.Builder().build()));
        server.setFileSystemFactory(new VirtualFileSystemFactory(root));
        server.start();
        try {
            KeyPair hostKey = hostKeys.loadKeys(null).iterator().next();
            SecretProvider secrets = request -> Optional.of(new SecretValue(Map.of(
                "username", "tester".toCharArray(), "password", "password".toCharArray())));
            SftpConnectionTester tester = new SftpConnectionTester(List.of(secrets));
            ConnectionTestRequest request = ConnectionTestRequest.builder().connectorCode("sftp")
                .connectorVersion("1.0").authProfileCode("PASSWORD").tenantId("000000")
                .secretRef("secret:sec_sftp").actorType("USER").actorId("1")
                .purpose("DATASOURCE_CONNECTION_TEST").endpointConfig(Map.of(
                    "host", "127.0.0.1", "port", server.getPort(), "rootPath", "/exchange",
                    "charset", "UTF-8", "connectTimeoutSeconds", 5,
                    "hostKeyFingerprint", KeyUtils.getFingerPrint(hostKey.getPublic())))
                .build();

            ConnectionTestResult result = tester.test(request);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getServerInfo()).containsEntry("protocol", "SFTP");
        } finally {
            server.stop(true);
        }
    }
}
