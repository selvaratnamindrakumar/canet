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

import jcifs.DialectVersion;
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

        // ── Connection / authentication ────────────────────────────────────
        factory.setHost(smb.getHost());
        factory.setPort(smb.getPort());
        factory.setShareAndDir(smb.getShare() + smb.getDirectory());
        factory.setDomain(smb.getDomain());
        factory.setUsername(smb.getUsername());
        factory.setPassword(smb.getPassword());

        // ── Protocol version (jcifs.DialectVersion enum) ───────────────────
        factory.setSmbMinVersion(toDialectVersion(smb.getMinVersion()));
        factory.setSmbMaxVersion(toDialectVersion(smb.getMaxVersion()));

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

    // ── helpers ───────────────────────────────────────────────────────────────

    private static File resolveLocalStaging(ForwarderProperties.Smb smb) {
        String path = smb.getLocalStagingDirectory();
        if (path == null || path.isBlank()) {
            path = System.getProperty("java.io.tmpdir") + "/smb-staging";
        }
        return new File(path);
    }

    /**
     * Maps a human-readable version string from application.properties to the
     * {@link DialectVersion} enum expected by the JCIFS-NG library.
     *
     * Supported values: SMB1, SMB2 (→ SMB210), SMB202, SMB210,
     *                   SMB3 (→ SMB300), SMB300, SMB302, SMB311
     */
    static DialectVersion toDialectVersion(String version) {
        if (version == null) return DialectVersion.SMB210;
        return switch (version.toUpperCase().trim()) {
            case "SMB1"              -> DialectVersion.SMB1;
            case "SMB202"            -> DialectVersion.SMB202;
            case "SMB2", "SMB210"    -> DialectVersion.SMB210;
            case "SMB3", "SMB300"    -> DialectVersion.SMB300;
            case "SMB302"            -> DialectVersion.SMB302;
            case "SMB311"            -> DialectVersion.SMB311;
            default -> {
                log.warn("Unknown SMB dialect version '{}', defaulting to SMB210", version);
                yield DialectVersion.SMB210;
            }
        };
    }
}
