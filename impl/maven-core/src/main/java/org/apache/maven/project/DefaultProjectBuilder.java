/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.project;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.ProjectCycleException;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.api.ArtifactCoordinates;
import org.apache.maven.api.Language;
import org.apache.maven.api.LocalRepository;
import org.apache.maven.api.ProjectScope;
import org.apache.maven.api.SessionData;
import org.apache.maven.api.annotations.Nonnull;
import org.apache.maven.api.annotations.Nullable;
import org.apache.maven.api.model.Build;
import org.apache.maven.api.model.Dependency;
import org.apache.maven.api.model.DependencyManagement;
import org.apache.maven.api.model.DeploymentRepository;
import org.apache.maven.api.model.Extension;
import org.apache.maven.api.model.Model;
import org.apache.maven.api.model.Plugin;
import org.apache.maven.api.model.Profile;
import org.apache.maven.api.model.ReportPlugin;
import org.apache.maven.api.model.Resource;
import org.apache.maven.api.services.ArtifactResolver;
import org.apache.maven.api.services.ArtifactResolverException;
import org.apache.maven.api.services.ArtifactResolverRequest;
import org.apache.maven.api.services.ArtifactResolverResult;
import org.apache.maven.api.services.BuilderProblem.Severity;
import org.apache.maven.api.services.ModelBuilder;
import org.apache.maven.api.services.ModelBuilderException;
import org.apache.maven.api.services.ModelBuilderRequest;
import org.apache.maven.api.services.ModelBuilderResult;
import org.apache.maven.api.services.ModelProblem;
import org.apache.maven.api.services.ModelProblem.Version;
import org.apache.maven.api.services.ModelProblemCollector;
import org.apache.maven.api.services.ModelSource;
import org.apache.maven.api.services.ModelTransformer;
import org.apache.maven.api.services.ProblemCollector;
import org.apache.maven.api.services.Source;
import org.apache.maven.api.services.Sources;
import org.apache.maven.api.services.model.LifecycleBindingsInjector;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.bridge.MavenRepositorySystem;
import org.apache.maven.impl.DefaultSourceRoot;
import org.apache.maven.impl.InternalSession;
import org.apache.maven.impl.resolver.ArtifactDescriptorUtils;
import org.apache.maven.internal.impl.InternalMavenSession;
import org.apache.maven.model.building.DefaultModelProblem;
import org.apache.maven.model.building.FileModelSource;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelSource2;
import org.apache.maven.model.root.RootLocator;
import org.apache.maven.plugin.PluginManagerException;
import org.apache.maven.plugin.PluginResolutionException;
import org.apache.maven.plugin.version.PluginVersionResolutionException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DefaultProjectBuilder
 *
 * @deprecated use {@code org.apache.maven.api.services.ProjectBuilder} instead
 */
@Deprecated(since = "4.0.0")
@Named
@Singleton
public class DefaultProjectBuilder implements ProjectBuilder {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ModelBuilder modelBuilder;
    private final ProjectBuildingHelper projectBuildingHelper;
    private final MavenRepositorySystem repositorySystem;
    private final ProjectDependenciesResolver dependencyResolver;
    private final RootLocator rootLocator;
    private final LifecycleBindingsInjector lifecycleBindingsInjector;

    @SuppressWarnings("checkstyle:ParameterNumber")
    @Inject
    public DefaultProjectBuilder(
            ModelBuilder modelBuilder,
            ProjectBuildingHelper projectBuildingHelper,
            MavenRepositorySystem repositorySystem,
            RepositorySystem repoSystem,
            ProjectDependenciesResolver dependencyResolver,
            RootLocator rootLocator,
            LifecycleBindingsInjector lifecycleBindingsInjector) {
        this.modelBuilder = modelBuilder;
        this.projectBuildingHelper = projectBuildingHelper;
        this.repositorySystem = repositorySystem;
        this.dependencyResolver = dependencyResolver;
        this.rootLocator = rootLocator;
        this.lifecycleBindingsInjector = lifecycleBindingsInjector;
    }
    // ----------------------------------------------------------------------
    // MavenProjectBuilder Implementation
    // ----------------------------------------------------------------------

    @Override
    public ProjectBuildingResult build(File pomFile, ProjectBuildingRequest request) throws ProjectBuildingException {
        try (BuildSession bs = new BuildSession(request)) {
            Path path = pomFile.toPath();
            return bs.build(false, path, Sources.buildSource(path));
        }
    }

    @Deprecated
    @Override
    public ProjectBuildingResult build(
            org.apache.maven.model.building.ModelSource modelSource, ProjectBuildingRequest request)
            throws ProjectBuildingException {
        return build(toSource(modelSource), request);
    }

