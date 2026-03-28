package com.canet.forwarder.integration;

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

import java.io.File;

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

    /** Local staging directory where SMB files are synchronised before sending */
    private static final String LOCAL_STAGING = System.getProperty("java.io.tmpdir") + "/smb-staging";

    @Bean
    public SmbSessionFactory smbSessionFactory() {
        ForwarderProperties.Smb smb = props.getSource().getSmb();

        SmbSessionFactory factory = new SmbSessionFactory();
        factory.setHost(smb.getHost());
        factory.setShareAndDir(smb.getShare() + smb.getDirectory());

        NtlmPasswordAuthenticator auth = new NtlmPasswordAuthenticator(
                smb.getDomain(),
                smb.getUsername(),
                smb.getPassword());
        factory.setAuthenticator(auth);

        log.info("SMB session factory configured, host={}, share={}", smb.getHost(), smb.getShare());
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
        SmbInboundFileSynchronizingMessageSource source =
                new SmbInboundFileSynchronizingMessageSource(smbInboundFileSynchronizer());
        source.setLocalDirectory(new File(LOCAL_STAGING));
        source.setAutoCreateLocalDirectory(true);
        source.setLocalFilter(new AcceptOnceFileListFilter<>());
        return source;
    }

    @Bean
    public IntegrationFlow smbInboundFlow() {
        long pollDelay = props.getSource().getSmb().getPollDelay();
        return IntegrationFlow
                .from(smbMessageSource(), spec -> spec.poller(Pollers.fixedDelay(pollDelay)))
                .channel(forwarderInputChannel)
                .get();
    }
}
