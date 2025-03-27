/*
 *    Copyright 2025 OICR and UCSC
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.dockstore.language;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.VersionTypeValidation;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author dyuen
 */
public class SnakemakeWorkflowPlugin extends Plugin {

    public static final Logger LOG = LoggerFactory.getLogger(SnakemakeWorkflowPlugin.class);
    public static final String SNAKEMAKE_WORKFLOW_CATALOG_YML = ".snakemake-workflow-catalog.yml";


    /**
     * Constructor to be used by plugin manager for plugin instantiation. Your plugins have to provide
     * constructor with this exact signature to be successfully loaded by manager.
     *
     * @param wrapper
     */
    public SnakemakeWorkflowPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class SnakemakeWorkflowPluginImpl implements RecommendedLanguageInterface {

        @Override
        public String launchInstructions(String trsID) {
            return null;
        }


        @Override
        public VersionTypeValidation validateWorkflowSet(String initialPath, String contents, Map<String, FileMetadata> indexedFiles) {
            VersionTypeValidation validation = new VersionTypeValidation(indexedFiles.containsKey(SNAKEMAKE_WORKFLOW_CATALOG_YML), new HashMap<>());
            // TODO hook up some real validation
            return validation;
        }

        @Override
        public VersionTypeValidation validateTestParameterSet(Map<String, FileMetadata> indexedFiles) {
            return new VersionTypeValidation(true, new HashMap<>());
        }


        @Override
        public DescriptorLanguage getDescriptorLanguage() {
            return DescriptorLanguage.SMK;
        }

        /**
         * TODO: non-standardized Snakemake files can technically have their Snakefile in the root, supporting this probably
         * affects some assumptions about folder structure below https://github.com/snakemake/snakemake-workflow-catalog?tab=readme-ov-file#standardized-usage-workflows
         * @return
         */
        @Override
        public Pattern initialPathPattern() {
            return Pattern.compile("workflow/Snakefile");
        }

        @Override
        public Map<String, FileMetadata> indexWorkflowFiles(String initialPath, String contents, FileReader reader) {
            Map<String, FileMetadata> results = new HashMap<>();
            // start with the workflow/Snakemake, some notes on workflow structure at https://snakemake.readthedocs.io/en/stable/snakefiles/deployment.html
            results.put(
                initialPath,
                //TODO: get real snakemake version number, if they have one
                new FileMetadata(contents, GenericFileType.IMPORTED_DESCRIPTOR, "1.0"));
            // there can be  licenses, readme files and other useful stuff in the root
            processFolder("/", null, reader, results);


            // Snakefile might be in same directory as catalog file or up in a workflow directory (a workflow directory will have other folders), is not named in the catalog file
            processFolder(initialPath, "/workflow", reader, results);
            if (results.containsKey("/workflow/Snakefile")) {
                processWorkflowFolders(reader, results);
            }
            // sometimes there is a config folder
            processFolder(initialPath, "/config", reader, results);
            // there are results and resources folders, developers are encouraged to keep the latter small
            // processFolder(initialPath, "results", reader, results);
            processFolder(initialPath, "/resources", reader, results);

            /// TODO modules support

            // workflow hub extensions
            // TODO will need to be recursive
            processFolder(initialPath, ".tests", reader, results);


            return results;
        }

        private void processWorkflowFolders(FileReader reader, Map<String, FileMetadata> results) {
            processFolder("/workflow", "workflow/envs", reader, results);
            processFolder("/workflow", "workflow/report", reader, results);
            processFolder("/workflow", "workflow/rules", reader, results);
            // TODO see comment in https://github.com/dockstore/snakemake-language-interface/pull/2#discussion_r2014758910
            // the schemas directory may be optional and potentially added by a `validate` call, investigate
            processFolder("/workflow", "workflow/schemas", reader, results);
            processFolder("/workflow", "workflow/scripts", reader, results);
            processFolder("/workflow", "workflow/notebooks", reader, results);
        }

        // TODO determine how to process non-descriptor content https://ucsc-cgl.atlassian.net/browse/DOCK-262
        private void processFolder(String initialPath, String folder, FileReader reader, Map<String, FileMetadata> results) {
            List<String> files = findFiles(initialPath, folder, reader);
            for (String file : files) {
                results.put(file, new FileMetadata(reader.readFile(file), GenericFileType.IMPORTED_DESCRIPTOR, null));
            }
        }

        // TODO: folderToCheck should probably be dealt with recursively
        // TODO: use typed files instead of FileUtils from Apache (Galaxy plugin)
        protected List<String> findFiles(final String initialPath, final String folderToCheck, final FileReader reader) {

            final int extensionPos = initialPath.lastIndexOf("/");
            final String base = initialPath.substring(0, extensionPos);

            final Path pathsInDir = folderToCheck == null ? Paths.get(base) : Paths.get(base, folderToCheck);
            // listing files is more rate limit friendly (e.g. GitHub counts each 404 "miss" as an API
            // call,
            // but listing a directory can be free if previously requested/cached)
            List<String> filennamesInDir = reader.listFiles(pathsInDir.toString());
            if (filennamesInDir == null) {
                return Lists.newArrayList();
            }
            final Set<String> filenameSet = Sets.newHashSet(filennamesInDir);
            return filenameSet.stream().map(s ->  (folderToCheck == null ? "" : folderToCheck + "/") + s).toList();
        }

        @Override
        public WorkflowMetadata parseWorkflowForMetadata(String initialPath, String contents, Map<String, FileMetadata> indexedFiles) {
            WorkflowMetadata metadata = new WorkflowMetadata();
            // TODO: grab real metadata, this is carried over from SWL and is non-functional
            for (String line : contents.split("\\r?\\n")) {
                if (line.startsWith("author")) {
                    final String[] s = line.split(":");
                    metadata.setAuthor(s[1].trim());
                }
                if (line.startsWith("description")) {
                    final String[] s = line.split(":");
                    metadata.setDescription(s[1].trim());
                }
            }
            return metadata;
        }
    }
}

