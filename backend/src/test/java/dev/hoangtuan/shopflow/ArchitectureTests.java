package dev.hoangtuan.shopflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ArchitectureTests {
  private static final String BASE = "dev.hoangtuan.shopflow.";
  private static final Pattern IMPORT = Pattern.compile("(?m)^import (?:static )?([^;]+);");
  private static final Set<String> LAYERS =
      Set.of(
          "config",
          "controller",
          "service",
          "repository",
          "model",
          "dto",
          "security",
          "exception",
          "integration");

  @Test
  void productionSourcesFollowLayerDependencies() throws Exception {
    Path root = Path.of(System.getProperty("basedir"), "src/main/java/dev/hoangtuan/shopflow");
    try (var paths = Files.walk(root)) {
      var sources = paths.filter(path -> path.toString().endsWith(".java")).toList();
      assertThat(sources.size()).isGreaterThan(100);
      for (Path path : sources) {
        var relative = root.relativize(path);
        if (relative.getNameCount() == 1) {
          assertThat(path.getFileName().toString()).isEqualTo("ShopflowApplication.java");
          continue;
        }
        String layer = relative.getName(0).toString();
        assertThat(LAYERS).as("layer for %s", relative).contains(layer);
        String source = Files.readString(path);
        if (layer.equals("service") || layer.equals("security")) {
          assertThat(source)
              .as("database access belongs in repositories: %s", relative)
              .doesNotContain("JdbcTemplate", "EntityManager", "java.sql.Connection");
        }
        var imports = IMPORT.matcher(source);
        while (imports.find()) {
          String dependency = imports.group(1);
          if (!dependency.startsWith(BASE)) continue;
          String target = dependency.substring(BASE.length()).split("\\.")[0];
          boolean forbidden =
              switch (layer) {
                case "controller" -> target.equals("repository") || target.equals("integration");
                case "service" -> target.equals("controller");
                case "repository" ->
                    Set.of("controller", "service", "security", "integration").contains(target);
                case "model", "dto" ->
                    Set.of("controller", "service", "repository", "security", "integration")
                        .contains(target);
                case "integration" ->
                    Set.of("controller", "service", "repository").contains(target);
                default -> false;
              };
          assertThat(forbidden).as("%s must not depend on %s", relative, dependency).isFalse();
        }
      }
    }
  }
}
