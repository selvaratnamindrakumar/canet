package com.canet.generator;

import com.canet.generator.handler.UpdateMessageHandler;
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

import java.time.Instant;
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
public class GeneratorApplication implements CommandLineRunner {

    @Autowired
    private UpdateMessageHandler updateMessageHandler;

    @Value("${num.threads:8}")
    private int numThreads;

    @Value("${queue.size:5000}")
    private int queueSize;

    @Value("${network.interface:}")
    private String networkInterface;

    private ExecutorService captureExecutor;
    private ThreadPoolExecutor processingExecutor;
    private ScheduledExecutorService monitorExecutor;

    private final AtomicLong rejectedCount   = new AtomicLong(0);
    private final AtomicLong capturedCount   = new AtomicLong(0);

    /**
     * Monotonic counter stamped inside the single-threaded pcap callback
     * before the task is handed to the processing pool.
     * Worker threads completing out of order do not affect this value.
     */
    private final AtomicLong sequenceCounter = new AtomicLong(0);

    public static void main(String[] args) {
        SpringApplication.run(GeneratorApplication.class, args);
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
                    long count = rejectedCount.incrementAndGet();
                    if (count % 100 == 1) {
                        log.error("Queue FULL — task rejected (total={}). active={} queue={}",
                                count, executor.getActiveCount(), executor.getQueue().size());
                    }
                }
        );
        processingExecutor.prestartAllCoreThreads();

        startMonitoring();

        List<PcapNetworkInterface> allInterfaces = Pcaps.findAllDevs();
        log.info("Available interfaces: {}",
                allInterfaces.stream().map(PcapNetworkInterface::getName).toList());

        PcapNetworkInterface selected = allInterfaces.stream()
                .filter(this::isConfiguredInterface)
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "No interface found for: " + networkInterface));

        log.info("Starting capture on interface={} numThreads={} queueSize={}",
                selected.getName(), numThreads, queueSize);

        captureExecutor.submit(() -> startCapture(selected));
    }

    private void startCapture(PcapNetworkInterface ni) {
        try {
            PcapHandle handle = ni.openLive(65536,
                    PcapNetworkInterface.PromiscuousMode.PROMISCUOUS, 10);

            handle.loop(-1, (org.pcap4j.core.PacketListener) packet -> {

                if (!packet.contains(UdpPacket.class)) return;

                UdpPacket udpPacket = packet.get(UdpPacket.class);
                if (udpPacket.getPayload() == null) return;

                /*
                 * Stamp sequence and receivedAt in the capture callback —
                 * before submit() — so the values reflect true arrival order
                 * and arrival time regardless of worker-thread scheduling.
                 */
                final long    seq     = sequenceCounter.getAndIncrement();
                final Instant rcvTime = Instant.now();
                capturedCount.incrementAndGet();

                final byte[]  payload = udpPacket.getPayload().getRawData();
                final int     dstPort = udpPacket.getHeader().getDstPort().valueAsInt();
                String dstIp = null;
                if (packet.contains(IpV4Packet.class)) {
                    dstIp = packet.get(IpV4Packet.class)
                            .getHeader().getDstAddr().getHostAddress();
                }
                final String finalDstIp = dstIp;

                processingExecutor.submit(() ->
                        updateMessageHandler.handleMessage(payload, dstPort, finalDstIp, seq, rcvTime));
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
        monitorExecutor.scheduleAtFixedRate(() ->
                log.info("MONITOR captured={} active={} queue={} completed={} rejected={}",
                        capturedCount.get(),
                        processingExecutor.getActiveCount(),
                        processingExecutor.getQueue().size(),
                        processingExecutor.getCompletedTaskCount(),
                        rejectedCount.get()),
                1, 1, TimeUnit.MINUTES);
    }

    private boolean isConfiguredInterface(PcapNetworkInterface nif) {
        if (networkInterface == null || networkInterface.isBlank()) {
            return !nif.getName().startsWith("lo");
        }
        return nif.getName().equalsIgnoreCase(networkInterface)
                || nif.getAddresses().stream()
                        .anyMatch(a -> networkInterface.equals(
                                a.getAddress() != null ? a.getAddress().getHostAddress() : null));
    }
}
