import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MainTest {
    @TempDir
    Path directory;

    @Test
    void configuredDataDirectoryTakesPrecedenceAndIsNormalized() {
        Path configured = directory.resolve("store").resolve("..").resolve("data");

        assertEquals(directory.resolve("data").toAbsolutePath().normalize(),
                Main.resolveDataDirectory(configured.toString(), directory, directory));
    }

    @Test
    void projectDataDirectoryIsResolvedFromApplicationLocation() throws Exception {
        Path project = directory.resolve("project");
        Path classes = project.resolve("target").resolve("classes");
        Path projectData = project.resolve("data");
        Path workingDirectory = directory.resolve("elsewhere");
        Files.createDirectories(classes);
        Files.createDirectories(projectData);
        Files.createDirectories(workingDirectory);
        Files.createFile(project.resolve("pom.xml"));
        Files.createFile(projectData.resolve("locations.csv"));

        assertEquals(projectData.toAbsolutePath().normalize(),
                Main.resolveDataDirectory(null, workingDirectory, classes));
    }
}
