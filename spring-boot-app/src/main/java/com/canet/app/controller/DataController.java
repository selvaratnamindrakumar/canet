package com.canet.app.controller;

import com.canet.app.service.HttpsDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class DataController {

    private final HttpsDataService dataService;

    public DataController(HttpsDataService dataService) {
        this.dataService = dataService;
    }

    /** Thymeleaf UI — columns and rows are derived from whatever the API returns */
    @GetMapping("/")
    public String index(Model model) {
        List<Map<String, Object>> rows = dataService.fetchAll();
        // Column names come from the first row's key set (LinkedHashMap preserves JSON order)
        List<String> columns = rows.isEmpty() ? List.of() : new ArrayList<>(rows.get(0).keySet());
        model.addAttribute("rows", rows);
        model.addAttribute("columns", columns);
        model.addAttribute("totalCount", rows.size());
        return "index";
    }

    /** REST endpoint — returns all records as JSON */
    @GetMapping("/api/data")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getAllData() {
        return ResponseEntity.ok(dataService.fetchAll());
    }

    /** REST endpoint — returns a single record by ID (string to support TAC codes etc.) */
    @GetMapping("/api/data/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getDataById(@PathVariable String id) {
        Map<String, Object> record = dataService.fetchById(id);
        return record != null ? ResponseEntity.ok(record) : ResponseEntity.notFound().build();
    }

    /** About/comparison page */
    @GetMapping("/about")
    public String about() {
        return "about";
    }
}