    @Deprecated
    static ModelSource toSource(org.apache.maven.model.building.ModelSource modelSource) {
        if (modelSource instanceof FileModelSource fms) {
            return Sources.buildSource(fms.getPath());
        } else {
            return new WrapModelSource(modelSource);
        }
    }

    @Override
    public ProjectBuildingResult build(ModelSource modelSource, ProjectBuildingRequest request)
            throws ProjectBuildingException {
        try (BuildSession bs = new BuildSession(request)) {
            return bs.build(false, null, modelSource);
        }
    }

    @Override
    public ProjectBuildingResult build(Artifact artifact, ProjectBuildingRequest request)
            throws ProjectBuildingException {
        return build(artifact, false, request);
    }

    @Override
    public ProjectBuildingResult build(Artifact artifact, boolean allowStubModel, ProjectBuildingRequest request)
            throws ProjectBuildingException {
        try (BuildSession bs = new BuildSession(request)) {
            return bs.build(false, artifact, allowStubModel);
        }
    }

    @Override
    public List<ProjectBuildingResult> build(List<File> pomFiles, boolean recursive, ProjectBuildingRequest request)
            throws ProjectBuildingException {
        try (BuildSession bs = new BuildSession(request)) {
            return bs.build(pomFiles, recursive);
        }
    }

    private static class StubModelSource implements ModelSource {
        private final String xml;
        private final Artifact artifact;

        StubModelSource(String xml, Artifact artifact) {
            this.xml = xml;
            this.artifact = artifact;
        }

        @Override
        @Nullable
        public ModelSource resolve(@Nonnull ModelLocator modelLocator, @Nonnull String relative) {
            return null;
        }

        @Override
        @Nullable
        public Path getPath() {
            return null;
        }

        @Override
        @Nonnull
        public InputStream openStream() throws IOException {
            return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        @Nonnull
        public String getLocation() {
            return artifact.getId();
        }

        @Override
        @Nullable
        public Source resolve(@Nonnull String relative) {
            return null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            StubModelSource that = (StubModelSource) o;
            return Objects.equals(xml, that.xml) && Objects.equals(artifact, that.artifact);
        }

        @Override
        public int hashCode() {
            return Objects.hash(xml, artifact);
        }
    }

    private static class WrapModelSource implements ModelSource {
        private final org.apache.maven.model.building.ModelSource modelSource;

        WrapModelSource(org.apache.maven.model.building.ModelSource modelSource) {
            this.modelSource = modelSource;
        }

        @Override
        @Nullable
        public ModelSource resolve(@Nonnull ModelLocator modelLocator, @Nonnull String relative) {
            return null;
        }

        @Override
        @Nullable
        public Path getPath() {
            return null;
        }

        @Override
        @Nonnull
        public InputStream openStream() throws IOException {
            return modelSource.getInputStream();
        }

        @Override
        @Nonnull
        public String getLocation() {
            return modelSource.getLocation();
        }

        @Override
        @Nullable
        public Source resolve(@Nonnull String relative) {
            if (modelSource instanceof ModelSource2 ms) {
                return toSource(ms.getRelatedSource(relative));
            } else {
                return null;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            WrapModelSource that = (WrapModelSource) o;
            return Objects.equals(modelSource, that.modelSource);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(modelSource);
        }
    }

    class BuildSession implements AutoCloseable {
        private final ProjectBuildingRequest request;
        private final InternalSession session;
        private final ModelBuilder.ModelBuilderSession modelBuilderSession;
        private final Map<String, MavenProject> projectIndex = new ConcurrentHashMap<>(256);

        BuildSession(ProjectBuildingRequest request) {
            this.request = request;
            InternalSession session = InternalSession.from(request.getRepositorySession());
            Path basedir = request.getLocalRepository() != null
                    ? request.getLocalRepository().getBasedirPath()
                    : null;
            if (basedir != null) {
                LocalRepository localRepository = session.createLocalRepository(basedir);
                session = InternalSession.from(session.withLocalRepository(localRepository));
            }
            this.session = session;
            this.modelBuilderSession = modelBuilder.newSession();
            // Save the ModelBuilderSession for later retrieval by the DefaultConsumerPomBuilder.
            // Use replace(key, null, value) to make sure the *main* session, i.e. the one used
            // to load the projects, is stored. This is to avoid the session being overwritten
            // if a plugin uses the ProjectBuilder.
            this.session
                    .getData()
                    .replace(SessionData.key(ModelBuilder.ModelBuilderSession.class), null, modelBuilderSession);
        }

        @Override
        public void close() {}

