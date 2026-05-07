package com.canet.mock.data;

import com.canet.mock.model.HandsetRecord;
import com.canet.mock.model.TacSearchResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * In-memory store of sample handset records.
 * Add or edit entries here to extend the mock dataset without touching the
 * controller or any other layer.
 */
@Component
public class HandsetRepository {

    private static final List<HandsetRecord> HANDSETS = List.of(

        new HandsetRecord(
            "35674108",
            "Samsung Galaxy S24 Ultra",
            "Samsung",
            "SM-S928B",
            "Android", "14",
            "2G/3G/4G/5G",
            6.8, 2024,
            true, true
        ),
        new HandsetRecord(
            "35282402",
            "Apple iPhone 15 Pro Max",
            "Apple",
            "A3105",
            "iOS", "17",
            "2G/3G/4G/5G",
            6.7, 2023,
            true, true
        ),
        new HandsetRecord(
            "35428109",
            "Google Pixel 8 Pro",
            "Google",
            "GC3VE",
            "Android", "14",
            "2G/3G/4G/5G",
            6.7, 2023,
            true, true
        ),
        new HandsetRecord(
            "86345601",
            "OnePlus 12",
            "OnePlus",
            "CPH2573",
            "Android", "14",
            "2G/3G/4G/5G",
            6.82, 2024,
            true, true
        ),
        new HandsetRecord(
            "35567812",
            "Sony Xperia 1 VI",
            "Sony",
            "XQ-EC72",
            "Android", "14",
            "2G/3G/4G/5G",
            6.5, 2024,
            true, true
        ),
        new HandsetRecord(
            "86456723",
            "Xiaomi 14 Pro",
            "Xiaomi",
            "23116PN5BC",
            "Android", "14",
            "2G/3G/4G/5G",
            6.73, 2024,
            true, true
        ),
        new HandsetRecord(
            "35678934",
            "Motorola Edge 50 Pro",
            "Motorola",
            "XT2401-2",
            "Android", "14",
            "2G/3G/4G/5G",
            6.7, 2024,
            true, true
        ),
        new HandsetRecord(
            "35789045",
            "Samsung Galaxy A54",
            "Samsung",
            "SM-A546B",
            "Android", "13",
            "2G/3G/4G/5G",
            6.4, 2023,
            true, false
        ),
        new HandsetRecord(
            "35890156",
            "Apple iPhone SE (3rd Gen)",
            "Apple",
            "A2595",
            "iOS", "17",
            "2G/3G/4G/5G",
            4.7, 2022,
            false, false
        ),
        new HandsetRecord(
            "35012367",
            "Google Pixel 7a",
            "Google",
            "GWKK3",
            "Android", "13",
            "2G/3G/4G/5G",
            6.1, 2023,
            true, true
        )
    );

    private final Map<String, HandsetRecord> byTac = HANDSETS.stream()
            .collect(Collectors.toMap(HandsetRecord::tac, Function.identity()));

    public List<HandsetRecord> findAll() {
        return HANDSETS;
    }

    public List<HandsetRecord> findByManufacturer(String manufacturer) {
        return HANDSETS.stream()
                .filter(h -> h.manufacturer().equalsIgnoreCase(manufacturer))
                .collect(Collectors.toList());
    }

    public Optional<HandsetRecord> findByTac(String tac) {
        return Optional.ofNullable(byTac.get(tac));
    }

    public TacSearchResponse findByTacs(List<String> tacs) {
        List<HandsetRecord> found    = new ArrayList<>();
        List<String>        notFound = new ArrayList<>();
        for (String t : tacs) {
            findByTac(t).ifPresentOrElse(found::add, () -> notFound.add(t));
        }
        return new TacSearchResponse(tacs.size(), found.size(), found, notFound);
    }
}
