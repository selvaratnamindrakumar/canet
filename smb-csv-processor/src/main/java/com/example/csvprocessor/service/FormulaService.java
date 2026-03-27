package com.example.csvprocessor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Evaluates named formulas against the full row context map.
 *
 * <p>Each formula receives the entire row as a {@code Map<String, String>} keyed by
 * source column name (lower-cased).  The formula returns the derived string value
 * for the target column, or {@code null} on error.
 *
 * <p>Supported formulas:
 * <ul>
 *   <li>{@code calculateTac} — returns 5g_tac when generation is 5G, otherwise 4g-tac</li>
 *   <li>{@code deriveEci} — Extended Cell Identity = enodeb_id * 256 + cell_id (LTE)</li>
 *   <li>{@code deriveNodebId} — Node B ID = cell_id / 16 (UMTS/3G derivation)</li>
 *   <li>{@code deriveCvMbx} — Constructs carrier/voice MBX identifier from site and sector</li>
 *   <li>{@code derivePci} — Physical Cell ID = validated pass-through of 4g_pci</li>
 *   <li>{@code deriveLatitude} — WGS84 latitude from BNG easting + northing</li>
 *   <li>{@code deriveLongitude} — WGS84 longitude from BNG easting + northing</li>
 * </ul>
 */
@Service
public class FormulaService {

    private static final Logger log = LoggerFactory.getLogger(FormulaService.class);

    @Autowired
    private BngConverter bngConverter;

    /**
     * Evaluates a named formula against the given row context.
     *
     * @param formula  the formula name (case-insensitive)
     * @param row      complete row values keyed by lower-cased source column name
     * @return computed string value, or {@code null} if the formula cannot produce a result
     */
    public String evaluate(String formula, Map<String, String> row) {
        if (formula == null) return null;
        switch (formula.toLowerCase()) {
            case "calculatetac":    return calculateTac(row);
            case "deriveeci":       return deriveEci(row);
            case "derivenodebid":   return deriveNodebId(row);
            case "derivecvmbx":     return deriveCvMbx(row);
            case "derivepci":       return derivePci(row);
            case "derivelatitude":  return deriveLatitude(row);
            case "derivelongitude": return deriveLongitude(row);
            default:
                log.warn("Unknown formula '{}' — returning null", formula);
                return null;
        }
    }

    // -------------------------------------------------------------------------
    // Formula implementations
    // -------------------------------------------------------------------------

    /**
     * Returns the TAC (Tracking Area Code) appropriate for the cell generation.
     *
     * <ul>
     *   <li>If {@code generation} contains "5G" (case-insensitive) → return {@code 5g_tac}</li>
     *   <li>Otherwise → return {@code 4g-tac}</li>
     * </ul>
     */
    private String calculateTac(Map<String, String> row) {
        String generation = row.getOrDefault("generation", "").trim().toUpperCase();
        if (generation.contains("5G")) {
            return row.getOrDefault("5g_tac", "");
        }
        return row.getOrDefault("4g-tac", row.getOrDefault("4g_tac", ""));
    }

