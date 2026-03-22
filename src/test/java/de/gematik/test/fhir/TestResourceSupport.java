package de.gematik.test.fhir;

import lombok.val;
import org.hl7.fhir.r4.formats.JsonParser;
import org.hl7.fhir.r4.formats.XmlParser;
import org.hl7.fhir.r4.model.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class TestResourceSupport {

  private static final XmlParser XML_PARSER = new XmlParser();
  private static final JsonParser JSON_PARSER = new JsonParser();

  private TestResourceSupport() {
  }

  static Resource readXmlResource(Path path) throws IOException {
    return XML_PARSER.parse(Files.readString(path, StandardCharsets.UTF_8));
  }

  static Resource readJsonResource(Path path) throws IOException {
    return JSON_PARSER.parse(Files.readString(path, StandardCharsets.UTF_8));
  }

  static List<Resource> loadFourSourceResources(Path baseDir) throws IOException {
    val sources = new ArrayList<Resource>();
    try (val paths = Files.walk(baseDir)) {
      paths
          .filter(Files::isRegularFile)
          .filter(TestResourceSupport::isXmlOrJsonFile)
          .sorted(Comparator.comparing(Path::toString))
          .forEach(path -> sources.add(readResource(path)));
    }
    return sources;
  }

  private static boolean isXmlOrJsonFile(Path path) {
    val fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
    return fileName.endsWith(".xml") || fileName.endsWith(".json");
  }

  private static Resource readResource(Path path) {
    try {
      if (path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xml")) {
        return readXmlResource(path);
      }
      return readJsonResource(path);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to read test resource: " + path, e);
    }
  }


  static void maybeWriteResource(Resource resource, Path outputPath) throws IOException {
    if (!Boolean.getBoolean("writeArtifacts")) {
      return;
    }
    Files.createDirectories(outputPath.getParent());
    Files.writeString(outputPath, JSON_PARSER.composeString(resource), StandardCharsets.UTF_8);
  }
}

