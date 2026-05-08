package com.canet.app.controller;

import com.canet.app.config.CaNetProperties;
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

    private static final int MAX_TACS = 20;

    private final HttpsDataService dataService;
    private final CaNetProperties caNetProperties;

    public DataController(HttpsDataService dataService, CaNetProperties caNetProperties) {
        this.dataService = dataService;
        this.caNetProperties = caNetProperties;
    }

    @GetMapping("/")
    public String index(Model model,
                        @RequestParam(required = false) String tac) {

        CaNetProperties.EndpointMapping mapping = caNetProperties.findDefault();
        String url = mapping != null ? mapping.getUrl() : "";

        List<Map<String, Object>> rows;
        List<String> notFound    = List.of();
        List<String> searchedTacs = List.of();

        if (tac != null && !tac.isBlank()) {
            searchedTacs = Arrays.stream(tac.split(","))
                    .map(String::trim).filter(s -> !s.isBlank())
                    .limit(MAX_TACS)
                    .collect(Collectors.toList());

            HttpsDataService.TacSearchResult result = dataService.fetchByTacs(url, searchedTacs);
            rows     = result.results();
            notFound = result.notFound();
        } else {
            rows = dataService.fetchAll(url);
        }

        List<String> allColumns    = rows.isEmpty() ? List.of() : new ArrayList<>(rows.get(0).keySet());
        List<String> compactFields = resolveCompact(mapping, allColumns);
        List<String> detailFields  = resolveDetail(mapping, allColumns, compactFields);
        Map<String, String> labels = mapping != null ? mapping.getLabels() : Map.of();

        model.addAttribute("rows",          rows);
        model.addAttribute("allColumns",    allColumns);
        model.addAttribute("compactFields", compactFields);
        model.addAttribute("detailFields",  detailFields);
        model.addAttribute("labels",        labels);
        model.addAttribute("totalCount",    rows.size());
        model.addAttribute("notFound",      notFound);
        model.addAttribute("searchedTacs",  searchedTacs);
        model.addAttribute("tacQuery",      tac != null ? tac : "");
        return "index";
    }

    private List<String> resolveCompact(CaNetProperties.EndpointMapping mapping, List<String> available) {
        if (mapping == null || mapping.getCompactFields().isEmpty()) return available;
        return mapping.getCompactFields().stream()
                .filter(available::contains)
                .collect(Collectors.toList());
    }

    private List<String> resolveDetail(CaNetProperties.EndpointMapping mapping,
                                        List<String> available,
                                        List<String> compact) {
        List<String> configured = mapping != null ? mapping.getDetailFields() : List.of();
        if (configured.isEmpty() || (configured.size() == 1 && "*".equals(configured.get(0)))) {
            return available.stream().filter(f -> !compact.contains(f)).collect(Collectors.toList());
        }
        return configured.stream().filter(available::contains).collect(Collectors.toList());
    }

    @GetMapping("/api/data")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getAllData() {
        CaNetProperties.EndpointMapping mapping = caNetProperties.findDefault();
        String url = mapping != null ? mapping.getUrl() : "";
        return ResponseEntity.ok(dataService.fetchAll(url));
    }

    @GetMapping("/api/data/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getDataById(@PathVariable String id) {
        CaNetProperties.EndpointMapping mapping = caNetProperties.findDefault();
        String url = mapping != null ? mapping.getUrl() : "";
        Map<String, Object> record = dataService.fetchById(url, id);
        return record != null ? ResponseEntity.ok(record) : ResponseEntity.notFound().build();
    }

    @GetMapping("/about")
    public String about() {
        return "about";
    }
}
