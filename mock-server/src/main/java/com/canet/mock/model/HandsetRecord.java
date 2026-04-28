package com.canet.mock.model;

/**
 * Represents a single handset entry as returned by the mock API.
 *
 * Field glossary:
 *   tac                      – Type Allocation Code (first 8 digits of IMEI)
 *   marketingName            – Consumer-facing device name
 *   manufacturer             – OEM name
 *   modelName                – Internal model / part number
 *   operatingSystem          – Base OS platform
 *   osVersion                – Version shipped at launch
 *   networkGenerations       – Supported radio access technologies
 *   displaySizeInches        – Screen diagonal in inches
 *   releaseYear              – Year the device was released to market
 *   nfcSupported             – Whether the device has an NFC chip
 *   wirelessChargingSupported– Whether the device supports wireless charging
 */
public record HandsetRecord(
        String  tac,
        String  marketingName,
        String  manufacturer,
        String  modelName,
        String  operatingSystem,
        String  osVersion,
        String  networkGenerations,
        double  displaySizeInches,
        int     releaseYear,
        boolean nfcSupported,
        boolean wirelessChargingSupported
) {}
