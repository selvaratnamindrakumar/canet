package com.canet.app.controller;

import com.canet.app.config.CaNetProperties;
import com.canet.app.service.HttpsDataService;
import com.canet.app.util.JsonFlattener;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class DataController {

    private static final int DEFAULT_MAX_ENTRIES = 20;

    private final HttpsDataService dataService;
    private final CaNetProperties caNetProperties;

    public DataController(HttpsDataService dataService, CaNetProperties caNetProperties) {
        this.dataService = dataService;
        this.caNetProperties = caNetProperties;
    }

    // ─── Internal result container ────────────────────────────────────────────
    private record SearchResult(
            List<Map<String, Object>> rows,
            List<String> notFound,
            List<String> searchedEntries,
            CaNetProperties.EndpointMapping mapping
    ) {}

    // ─── Core search logic ────────────────────────────────────────────────────
    private SearchResult doSearch(String mode, String entries) {
        CaNetProperties.EndpointMapping mapping = caNetProperties.findByType(mode);
        if (mapping == null) mapping = caNetProperties.findDefault();
        String url = mapping != null ? mapping.getUrl() : "";

        List<Map<String, Object>> rows    = List.of();
        List<String>               notFound = List.of();
        List<String>               searched = List.of();

        int maxEnt = (mapping != null && mapping.getMaxEntries() > 0)
                ? mapping.getMaxEntries() : DEFAULT_MAX_ENTRIES;

        if (entries != null && !entries.isBlank()) {
            searched = Arrays.stream(entries.split(","))
                    .map(String::trim).filter(s -> !s.isBlank())
                    .limit(maxEnt)
                    .collect(Collectors.toList());

            // Build lookup-key → original-input map for result annotation.
            // Cell mode:    IMEI → TAC (first 8 chars); inputValue = original IMEI
            // Handset mode: TAC  → TAC;                 inputValue = searched TAC
            Map<String, String> tacToInput = new LinkedHashMap<>();
            if ("cell".equals(mode)) {
                for (String entry : searched) {
                    String tac = entry.length() >= 8 ? entry.substring(0, 8) : entry;
                    tacToInput.putIfAbsent(tac, entry);
                }
            } else {
                for (String entry : searched) {
                    tacToInput.putIfAbsent(entry, entry);
                }
            }

            HttpsDataService.TacSearchResult r =
                    dataService.fetchByTacs(url, new ArrayList<>(tacToInput.keySet()));
            rows = r.results().stream()
                    .map(row -> annotateInput(row, tacToInput))
                    .collect(Collectors.toList());
            notFound = r.notFound().stream()
                    .map(tac -> tacToInput.getOrDefault(tac, tac))
                    .collect(Collectors.toList());

            // Flatten nested JSON objects if configured
            if (mapping != null && mapping.isFlatten()) {
                final String sep = mapping.getFlattenSeparator();
                rows = rows.stream()
                        .map(row -> JsonFlattener.flatten(row, sep))
                        .collect(Collectors.toList());
            }
        }

        return new SearchResult(rows, notFound, searched, mapping);
    }

    private Map<String, Object> annotateInput(Map<String, Object> row, Map<String, String> tacToInput) {
        Map<String, Object> m = new LinkedHashMap<>(row);
        m.put("inputValue", tacToInput.getOrDefault(String.valueOf(m.getOrDefault("tac", "")), ""));
        return m;
    }

    private List<String> resolveCompact(CaNetProperties.EndpointMapping mapping, List<String> available) {
        if (mapping == null || mapping.getCompactFields().isEmpty()) return available;
        return mapping.getCompactFields().stream()
                .filter(available::contains).collect(Collectors.toList());
    }

    private List<String> resolveDetail(CaNetProperties.EndpointMapping mapping,
                                        List<String> available, List<String> compact) {
        List<String> cfg = mapping != null ? mapping.getDetailFields() : List.of();
        if (cfg.isEmpty() || (cfg.size() == 1 && "*".equals(cfg.get(0)))) {
            return available.stream().filter(f -> !compact.contains(f)).collect(Collectors.toList());
        }
        return cfg.stream().filter(available::contains).collect(Collectors.toList());
    }

    // Serialised to JSON for the client-side mode-switcher and validator
    private Map<String, Map<String, Object>> buildEndpointConfigs() {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        for (CaNetProperties.EndpointMapping ep : caNetProperties.getEndpoints()) {
            Map<String, Object> cfg = new LinkedHashMap<>();
            cfg.put("delay",       ep.getSubmitDelaySeconds());
            cfg.put("pattern",     ep.getValidationPattern());
            cfg.put("message",     ep.getValidationMessage());
            cfg.put("placeholder", "cell".equals(ep.getType())
                    ? "Enter one IMEI (15 digits) per line" : "Enter one TAC (8 digits) per line");
            cfg.put("maxEntries", ep.getMaxEntries() > 0 ? ep.getMaxEntries() : DEFAULT_MAX_ENTRIES);
            map.put(ep.getType(), cfg);
        }
        return map;
    }

    // ─── Main UI handler ──────────────────────────────────────────────────────
    @GetMapping("/")
    public String index(Model model,
                        @RequestParam(defaultValue = "handset") String mode,
                        @RequestParam(required = false) String entries) {

        SearchResult sr     = doSearch(mode, entries);
        CaNetProperties.EndpointMapping mapping = sr.mapping();

        List<String> allColumns    = sr.rows().isEmpty() ? List.of() : new ArrayList<>(sr.rows().get(0).keySet());
        List<String> compactFields = resolveCompact(mapping, allColumns);
        List<String> detailFields  = resolveDetail(mapping, allColumns, compactFields);
        Map<String, String> labels = mapping != null ? mapping.getLabels() : Map.of();

        model.addAttribute("mode",            mode);
        model.addAttribute("rows",            sr.rows());
        model.addAttribute("allColumns",      allColumns);
        model.addAttribute("compactFields",   compactFields);
        model.addAttribute("detailFields",    detailFields);
        model.addAttribute("labels",          labels);
        model.addAttribute("totalCount",      sr.rows().size());
        model.addAttribute("notFound",        sr.notFound());
        model.addAttribute("searchedEntries", sr.searchedEntries());
        model.addAttribute("endpointConfigs", buildEndpointConfigs());
        return "index";
    }

    // ─── CSV export ───────────────────────────────────────────────────────────
    @GetMapping("/api/data/export.csv")
    public void exportCsv(HttpServletResponse response,
                          @RequestParam(defaultValue = "handset") String mode,
                          @RequestParam(required = false) String entries) throws IOException {

        SearchResult sr    = doSearch(mode, entries);
        CaNetProperties.EndpointMapping mapping = sr.mapping();
        Map<String, String> labels = mapping != null ? mapping.getLabels() : Map.of();

        List<String> cols = sr.rows().isEmpty()
                ? List.of()
                : new ArrayList<>(sr.rows().get(0).keySet());

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=export.csv");

        PrintWriter w = response.getWriter();
        w.println(cols.stream().map(c -> q(labels.getOrDefault(c, c))).collect(Collectors.joining(",")));
        for (Map<String, Object> row : sr.rows()) {
            w.println(cols.stream()
                    .map(c -> q(Objects.toString(row.getOrDefault(c, ""), "")))
                    .collect(Collectors.joining(",")));
        }
    }

    private String q(String s) { return '"' + s.replace("\"", "\"\"") + '"'; }

    // ─── REST JSON endpoints ──────────────────────────────────────────────────
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
