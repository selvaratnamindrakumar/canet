package com.canet.mock.model;

/**
 * Represents a single handset entry as returned by the mock API.
 *
 * {@code network} and {@code capabilities} are intentionally nested objects so that
 * the main application's flatten feature (flatten-separator: "-") converts them to
 * flat columns:
 *
 *   network.generations         → network-generations
 *   network.type                → network-type
 *   capabilities.nfc            → capabilities-nfc
 *   capabilities.wirelessCharging → capabilities-wirelessCharging
 *
 * Field glossary:
 *   tac               – Type Allocation Code (first 8 digits of IMEI)
 *   marketingName     – Consumer-facing device name
 *   manufacturer      – OEM name
 *   modelName         – Internal model / part number
 *   operatingSystem   – Base OS platform
 *   osVersion         – Version shipped at launch
 *   network           – Radio / network technology details (nested)
 *   displaySizeInches – Screen diagonal in inches
 *   releaseYear       – Year the device was released to market
 *   capabilities      – Boolean feature flags (nested)
 */
public record HandsetRecord(
        String             tac,
        String             marketingName,
        String             manufacturer,
        String             modelName,
        String             operatingSystem,
        String             osVersion,
        NetworkInfo        network,
        double             displaySizeInches,
        int                releaseYear,
        DeviceCapabilities capabilities
) {}