        ProjectBuildingResult build(boolean parent, Path pomFile, ModelSource modelSource)
                throws ProjectBuildingException {
            ClassLoader oldContextClassLoader = Thread.currentThread().getContextClassLoader();

            try {
                MavenProject project = request.getProject();

                ProblemCollector<ModelProblem> problemCollector = null;
                Throwable error = null;

                if (project == null) {
                    project = new MavenProject();
                    project.setFile(pomFile != null ? pomFile.toFile() : null);

                    boolean reactorMember = pomFile != null
                            && session.getProjects() != null // this is for UTs
                            && session.getProjects().stream()
                                    .anyMatch(
                                            p -> p.getPomPath().toAbsolutePath().equals(pomFile.toAbsolutePath()));
                    boolean isStandalone = pomFile == null
                            && modelSource != null
                            && modelSource.getLocation().startsWith("jar:")
                            && modelSource.getLocation().endsWith("/org/apache/maven/project/standalone.xml");

                    ModelBuilderRequest.ModelBuilderRequestBuilder builder = getModelBuildingRequest();
                    ModelBuilderRequest.RequestType type = reactorMember
                                    || isStandalone
                                    || (pomFile != null
                                            && this.request.isProcessPlugins()
                                            && this.request.getValidationLevel()
                                                    == ModelBuildingRequest.VALIDATION_LEVEL_STRICT)
                            ? ModelBuilderRequest.RequestType.BUILD_EFFECTIVE
                            : (parent
                                    ? ModelBuilderRequest.RequestType.CONSUMER_PARENT
                                    : ModelBuilderRequest.RequestType.CONSUMER_DEPENDENCY);
                    MavenProject theProject = project;
                    ModelBuilderRequest request = builder.source(modelSource)
                            .requestType(type)
                            .locationTracking(true)
                            .lifecycleBindingsInjector(
                                    (m, r, p) -> injectLifecycleBindings(m, r, p, theProject, this.request))
                            .build();

                    if (pomFile != null) {
                        project.setRootDirectory(rootLocator.findRoot(pomFile.getParent()));
                    }

                    ModelBuilderResult result;
                    try {
                        result = modelBuilderSession.build(request);
                    } catch (ModelBuilderException e) {
                        result = e.getResult();
                        if (result == null || result.getEffectiveModel() == null) {
                            throw new ProjectBuildingException(
                                    e.getModelId(), e.getMessage(), pomFile != null ? pomFile.toFile() : null, e);
                        }
                        // validation error, continue project building and delay failing to help IDEs
                        error = e;
                    }

                    problemCollector = result.getProblemCollector();

                    initProject(project, result);
                }

                DependencyResolutionResult resolutionResult = null;

                if (request.isResolveDependencies()) {
                    projectBuildingHelper.selectProjectRealm(project);
                    resolutionResult = resolveDependencies(project);
                }

                ProjectBuildingResult result =
                        new DefaultProjectBuildingResult(project, convert(problemCollector), resolutionResult);

                if (error != null) {
                    ProjectBuildingException e = new ProjectBuildingException(List.of(result));
                    e.initCause(error);
                    throw e;
                }

                return result;
            } finally {
                Thread.currentThread().setContextClassLoader(oldContextClassLoader);
            }
        }

        ProjectBuildingResult build(boolean parent, Artifact artifact, boolean allowStubModel)
                throws ProjectBuildingException {
            org.eclipse.aether.artifact.Artifact pomArtifact = RepositoryUtils.toArtifact(artifact);
            pomArtifact = ArtifactDescriptorUtils.toPomArtifact(pomArtifact);

            boolean localProject;

            try {
                ArtifactCoordinates coordinates = session.createArtifactCoordinates(session.getArtifact(pomArtifact));
                ArtifactResolverRequest req = ArtifactResolverRequest.builder()
                        .session(session)
                        .repositories(request.getRemoteRepositories().stream()
                                .map(RepositoryUtils::toRepo)
                                .map(session::getRemoteRepository)
                                .toList())
                        .coordinates(List.of(coordinates))
                        .build();
                ArtifactResolverResult res =
                        session.getService(ArtifactResolver.class).resolve(req);
                ArtifactResolverResult.ResultItem resItem = res.getResult(coordinates);

                pomArtifact = InternalMavenSession.from(session).toArtifact(resItem.getArtifact());
                localProject = resItem.getRepository() instanceof org.apache.maven.api.WorkspaceRepository;
            } catch (ArtifactResolverException e) {
                if (e.getResult().getResults().values().iterator().next().isMissing() && allowStubModel) {
                    return build(parent, null, createStubModelSource(artifact));
                }
                throw new ProjectBuildingException(
                        artifact.getId(), "Error resolving project artifact: " + e.getMessage(), e);
            }

            Path pomFile = pomArtifact.getPath();

            if (!artifact.isResolved() && "pom".equals(artifact.getType())) {
                artifact.selectVersion(pomArtifact.getVersion());
                artifact.setFile(pomFile.toFile());
                artifact.setResolved(true);
            }

            if (localProject) {
                return build(parent, pomFile, Sources.buildSource(pomFile));
            } else {
                return build(
                        parent,
                        null,
                        Sources.resolvedSource(
                                pomFile,
                                artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion()));
            }
        }

