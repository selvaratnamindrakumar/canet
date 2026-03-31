package com.example.csvprocessor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.Map;

/**
 * Evaluates named formulas against the full row context map.
 *
 * <p>Each formula receives the entire row as a {@code Map<String, String>} keyed
 * by lower-cased source column name.  The formula returns the derived string value
 * for the target column, or {@code null} when inputs are missing/invalid.
 *
 * <p>All generation-dependent formulas check {@code generation} (case-insensitive)
 * and route to the 5G path when it contains "5G", otherwise the 4G/legacy path.
 *
 * <p>Supported formulas:
 * <ul>
 *   <li>{@code calculateNodebId}  — ENODEB_ID: 4G→enodeb_id, 5G→gnodeb_id</li>
 *   <li>{@code calculateTac}      — TAC: 4G→4g-tac, 5G→5g_tac</li>
 *   <li>{@code calculateIpAddress}— IP address: 4G→4g_enb_ip_address, 5G→5g_gnb_ip_address</li>
 *   <li>{@code calculatePci}      — PCI: 4G→4g_pci (0-503), 5G→5g_pci (0-1007)</li>
 *   <li>{@code calculateCbMhz}    — channel bandwidth: 4G→4g_cb_mhz, 5G→5g_cb_mhz</li>
 *   <li>{@code deriveEci}         — 4G: eNB_ID×256 + cell_index;
 *                                   5G: gNB_ID × 2^(36−gNB_ID_length) + cell_id (BigInteger)</li>
 *   <li>{@code deriveLatitude}    — WGS84 lat from BNG easting + northing</li>
 *   <li>{@code deriveLongitude}   — WGS84 lon from BNG easting + northing</li>
 * </ul>
 */
@Service
public class FormulaService {

    private static final Logger log = LoggerFactory.getLogger(FormulaService.class);

    /** Default gNB-ID bit-length used when gnodeb_id_lenth column is absent/empty. */
    private static final int DEFAULT_GNB_ID_LENGTH = 22;

    @Autowired
    private BngConverter bngConverter;

    // -------------------------------------------------------------------------
    // Dispatch
    // -------------------------------------------------------------------------

    /**
     * Evaluates a named formula against the given row context.
     *
     * @param formula formula name (case-insensitive)
     * @param row     complete row values keyed by lower-cased source column name
     * @return computed string value, or {@code null} if the formula cannot produce a result
     */
    public String evaluate(String formula, Map<String, String> row) {
        if (formula == null) return null;
        switch (formula.toLowerCase()) {
            case "calculatenodebid":   return calculateNodebId(row);
            case "calculatetac":       return calculateTac(row);
            case "calculateipaddress": return calculateIpAddress(row);
            case "calculatepci":       return calculatePci(row);
            case "calculatecbmhz":     return calculateCbMhz(row);
            case "deriveeci":          return deriveEci(row);
            case "derivelatitude":     return deriveLatitude(row);
            case "derivelongitude":    return deriveLongitude(row);
            default:
                log.warn("Unknown formula '{}' — returning null", formula);
                return null;
        }
    }

    // -------------------------------------------------------------------------
    // Formula implementations
    // -------------------------------------------------------------------------

    /**
     * Returns the node/base-station identifier for the output ENODEB_ID column.
     * <ul>
     *   <li>4G/LTE → {@code enodeb_id}</li>
     *   <li>5G/NR  → {@code gnodeb_id}</li>
     * </ul>
     */
    private String calculateNodebId(Map<String, String> row) {
        if (is5G(row)) {
            return trimOrNull(row, "gnodeb_id");
        }
        return trimOrNull(row, "enodeb_id");
    }

    /**
     * Returns the Tracking Area Code (TAC) for the output TAC column.
     * <ul>
     *   <li>5G/NR  → {@code 5g_tac}</li>
     *   <li>4G/LTE → {@code 4g-tac}</li>
     * </ul>
     */
    private String calculateTac(Map<String, String> row) {
        if (is5G(row)) {
            return trimOrNull(row, "5g_tac");
        }
        return trimOrNull(row, "4g-tac");
    }

    /**
     * Returns the IP address of the base station controller.
     * <ul>
     *   <li>5G/NR  → {@code 5g_gnb_ip_address}</li>
     *   <li>4G/LTE → {@code 4g_enb_ip_address}</li>
     * </ul>
     */
    private String calculateIpAddress(Map<String, String> row) {
        if (is5G(row)) {
            return trimOrNull(row, "5g_gnb_ip_address");
        }
        return trimOrNull(row, "4g_enb_ip_address");
    }

    /**
     * Returns the Physical Cell ID (PCI) for the output PCI column.
     * <ul>
     *   <li>5G/NR  → {@code 5g_pci} (valid range 0–1007)</li>
     *   <li>4G/LTE → {@code 4g_pci} (valid range 0–503)</li>
     * </ul>
     */
    private String calculatePci(Map<String, String> row) {
        String pci = is5G(row)
                ? row.getOrDefault("5g_pci", "").trim()
                : row.getOrDefault("4g_pci", "").trim();

        if (pci.isEmpty()) return null;
        try {
            int val = Integer.parseInt(pci);
            int maxPci = is5G(row) ? 1007 : 503;
            if (val < 0 || val > maxPci) {
                log.warn("PCI value {} out of range 0–{}", val, maxPci);
                return null;
            }
            return String.valueOf(val);
        } catch (NumberFormatException e) {
            log.debug("calculatePci: non-numeric PCI value '{}'", pci);
            return null;
        }
    }

