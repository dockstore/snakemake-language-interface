package io.dockstore.language;

import static io.dockstore.language.SnakemakeWorkflowPlugin.SNAKEMAKE_WORKFLOW_CATALOG_YML;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.io.Resources;
import io.dockstore.common.VersionTypeValidation;
import io.dockstore.language.MinimalLanguageInterface.FileMetadata;
import io.dockstore.language.MinimalLanguageInterface.GenericFileType;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

public class SnakemakeWorkflowPluginTest {

    @Test
    public void testWorkflowParsingHelloWorldWithoutCatalog() {
        VersionTypeValidation versionTypeValidation = attemptValidation("nathanhaigh/snakemake-hello-world", false);
        assertFalse(versionTypeValidation.isValid());
    }

    private static VersionTypeValidation attemptValidation(String repoName, boolean isCatalogPresent) {
        final SnakemakeWorkflowPlugin.SnakemakeWorkflowPluginImpl plugin =
            new SnakemakeWorkflowPlugin.SnakemakeWorkflowPluginImpl();
        final ResourceFileReader reader = new ResourceFileReader(repoName);
        final String initialPath = "/workflow/Snakefile";
        final String contents = reader.readFile(initialPath);
        final Map<String, MinimalLanguageInterface.FileMetadata> fileMap =
            plugin.indexWorkflowFiles(initialPath, contents, reader);

        // TODO parse some actual information
        plugin.parseWorkflowForMetadata(initialPath, contents, fileMap);

        assertFalse(fileMap.isEmpty());
        assertEquals(fileMap.containsKey(SNAKEMAKE_WORKFLOW_CATALOG_YML), isCatalogPresent);
        assertTrue(fileMap.containsKey("/README.md"));
        return plugin.validateWorkflowSet(initialPath, contents, fileMap);
    }

    @Test
    public void testWorkflowParsingHelloWorld() {
        VersionTypeValidation versionTypeValidation = attemptValidation("nathanhaigh/snakemake-hello-world-with-catalog", true);
        assertTrue(versionTypeValidation.isValid());
    }

    @Test
    public void testWorkflowParsingComplex() {
        final SnakemakeWorkflowPlugin.SnakemakeWorkflowPluginImpl plugin =
            new SnakemakeWorkflowPlugin.SnakemakeWorkflowPluginImpl();
        final ResourceFileReader reader = new ResourceFileReader("snakemake-workflows/rna-seq-star-deseq2");
        final String initialPath = "/workflow/Snakefile";
        final String contents = reader.readFile(initialPath);
        final Map<String, MinimalLanguageInterface.FileMetadata> fileMap =
            plugin.indexWorkflowFiles(initialPath, contents, reader);

        assertFalse(fileMap.isEmpty());
        assertTrue(fileMap.containsKey("/workflow/rules/align.smk"));
        // TODO recursion, may need to update the plugin handler in dockstore itself
        // assertTrue(fileMap.containsKey(".test/config_basic/config.yaml"));
        assertTrue(fileMap.containsKey("/LICENSE"));
        assertTrue(fileMap.get("/LICENSE").content().contains("The above copyright notice and this permission notice shall be included in all"));
        assertTrue(fileMap.get("/workflow/rules/align.smk").content().contains("3.5.3/bio/star/align"));
        assertTrue(plugin.validateWorkflowSet(initialPath, contents,fileMap).isValid());
    }

    private static Map<String, FileMetadata> createDummyFileMap(String initialPath) {
        return Map.of(
            initialPath, new FileMetadata("dummy snakefile content", GenericFileType.IMPORTED_DESCRIPTOR, "1.0"),
            SnakemakeWorkflowPlugin.SNAKEMAKE_WORKFLOW_CATALOG_YML, new FileMetadata("dummy workflow catalog content", GenericFileType.IMPORTED_DESCRIPTOR, "1.0")
        );
    }

    @Test
    public void testWorkflowPrimaryDescriptorMustBeSnakefile() {
        final SnakemakeWorkflowPlugin.SnakemakeWorkflowPluginImpl plugin =
            new SnakemakeWorkflowPlugin.SnakemakeWorkflowPluginImpl();
        for (String validPath: List.of("/Snakefile", "/workflow/Snakefile", "/snakefile", "/workflow/snakefile")) {
            VersionTypeValidation validation = plugin.validateWorkflowSet(validPath, "", createDummyFileMap(validPath));
            assertTrue(validation.isValid());
        }
        for (String invalidPath: List.of("/workflow.wdl", "/data.txt")) {
            VersionTypeValidation validation = plugin.validateWorkflowSet(invalidPath, "", createDummyFileMap(invalidPath));
            assertFalse(validation.isValid());
            assertEquals(validation.getMessage().get(invalidPath), SnakemakeWorkflowPlugin.INVALID_INITIAL_PATH_MESSAGE);
        }
    }

    abstract static class URLFileReader implements MinimalLanguageInterface.FileReader {
        // URL to repo
        protected final String repo;
        // extracted ID
        protected final Optional<String> id;

        URLFileReader(final String repo) {
            this.repo = repo;
            final String[] split = repo.split("/");
            if (split.length >= 2) {
                id = Optional.of(split[split.length - 2] + "/" + split[split.length - 1]);
            } else {
                id = Optional.empty();
            }
        }

        protected abstract URL getUrl(final String path) throws IOException;

        @Override
        public String readFile(String path) {
            try {
                if (path.startsWith("/")) {
                    path = path.substring(1);
                }
                URL url = this.getUrl(path);
                return Resources.toString(url, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public List<String> listFiles(String pathToDirectory) {
            if (id.isEmpty()) {
                return new ArrayList<>();
            }
            String fileDirectory = "src/test/resources/io/dockstore/language/" + this.id.get() + "/" + pathToDirectory;
            return FileUtils.listFiles(new File(fileDirectory), null, false).stream().map(f -> f.getName()).toList();
        }
    }

    static class ResourceFileReader extends URLFileReader {

        ResourceFileReader(final String repo) {
            super(repo);
        }

        @Override
        protected URL getUrl(String path) throws IOException {
            final String classPath = this.repo + "/" + path;
            final URL url = SnakemakeWorkflowPluginTest.class.getResource(classPath);
            if (url == null) {
                throw new IOException("No such file " + classPath);
            }
            return url;
        }
    }
}