        List<ProjectBuildingResult> build(List<File> pomFiles, boolean recursive) throws ProjectBuildingException {
            List<ProjectBuildingResult> results = doBuild(pomFiles, recursive);
            if (results.stream()
                    .flatMap(r -> r.getProblems().stream())
                    .anyMatch(p -> p.getSeverity() != org.apache.maven.model.building.ModelProblem.Severity.WARNING)) {
                org.apache.maven.model.building.ModelProblem cycle = results.stream()
                        .flatMap(r -> r.getProblems().stream())
                        .filter(p -> p.getException() instanceof CycleDetectedException)
                        .findAny()
                        .orElse(null);
                if (cycle != null) {
                    throw new RuntimeException(new ProjectCycleException(
                            "The projects in the reactor contain a cyclic reference: " + cycle.getMessage(),
                            (CycleDetectedException) cycle.getException()));
                }
                throw new ProjectBuildingException(results);
            }

            return results;
        }

        List<ProjectBuildingResult> doBuild(List<File> pomFiles, boolean recursive) {
            ClassLoader oldContextClassLoader = Thread.currentThread().getContextClassLoader();
            try {
                return pomFiles.stream()
                        .map(pomFile -> build(pomFile, recursive))
                        .flatMap(List::stream)
                        .collect(Collectors.toList());
            } finally {
                Thread.currentThread().setContextClassLoader(oldContextClassLoader);
            }
        }

        @SuppressWarnings("checkstyle:parameternumber")
        private List<ProjectBuildingResult> build(File pomFile, boolean recursive) {
            ModelBuilderResult result;
            try {
                ModelTransformer injector = (m, r, p) -> {
                    MavenProject project = projectIndex.computeIfAbsent(m.getId(), f -> new MavenProject());
                    return injectLifecycleBindings(m, r, p, project, request);
                };
                ModelBuilderRequest modelBuildingRequest = getModelBuildingRequest()
                        .source(Sources.buildSource(pomFile.toPath()))
                        .requestType(ModelBuilderRequest.RequestType.BUILD_PROJECT)
                        .locationTracking(true)
                        .recursive(recursive)
                        .lifecycleBindingsInjector(injector)
                        .build();
                result = modelBuilderSession.build(modelBuildingRequest);
            } catch (ModelBuilderException e) {
                result = e.getResult();
                if (result == null || result.getEffectiveModel() == null) {
                    return List.of(new DefaultProjectBuildingResult(
                            e.getModelId(), pomFile, convert(e.getProblemCollector())));
                }
            }

            List<ProjectBuildingResult> results = new ArrayList<>();
            List<ModelBuilderResult> allModels = results(result).toList();
            for (ModelBuilderResult r : allModels) {
                if (r.getEffectiveModel() != null) {
                    File pom = r.getSource().getPath().toFile();
                    MavenProject project =
                            projectIndex.get(r.getEffectiveModel().getId());
                    Path rootDirectory =
                            rootLocator.findRoot(pom.getParentFile().toPath());
                    project.setRootDirectory(rootDirectory);
                    project.setFile(pom);
                    project.setExecutionRoot(pom.equals(pomFile));
                    initProject(project, r);
                    project.setCollectedProjects(results(r)
                            .filter(cr -> cr != r && cr.getEffectiveModel() != null)
                            .map(cr -> projectIndex.get(cr.getEffectiveModel().getId()))
                            .collect(Collectors.toList()));

                    DependencyResolutionResult resolutionResult = null;
                    if (request.isResolveDependencies()) {
                        resolutionResult = resolveDependencies(project);
                    }
                    results.add(new DefaultProjectBuildingResult(
                            project, convert(r.getProblemCollector()), resolutionResult));
                } else {
                    results.add(new DefaultProjectBuildingResult(null, convert(r.getProblemCollector()), null));
                }
            }
            return results;
        }

        private Stream<ModelBuilderResult> results(ModelBuilderResult result) {
            return Stream.concat(result.getChildren().stream().flatMap(this::results), Stream.of(result));
        }

