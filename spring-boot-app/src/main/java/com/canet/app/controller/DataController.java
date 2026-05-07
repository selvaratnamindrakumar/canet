package com.canet.app.controller;

import com.canet.app.service.HttpsDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class DataController {

    private static final int MAX_TACS    = 20;
    private static final int COMPACT_COLS = 5;

    private final HttpsDataService dataService;

    public DataController(HttpsDataService dataService) {
        this.dataService = dataService;
    }

    @GetMapping("/")
    public String index(Model model,
                        @RequestParam(required = false) String tac) {

        List<Map<String, Object>> rows;
        List<String> notFound = List.of();
        List<String> searchedTacs = List.of();

        if (tac != null && !tac.isBlank()) {
            searchedTacs = Arrays.stream(tac.split(","))
                    .map(String::trim).filter(s -> !s.isBlank())
                    .limit(MAX_TACS)
                    .collect(Collectors.toList());

            HttpsDataService.TacSearchResult result = dataService.fetchByTacs(searchedTacs);
            rows     = result.results();
            notFound = result.notFound();
        } else {
            rows = dataService.fetchAll();
        }

        List<String> columns = rows.isEmpty()
                ? List.of()
                : new ArrayList<>(rows.get(0).keySet());

        model.addAttribute("rows",         rows);
        model.addAttribute("columns",      columns);
        model.addAttribute("compactCols",  COMPACT_COLS);
        model.addAttribute("totalCount",   rows.size());
        model.addAttribute("notFound",     notFound);
        model.addAttribute("searchedTacs", searchedTacs);
        model.addAttribute("tacQuery",     tac != null ? tac : "");
        return "index";
    }

    @GetMapping("/api/data")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getAllData() {
        return ResponseEntity.ok(dataService.fetchAll());
    }

    @GetMapping("/api/data/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getDataById(@PathVariable String id) {
        Map<String, Object> record = dataService.fetchById(id);
        return record != null ? ResponseEntity.ok(record) : ResponseEntity.notFound().build();
    }

    @GetMapping("/about")
    public String about() {
        return "about";
    }
}
