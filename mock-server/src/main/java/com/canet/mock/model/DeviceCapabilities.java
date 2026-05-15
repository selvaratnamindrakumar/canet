package com.canet.mock.model;

/**
 * Hardware capability flags returned as a nested object so that flattening produces:
 *   capabilities.nfc              → capabilities-nfc
 *   capabilities.wirelessCharging → capabilities-wirelessCharging
 */
public record DeviceCapabilities(
        boolean nfc,
        boolean wirelessCharging
) {}
