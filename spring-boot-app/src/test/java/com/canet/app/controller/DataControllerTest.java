package com.canet.app.controller;

import com.canet.app.config.CaNetProperties;
import com.canet.app.service.HttpsDataService;
import com.canet.app.service.HttpsDataService.TacSearchResult;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DataController.class)
class DataControllerTest {

    private static final String TEST_URL = "https://test.example.com";
    private static final List<String> COMPACT_FIELDS =
            List.of("inputValue", "tac", "marketingName", "manufacturer", "modelName", "operatingSystem");
    private static final List<String> CELL_COMPACT =
            List.of("inputValue", "tac", "marketingName", "manufacturer", "modelName");

    @Autowired MockMvc mockMvc;
    @MockBean  HttpsDataService dataService;
    @MockBean  CaNetProperties  caNetProperties;

    @BeforeEach
    void setupMappings() {
        CaNetProperties.EndpointMapping handset = new CaNetProperties.EndpointMapping();
        handset.setType("handset");
        handset.setUrl(TEST_URL);
        handset.setDefaultEndpoint(true);
        handset.setCompactFields(COMPACT_FIELDS);
        handset.setDetailFields(List.of("*"));
        handset.setLabels(Map.of());
        handset.setValidationPattern("^\\d{8}$");
        handset.setValidationMessage("TAC must be 8 digits");
        handset.setSubmitDelaySeconds(0);

        CaNetProperties.EndpointMapping cell = new CaNetProperties.EndpointMapping();
        cell.setType("cell");
        cell.setUrl(TEST_URL);
        cell.setCompactFields(CELL_COMPACT);
        cell.setDetailFields(List.of("*"));
        cell.setLabels(Map.of());
        cell.setValidationPattern("^\\d{15}$");
        cell.setValidationMessage("IMEI must be 15 digits");
        cell.setSubmitDelaySeconds(0);

        when(caNetProperties.getEndpoints()).thenReturn(List.of(handset, cell));
        when(caNetProperties.findDefault()).thenReturn(handset);
        when(caNetProperties.findByType("handset")).thenReturn(handset);
        when(caNetProperties.findByType("cell")).thenReturn(cell);
    }

    // =========================================================================
    // GET / — no entries → empty search form
    // =========================================================================

