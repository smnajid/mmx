package com.mmx.order.architecture;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Red-first meta-test enforcing the {@code test-feedback-loop} taxonomy (spec `test-feedback-loop`):
 * every automated backend test class SHALL carry exactly one of `fast` / `integration` / `e2e` /
 * `architecture`. A class with no category tag or with more than one category tag fails the build,
 * naming the offending class(es).
 */
@Tag("architecture")
class TestCategoryTaggingTest {

    /** The four single category tags; exactly one must appear on each test class source file. */
    private static final Set<String> CATEGORIES = Set.of("fast", "integration", "e2e", "architecture");

    private static final Pattern CATEGORY_TAG_PATTERN =
            Pattern.compile("@Tag\\s*\\(\\s*\"(fast|integration|e2e|architecture)\"\\s*\\)");

    @Test
    void every_test_class_under_backend_has_exactly_one_category_tag() throws IOException {
        Path reactorRoot = findBackendReactorRoot();
        List<String> violations = new ArrayList<>();
        List<String> classified = new ArrayList<>();

        try (Stream<Path> moduleDirs = Files.list(reactorRoot)) {
            List<Path> modules = moduleDirs
                    .filter(module -> Files.isDirectory(module)
                            && module.getFileName().toString().startsWith("mmx-"))
                    .toList();

            for (Path module : modules) {
                Path testJava = module.resolve("src/test/java");
                if (!Files.isDirectory(testJava)) {
                    continue;
                }
                try (Stream<Path> sources = Files.walk(testJava)) {
                    List<Path> testSources = sources
                            .filter(path -> Files.isRegularFile(path)
                                    && path.getFileName().toString().endsWith("Test.java"))
                            .toList();

                    for (Path source : testSources) {
                        String className = relativeClassFor(source, module);
                        List<String> tags = categoryTags(source);
                        if (tags.isEmpty()) {
                            violations.add(className + " has NO category tag (expected exactly one of "
                                    + CATEGORIES + ")");
                        } else if (tags.size() > 1) {
                            violations.add(className + " has MULTIPLE category tags " + tags
                                    + " (expected exactly one)");
                        } else {
                            classified.add(className + " -> " + tags.get(0));
                        }
                    }
                }
            }
        }

        assertThat(violations)
                .as("test classes missing or duplicating a single category tag "
                        + "(repair by tagging each with exactly one of " + CATEGORIES + ")")
                .isEmpty();

        assertThat(classified)
                .as("classified test classes across the backend reactor")
                .isNotEmpty();
    }

    private static List<String> categoryTags(Path source) throws IOException {
        String content = Files.readString(source);
        Matcher matcher = CATEGORY_TAG_PATTERN.matcher(content);
        List<String> tags = new ArrayList<>();
        while (matcher.find()) {
            String tag = matcher.group(1);
            if (!tags.contains(tag)) {
                tags.add(tag);
            }
        }
        return tags;
    }

    private static String relativeClassFor(Path source, Path module) {
        Path testJava = module.resolve("src/test/java");
        Path rel = testJava.relativize(source);
        String noExt = rel.toString().replace('\\', '/').replaceAll("\\.java$", "");
        return noExt.replace('/', '.');
    }

    /**
     * Locate the {@code backend/} reactor root: the ancestor directory of the current working
     * directory whose {@code pom.xml} enumerates both {@code <module>mmx-bootstrap</module>} and
     * {@code <module>mmx-domain</module>}.
     */
    private static Path findBackendReactorRoot() {
        Path dir = Paths.get("").toAbsolutePath().normalize();
        while (dir != null) {
            Path pom = dir.resolve("pom.xml");
            if (Files.isRegularFile(pom)) {
                try {
                    String content = Files.readString(pom);
                    if (content.contains("<module>mmx-bootstrap</module>")
                            && content.contains("<module>mmx-domain</module>")) {
                        return dir;
                    }
                } catch (IOException ignored) {
                    // keep walking on read failure
                }
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "Could not locate backend/reactor root with <module>mmx-bootstrap</module> "
                        + "from working directory " + Paths.get("").toAbsolutePath());
    }
}