        private List<org.apache.maven.model.building.ModelProblem> convert(
                ProblemCollector<ModelProblem> problemCollector) {
            if (problemCollector == null) {
                return null;
            }
            ArrayList<org.apache.maven.model.building.ModelProblem> problems = new ArrayList<>();
            problemCollector.problems().map(BuildSession::convert).forEach(problems::add);
            if (problemCollector.problemsOverflow()) {
                problems.add(
                        0,
                        new DefaultModelProblem(
                                "Too many model problems reported (listed problems are just a subset of reported problems)",
                                org.apache.maven.model.building.ModelProblem.Severity.WARNING,
                                null,
                                null,
                                -1,
                                -1,
                                null,
                                null));
                return new ArrayList<>(problems) {
                    @Override
                    public int size() {
                        return problemCollector.totalProblemsReported();
                    }
                };
            } else {
                return problems;
            }
        }

        private static org.apache.maven.model.building.ModelProblem convert(ModelProblem p) {
            return new DefaultModelProblem(
                    p.getMessage(),
                    org.apache.maven.model.building.ModelProblem.Severity.valueOf(
                            p.getSeverity().name()),
                    org.apache.maven.model.building.ModelProblem.Version.valueOf(
                            p.getVersion().name()),
                    p.getSource(),
                    p.getLineNumber(),
                    p.getColumnNumber(),
                    p.getModelId(),
                    p.getException());
        }

