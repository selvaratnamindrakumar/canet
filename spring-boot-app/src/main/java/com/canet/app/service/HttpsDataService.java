package com.canet.app.service;

import com.canet.app.model.DataItem;

import java.util.List;

public interface HttpsDataService {

    List<DataItem> fetchAll();

    DataItem fetchById(int id);
}
