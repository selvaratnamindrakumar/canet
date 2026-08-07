package com.canet.mock.data;

import com.canet.mock.model.DeviceCapabilities;
import com.canet.mock.model.HandsetRecord;
import com.canet.mock.model.NetworkInfo;
import com.canet.mock.model.TacSearchResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * In-memory store of 20 sample handset records covering a range of manufacturers,
 * OS versions, and network types.
 *
 * Handset mode — search by TAC (8 digits):
 *   GET /handsetdetails?tac=35674108,35282402
 *
 * Cell mode — the main app extracts the first 8 digits of each IMEI as the TAC,
 *   then calls the same TAC endpoint. Example IMEI → TAC mappings:
 *
 *   IMEI              TAC        Device
 *   353456789012345   35345678   Samsung Galaxy S23 Ultra
 *   354567890123456   35456789   Apple iPhone 14 Pro
 *   352678012345678   35267801   Google Pixel 6a
 *   866789012345678   86678901   OnePlus 11
 *   867890123456789   86789012   Xiaomi 13 Pro
 *   868901234567890   86890123   OPPO Find X6 Pro
 *   359012345678901   35901234   Nokia G60 5G
 *   860123456789012   86012345   Realme GT 5 Pro
 *   861234567890123   86123456   Vivo X90 Pro+
 *   352345678901234   35234567   Motorola Razr 40 Ultra
 *
 * Generated load-test TACs (10000000–10000999) are also resolved via
 * HandsetDataGenerator — paste output from /loadtest/tacs into the UI.
 *
 * The nested {@code network} and {@code capabilities} objects are flattened by the
 * main app (flatten-separator: "-") into columns such as:
 *   network-generations, network-type, capabilities-nfc, capabilities-wirelessCharging
 */
@Component
public class HandsetRepository {

    private static final NetworkInfo NET_5G_NSA_SA = new NetworkInfo("2G/3G/4G/5G", "NSA/SA");
    private static final NetworkInfo NET_5G_NSA    = new NetworkInfo("2G/3G/4G/5G", "NSA");
    private static final NetworkInfo NET_4G        = new NetworkInfo("2G/3G/4G",    "LTE");

    private static final DeviceCapabilities CAPS_NFC_WC   = new DeviceCapabilities(true,  true);
    private static final DeviceCapabilities CAPS_NFC_ONLY = new DeviceCapabilities(true,  false);
    private static final DeviceCapabilities CAPS_NONE     = new DeviceCapabilities(false, false);