        @SuppressWarnings({"checkstyle:methodlength", "deprecation"})
        private void initProject(MavenProject project, ModelBuilderResult result) {
            project.setModel(new org.apache.maven.model.Model(result.getEffectiveModel()));
            project.setOriginalModel(new org.apache.maven.model.Model(result.getFileModel()));

            initParent(project, result);

            Artifact projectArtifact = repositorySystem.createArtifact(
                    project.getGroupId(), project.getArtifactId(), project.getVersion(), null, project.getPackaging());
            project.setArtifact(projectArtifact);

            // only set those on 2nd phase, ignore on 1st pass
            if (project.getFile() != null) {
                Build build = project.getBuild().getDelegate();
                List<org.apache.maven.api.model.Source> sources = build.getSources();
                Path baseDir = project.getBaseDirectory();
                boolean hasScript = false;
                boolean hasMain = false;
                boolean hasTest = false;
                for (var source : sources) {
                    var src = new DefaultSourceRoot(session, baseDir, source);
                    project.addSourceRoot(src);
                    Language language = src.language();
                    if (Language.JAVA_FAMILY.equals(language)) {
                        ProjectScope scope = src.scope();
                        if (ProjectScope.MAIN.equals(scope)) {
                            hasMain = true;
                        } else {
                            hasTest |= ProjectScope.TEST.equals(scope);
                        }
                    } else {
                        hasScript |= Language.SCRIPT.equals(language);
                    }
                }
                /*
                 * `sourceDirectory`, `testSourceDirectory` and `scriptSourceDirectory`
                 * are ignored if the POM file contains at least one <source> element
                 * for the corresponding scope and language. This rule exists because
                 * Maven provides default values for those elements which may conflict
                 * with user's configuration.
                 */
                if (!hasScript) {
                    project.addScriptSourceRoot(build.getScriptSourceDirectory());
                }
                if (!hasMain) {
                    project.addCompileSourceRoot(build.getSourceDirectory());
                }
                if (!hasTest) {
                    project.addTestCompileSourceRoot(build.getTestSourceDirectory());
                }
                for (Resource resource : project.getBuild().getDelegate().getResources()) {
                    project.addSourceRoot(new DefaultSourceRoot(baseDir, ProjectScope.MAIN, resource));
                }
                for (Resource resource : project.getBuild().getDelegate().getTestResources()) {
                    project.addSourceRoot(new DefaultSourceRoot(baseDir, ProjectScope.TEST, resource));
                }
            }

            project.setActiveProfiles(
                    Stream.concat(result.getActivePomProfiles().stream(), result.getActiveExternalProfiles().stream())
                            .map(org.apache.maven.model.Profile::new)
                            .toList());

            project.setInjectedProfileIds("external", getProfileIds(result.getActiveExternalProfiles()));
            project.setInjectedProfileIds(
                    result.getEffectiveModel().getId(), getProfileIds(result.getActivePomProfiles()));

            //
            // All the parts that were taken out of MavenProject for Maven 4.0.0
            //

            project.setProjectBuildingRequest(request);

            // pluginArtifacts
            Set<Artifact> pluginArtifacts = new HashSet<>();
            for (Plugin plugin : project.getModel().getDelegate().getBuild().getPlugins()) {
                Artifact artifact = repositorySystem.createPluginArtifact(new org.apache.maven.model.Plugin(plugin));

                if (artifact != null) {
                    pluginArtifacts.add(artifact);
                }
            }
            project.setPluginArtifacts(pluginArtifacts);

            // reportArtifacts
            Set<Artifact> reportArtifacts = new HashSet<>();
            for (ReportPlugin report :
                    project.getModel().getDelegate().getReporting().getPlugins()) {
                Plugin pp = Plugin.newBuilder()
                        .groupId(report.getGroupId())
                        .artifactId(report.getArtifactId())
                        .version(report.getVersion())
                        .build();

                Artifact artifact = repositorySystem.createPluginArtifact(new org.apache.maven.model.Plugin(pp));

                if (artifact != null) {
                    reportArtifacts.add(artifact);
                }
            }
            project.setReportArtifacts(reportArtifacts);

            // extensionArtifacts
            Set<Artifact> extensionArtifacts = new HashSet<>();
            List<Extension> extensions =
                    project.getModel().getDelegate().getBuild().getExtensions();
            if (extensions != null) {
                for (Extension ext : extensions) {
                    String version;
                    if (ext.getVersion() == null || ext.getVersion().isEmpty()) {
                        version = "RELEASE";
                    } else {
                        version = ext.getVersion();
                    }

                    Artifact artifact = repositorySystem.createArtifact(
                            ext.getGroupId(), ext.getArtifactId(), version, null, "jar");

                    if (artifact != null) {
                        extensionArtifacts.add(artifact);
                    }
                }
            }
            project.setExtensionArtifacts(extensionArtifacts);

            // managedVersionMap
            Map<String, Artifact> map = Collections.emptyMap();
            final DependencyManagement dependencyManagement =
                    project.getModel().getDelegate().getDependencyManagement();
            if (dependencyManagement != null
                    && dependencyManagement.getDependencies() != null
                    && !dependencyManagement.getDependencies().isEmpty()) {
                map = new LazyMap<>(() -> {
                    Map<String, Artifact> tmp = new HashMap<>();
                    for (Dependency d : dependencyManagement.getDependencies()) {
                        Artifact artifact =
                                repositorySystem.createDependencyArtifact(new org.apache.maven.model.Dependency(d));
                        if (artifact != null) {
                            tmp.put(d.getManagementKey(), artifact);
                        }
                    }
                    return Collections.unmodifiableMap(tmp);
                });
            }
            project.setManagedVersionMap(map);

            // release artifact repository
            if (project.getDistributionManagement() != null
                    && project.getDistributionManagement().getRepository() != null) {
                try {
                    DeploymentRepository r = project.getModel()
                            .getDelegate()
                            .getDistributionManagement()
                            .getRepository();
                    if (r.getId() != null
                            && !r.getId().isEmpty()
                            && r.getUrl() != null
                            && !r.getUrl().isEmpty()) {
                        ArtifactRepository repo = MavenRepositorySystem.buildArtifactRepository(
                                new org.apache.maven.model.DeploymentRepository(r));
                        repositorySystem.injectProxy(request.getRepositorySession(), List.of(repo));
                        repositorySystem.injectAuthentication(request.getRepositorySession(), List.of(repo));
                        project.setReleaseArtifactRepository(repo);
                    }
                } catch (InvalidRepositoryException e) {
                    throw new IllegalStateException(
                            "Failed to create release distribution repository for " + project.getId(), e);
                }
            }

            // snapshot artifact repository
            if (project.getDistributionManagement() != null
                    && project.getDistributionManagement().getSnapshotRepository() != null) {
                try {
                    DeploymentRepository r = project.getModel()
                            .getDelegate()
                            .getDistributionManagement()
                            .getSnapshotRepository();
                    if (r.getId() != null
                            && !r.getId().isEmpty()
                            && r.getUrl() != null
                            && !r.getUrl().isEmpty()) {
                        ArtifactRepository repo = MavenRepositorySystem.buildArtifactRepository(
                                new org.apache.maven.model.DeploymentRepository(r));
                        repositorySystem.injectProxy(request.getRepositorySession(), List.of(repo));
                        repositorySystem.injectAuthentication(request.getRepositorySession(), List.of(repo));
                        project.setSnapshotArtifactRepository(repo);
                    }
                } catch (InvalidRepositoryException e) {
                    throw new IllegalStateException(
                            "Failed to create snapshot distribution repository for " + project.getId(), e);
                }
            }

            // remote repositories
            List<ArtifactRepository> remoteRepositories = request.getRemoteRepositories();
            try {
                remoteRepositories = projectBuildingHelper.createArtifactRepositories(
                        project.getModel().getRepositories(), remoteRepositories, request);
            } catch (Exception e) {
                result.getProblemCollector()
                        .reportProblem(new org.apache.maven.impl.model.DefaultModelProblem(
                                "",
                                Severity.ERROR,
                                Version.BASE,
                                project.getModel().getDelegate(),
                                -1,
                                -1,
                                e));
            }
            project.setRemoteArtifactRepositories(remoteRepositories);
        }

