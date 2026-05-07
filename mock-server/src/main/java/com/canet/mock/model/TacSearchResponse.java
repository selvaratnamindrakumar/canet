package com.canet.mock.model;

import java.util.List;

/**
 * Returned by multi-TAC and IMEI-list endpoints so callers can see
 * which of their requested identifiers were found and which were not.
 */
public record TacSearchResponse(
        int             requested,
        int             found,
        List<HandsetRecord> results,
        List<String>    notFound
) {}