    @Test
    void index_noEntries_showsEmptyForm() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("totalCount", 0))
                .andExpect(model().attribute("mode",       "handset"))
                .andExpect(content().string(containsString("Enter TAC codes")));
    }

    @Test
    void index_noEntries_compactFieldsInModel() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(model().attribute("compactFields", empty()));
    }

    // =========================================================================
    // GET /?mode=handset&entries=… — handset TAC search
    // =========================================================================

    @Test
    void index_handset_entriesProvided_rendersResults() throws Exception {
        when(dataService.fetchByTacs(TEST_URL, List.of("35674108", "35282402")))
                .thenReturn(new TacSearchResult(sampleRows(), List.of()));

        mockMvc.perform(get("/?mode=handset&entries=35674108,35282402"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("totalCount",   2))
                .andExpect(model().attribute("mode",         "handset"))
                .andExpect(content().string(containsString("Galaxy S24")))
                .andExpect(content().string(containsString("iPhone 15 Pro")));
    }

    @Test
    void index_handset_compactFieldsFromConfig() throws Exception {
        when(dataService.fetchByTacs(eq(TEST_URL), any()))
                .thenReturn(new TacSearchResult(sampleRows(), List.of()));

        mockMvc.perform(get("/?mode=handset&entries=35674108"))
                .andExpect(model().attribute("compactFields", hasSize(6)));
    }

    @Test
    void index_handset_rowAnnotatedWithInputValueEqualToSearchedTac() throws Exception {
        when(dataService.fetchByTacs(TEST_URL, List.of("35674108")))
                .thenReturn(new TacSearchResult(List.of(sampleRows().get(0)), List.of()));

        // In handset mode the searched TAC IS the inputValue — both appear in compact columns
        mockMvc.perform(get("/?mode=handset&entries=35674108"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalCount", 1))
                // inputValue column (labelled "Searched Value") shows the searched TAC
                .andExpect(content().string(containsString("35674108")));
    }

    @Test
    void index_handset_notFoundShownInAlert() throws Exception {
        when(dataService.fetchByTacs(eq(TEST_URL), any()))
                .thenReturn(new TacSearchResult(
                        List.of(sampleRows().get(0)), List.of("22222222", "11111111")));

        mockMvc.perform(get("/?mode=handset&entries=35674108,22222222,11111111"))
                .andExpect(model().attribute("totalCount", 1))
                .andExpect(content().string(containsString("22222222")))
                .andExpect(content().string(containsString("11111111")));
    }

    @Test
    void index_handset_allNotFound_showsZeroResults() throws Exception {
        when(dataService.fetchByTacs(eq(TEST_URL), any()))
                .thenReturn(new TacSearchResult(List.of(), List.of("00000000")));

        mockMvc.perform(get("/?mode=handset&entries=00000000"))
                .andExpect(model().attribute("totalCount", 0))
                .andExpect(content().string(containsString("00000000")));
    }

    @Test
    void index_handset_emptyApiResponse_showsNoRecordsWarning() throws Exception {
        when(dataService.fetchByTacs(eq(TEST_URL), any()))
                .thenReturn(new TacSearchResult(List.of(), List.of("99999999")));

        mockMvc.perform(get("/?mode=handset&entries=99999999"))
                .andExpect(model().attribute("totalCount", 0));
    }

    // =========================================================================
    // GET /?mode=cell&entries=… — cell IMEI search
    // =========================================================================

    @Test
    void index_cell_extractsTacFromImeiAndAnnotatesRow() throws Exception {
        // IMEI 353456789012345 → TAC 35345678
        when(dataService.fetchByTacs(TEST_URL, List.of("35345678")))
                .thenReturn(new TacSearchResult(List.of(sampleRows().get(0)), List.of()));

        mockMvc.perform(get("/?mode=cell&entries=353456789012345"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalCount", 1))
                .andExpect(model().attribute("mode", "cell"));
    }

    @Test
    void index_cell_notFoundMappedBackToInputImei() throws Exception {
        // IMEI 353456789012345 → TAC 35345678 → not found → should show original IMEI
        when(dataService.fetchByTacs(TEST_URL, List.of("35345678")))
                .thenReturn(new TacSearchResult(List.of(), List.of("35345678")));

        mockMvc.perform(get("/?mode=cell&entries=353456789012345"))
                .andExpect(content().string(containsString("353456789012345")));
    }

    // =========================================================================
    // GET /api/data — REST JSON
    // =========================================================================

    @Test
    void apiData_returnsJsonArray() throws Exception {
        when(dataService.fetchAll(TEST_URL)).thenReturn(sampleRows());

        mockMvc.perform(get("/api/data").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].tac",          is("35674108")))
                .andExpect(jsonPath("$[1].manufacturer", is("Apple")));
    }

    // =========================================================================
    // GET /api/data/{id}
    // =========================================================================

    @Test
    void apiDataById_found_returnsRecord() throws Exception {
        when(dataService.fetchById(TEST_URL, "35674108")).thenReturn(sampleRows().get(0));

        mockMvc.perform(get("/api/data/35674108").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tac", is("35674108")));
    }

    @Test
    void apiDataById_notFound_returns404() throws Exception {
        when(dataService.fetchById(TEST_URL, "00000000")).thenReturn(null);

        mockMvc.perform(get("/api/data/00000000").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private List<Map<String, Object>> sampleRows() {
        Map<String, Object> s24 = new LinkedHashMap<>();
        s24.put("tac",               "35674108");
        s24.put("marketingName",     "Galaxy S24 Ultra");
        s24.put("manufacturer",      "Samsung");
        s24.put("modelName",         "SM-S928B");
        s24.put("operatingSystem",   "Android");
        s24.put("osVersion",         "14");
        s24.put("networkGenerations","2G/3G/4G/5G");

        Map<String, Object> iphone = new LinkedHashMap<>();
        iphone.put("tac",               "35282402");
        iphone.put("marketingName",     "iPhone 15 Pro");
        iphone.put("manufacturer",      "Apple");
        iphone.put("modelName",         "A3105");
        iphone.put("operatingSystem",   "iOS");
        iphone.put("osVersion",         "17");
        iphone.put("networkGenerations","2G/3G/4G/5G");

        return List.of(s24, iphone);
    }
}
