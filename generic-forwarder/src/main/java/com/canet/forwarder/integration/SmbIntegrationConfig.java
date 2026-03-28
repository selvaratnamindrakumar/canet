package com.canet.forwarder.integration;

import java.io.File;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.Pollers;
import org.springframework.integration.file.filters.AcceptOnceFileListFilter;
import org.springframework.integration.smb.filters.SmbSimplePatternFileListFilter;
import org.springframework.integration.smb.inbound.SmbInboundFileSynchronizer;
import org.springframework.integration.smb.inbound.SmbInboundFileSynchronizingMessageSource;
import org.springframework.integration.smb.session.SmbSessionFactory;
import org.springframework.messaging.MessageChannel;

import com.canet.forwarder.config.ForwarderProperties;

import jcifs.smb.NtlmPasswordAuthenticator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring Integration – SMB inbound adapter.
 * Polls a remote SMB share and forwards files to the common channel.
 * Active only when {@code source.type=smb}.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "source", name = "type", havingValue = "smb")
public class SmbIntegrationConfig {

    private final ForwarderProperties props;
    private final MessageChannel forwarderInputChannel;

    @Bean
    public SmbSessionFactory smbSessionFactory() {
        ForwarderProperties.Smb smb = props.getSource().getSmb();

        SmbSessionFactory factory = new SmbSessionFactory();

        // ── Connection ─────────────────────────────────────────────────────
        factory.setHost(smb.getHost());
        factory.setPort(smb.getPort());
        factory.setShareAndDir(smb.getShare() + smb.getDirectory());

        // ── Authentication ─────────────────────────────────────────────────
        NtlmPasswordAuthenticator auth = new NtlmPasswordAuthenticator(
                smb.getDomain(),
                smb.getUsername(),
                smb.getPassword());
        factory.setAuthenticator(auth);

        // ── Protocol version ───────────────────────────────────────────────
        factory.setSmbMinVersion(smb.getMinVersion());
        factory.setSmbMaxVersion(smb.getMaxVersion());

        // ── DFS ────────────────────────────────────────────────────────────
        factory.setDfsEnabled(smb.isDfsEnabled());

        // ── Timeouts ───────────────────────────────────────────────────────
        factory.setSocketTimeout(smb.getSocketTimeoutMs());
        factory.setResponseTimeout(smb.getResponseTimeoutMs());

        log.info("SMB session factory: host={}:{}, share={}, dir={}, minVer={}, maxVer={}",
                smb.getHost(), smb.getPort(), smb.getShare(), smb.getDirectory(),
                smb.getMinVersion(), smb.getMaxVersion());
        return factory;
    }

    @Bean
    public SmbInboundFileSynchronizer smbInboundFileSynchronizer() {
        ForwarderProperties.Smb smb = props.getSource().getSmb();

        SmbInboundFileSynchronizer synchronizer = new SmbInboundFileSynchronizer(smbSessionFactory());
        synchronizer.setRemoteDirectory(smb.getDirectory());
        synchronizer.setDeleteRemoteFiles(smb.isDeleteAfterRead());
        synchronizer.setFilter(new SmbSimplePatternFileListFilter(smb.getFilePattern()));
        return synchronizer;
    }

    @Bean
    public SmbInboundFileSynchronizingMessageSource smbMessageSource() {
        ForwarderProperties.Smb smb = props.getSource().getSmb();

        SmbInboundFileSynchronizingMessageSource source =
                new SmbInboundFileSynchronizingMessageSource(smbInboundFileSynchronizer());
        source.setLocalDirectory(resolveLocalStaging(smb));
        source.setAutoCreateLocalDirectory(true);
        source.setLocalFilter(new AcceptOnceFileListFilter<>());
        return source;
    }

    @Bean
    public IntegrationFlow smbInboundFlow() {
        ForwarderProperties.Smb smb = props.getSource().getSmb();

        var pollerSpec = smb.getMaxFilesPerPoll() > 0
                ? Pollers.fixedDelay(smb.getPollDelay()).maxMessagesPerPoll(smb.getMaxFilesPerPoll())
                : Pollers.fixedDelay(smb.getPollDelay());

        return IntegrationFlow
                .from(smbMessageSource(), spec -> spec.poller(pollerSpec))
                .channel(forwarderInputChannel)
                .get();
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static File resolveLocalStaging(ForwarderProperties.Smb smb) {
        String path = smb.getLocalStagingDirectory();
        if (path == null || path.isBlank()) {
            path = System.getProperty("java.io.tmpdir") + "/smb-staging";
        }
        return new File(path);
    }
}
