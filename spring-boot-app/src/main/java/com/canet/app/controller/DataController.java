package com.canet.app.controller;

import com.canet.app.model.DataItem;
import com.canet.app.service.HttpsDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
public class DataController {

    private final HttpsDataService dataService;

    public DataController(HttpsDataService dataService) {
        this.dataService = dataService;
    }

    /** Thymeleaf UI page — fetches all items and renders the table */
    @GetMapping("/")
    public String index(Model model) {
        List<DataItem> items = dataService.fetchAll();
        model.addAttribute("items", items);
        model.addAttribute("totalCount", items.size());
        model.addAttribute("completedCount", items.stream().filter(DataItem::isCompleted).count());
        return "index";
    }

    /** REST endpoint — returns all items as JSON */
    @GetMapping("/api/data")
    @ResponseBody
    public ResponseEntity<List<DataItem>> getAllData() {
        List<DataItem> items = dataService.fetchAll();
        return ResponseEntity.ok(items);
    }

    /** REST endpoint — returns a single item by ID */
    @GetMapping("/api/data/{id}")
    @ResponseBody
    public ResponseEntity<DataItem> getDataById(@PathVariable int id) {
        DataItem item = dataService.fetchById(id);
        return item != null ? ResponseEntity.ok(item) : ResponseEntity.notFound().build();
    }
}
