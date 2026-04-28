package com.canet.app.service;

import java.util.List;
import java.util.Map;

public interface HttpsDataService {

    List<Map<String, Object>> fetchAll();

    Map<String, Object> fetchById(String id);
}
