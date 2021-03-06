package com.walmartlabs.concord.project;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.project.model.Profile;
import com.walmartlabs.concord.project.model.ProjectDefinition;
import com.walmartlabs.concord.project.model.Trigger;
import com.walmartlabs.concord.project.yaml.*;
import com.walmartlabs.concord.project.yaml.model.*;
import com.walmartlabs.concord.project.yaml.validator.Validator;
import com.walmartlabs.concord.project.yaml.validator.ValidatorContext;
import com.walmartlabs.concord.sdk.Constants;
import io.takari.bpm.model.ProcessDefinition;
import io.takari.bpm.model.form.FormDefinition;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class ProjectLoader {

    public static final String[] PROJECT_FILE_NAMES = {".concord.yml", "concord.yml"}; // NOSONAR

    private final YamlParser parser = new YamlParser();

    public ProjectDefinition loadProject(Path baseDir) throws IOException {
        ProjectDefinitionBuilder b = new ProjectDefinitionBuilder(parser);

        Path projectsDir = baseDir.resolve(Constants.Files.PROJECT_FILES_DIR_NAME);
        if (Files.exists(projectsDir)) {
            b.addProjects(projectsDir);
        }

        for (String n : PROJECT_FILE_NAMES) {
            Path p = baseDir.resolve(n);
            if (Files.exists(p)) {
                b.addProjectFile(p);
                break;
            }
        }

        for (String n : Constants.Files.DEFINITIONS_DIR_NAMES) {
            Path p = baseDir.resolve(n);
            if (Files.exists(p)) {
                b.addDefinitions(p);
            }
        }

        Path profilesDir = baseDir.resolve(Constants.Files.PROFILES_DIR_NAME);
        if (Files.exists(profilesDir)) {
            b.addProfiles(profilesDir);
        }

        return b.build();
    }

    public ProjectDefinition loadProject(InputStream in) throws IOException {
        ProjectDefinitionBuilder b = new ProjectDefinitionBuilder(parser);
        b.loadDefinitions(in);
        return b.build();
    }

    private static class ProjectDefinitionBuilder {
        private ValidatorContext validatorContext = new ValidatorContext();

        private final YamlParser parser;

        private Map<String, ProcessDefinition> flows;
        private Map<String, FormDefinition> forms;
        private Map<String, Profile> profiles;
        private List<ProjectDefinition> projectDefinitions;

        private ProjectDefinitionBuilder(YamlParser parser) {
            this.parser = parser;
        }

        public ProjectDefinitionBuilder addProjectFile(Path file) throws IOException {
            YamlProject yml = parser.parseProject(file);
            if (yml == null) {
                throw new IOException("Empty project definition: " + file);
            }

            Validator.validate(validatorContext, yml);

            ProjectDefinition pd = YamlProjectConverter.convert(yml);

            if (projectDefinitions == null) {
                projectDefinitions = new ArrayList<>();
            }
            projectDefinitions.add(pd);

            return this;
        }

        public void addDefinitions(Path path) throws IOException {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!isYaml(file)) {
                        return FileVisitResult.CONTINUE;
                    }

                    loadDefinitions(file);
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        public void addProfiles(Path path) throws IOException {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!isYaml(file)) {
                        return FileVisitResult.CONTINUE;
                    }

                    loadProfiles(file);
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        public void addProjects(Path path) throws IOException {
            List<Path> files = new ArrayList<>();

            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (isYaml(file)) {
                        files.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            Collections.sort(files);

            for (Path f : files) {
                addProjectFile(f);
            }
        }

        private void loadDefinitions(Path file) throws IOException {
            YamlDefinitionFile df = parser.parseDefinitionFile(file);
            loadDefinitions(df);
        }

        private void loadDefinitions(InputStream in) throws IOException {
            YamlDefinitionFile df = parser.parseDefinitionFile(in);
            loadDefinitions(df);
        }

        private void loadDefinitions(YamlDefinitionFile df) throws YamlConverterException {
            if (df == null) {
                return;
            }

            Map<String, YamlDefinition> m = df.getDefinitions();
            if (m != null) {
                for (Map.Entry<String, YamlDefinition> e : m.entrySet()) {
                    String k = e.getKey();
                    YamlDefinition v = e.getValue();

                    if (v instanceof YamlProcessDefinition) {
                        if (flows == null) {
                            flows = new HashMap<>();
                        }

                        Validator.validate(validatorContext, (YamlProcessDefinition) v);

                        flows.put(k, YamlProcessConverter.convert((YamlProcessDefinition) v));
                    } else if (v instanceof YamlFormDefinition) {
                        if (forms == null) {
                            forms = new HashMap<>();
                        }
                        forms.put(k, YamlFormConverter.convert((YamlFormDefinition) v));
                    }
                }
            }
        }

        private void loadProfiles(Path file) throws IOException {
            if (!isYaml(file)) {
                return;
            }

            YamlProfileFile pf = parser.parseProfileFile(file);
            loadProfiles(pf);
        }

        private void loadProfiles(YamlProfileFile pf) throws YamlConverterException {
            if (pf == null) {
                return;
            }

            Map<String, YamlProfile> m = pf.getProfiles();

            for (Map.Entry<String, YamlProfile> e : m.entrySet()) {
                String k = e.getKey();
                YamlProfile v = e.getValue();

                Profile p = YamlProjectConverter.convert(v);
                if (profiles == null) {
                    profiles = new HashMap<>();
                }
                profiles.put(k, p);
            }
        }

        public ProjectDefinition build() {
            if (flows == null) {
                flows = new HashMap<>();
            }

            if (forms == null) {
                forms = new HashMap<>();
            }

            if (profiles == null) {
                profiles = new HashMap<>();
            }

            Map<String, Object> variables = new LinkedHashMap<>();
            List<Trigger> triggers = new ArrayList<>();

            if (projectDefinitions != null) {
                for (ProjectDefinition pd : projectDefinitions) {
                    if (pd.getFlows() != null) {
                        flows.putAll(pd.getFlows());
                    }

                    if (pd.getForms() != null) {
                        forms.putAll(pd.getForms());
                    }

                    if (pd.getConfiguration() != null) {
                        variables = ConfigurationUtils.deepMerge(variables, pd.getConfiguration());
                    }

                    if (pd.getProfiles() != null) {
                        for (Map.Entry<String, Profile> p : pd.getProfiles().entrySet()) {
                            merge(profiles, p.getKey(), p.getValue());
                        }
                    }

                    if (pd.getTriggers() != null) {
                        triggers.addAll(pd.getTriggers());
                    }
                }
            }

            return new ProjectDefinition(flows, forms, variables, profiles, triggers);
        }

        private static boolean isYaml(Path p) {
            String n = p.getFileName().toString().toLowerCase();
            return n.endsWith(".yml") || n.endsWith(".yaml");
        }

        private static void merge(Map<String, Profile> profiles, String k, Profile b) {
            Profile a = profiles.get(k);
            if (a == null) {
                profiles.put(k, b);
                return;
            }

            Map<String, ProcessDefinition> flows = new HashMap<>();
            if (a.getFlows() != null) {
                flows.putAll(a.getFlows());
            }
            if (b.getFlows() != null) {
                flows.putAll(b.getFlows());
            }

            Map<String, FormDefinition> forms = new HashMap<>();
            if (a.getForms() != null) {
                forms.putAll(a.getForms());
            }
            if (b.getForms() != null) {
                forms.putAll(b.getForms());
            }

            Map<String, Object> cfg = new HashMap<>();
            if (a.getConfiguration() != null) {
                cfg.putAll(a.getConfiguration());
            }
            if (b.getConfiguration() != null) {
                cfg = ConfigurationUtils.deepMerge(cfg, b.getConfiguration());
            }

            profiles.put(k, new Profile(flows, forms, cfg));
        }
    }
}
