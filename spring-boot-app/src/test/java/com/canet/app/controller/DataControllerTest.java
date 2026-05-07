package com.canet.app.controller;

import com.canet.app.service.HttpsDataService;
import com.canet.app.service.HttpsDataService.TacSearchResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DataController.class)
class DataControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean  HttpsDataService dataService;

    // =========================================================================
    // GET / — no search param → load all
    // =========================================================================

    @Test
    void index_noTacParam_rendersAllRecords() throws Exception {
        when(dataService.fetchAll()).thenReturn(sampleRows());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("totalCount", 2))
                .andExpect(model().attribute("tacQuery",   ""))
                .andExpect(content().string(containsString("Galaxy S24")))
                .andExpect(content().string(containsString("iPhone 15 Pro")));
    }

    @Test
    void index_noTacParam_compactColsPassedToTemplate() throws Exception {
        when(dataService.fetchAll()).thenReturn(sampleRows());

        mockMvc.perform(get("/"))
                .andExpect(model().attribute("compactCols", 5));
    }

    @Test
    void index_emptyResult_showsWarning() throws Exception {
        when(dataService.fetchAll()).thenReturn(List.of());

        mockMvc.perform(get("/"))
                .andExpect(model().attribute("totalCount", 0))
                .andExpect(content().string(containsString("No records returned")));
    }

    // =========================================================================
    // GET /?tac=T1,T2 — TAC search
    // =========================================================================

    @Test
    void index_tacParam_callsFetchByTacs_andPopulatesModel() throws Exception {
        when(dataService.fetchByTacs(List.of("35674108", "35282402")))
                .thenReturn(new TacSearchResult(sampleRows(), List.of()));

        mockMvc.perform(get("/?tac=35674108,35282402"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalCount",   2))
                .andExpect(model().attribute("tacQuery",     "35674108,35282402"))
                .andExpect(content().string(containsString("Galaxy S24")));
    }

    @Test
    void index_tacParam_notFoundShownInAlert() throws Exception {
        when(dataService.fetchByTacs(any()))
                .thenReturn(new TacSearchResult(List.of(sampleRows().get(0)),
                                                 List.of("22222222", "11111111")));

        mockMvc.perform(get("/?tac=35674108,22222222,11111111"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalCount", 1))
                .andExpect(content().string(containsString("22222222")))
                .andExpect(content().string(containsString("11111111")));
    }

    @Test
    void index_tacParam_allNotFound_showsZeroResults() throws Exception {
        when(dataService.fetchByTacs(any()))
                .thenReturn(new TacSearchResult(List.of(), List.of("00000000")));

        mockMvc.perform(get("/?tac=00000000"))
                .andExpect(model().attribute("totalCount", 0))
                .andExpect(content().string(containsString("00000000")));
    }

    // =========================================================================
    // GET /api/data — REST JSON
    // =========================================================================

    @Test
    void apiData_returnsJsonArray() throws Exception {
        when(dataService.fetchAll()).thenReturn(sampleRows());

        mockMvc.perform(get("/api/data").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].tac",           is("35674108")))
                .andExpect(jsonPath("$[1].manufacturer",  is("Apple")));
    }

    // =========================================================================
    // GET /api/data/{id}
    // =========================================================================

    @Test
    void apiDataById_found_returnsRecord() throws Exception {
        when(dataService.fetchById("35674108")).thenReturn(sampleRows().get(0));

        mockMvc.perform(get("/api/data/35674108").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tac", is("35674108")));
    }

    @Test
    void apiDataById_notFound_returns404() throws Exception {
        when(dataService.fetchById("00000000")).thenReturn(null);

        mockMvc.perform(get("/api/data/00000000").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private List<Map<String, Object>> sampleRows() {
        Map<String, Object> s24 = new LinkedHashMap<>();
        s24.put("tac", "35674108");
        s24.put("marketingName", "Galaxy S24 Ultra");
        s24.put("manufacturer", "Samsung");
        s24.put("modelName", "SM-S928B");
        s24.put("operatingSystem", "Android");
        s24.put("osVersion", "14");
        s24.put("networkGenerations", "2G/3G/4G/5G");

        Map<String, Object> iphone = new LinkedHashMap<>();
        iphone.put("tac", "35282402");
        iphone.put("marketingName", "iPhone 15 Pro");
        iphone.put("manufacturer", "Apple");
        iphone.put("modelName", "A3105");
        iphone.put("operatingSystem", "iOS");
        iphone.put("osVersion", "17");
        iphone.put("networkGenerations", "2G/3G/4G/5G");

        return List.of(s24, iphone);
    }
}
