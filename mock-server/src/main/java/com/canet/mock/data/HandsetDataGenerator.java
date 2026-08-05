package com.canet.mock.data;

import com.canet.mock.model.DeviceCapabilities;
import com.canet.mock.model.HandsetRecord;
import com.canet.mock.model.NetworkInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates deterministic synthetic HandsetRecords for load and performance testing.
 *
 * TAC range: 10000000 – 10000999 (1 000 unique generated records cycling every 1 000 indexes).
 * All properties are derived from the index so results are reproducible and predictable.
 */
public class HandsetDataGenerator {

    private static final String[] MANUFACTURERS =
            {"Samsung", "Apple", "Google", "OnePlus", "Xiaomi",
             "Sony",    "Motorola", "Nokia", "Realme", "Vivo"};
    private static final String[] OS_VERSIONS   = {"12", "13", "14"};
    private static final double[] SCREENS        = {6.1, 6.4, 6.5, 6.7, 6.8};
    private static final int[]    YEARS          = {2021, 2022, 2023, 2024};

    private static final NetworkInfo   NET_5G_SA  = new NetworkInfo("2G/3G/4G/5G", "NSA/SA");
    private static final NetworkInfo   NET_5G_NSA = new NetworkInfo("2G/3G/4G/5G", "NSA");
    private static final NetworkInfo   NET_4G     = new NetworkInfo("2G/3G/4G",    "LTE");
    private static final NetworkInfo[] NETWORKS   = {NET_5G_SA, NET_5G_NSA, NET_4G};

    private static final DeviceCapabilities CAPS_FULL = new DeviceCapabilities(true,  true);
    private static final DeviceCapabilities CAPS_NFC  = new DeviceCapabilities(true,  false);
    private static final DeviceCapabilities CAPS_NONE = new DeviceCapabilities(false, false);
    private static final DeviceCapabilities[] CAPS    = {CAPS_FULL, CAPS_NFC, CAPS_NONE};

    /** Returns the 8-digit generated TAC for a given index (wraps at 1 000). */
    public static String tacFor(int index) {
        return String.format("10%06d", index % 1_000);
    }

    /** Generates a single deterministic HandsetRecord for the given index. */
    public static HandsetRecord generate(int index) {
        int    slot         = index % MANUFACTURERS.length;
        String manufacturer = MANUFACTURERS[slot];
        String os           = "Apple".equals(manufacturer) ? "iOS" : "Android";
        return new HandsetRecord(
                tacFor(index),
                manufacturer + " Load Test Device " + index,
                manufacturer,
                manufacturer.substring(0, 2).toUpperCase() + "-LT" + String.format("%04d", index),
                os,
                OS_VERSIONS[index % OS_VERSIONS.length],
                NETWORKS[index % NETWORKS.length],
                SCREENS[index % SCREENS.length],
                YEARS[index % YEARS.length],
                CAPS[index % CAPS.length]
        );
    }

    /**
     * Generates {@code count} records starting from {@code startIndex}.
     * Count is capped at 1 000 to prevent excessive memory allocation.
     */
    public static List<HandsetRecord> generate(int startIndex, int count) {
        int limit = Math.min(count, 1_000);
        List<HandsetRecord> list = new ArrayList<>(limit);
        for (int i = startIndex; i < startIndex + limit; i++) {
            list.add(generate(i));
        }
        return list;
    }
}