        private void initParent(MavenProject project, ModelBuilderResult result) {
            Model parentModel = result.getParentModel();

            if (parentModel != null) {
                final String parentGroupId = getGroupId(parentModel);
                final String parentVersion = getVersion(parentModel);

                project.setParentArtifact(repositorySystem.createProjectArtifact(
                        parentGroupId, parentModel.getArtifactId(), parentVersion));

                MavenProject parent = projectIndex.get(parentModel.getId());
                if (parent == null) {
                    //
                    // At this point the DefaultModelBuildingListener has fired, and it populates the
                    // remote repositories with those found in the pom.xml, along with the existing externally
                    // defined repositories.
                    //
                    request.getRemoteRepositories().addAll(project.getRemoteArtifactRepositories());
                    Path parentPomFile = parentModel.getPomFile();
                    if (parentPomFile != null) {
                        project.setParentFile(parentPomFile.toFile());
                        try {
                            parent = build(true, parentPomFile, Sources.buildSource(parentPomFile))
                                    .getProject();
                        } catch (ProjectBuildingException e) {
                            // MNG-4488 where let invalid parents slide on by
                            if (logger.isDebugEnabled()) {
                                // Message below is checked for in the MNG-2199 core IT.
                                logger.warn("Failed to build parent project for " + project.getId(), e);
                            } else {
                                // Message below is checked for in the MNG-2199 core IT.
                                logger.warn("Failed to build parent project for " + project.getId());
                            }
                        }
                    } else {
                        Artifact parentArtifact = project.getParentArtifact();
                        try {
                            parent = build(true, parentArtifact, false).getProject();
                        } catch (ProjectBuildingException e) {
                            // MNG-4488 where let invalid parents slide on by
                            if (logger.isDebugEnabled()) {
                                // Message below is checked for in the MNG-2199 core IT.
                                logger.warn("Failed to build parent project for " + project.getId(), e);
                            } else {
                                // Message below is checked for in the MNG-2199 core IT.
                                logger.warn("Failed to build parent project for " + project.getId());
                            }
                        }
                    }
                }
                project.setParent(parent);
                if (project.getParentFile() == null && parent != null) {
                    project.setParentFile(parent.getFile());
                }
            }
        }

        private ModelBuilderRequest.ModelBuilderRequestBuilder getModelBuildingRequest() {
            ModelBuilderRequest.ModelBuilderRequestBuilder modelBuildingRequest = ModelBuilderRequest.builder();

            modelBuildingRequest.session(session);
            modelBuildingRequest.requestType(ModelBuilderRequest.RequestType.BUILD_PROJECT);
            modelBuildingRequest.profiles(
                    request.getProfiles() != null
                            ? request.getProfiles().stream()
                                    .map(org.apache.maven.model.Profile::getDelegate)
                                    .toList()
                            : null);
            modelBuildingRequest.activeProfileIds(request.getActiveProfileIds());
            modelBuildingRequest.inactiveProfileIds(request.getInactiveProfileIds());
            modelBuildingRequest.systemProperties(toMap(request.getSystemProperties()));
            modelBuildingRequest.userProperties(toMap(request.getUserProperties()));
            modelBuildingRequest.repositoryMerging(ModelBuilderRequest.RepositoryMerging.valueOf(
                    request.getRepositoryMerging().name()));
            modelBuildingRequest.repositories(request.getRemoteRepositories().stream()
                    .map(r -> session.getRemoteRepository(RepositoryUtils.toRepo(r)))
                    .toList());
            return modelBuildingRequest;
        }

        private DependencyResolutionResult resolveDependencies(MavenProject project) {
            DependencyResolutionResult resolutionResult;

            RepositorySystemSession session = this.session.getSession();
            try {
                DefaultDependencyResolutionRequest resolution =
                        new DefaultDependencyResolutionRequest(project, session);
                resolutionResult = dependencyResolver.resolve(resolution);
            } catch (DependencyResolutionException e) {
                resolutionResult = e.getResult();
            }

            Set<Artifact> artifacts = new LinkedHashSet<>();
            if (resolutionResult.getDependencyGraph() != null) {
                RepositoryUtils.toArtifacts(
                        artifacts,
                        resolutionResult.getDependencyGraph().getChildren(),
                        Collections.singletonList(project.getArtifact().getId()),
                        null);

                // Maven 2.x quirk: an artifact always points at the local repo, regardless whether resolved or not
                LocalRepositoryManager lrm = session.getLocalRepositoryManager();
                for (Artifact artifact : artifacts) {
                    if (!artifact.isResolved()) {
                        Path path = lrm.getAbsolutePathForLocalArtifact(RepositoryUtils.toArtifact(artifact));
                        artifact.setFile(path.toFile());
                    }
                }
            }
            project.setResolvedArtifacts(artifacts);
            project.setArtifacts(artifacts);

            return resolutionResult;
        }
    }

