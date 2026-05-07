package com.canet.mock.controller;

import com.canet.mock.data.HandsetRepository;
import com.canet.mock.model.HandsetRecord;
import com.canet.mock.model.TacSearchResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Simulates a real handset-details API.
 *
 * ── Endpoints ────────────────────────────────────────────────────────────────
 *
 * List / filter
 *   GET /handsetdetails                          all 10 sample records
 *   GET /handsetdetails?manufacturer=Samsung     case-insensitive filter
 *
 * Single TAC lookup
 *   GET /handsetdetails/{tac}                    one record; 404 if unknown
 *
 * Multi-TAC lookup (up to 20)
 *   GET /handsetdetails?tac=35674108,35282402    returns TacSearchResponse
 *                                                (found list + notFound list)
 *
 * IMEI list (TAC = first 8 digits of each IMEI, up to 20)
 *   GET /rs/v1/handsetbyIMEIList?imeis=358004117700001,35282402
 *
 * All paths also available under /handsets/* for backward compatibility.
 * ─────────────────────────────────────────────────────────────────────────────
 */
@RestController
public class HandsetController {

    private static final int MAX_TACS = 20;

    private final HandsetRepository repository;

    public HandsetController(HandsetRepository repository) {
        this.repository = repository;
    }

    // ── /handsetdetails ───────────────────────────────────────────────────────

    @GetMapping("/handsetdetails")
    public ResponseEntity<?> handsetdetails(
            @RequestParam(required = false) String tac,
            @RequestParam(required = false) String manufacturer) {

        if (tac != null && !tac.isBlank()) {
            return ResponseEntity.ok(repository.findByTacs(parseTacs(tac)));
        }
        return buildList(manufacturer);
    }

    @GetMapping("/handsetdetails/{tac}")
    public ResponseEntity<HandsetRecord> handsetdetailByTac(@PathVariable String tac) {
        return singleByTac(tac);
    }

    // ── /handsets (alternate path) ────────────────────────────────────────────

    @GetMapping("/handsets")
    public ResponseEntity<?> handsets(
            @RequestParam(required = false) String tac,
            @RequestParam(required = false) String manufacturer) {

        if (tac != null && !tac.isBlank()) {
            return ResponseEntity.ok(repository.findByTacs(parseTacs(tac)));
        }
        return buildList(manufacturer);
    }

    @GetMapping("/handsets/{tac}")
    public ResponseEntity<HandsetRecord> handsetByTac(@PathVariable String tac) {
        return singleByTac(tac);
    }

    // ── /rs/v1/handsetbyIMEIList ──────────────────────────────────────────────

    @GetMapping("/rs/v1/handsetbyIMEIList")
    public ResponseEntity<TacSearchResponse> handsetByImeiList(
            @RequestParam String imeis) {

        List<String> tacs = Arrays.stream(imeis.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                // TAC = first 8 digits of the IMEI (IMEI is 15 digits)
                .map(imei -> imei.length() >= 8 ? imei.substring(0, 8) : imei)
                .distinct()
                .limit(MAX_TACS)
                .collect(Collectors.toList());

        return ResponseEntity.ok(repository.findByTacs(tacs));
    }

    // ── shared helpers ────────────────────────────────────────────────────────

    private List<String> parseTacs(String tacParam) {
        List<String> tacs = Arrays.stream(tacParam.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .limit(MAX_TACS)
                .collect(Collectors.toList());

        if (tacs.size() > MAX_TACS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Maximum " + MAX_TACS + " TACs per request");
        }
        return tacs;
    }

    private ResponseEntity<List<HandsetRecord>> buildList(String manufacturer) {
        List<HandsetRecord> result = (manufacturer == null || manufacturer.isBlank())
                ? repository.findAll()
                : repository.findByManufacturer(manufacturer);
        return ResponseEntity.ok(result);
    }

    private ResponseEntity<HandsetRecord> singleByTac(String tac) {
        return repository.findByTac(tac)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
