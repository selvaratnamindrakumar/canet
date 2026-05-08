package com.canet.app.service;

import java.util.List;
import java.util.Map;

public interface HttpsDataService {

    List<Map<String, Object>> fetchAll(String url);

    Map<String, Object> fetchById(String url, String id);

    /**
     * Looks up up to 20 TACs in one call.
     * The remote endpoint returns a TacSearchResponse envelope; this method
     * unpacks it so callers get strongly-partitioned found / notFound lists.
     */
    TacSearchResult fetchByTacs(String url, List<String> tacs);

    record TacSearchResult(
            List<Map<String, Object>> results,
            List<String> notFound
    ) {}
}