    private static final List<HandsetRecord> HANDSETS = List.of(

        // ── Original 10 ─────────────────────────────────────────────────────

        new HandsetRecord("35674108", "Samsung Galaxy S24 Ultra",  "Samsung",  "SM-S928B",   "Android", "14", NET_5G_NSA_SA, 6.8,  2024, CAPS_NFC_WC),
        new HandsetRecord("35282402", "Apple iPhone 15 Pro Max",   "Apple",    "A3105",       "iOS",     "17", NET_5G_NSA_SA, 6.7,  2023, CAPS_NFC_WC),
        new HandsetRecord("35428109", "Google Pixel 8 Pro",        "Google",   "GC3VE",       "Android", "14", NET_5G_NSA_SA, 6.7,  2023, CAPS_NFC_WC),
        new HandsetRecord("86345601", "OnePlus 12",                "OnePlus",  "CPH2573",     "Android", "14", NET_5G_NSA_SA, 6.82, 2024, CAPS_NFC_WC),
        new HandsetRecord("35567812", "Sony Xperia 1 VI",          "Sony",     "XQ-EC72",     "Android", "14", NET_5G_NSA_SA, 6.5,  2024, CAPS_NFC_WC),
        new HandsetRecord("86456723", "Xiaomi 14 Pro",             "Xiaomi",   "23116PN5BC",  "Android", "14", NET_5G_NSA_SA, 6.73, 2024, CAPS_NFC_WC),
        new HandsetRecord("35678934", "Motorola Edge 50 Pro",      "Motorola", "XT2401-2",    "Android", "14", NET_5G_NSA_SA, 6.7,  2024, CAPS_NFC_WC),
        new HandsetRecord("35789045", "Samsung Galaxy A54",        "Samsung",  "SM-A546B",    "Android", "13", NET_5G_NSA,    6.4,  2023, CAPS_NFC_ONLY),
        new HandsetRecord("35890156", "Apple iPhone SE (3rd Gen)", "Apple",    "A2595",        "iOS",     "17", NET_5G_NSA,    4.7,  2022, CAPS_NONE),
        new HandsetRecord("35012367", "Google Pixel 7a",           "Google",   "GWKK3",        "Android", "13", NET_5G_NSA_SA, 6.1,  2023, CAPS_NFC_WC),

        // ── 10 additional — TAC / IMEI pairs for cell-mode testing ────────────
        // IMEI 353456789012345 → TAC 35345678
        new HandsetRecord("35345678", "Samsung Galaxy S23 Ultra",  "Samsung",  "SM-S918B",    "Android", "13", NET_5G_NSA_SA, 6.8,  2023, CAPS_NFC_WC),
        // IMEI 354567890123456 → TAC 35456789
        new HandsetRecord("35456789", "Apple iPhone 14 Pro",       "Apple",    "A2651",        "iOS",     "16", NET_5G_NSA,    6.1,  2022, CAPS_NFC_WC),
        // IMEI 352678012345678 → TAC 35267801
        new HandsetRecord("35267801", "Google Pixel 6a",           "Google",   "GB17L",        "Android", "12", NET_5G_NSA,    6.1,  2022, CAPS_NFC_ONLY),
        // IMEI 866789012345678 → TAC 86678901
        new HandsetRecord("86678901", "OnePlus 11",                "OnePlus",  "CPH2449",      "Android", "13", NET_5G_NSA_SA, 6.7,  2023, CAPS_NFC_WC),
        // IMEI 867890123456789 → TAC 86789012
        new HandsetRecord("86789012", "Xiaomi 13 Pro",             "Xiaomi",   "2210132C",     "Android", "13", NET_5G_NSA_SA, 6.73, 2023, CAPS_NFC_WC),
        // IMEI 868901234567890 → TAC 86890123
        new HandsetRecord("86890123", "OPPO Find X6 Pro",          "OPPO",     "PGEM10",       "Android", "13", NET_5G_NSA_SA, 6.82, 2023, CAPS_NFC_WC),
        // IMEI 359012345678901 → TAC 35901234
        new HandsetRecord("35901234", "Nokia G60 5G",              "Nokia",    "TA-1490",      "Android", "12", NET_5G_NSA,    6.58, 2022, CAPS_NFC_ONLY),
        // IMEI 860123456789012 → TAC 86012345
        new HandsetRecord("86012345", "Realme GT 5 Pro",           "Realme",   "RMX3888",      "Android", "14", NET_5G_NSA_SA, 6.78, 2024, CAPS_NFC_WC),
        // IMEI 861234567890123 → TAC 86123456
        new HandsetRecord("86123456", "Vivo X90 Pro+",             "Vivo",     "V2227A",       "Android", "13", NET_5G_NSA_SA, 6.78, 2023, CAPS_NFC_WC),
        // IMEI 352345678901234 → TAC 35234567
        new HandsetRecord("35234567", "Motorola Razr 40 Ultra",    "Motorola", "XT2321-1",     "Android", "13", NET_5G_NSA_SA, 6.9,  2023, CAPS_NFC_WC)
    );

    private final Map<String, HandsetRecord> byTac = HANDSETS.stream()
            .collect(Collectors.toMap(HandsetRecord::tac, Function.identity()));

    public List<HandsetRecord> findAll() {
        return HANDSETS;
    }

    public List<HandsetRecord> findByManufacturer(String manufacturer) {
        return HANDSETS.stream()
                .filter(h -> h.manufacturer().equalsIgnoreCase(manufacturer))
                .collect(Collectors.toList());
    }

    public Optional<HandsetRecord> findByTac(String tac) {
        HandsetRecord record = byTac.get(tac);
        if (record != null) return Optional.of(record);
        // Fall back to generated records for load-test TAC range (10000000–10000999)
        if (tac.length() == 8 && tac.startsWith("10")) {
            try {
                int index = Integer.parseInt(tac.substring(2));
                if (index < 1_000) return Optional.of(HandsetDataGenerator.generate(index));
            } catch (NumberFormatException ignored) {}
        }
        return Optional.empty();
    }

    public TacSearchResponse findByTacs(List<String> tacs) {
        List<HandsetRecord> found    = new ArrayList<>();
        List<String>        notFound = new ArrayList<>();
        for (String t : tacs) {
            findByTac(t).ifPresentOrElse(found::add, () -> notFound.add(t));
        }
        return new TacSearchResponse(tacs.size(), found.size(), found, notFound);
    }
}