    /**
     * Returns the channel bandwidth (MHz) for the output CB_MHZ column.
     * <ul>
     *   <li>5G/NR  → {@code 5g_cb_mhz}</li>
     *   <li>4G/LTE → {@code 4g_cb_mhz}</li>
     * </ul>
     */
    private String calculateCbMhz(Map<String, String> row) {
        if (is5G(row)) {
            return trimOrNull(row, "5g_cb_mhz");
        }
        return trimOrNull(row, "4g_cb_mhz");
    }

    /**
     * Derives the Extended/NR Cell Identity (ECI / NCI).
     *
     * <p><b>4G LTE</b> (3GPP TS 36.413):
     * <pre>  ECI = eNB_ID × 256 + cell_index
     *  cell_index = lower 8 bits of cell_id</pre>
     *
     * <p><b>5G NR</b> (3GPP TS 38.413):
     * <pre>  NCI = gNB_ID × 2^(36 − gNB_ID_length) + cell_id</pre>
     * {@code gNB_ID_length} defaults to {@value #DEFAULT_GNB_ID_LENGTH} if absent.
     * BigInteger is used to handle the full 36-bit NCI range safely.
     */
    private String deriveEci(Map<String, String> row) {
        try {
            String cellIdStr = row.getOrDefault("cell_id", "").trim();
            if (cellIdStr.isEmpty()) {
                log.debug("deriveEci: cell_id is empty");
                return null;
            }
            BigInteger cellId = new BigInteger(cellIdStr);

            if (is5G(row)) {
                // 5G NR: NCI = gNB_ID * 2^(36 - gNB_ID_length) + cell_id
                String gnbStr = row.getOrDefault("gnodeb_id", "").trim();
                if (gnbStr.isEmpty()) {
                    log.debug("deriveEci: gnodeb_id is empty for 5G row");
                    return null;
                }
                BigInteger gnodebId = new BigInteger(gnbStr);

                String lenStr = row.getOrDefault("gnodeb_id_lenth", "").trim(); // source typo preserved
                int gnbIdLength = lenStr.isEmpty()
                        ? DEFAULT_GNB_ID_LENGTH
                        : Integer.parseInt(lenStr);

                // Validate length range per 3GPP TS 38.413 (22–32 bits)
                if (gnbIdLength < 22 || gnbIdLength > 32) {
                    log.warn("deriveEci: gnodeb_id_length {} out of range 22–32, using default {}",
                            gnbIdLength, DEFAULT_GNB_ID_LENGTH);
                    gnbIdLength = DEFAULT_GNB_ID_LENGTH;
                }

                BigInteger shift = BigInteger.valueOf(1L << (36 - gnbIdLength));
                return gnodebId.multiply(shift).add(cellId).toString();

            } else {
                // 4G LTE: ECI = eNB_ID * 256 + cell_index (lower 8 bits of cell_id)
                String enbStr = row.getOrDefault("enodeb_id", "").trim();
                if (enbStr.isEmpty()) {
                    log.debug("deriveEci: enodeb_id is empty for 4G row");
                    return null;
                }
                BigInteger enodebId = new BigInteger(enbStr);
                BigInteger cellIndex = cellId.and(BigInteger.valueOf(0xFF));
                return enodebId.multiply(BigInteger.valueOf(256)).add(cellIndex).toString();
            }
        } catch (Exception e) {
            log.debug("deriveEci failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Derives WGS84 latitude from BNG (OSGB36) easting and northing.
     * Returns latitude to 7 decimal places (~1 cm precision).
     */
    private String deriveLatitude(Map<String, String> row) {
        try {
            double easting  = parseDouble(row, "bng_easting_m");
            double northing = parseDouble(row, "bng_northing_m");
            BngConverter.LatLon ll = bngConverter.convert(easting, northing);
            if (ll == null) return null;
            return String.format("%.7f", ll.latitude);
        } catch (Exception e) {
            log.debug("deriveLatitude failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Derives WGS84 longitude from BNG (OSGB36) easting and northing.
     */
    private String deriveLongitude(Map<String, String> row) {
        try {
            double easting  = parseDouble(row, "bng_easting_m");
            double northing = parseDouble(row, "bng_northing_m");
            BngConverter.LatLon ll = bngConverter.convert(easting, northing);
            if (ll == null) return null;
            return String.format("%.7f", ll.longitude);
        } catch (Exception e) {
            log.debug("deriveLongitude failed: {}", e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Returns {@code true} when the row's generation field contains "5G". */
    private boolean is5G(Map<String, String> row) {
        return row.getOrDefault("generation", "").trim().toUpperCase().contains("5G");
    }

    private String trimOrNull(Map<String, String> row, String key) {
        String val = row.getOrDefault(key, "").trim();
        return val.isEmpty() ? null : val;
    }

    private double parseDouble(Map<String, String> row, String key) {
        String val = row.getOrDefault(key, "").trim();
        if (val.isEmpty()) throw new IllegalArgumentException("Missing value for: " + key);
        return Double.parseDouble(val);
    }
}
