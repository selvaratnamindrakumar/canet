package com.canet.mock.controller;

import com.canet.mock.data.HandsetRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HandsetController.class)
@Import(HandsetRepository.class)
class HandsetControllerTest {

    @Autowired
    MockMvc mockMvc;

    // =========================================================================
    // GET /handsetdetails — full list
    // =========================================================================

    @Test
    void list_returns10Records() throws Exception {
        mockMvc.perform(get("/handsetdetails").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)));
    }

    @Test
    void list_firstRecordIsSamsungS24Ultra() throws Exception {
        mockMvc.perform(get("/handsetdetails").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tac",           is("35674108")))
                .andExpect(jsonPath("$[0].marketingName", is("Samsung Galaxy S24 Ultra")))
                .andExpect(jsonPath("$[0].manufacturer",  is("Samsung")))
                .andExpect(jsonPath("$[0].modelName",     is("SM-S928B")))
                .andExpect(jsonPath("$[0].osVersion",     is("14")))
                .andExpect(jsonPath("$[0].displaySizeInches",         is(6.8)))
                .andExpect(jsonPath("$[0].nfcSupported",              is(true)))
                .andExpect(jsonPath("$[0].wirelessChargingSupported", is(true)));
    }

    @Test
    void list_allTacsAreEightDigits() throws Exception {
        mockMvc.perform(get("/handsetdetails").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].tac", everyItem(matchesRegex("\\d{8}"))));
    }

    @Test
    void list_containsBothAndroidAndIos() throws Exception {
        mockMvc.perform(get("/handsetdetails").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].operatingSystem", hasItems("Android", "iOS")));
    }

    // =========================================================================
    // GET /handsetdetails?manufacturer=X — filter
    // =========================================================================

    @Test
    void filter_bySamsung_returnsTwoRecords() throws Exception {
        mockMvc.perform(get("/handsetdetails?manufacturer=Samsung").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].manufacturer", everyItem(is("Samsung"))));
    }

    @Test
    void filter_byApple_returnsTwoRecords() throws Exception {
        mockMvc.perform(get("/handsetdetails?manufacturer=Apple").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].manufacturer", everyItem(is("Apple"))));
    }

    @Test
    void filter_caseInsensitive() throws Exception {
        mockMvc.perform(get("/handsetdetails?manufacturer=google").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].manufacturer", everyItem(is("Google"))));
    }

    @Test
    void filter_unknownManufacturer_returnsEmptyArray() throws Exception {
        mockMvc.perform(get("/handsetdetails?manufacturer=Nokia").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // =========================================================================
    // GET /handsetdetails/{tac} — single record
    // =========================================================================

    @Test
    void single_knownTac_returnsRecord() throws Exception {
        mockMvc.perform(get("/handsetdetails/35282402").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tac",           is("35282402")))
                .andExpect(jsonPath("$.marketingName", is("Apple iPhone 15 Pro Max")))
                .andExpect(jsonPath("$.manufacturer",  is("Apple")))
                .andExpect(jsonPath("$.modelName",     is("A3105")));
    }

    @Test
    void single_unknownTac_returns404() throws Exception {
        mockMvc.perform(get("/handsetdetails/00000000").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // GET /handsetdetails?tac=T1,T2,... — multi-TAC lookup (up to 20)
    // =========================================================================

    @Test
    void multiTac_twoKnownTacs_returnsBothInResults() throws Exception {
        mockMvc.perform(get("/handsetdetails?tac=35674108,35282402").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested", is(2)))
                .andExpect(jsonPath("$.found",     is(2)))
                .andExpect(jsonPath("$.results",   hasSize(2)))
                .andExpect(jsonPath("$.notFound",  hasSize(0)))
                .andExpect(jsonPath("$.results[*].tac",
                        containsInAnyOrder("35674108", "35282402")));
    }

    @Test
    void multiTac_mixedFoundAndNotFound_partitionsCorrectly() throws Exception {
        mockMvc.perform(get("/handsetdetails?tac=35674108,22222222,11111111,35282402")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested", is(4)))
                .andExpect(jsonPath("$.found",     is(2)))
                .andExpect(jsonPath("$.results",   hasSize(2)))
                .andExpect(jsonPath("$.notFound",  hasSize(2)))
                .andExpect(jsonPath("$.notFound",  containsInAnyOrder("22222222", "11111111")));
    }

    @Test
    void multiTac_allUnknown_returnsEmptyResults() throws Exception {
        mockMvc.perform(get("/handsetdetails?tac=11111111,22222222,33333333,44444444")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested", is(4)))
                .andExpect(jsonPath("$.found",     is(0)))
                .andExpect(jsonPath("$.results",   hasSize(0)))
                .andExpect(jsonPath("$.notFound",  hasSize(4)));
    }

    @Test
    void multiTac_singleTac_wrapsInSearchResponse() throws Exception {
        mockMvc.perform(get("/handsetdetails?tac=35674108").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested", is(1)))
                .andExpect(jsonPath("$.found",     is(1)))
                .andExpect(jsonPath("$.results[0].marketingName", is("Samsung Galaxy S24 Ultra")));
    }

    @Test
    void multiTac_up20Tacs_honoured() throws Exception {
        // 10 real + 10 fake = 20 total, all within the limit
        String tacs = "35674108,35282402,35428109,86345601,35567812,"
                    + "86456723,35678934,35789045,35890156,35012367,"
                    + "11111111,22222222,33333333,44444444,55555555,"
                    + "66666666,77777777,88888888,99999999,10101010";

        mockMvc.perform(get("/handsetdetails?tac=" + tacs).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested", is(20)))
                .andExpect(jsonPath("$.found",     is(10)))
                .andExpect(jsonPath("$.notFound",  hasSize(10)));
    }

    @Test
    void multiTac_exceedingLimitIsTruncatedTo20() throws Exception {
        // 21 TACs submitted — limit silently caps at 20
        String tacs = "35674108,35282402,35428109,86345601,35567812,"
                    + "86456723,35678934,35789045,35890156,35012367,"
                    + "11111111,22222222,33333333,44444444,55555555,"
                    + "66666666,77777777,88888888,99999999,10101010,"
                    + "12121212";  // 21st — should be dropped

        mockMvc.perform(get("/handsetdetails?tac=" + tacs).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested", is(20)));  // capped at 20
    }

    // =========================================================================
    // GET /rs/v1/handsetbyIMEIList?imeis=... — IMEI list
    // =========================================================================

    @Test
    void imeiList_validImeisMatchByTac() throws Exception {
        // IMEI 358004117700001 → TAC 35800411 (not in our sample data)
        // IMEI 352824021234567 → TAC 35282402 (Apple iPhone 15 Pro Max)
        mockMvc.perform(get("/rs/v1/handsetbyIMEIList?imeis=358004117700001,352824021234567")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested", is(2)))
                .andExpect(jsonPath("$.found",     is(1)))
                .andExpect(jsonPath("$.results[0].manufacturer", is("Apple")));
    }

    @Test
    void imeiList_shortCodesUsedAsTacs() throws Exception {
        // Short codes (< 15 digits) are used as-is for lookup
        mockMvc.perform(get("/rs/v1/handsetbyIMEIList?imeis=35674108,35282402")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found", is(2)));
    }

    @Test
    void imeiList_duplicateImeisDeduplicatedBeforeLookup() throws Exception {
        mockMvc.perform(get("/rs/v1/handsetbyIMEIList?imeis=35674108,35674108,35674108")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested", is(1)));  // de-duped to 1
    }

    @Test
    void imeiList_noMatch_returnsEmptyResults() throws Exception {
        mockMvc.perform(get("/rs/v1/handsetbyIMEIList?imeis=00000000,11111111")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found",    is(0)))
                .andExpect(jsonPath("$.notFound", hasSize(2)));
    }

    // =========================================================================
    // /handsets alternate path parity
    // =========================================================================

    @Test
    void handsets_multiTac_sameResponseAsHandsetdetails() throws Exception {
        mockMvc.perform(get("/handsets?tac=35674108,35282402").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found", is(2)));
    }
}
