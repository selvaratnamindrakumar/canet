package com.canet.app.controller;

import com.canet.app.service.HttpsDataService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test for DataController using handset-shaped data.
 * The controller is generic — it passes whatever fields the service returns
 * straight to the template, so these tests verify that behaviour with Map data.
 */
@WebMvcTest(DataController.class)
class DataControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    HttpsDataService dataService;

    // -----------------------------------------------------------------------
    // UI (Thymeleaf) endpoint
    // -----------------------------------------------------------------------

    @Test
    void indexPage_rendersColumnsFromFirstRow() throws Exception {
        when(dataService.fetchAll()).thenReturn(sampleHandsets());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("rows", "columns", "totalCount"))
                .andExpect(model().attribute("totalCount", 2))
                // Column names from the JSON keys should appear as table headers
                .andExpect(content().string(containsString("tac")))
                .andExpect(content().string(containsString("marketingName")))
                .andExpect(content().string(containsString("manufacturer")))
                // Values should appear as cells
                .andExpect(content().string(containsString("Galaxy S24")))
                .andExpect(content().string(containsString("iPhone 15 Pro")));
    }

    @Test
    void indexPage_columnsAdaptWhenSchemaChanges() throws Exception {
        // Simulates a schema update: new field "bandSupport" added, "modelName" removed
        when(dataService.fetchAll()).thenReturn(updatedSchemaHandsets());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("bandSupport")))
                .andExpect(content().string(containsString("5G SA/NSA")))
                // Old field no longer present in API — should not appear in headers
                .andExpect(content().string(not(containsString("modelName"))));
    }

    @Test
    void indexPage_whenServiceReturnsEmpty_showsWarning() throws Exception {
        when(dataService.fetchAll()).thenReturn(List.of());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalCount", 0))
                .andExpect(content().string(containsString("No data returned")));
    }

    // -----------------------------------------------------------------------
    // REST /api/data
    // -----------------------------------------------------------------------

    @Test
    void getAll_returnsJsonList() throws Exception {
        when(dataService.fetchAll()).thenReturn(sampleHandsets());

        mockMvc.perform(get("/api/data").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].tac", is("35674108")))
                .andExpect(jsonPath("$[0].marketingName", is("Galaxy S24")))
                .andExpect(jsonPath("$[1].manufacturer", is("Apple")));
    }

    @Test
    void getAll_whenEmpty_returnsEmptyArray() throws Exception {
        when(dataService.fetchAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/data").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // -----------------------------------------------------------------------
    // REST /api/data/{id}
    // -----------------------------------------------------------------------

    @Test
    void getById_whenFound_returnsRecord() throws Exception {
        when(dataService.fetchById("35674108")).thenReturn(sampleHandsets().get(0));

        mockMvc.perform(get("/api/data/35674108").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tac", is("35674108")))
                .andExpect(jsonPath("$.marketingName", is("Galaxy S24")));
    }

    @Test
    void getById_whenNotFound_returns404() throws Exception {
        when(dataService.fetchById("00000000")).thenReturn(null);

        mockMvc.perform(get("/api/data/00000000").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // -----------------------------------------------------------------------
    // Helpers — use LinkedHashMap so column order matches JSON field order
    // -----------------------------------------------------------------------

    private List<Map<String, Object>> sampleHandsets() {
        Map<String, Object> s24 = new LinkedHashMap<>();
        s24.put("tac", "35674108");
        s24.put("marketingName", "Galaxy S24");
        s24.put("manufacturer", "Samsung");
        s24.put("modelName", "SM-S921");

        Map<String, Object> iphone = new LinkedHashMap<>();
        iphone.put("tac", "35899234");
        iphone.put("marketingName", "iPhone 15 Pro");
        iphone.put("manufacturer", "Apple");
        iphone.put("modelName", "A3293");

        return List.of(s24, iphone);
    }

    /** Same shape but with a new field and one removed — tests schema adaptability */
    private List<Map<String, Object>> updatedSchemaHandsets() {
        Map<String, Object> device = new LinkedHashMap<>();
        device.put("tac", "35674108");
        device.put("marketingName", "Galaxy S24");
        device.put("manufacturer", "Samsung");
        device.put("bandSupport", "5G SA/NSA");   // new field
        // "modelName" intentionally absent — schema changed
        return List.of(device);
    }
}
