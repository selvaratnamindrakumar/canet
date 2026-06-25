package com.canet.fileproc.movement;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class FileMovementService {

    private static final DateTimeFormatter DATE_PREFIX = DateTimeFormatter.ofPattern("yyyyMMdd");

    public Path copy(Path src, Path dest) {
        try {
            Files.createDirectories(dest.getParent());
            return Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path move(Path src, Path dest) {
        try {
            Files.createDirectories(dest.getParent());
            try {
                return Files.move(src, dest, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicFailed) {
                // ATOMIC_MOVE may not be supported across filesystems
                return Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path archiveProcessed(Path file, Path archiveDir) {
        String prefix = LocalDate.now(ZoneOffset.UTC).format(DATE_PREFIX) + "_";
        Path dest = archiveDir.resolve(prefix + file.getFileName());
        return move(file, dest);
    }

    public List<Path> listPending(Path watchDir, Predicate<Path> filter) {
        try (Stream<Path> walk = Files.walk(watchDir)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(filter)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<Path> listByExtension(Path dir, String ext) {
        return listPending(dir, p -> p.toString().endsWith(ext));
    }

    public int purgeOlderThan(Path dir, int days) {
        Instant cutoff = Instant.now().minusSeconds((long) days * 86400);
        try (Stream<Path> walk = Files.walk(dir)) {
            List<Path> toDelete = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> isOlderThan(p, cutoff))
                    .collect(Collectors.toList());
            toDelete.forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            return toDelete.size();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean isOlderThan(Path path, Instant cutoff) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            return attrs.lastModifiedTime().toInstant().isBefore(cutoff);
        } catch (IOException e) {
            return false;
        }
    }
}
