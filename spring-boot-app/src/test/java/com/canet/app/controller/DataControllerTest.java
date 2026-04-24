package com.canet.app.controller;

import com.canet.app.model.DataItem;
import com.canet.app.service.HttpsDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test for DataController — uses MockMvc and stubs HttpsDataService.
 * No real HTTP calls are made; the service layer is fully mocked.
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
    void indexPage_rendersTableWithItems() throws Exception {
        when(dataService.fetchAll()).thenReturn(sampleItems());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("items", "totalCount", "completedCount"))
                .andExpect(model().attribute("totalCount", 2))
                .andExpect(model().attribute("completedCount", 1L))
                .andExpect(content().string(containsString("Buy milk")))
                .andExpect(content().string(containsString("Read a book")));
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
        when(dataService.fetchAll()).thenReturn(sampleItems());

        mockMvc.perform(get("/api/data").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title", is("Buy milk")))
                .andExpect(jsonPath("$[1].completed", is(false)));
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
    void getById_whenFound_returnsItem() throws Exception {
        when(dataService.fetchById(1)).thenReturn(sampleItems().get(0));

        mockMvc.perform(get("/api/data/1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.title", is("Buy milk")))
                .andExpect(jsonPath("$.completed", is(true)));
    }

    @Test
    void getById_whenNotFound_returns404() throws Exception {
        when(dataService.fetchById(999)).thenReturn(null);

        mockMvc.perform(get("/api/data/999").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private List<DataItem> sampleItems() {
        return List.of(
                new DataItem(1, 1, "Buy milk", true),
                new DataItem(1, 2, "Read a book", false)
        );
    }
}
