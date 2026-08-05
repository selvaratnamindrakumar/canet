package com.canet.mock.controller;

import com.canet.mock.data.HandsetRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LoadTestController.class)
@Import(HandsetRepository.class)
class LoadTestControllerTest {

    @Autowired
    MockMvc mockMvc;

    // =========================================================================
    // /loadtest/bulk
    // =========================================================================

    @Test
    void bulk_defaultCount_returns100Records() throws Exception {
        mockMvc.perform(get("/loadtest/bulk").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(100)));
    }

    @Test
    void bulk_count50_returns50Records() throws Exception {
        mockMvc.perform(get("/loadtest/bulk?count=50").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(50)));
    }

    @Test
    void bulk_countExceeds1000_cappedAt1000() throws Exception {
        mockMvc.perform(get("/loadtest/bulk?count=9999").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1000)));
    }

    @Test
    void bulk_allTacsAreEightDigits() throws Exception {
        mockMvc.perform(get("/loadtest/bulk?count=10").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].tac", everyItem(matchesRegex("\\d{8}"))));
    }

    @Test
    void bulk_tacsStartWith10() throws Exception {
        mockMvc.perform(get("/loadtest/bulk?count=5").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].tac", everyItem(startsWith("10"))));
    }

    @Test
    void bulk_nestedNetworkAndCapabilitiesPresent() throws Exception {
        mockMvc.perform(get("/loadtest/bulk?count=1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].network.generations", notNullValue()))
                .andExpect(jsonPath("$[0].network.type",        notNullValue()))
                .andExpect(jsonPath("$[0].capabilities.nfc",             instanceOf(Boolean.class)))
                .andExpect(jsonPath("$[0].capabilities.wirelessCharging", instanceOf(Boolean.class)));
    }

    @Test
    void bulk_recordsContainExpectedManufacturers() throws Exception {
        // 10 manufacturers cycling — 100 records covers all of them
        mockMvc.perform(get("/loadtest/bulk?count=100").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].manufacturer",
                        hasItems("Samsung", "Apple", "Google", "OnePlus", "Xiaomi")));
    }

    // =========================================================================
    // /loadtest/search
    // =========================================================================

    @Test
    void search_default20_returnsEnvelopeAllFound() throws Exception {
        mockMvc.perform(get("/loadtest/search").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested", is(20)))
                .andExpect(jsonPath("$.found",     is(20)))
                .andExpect(jsonPath("$.results",   hasSize(20)))
                .andExpect(jsonPath("$.notFound",  hasSize(0)));
    }

    @Test
    void search_count5_returnsCorrectCounts() throws Exception {
        mockMvc.perform(get("/loadtest/search?count=5").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested", is(5)))
                .andExpect(jsonPath("$.found",     is(5)))
                .andExpect(jsonPath("$.results",   hasSize(5)));
    }

    // =========================================================================
    // /loadtest/slow
    // =========================================================================

    @Test
    void slow_knownTac_returnsStaticRecord() throws Exception {
        mockMvc.perform(get("/loadtest/slow?tac=35674108&delayMs=0")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found",              is(1)))
                .andExpect(jsonPath("$.results[0].tac",     is("35674108")))
                .andExpect(jsonPath("$.results[0].manufacturer", is("Samsung")));
    }

    @Test
    void slow_unknownTac_returnsNotFound() throws Exception {
        mockMvc.perform(get("/loadtest/slow?tac=00000000&delayMs=0")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found",    is(0)))
                .andExpect(jsonPath("$.notFound", hasSize(1)));
    }

    // =========================================================================
    // /loadtest/tacs
    // =========================================================================

    @Test
    void tacs_newlineSeparated_byDefault() throws Exception {
        MvcResult result = mockMvc.perform(get("/loadtest/tacs?count=5"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        String[] lines = body.split("\n");
        assertEquals(5, lines.length);
    }

    @Test
    void tacs_commaSeparated_whenRequested() throws Exception {
        MvcResult result = mockMvc.perform(get("/loadtest/tacs?count=5&separator=comma"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        String[] parts = body.split(",");
        assertEquals(5, parts.length);
    }

    @Test
    void tacs_allEightDigits() throws Exception {
        MvcResult result = mockMvc.perform(get("/loadtest/tacs?count=10"))
                .andExpect(status().isOk())
                .andReturn();
        String[] lines = result.getResponse().getContentAsString().split("\n");
        for (String tac : lines) {
            assert tac.trim().matches("\\d{8}") : "Expected 8-digit TAC but got: " + tac;
        }
    }

    // =========================================================================
    // /loadtest/imeis
    // =========================================================================

    @Test
    void imeis_newlineSeparated_byDefault() throws Exception {
        MvcResult result = mockMvc.perform(get("/loadtest/imeis?count=5"))
                .andExpect(status().isOk())
                .andReturn();
        String[] lines = result.getResponse().getContentAsString().split("\n");
        assertEquals(5, lines.length);
    }

    @Test
    void imeis_are15Digits() throws Exception {
        MvcResult result = mockMvc.perform(get("/loadtest/imeis?count=5"))
                .andExpect(status().isOk())
                .andReturn();
        String[] lines = result.getResponse().getContentAsString().split("\n");
        for (String imei : lines) {
            assertEquals(15, imei.trim().length(), "IMEI should be 15 digits: " + imei);
            assert imei.trim().matches("\\d{15}") : "IMEI should be all digits: " + imei;
        }
    }

    @Test
    void imeis_tacPrefixMatchesGeneratedTac() throws Exception {
        MvcResult result = mockMvc.perform(get("/loadtest/imeis?count=1"))
                .andExpect(status().isOk())
                .andReturn();
        String imei = result.getResponse().getContentAsString().trim();
        // First 8 digits of the IMEI should be the generated TAC for index 0
        assertEquals("10000000", imei.substring(0, 8));
    }

    // =========================================================================
    // /loadtest/stats + /loadtest/reset
    // =========================================================================

    @Test
    void stats_returnsRequestCount() throws Exception {
        mockMvc.perform(get("/loadtest/stats").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestsServed", notNullValue()))
                .andExpect(jsonPath("$.uptimeSeconds",  notNullValue()));
    }

    @Test
    void reset_clearsCounters() throws Exception {
        // Warm up the counter
        mockMvc.perform(get("/loadtest/bulk?count=1")).andExpect(status().isOk());

        // Reset
        mockMvc.perform(post("/loadtest/reset"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("reset")));

        // Stats should now show 0
        mockMvc.perform(get("/loadtest/stats").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestsServed", is(0)));
    }
}