    private List<String> getProfileIds(List<Profile> profiles) {
        return profiles.stream().map(Profile::getId).collect(Collectors.toList());
    }

    private static ModelSource createStubModelSource(Artifact artifact) {
        String xml = "<?xml version='1.0'?>" + "<project>"
                + "<modelVersion>4.0.0</modelVersion>"
                + "<groupId>"
                + artifact.getGroupId() + "</groupId>" + "<artifactId>"
                + artifact.getArtifactId() + "</artifactId>" + "<version>"
                + artifact.getBaseVersion() + "</version>" + "<packaging>"
                + artifact.getType() + "</packaging>" + "</project>";
        return new StubModelSource(xml, artifact);
    }

    static String getGroupId(Model model) {
        String groupId = model.getGroupId();
        if (groupId == null && model.getParent() != null) {
            groupId = model.getParent().getGroupId();
        }
        return groupId;
    }

    static String getVersion(Model model) {
        String version = model.getVersion();
        if (version == null && model.getParent() != null) {
            version = model.getParent().getVersion();
        }
        return version;
    }

    private static Map<String, String> toMap(Properties properties) {
        if (properties != null && !properties.isEmpty()) {
            return properties.entrySet().stream()
                    .collect(Collectors.toMap(e -> String.valueOf(e.getKey()), e -> String.valueOf(e.getValue())));
        } else {
            return null;
        }
    }

    static class LazyMap<K, V> extends AbstractMap<K, V> {
        private final Supplier<Map<K, V>> supplier;
        private volatile Map<K, V> delegate;

        LazyMap(Supplier<Map<K, V>> supplier) {
            this.supplier = supplier;
        }

        @Override
        public Set<Entry<K, V>> entrySet() {
            if (delegate == null) {
                synchronized (this) {
                    if (delegate == null) {
                        delegate = supplier.get();
                    }
                }
            }
            return delegate.entrySet();
        }
    }

    private Model injectLifecycleBindings(
            Model model,
            ModelBuilderRequest request,
            ModelProblemCollector problems,
            MavenProject project,
            ProjectBuildingRequest projectBuildingRequest) {
        org.apache.maven.model.Model model3 = new org.apache.maven.model.Model(model);
        List<ArtifactRepository> remoteRepositories = projectBuildingRequest.getRemoteRepositories();
        List<ArtifactRepository> pluginRepositories = projectBuildingRequest.getPluginArtifactRepositories();
        try {
            pluginRepositories = projectBuildingHelper.createArtifactRepositories(
                    model3.getPluginRepositories(), pluginRepositories, projectBuildingRequest);
        } catch (Exception e) {
            problems.add(Severity.ERROR, Version.BASE, "Invalid plugin repository: " + e.getMessage(), e);
        }
        project.setPluginArtifactRepositories(pluginRepositories);

        if (request.getRequestType() == ModelBuilderRequest.RequestType.BUILD_PROJECT) {
            try {
                ProjectRealmCache.CacheRecord record =
                        projectBuildingHelper.createProjectRealm(project, model3, projectBuildingRequest);

                project.setClassRealm(record.getRealm());
                project.setExtensionDependencyFilter(record.getExtensionArtifactFilter());
            } catch (PluginResolutionException | PluginManagerException | PluginVersionResolutionException e) {

                problems.add(Severity.ERROR, Version.BASE, "Unresolvable build extension: " + e.getMessage(), e);
            }
            projectBuildingHelper.selectProjectRealm(project);
        }

        // (re)build the regular repos after extensions are loaded to allow for custom layouts
        try {
            remoteRepositories = projectBuildingHelper.createArtifactRepositories(
                    model3.getRepositories(), remoteRepositories, projectBuildingRequest);
        } catch (Exception e) {
            problems.add(Severity.ERROR, Version.BASE, "Invalid artifact repository: " + e.getMessage(), e);
        }
        project.setRemoteArtifactRepositories(remoteRepositories);

        if (projectBuildingRequest.isProcessPlugins()) {
            return lifecycleBindingsInjector.injectLifecycleBindings(model3.getDelegate(), request, problems);
        } else {
            return model3.getDelegate();
        }
    }
}
