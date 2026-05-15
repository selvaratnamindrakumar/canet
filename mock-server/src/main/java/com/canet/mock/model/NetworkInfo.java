package com.canet.mock.model;

/**
 * Network technology details returned as a nested object so that the main app's
 * JSON flattening feature (flatten-separator: "-") produces readable column names:
 *   network.generations → network-generations
 *   network.type        → network-type
 */
public record NetworkInfo(
        String generations,   // e.g. "2G/3G/4G/5G"
        String type           // "NSA", "SA", "NSA/SA", "LTE"
) {}
