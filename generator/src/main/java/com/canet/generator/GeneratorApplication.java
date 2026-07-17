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

    @Value("${network.interface.name:}")
    private String networkInterfaceName;

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

        if (networkInterfaceName == null || networkInterfaceName.isBlank()) {
            logAvailableInterfaces(allInterfaces);
            log.warn("network.interface.name is not set — stopping.");
            log.warn("Add the chosen interface name to application.properties:");
            log.warn("  network.interface.name=<name from list above>");
            System.exit(0);
        }

        PcapNetworkInterface selected = allInterfaces.stream()
                .filter(this::matchesConfiguredName)
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "No interface found matching: '" + networkInterfaceName
                        + "'. Set network.interface.name to one of the names logged at startup."));

        log.info("Capture interface  : {}", selected.getName());
        if (selected.getDescription() != null) {
            log.info("Description        : {}", selected.getDescription());
        }
        log.info("numThreads={} queueSize={}", numThreads, queueSize);

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

                final byte[] payload = udpPacket.getPayload().getRawData();
                final int    srcPort = udpPacket.getHeader().getSrcPort().valueAsInt();
                final int    dstPort = udpPacket.getHeader().getDstPort().valueAsInt();

                String srcIp = null;
                String dstIp = null;
                if (packet.contains(IpV4Packet.class)) {
                    IpV4Packet.IpV4Header ip = packet.get(IpV4Packet.class).getHeader();
                    srcIp = ip.getSrcAddr().getHostAddress();
                    dstIp = ip.getDstAddr().getHostAddress();
                }
                final String finalSrcIp = srcIp;
                final String finalDstIp = dstIp;

                processingExecutor.submit(() ->
                        updateMessageHandler.handleMessage(
                                payload, srcPort, finalSrcIp, dstPort, finalDstIp, seq, rcvTime));
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

    /**
     * Logs every pcap interface with its index, pcap device name, human-readable
     * description, and bound IP addresses.  Called at startup when
     * network.interface.name is blank so the operator can identify the correct
     * device and set the property.
     *
     * On Windows the pcap name looks like:
     *   \Device\NPF_{XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX}
     * In application.properties escape each backslash:
     *   network.interface.name=\\Device\\NPF_{XXXXXXXX-...}
     */
    private void logAvailableInterfaces(List<PcapNetworkInterface> interfaces) {
        log.info("──────────────────────────────────────────────────────────────");
        log.info("network.interface.name is not configured.");
        log.info("Available pcap interfaces ({} found):", interfaces.size());
        log.info("──────────────────────────────────────────────────────────────");
        for (int i = 0; i < interfaces.size(); i++) {
            PcapNetworkInterface nif = interfaces.get(i);
            log.info("[{}] Name        : {}", i, nif.getName());
            if (nif.getDescription() != null && !nif.getDescription().isBlank()) {
                log.info("    Description : {}", nif.getDescription());
            }
            nif.getAddresses().forEach(addr -> {
                if (addr.getAddress() != null) {
                    log.info("    Address     : {}", addr.getAddress().getHostAddress());
                }
            });
        }
        log.info("──────────────────────────────────────────────────────────────");
        log.info("Set network.interface.name in application.properties.");
        log.info("On Windows, escape backslashes — example:");
        log.info("  network.interface.name=\\\\Device\\\\NPF_{{XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX}}");
        log.info("──────────────────────────────────────────────────────────────");
    }

    /**
     * Returns true if {@code nif} matches the configured name.
     * Matching is case-insensitive and also accepts an IP address
     * bound to the interface, so both forms work in application.properties:
     *   network.interface.name=\\Device\\NPF_{...}
     *   network.interface.name=192.168.1.10
     */
    private boolean matchesConfiguredName(PcapNetworkInterface nif) {
        if (nif.getName().equalsIgnoreCase(networkInterfaceName)) {
            return true;
        }
        return nif.getAddresses().stream()
                .anyMatch(a -> networkInterfaceName.equals(
                        a.getAddress() != null ? a.getAddress().getHostAddress() : null));
    }
}
