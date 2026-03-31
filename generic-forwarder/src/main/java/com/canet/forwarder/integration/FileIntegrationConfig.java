package com.canet.forwarder.integration;

import java.io.File;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.Pollers;
import org.springframework.integration.file.FileReadingMessageSource;
import org.springframework.integration.file.filters.AbstractFileListFilter;
import org.springframework.integration.file.filters.AcceptOnceFileListFilter;
import org.springframework.integration.file.filters.CompositeFileListFilter;
import org.springframework.integration.file.filters.RegexPatternFileListFilter;
import org.springframework.messaging.MessageChannel;

import com.canet.forwarder.config.ForwarderProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring Integration – local file system inbound adapter.
 * Polls a directory for new files (log, xml, json, txt, csv, …) and
 * forwards them to the common channel.
 * Active only when {@code source.type=file}.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "source", name = "type", havingValue = "file")
public class FileIntegrationConfig {

    private final ForwarderProperties props;
    private final MessageChannel forwarderInputChannel;

    @Bean
    public FileReadingMessageSource fileMessageSource() {
        ForwarderProperties.FileSource fileCfg = props.getSource().getFile();

        CompositeFileListFilter<File> filter = new CompositeFileListFilter<>();
        filter.addFilter(new RegexPatternFileListFilter(fileCfg.getFilePattern()));
        filter.addFilter(new AcceptOnceFileListFilter<>());

        // Optional: skip files that exceed the configured size limit
        if (fileCfg.getMaxFileSizeBytes() > 0) {
            long maxBytes = fileCfg.getMaxFileSizeBytes();
            filter.addFilter(new AbstractFileListFilter<File>() {
                @Override
                public boolean accept(File file) {
                    return file.length() <= maxBytes;
                }
            });
        }

        FileReadingMessageSource source = new FileReadingMessageSource();
        source.setDirectory(new File(fileCfg.getDirectory()));
        source.setFilter(filter);
        source.setUseWatchService(true);
        source.setWatchEvents(
                FileReadingMessageSource.WatchEventType.CREATE,
                FileReadingMessageSource.WatchEventType.MODIFY);

        log.info("File inbound adapter: directory={}, pattern={}, maxSize={}, pollDelay={}ms",
                fileCfg.getDirectory(), fileCfg.getFilePattern(),
                fileCfg.getMaxFileSizeBytes() > 0 ? fileCfg.getMaxFileSizeBytes() + "B" : "unlimited",
                fileCfg.getPollDelay());
        return source;
    }

    @Bean
    public IntegrationFlow fileInboundFlow() {
        ForwarderProperties.FileSource fileCfg = props.getSource().getFile();

        var pollerSpec = fileCfg.getMaxFilesPerPoll() > 0
                ? Pollers.fixedDelay(fileCfg.getPollDelay()).maxMessagesPerPoll(fileCfg.getMaxFilesPerPoll())
                : Pollers.fixedDelay(fileCfg.getPollDelay());

        return IntegrationFlow
                .from(fileMessageSource(), spec -> spec.poller(pollerSpec))
                .channel(forwarderInputChannel)
                .get();
    }
}
