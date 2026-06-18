package com.canet.udplistener;

import com.canet.udplistener.handler.UpdateMessageHandler;
import lombok.extern.slf4j.Slf4j;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.core.PcapNetworkInterface;
import org.pcap4j.core.Pcaps;
import org.pcap4j.packet.IpV4Packet;
import org.pcap4j.packet.UdpPacket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@SpringBootApplication
public class UdpListenerApplication implements CommandLineRunner {

    @Autowired
    private UpdateMessageHandler updateMessageHandler;

    @Value("${num.threads:8}")
    private int numThreads;

    @Value("${queue.size:5000}")
    private int queueSize;

    @Value("${network.interface:}")
    private String networkInterface;

    @Value("${udp.receive.buffer.size:16777216}")
    private int udpReceiveBufferSize;

    private ExecutorService captureExecutor;
    private ThreadPoolExecutor processingExecutor;
    private ScheduledExecutorService monitorExecutor;

    private final AtomicLong rejectedCount = new AtomicLong(0);
    private final AtomicLong capturedCount = new AtomicLong(0);

    public static void main(String[] args) {
        SpringApplication.run(UdpListenerApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {

        captureExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "pcap-capture-thread");
            t.setDaemon(false);
            return t;
        });

        processingExecutor = new ThreadPoolExecutor(
                numThreads,
                numThreads,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueSize),
                (runnable, executor) -> {
                    // Log every rejection so overload is visible immediately
                    long count = rejectedCount.incrementAndGet();
                    if (count % 100 == 1) {
                        log.error("Processing queue FULL — task rejected (total rejections={}). "
                                + "active={} queueSize={}",
                                count,
                                executor.getActiveCount(),
                                executor.getQueue().size());
                    }
                }
        );
        processingExecutor.prestartAllCoreThreads();

        startMonitoring();

        List<PcapNetworkInterface> allInterfaces = Pcaps.findAllDevs();
        log.info("Available network interfaces: {}",
                allInterfaces.stream().map(PcapNetworkInterface::getName).toList());

        PcapNetworkInterface selectedInterface = allInterfaces.stream()
                .filter(this::isConfiguredInterface)
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "No matching network interface found for: " + networkInterface));

        log.info("Starting packet capture on interface: {}", selectedInterface.getName());

        captureExecutor.submit(() -> startPacketCapture(selectedInterface));
    }

    private void startPacketCapture(PcapNetworkInterface ni) {
        try {
            PcapHandle handle = ni.openLive(
                    65536,
                    PcapNetworkInterface.PromiscuousMode.PROMISCUOUS,
                    10
            );

            // Increase OS-level socket receive buffer to reduce kernel drops
            handle.setSnaplen(65536);

            log.info("Packet capture started. numThreads={} queueSize={}", numThreads, queueSize);

            handle.loop(-1, packet -> {
                if (!packet.contains(UdpPacket.class)) {
                    return;
                }

                UdpPacket udpPacket = packet.get(UdpPacket.class);
                if (udpPacket.getPayload() == null) {
                    return;
                }

                byte[] payloadBytes = udpPacket.getPayload().getRawData();
                int dstPort = udpPacket.getHeader().getDstPort().valueAsInt();

                String dstIp = null;
                if (packet.contains(IpV4Packet.class)) {
                    dstIp = packet.get(IpV4Packet.class)
                            .getHeader()
                            .getDstAddr()
                            .getHostAddress();
                }

                final String finalDstIp = dstIp;
                capturedCount.incrementAndGet();

                processingExecutor.submit(() ->
                        updateMessageHandler.handleMessage(payloadBytes, dstPort, finalDstIp));
            });

        } catch (Exception e) {
            log.error("Packet capture failed", e);
        }
    }

    private void startMonitoring() {
        monitorExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "executor-monitor");
            t.setDaemon(true);
            return t;
        });

        monitorExecutor.scheduleAtFixedRate(() -> {
            log.info("MONITOR captured={} active={} queue={} completed={} rejected={}",
                    capturedCount.get(),
                    processingExecutor.getActiveCount(),
                    processingExecutor.getQueue().size(),
                    processingExecutor.getCompletedTaskCount(),
                    rejectedCount.get());
        }, 1, 1, TimeUnit.MINUTES);
    }

    private boolean isConfiguredInterface(PcapNetworkInterface nif) {
        if (networkInterface == null || networkInterface.isBlank()) {
            // Take first non-loopback interface if none configured
            return !nif.getName().startsWith("lo");
        }
        return nif.getName().equalsIgnoreCase(networkInterface)
                || nif.getAddresses().stream()
                        .anyMatch(a -> networkInterface.equals(
                                a.getAddress() != null ? a.getAddress().getHostAddress() : null));
    }
}