    /**
     * Derives the LTE Extended Cell Identity (ECI).
     *
     * <p>Formula: {@code ECI = eNodeB_ID * 256 + cell_index}
     *
     * <p>The cell_index (0–255) is taken from the lower 8 bits of {@code cell_id}.
     * For 4G LTE: ECI is a 28-bit identifier = eNB_ID (20 bits) * 256 + cell_index (8 bits).
     */
    private String deriveEci(Map<String, String> row) {
        try {
            long enodebId = parseLong(row, "enodeb_id");
            long cellId   = parseLong(row, "cell_id");
            long cellIndex = cellId & 0xFF;  // lower 8 bits
            long eci = enodebId * 256L + cellIndex;
            return String.valueOf(eci);
        } catch (Exception e) {
            log.debug("deriveEci failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Derives the UMTS Node B identifier from the WCDMA cell ID.
     *
     * <p>Formula: {@code NodeB_ID = cell_id / 16} (standard WCDMA cell_id encoding).
     * If {@code gnodeb_id} is present it is returned directly (5G NR gNodeB ID takes precedence).
     */
    private String deriveNodebId(Map<String, String> row) {
        // Prefer explicit gnodeb_id
        String gnbId = row.getOrDefault("gnodeb_id", "").trim();
        if (!gnbId.isEmpty()) return gnbId;

        try {
            long cellId  = parseLong(row, "cell_id");
            long nodebId = cellId / 16L;   // WCDMA: cell_id = NodeB_ID * 16 + cell_index
            return String.valueOf(nodebId);
        } catch (Exception e) {
            log.debug("deriveNodebId failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Derives a CV-MBX (Carrier Voice Mobile Broadband eXchange) identifier.
     *
     * <p>Format: {@code <site_code>-<sector_code>-<carrier_band>}
     * where carrier band is inferred from the CB (channel bandwidth) field for the active
     * generation, or "UNKNOWN" if unavailable.
     */
    private String deriveCvMbx(Map<String, String> row) {
        String siteCode   = row.getOrDefault("site_code", "").trim();
        String sectorCode = row.getOrDefault("sector_code", "").trim();
        String generation = row.getOrDefault("generation", "").trim().toUpperCase();

        String cbMhz;
        if (generation.contains("5G")) {
            cbMhz = row.getOrDefault("5g_cb_mhz", "").trim();
        } else if (generation.contains("4G") || generation.contains("LTE")) {
            cbMhz = row.getOrDefault("4g_cb_mhz", "").trim();
        } else {
            cbMhz = "";
        }

        String band = cbMhz.isEmpty() ? "UNKNOWN" : cbMhz + "MHz";
        return siteCode + "-" + sectorCode + "-" + band;
    }

    /**
     * Returns the Physical Cell ID (PCI).
     *
     * <p>For 4G/LTE the PCI is in range 0–503: PCI = 3 * PSS_ID + SSS_ID.
     * Here we validate and return the {@code 4g_pci} field directly; if the cell
     * is 5G, the 5G NR PCI (same range 0–1007) would come from a 5G-specific column.
     */
    private String derivePci(Map<String, String> row) {
        String generation = row.getOrDefault("generation", "").trim().toUpperCase();
        String pci;
        if (generation.contains("5G")) {
            // 5G NR PCI range: 0–1007
            pci = row.getOrDefault("5g_pci", row.getOrDefault("4g_pci", "")).trim();
        } else {
            pci = row.getOrDefault("4g_pci", "").trim();
        }

        if (pci.isEmpty()) return null;
        try {
            int pciVal = Integer.parseInt(pci);
            if (pciVal < 0 || pciVal > 1007) {
                log.warn("PCI value {} out of range 0–1007", pciVal);
                return null;
            }
            return String.valueOf(pciVal);
        } catch (NumberFormatException e) {
            log.debug("derivePci: invalid PCI value '{}'", pci);
            return null;
        }
    }

    /**
     * Derives WGS84 latitude from BNG (OSGB36) easting and northing values.
     *
     * <p>Uses the Ordnance Survey 7-parameter Helmert transformation.
     * Returns latitude to 7 decimal places (approximately 1 cm precision).
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
     * Derives WGS84 longitude from BNG (OSGB36) easting and northing values.
     *
     * <p>Uses the Ordnance Survey 7-parameter Helmert transformation.
     * Returns longitude to 7 decimal places.
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

    private long parseLong(Map<String, String> row, String key) {
        String val = row.getOrDefault(key, "").trim();
        if (val.isEmpty()) throw new IllegalArgumentException("Missing value for key: " + key);
        return Long.parseLong(val);
    }

    private double parseDouble(Map<String, String> row, String key) {
        String val = row.getOrDefault(key, "").trim();
        if (val.isEmpty()) throw new IllegalArgumentException("Missing value for key: " + key);
        return Double.parseDouble(val);
    }
}
