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
    // /handsetdetails  — primary endpoint
    // =========================================================================

    @Test
    void handsetdetails_list_returns10Records() throws Exception {
        mockMvc.perform(get("/handsetdetails").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)));
    }

    @Test
    void handsetdetails_list_responseHasAllExpectedFields() throws Exception {
        mockMvc.perform(get("/handsetdetails").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tac").isNotEmpty())
                .andExpect(jsonPath("$[0].marketingName").isNotEmpty())
                .andExpect(jsonPath("$[0].manufacturer").isNotEmpty())
                .andExpect(jsonPath("$[0].modelName").isNotEmpty())
                .andExpect(jsonPath("$[0].operatingSystem").isNotEmpty())
                .andExpect(jsonPath("$[0].osVersion").isNotEmpty())
                .andExpect(jsonPath("$[0].networkGenerations").isNotEmpty())
                .andExpect(jsonPath("$[0].displaySizeInches").isNumber())
                .andExpect(jsonPath("$[0].releaseYear").isNumber())
                .andExpect(jsonPath("$[0].nfcSupported").isBoolean())
                .andExpect(jsonPath("$[0].wirelessChargingSupported").isBoolean());
    }

    @Test
    void handsetdetails_list_firstRecordIsSamsungS24Ultra() throws Exception {
        mockMvc.perform(get("/handsetdetails").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tac",                      is("35674108")))
                .andExpect(jsonPath("$[0].marketingName",             is("Samsung Galaxy S24 Ultra")))
                .andExpect(jsonPath("$[0].manufacturer",              is("Samsung")))
                .andExpect(jsonPath("$[0].modelName",                 is("SM-S928B")))
                .andExpect(jsonPath("$[0].operatingSystem",           is("Android")))
                .andExpect(jsonPath("$[0].osVersion",                 is("14")))
                .andExpect(jsonPath("$[0].networkGenerations",        is("2G/3G/4G/5G")))
                .andExpect(jsonPath("$[0].displaySizeInches",         is(6.8)))
                .andExpect(jsonPath("$[0].releaseYear",               is(2024)))
                .andExpect(jsonPath("$[0].nfcSupported",              is(true)))
                .andExpect(jsonPath("$[0].wirelessChargingSupported", is(true)));
    }

    @Test
    void handsetdetails_filterBySamsung_returnsOnlySamsungDevices() throws Exception {
        mockMvc.perform(get("/handsetdetails?manufacturer=Samsung").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].manufacturer", everyItem(is("Samsung"))))
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void handsetdetails_filterByApple_returnsTwoAppleDevices() throws Exception {
        mockMvc.perform(get("/handsetdetails?manufacturer=Apple").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].manufacturer", everyItem(is("Apple"))))
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void handsetdetails_filterByGoogle_returnsTwoGoogleDevices() throws Exception {
        mockMvc.perform(get("/handsetdetails?manufacturer=Google").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].manufacturer", everyItem(is("Google"))))
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void handsetdetails_filterIsCaseInsensitive() throws Exception {
        mockMvc.perform(get("/handsetdetails?manufacturer=apple").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].manufacturer", everyItem(is("Apple"))));
    }

    @Test
    void handsetdetails_filterByUnknownManufacturer_returnsEmptyArray() throws Exception {
        mockMvc.perform(get("/handsetdetails?manufacturer=Nokia").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void handsetdetails_byTac_iPhoneRecord_returnsCorrectData() throws Exception {
        mockMvc.perform(get("/handsetdetails/35282402").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tac",           is("35282402")))
                .andExpect(jsonPath("$.marketingName", is("Apple iPhone 15 Pro Max")))
                .andExpect(jsonPath("$.manufacturer",  is("Apple")))
                .andExpect(jsonPath("$.modelName",     is("A3105")))
                .andExpect(jsonPath("$.osVersion",     is("17")))
                .andExpect(jsonPath("$.displaySizeInches", is(6.7)));
    }

    @Test
    void handsetdetails_byTac_pixelRecord_returnsCorrectData() throws Exception {
        mockMvc.perform(get("/handsetdetails/35428109").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marketingName", is("Google Pixel 8 Pro")))
                .andExpect(jsonPath("$.manufacturer",  is("Google")));
    }

    @Test
    void handsetdetails_byTac_unknownTac_returns404() throws Exception {
        mockMvc.perform(get("/handsetdetails/00000000").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void handsetdetails_list_containsBothAndroidAndIos() throws Exception {
        mockMvc.perform(get("/handsetdetails").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].operatingSystem", hasItems("Android", "iOS")));
    }

    @Test
    void handsetdetails_list_allTacsAreEightDigits() throws Exception {
        mockMvc.perform(get("/handsetdetails").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].tac", everyItem(matchesRegex("\\d{8}"))));
    }

    // =========================================================================
    // /handsets — alternate path, same data
    // =========================================================================

    @Test
    void handsets_list_returnsSameDataAsHandsetdetails() throws Exception {
        mockMvc.perform(get("/handsets").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)));
    }

    @Test
    void handsets_byTac_returnsSameDataAsHandsetdetails() throws Exception {
        mockMvc.perform(get("/handsets/35674108").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marketingName", is("Samsung Galaxy S24 Ultra")));
    }
}
