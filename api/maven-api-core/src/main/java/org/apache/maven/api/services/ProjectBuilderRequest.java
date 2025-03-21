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
package org.apache.maven.api.services;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.maven.api.RemoteRepository;
import org.apache.maven.api.Session;
import org.apache.maven.api.annotations.Experimental;
import org.apache.maven.api.annotations.Immutable;
import org.apache.maven.api.annotations.Nonnull;
import org.apache.maven.api.annotations.NotThreadSafe;
import org.apache.maven.api.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Request used to build a {@link org.apache.maven.api.Project} using
 * the {@link ProjectBuilder} service.
 *
 * TODO: add validationLevel, activeProfileIds, inactiveProfileIds, resolveDependencies
 *
 * @since 4.0.0
 */
@Experimental
@Immutable
public interface ProjectBuilderRequest extends Request<Session> {

    @Nonnull
    Optional<Path> getPath();

    @Nonnull
    Optional<Source> getSource();

    boolean isAllowStubModel();

    boolean isRecursive();

    boolean isProcessPlugins();

    @Nullable
    List<RemoteRepository> getRepositories();

    @Nonnull
    static ProjectBuilderRequest build(@Nonnull Session session, @Nonnull Source source) {
        return builder()
                .session(requireNonNull(session, "session cannot be null"))
                .source(requireNonNull(source, "source cannot be null"))
                .build();
    }

    @Nonnull
    static ProjectBuilderRequest build(@Nonnull Session session, @Nonnull Path path) {
        return builder()
                .session(requireNonNull(session, "session cannot be null"))
                .path(requireNonNull(path, "path cannot be null"))
                .build();
    }

    @Nonnull
    static ProjectBuilderRequestBuilder builder() {
        return new ProjectBuilderRequestBuilder();
    }

    @NotThreadSafe
    class ProjectBuilderRequestBuilder {
        Session session;
        RequestTrace trace;
        Path path;
        Source source;
        boolean allowStubModel;
        boolean recursive;
        boolean processPlugins = true;
        List<RemoteRepository> repositories;

        ProjectBuilderRequestBuilder() {}

        public ProjectBuilderRequestBuilder session(Session session) {
            this.session = session;
            return this;
        }

        public ProjectBuilderRequestBuilder trace(RequestTrace trace) {
            this.trace = trace;
            return this;
        }

        public ProjectBuilderRequestBuilder path(Path path) {
            this.path = path;
            return this;
        }

        public ProjectBuilderRequestBuilder source(Source source) {
            this.source = source;
            return this;
        }

        public ProjectBuilderRequestBuilder processPlugins(boolean processPlugins) {
            this.processPlugins = processPlugins;
            return this;
        }

        public ProjectBuilderRequestBuilder repositories(List<RemoteRepository> repositories) {
            this.repositories = repositories;
            return this;
        }

        public ProjectBuilderRequest build() {
            return new DefaultProjectBuilderRequest(
                    session, trace, path, source, allowStubModel, recursive, processPlugins, repositories);
        }

        private static class DefaultProjectBuilderRequest extends BaseRequest<Session>
                implements ProjectBuilderRequest {
            private final Path path;
            private final Source source;
            private final boolean allowStubModel;
            private final boolean recursive;
            private final boolean processPlugins;
            private final List<RemoteRepository> repositories;

            @SuppressWarnings("checkstyle:ParameterNumber")
            DefaultProjectBuilderRequest(
                    @Nonnull Session session,
                    @Nullable RequestTrace trace,
                    @Nullable Path path,
                    @Nullable Source source,
                    boolean allowStubModel,
                    boolean recursive,
                    boolean processPlugins,
                    @Nullable List<RemoteRepository> repositories) {
                super(session, trace);
                this.path = path;
                this.source = source;
                this.allowStubModel = allowStubModel;
                this.recursive = recursive;
                this.processPlugins = processPlugins;
                this.repositories = repositories;
            }

            @Nonnull
            @Override
            public Optional<Path> getPath() {
                return Optional.ofNullable(path);
            }

            @Nonnull
            @Override
            public Optional<Source> getSource() {
                return Optional.ofNullable(source);
            }

            @Override
            public boolean isAllowStubModel() {
                return allowStubModel;
            }

            @Override
            public boolean isRecursive() {
                return recursive;
            }

            @Override
            public boolean isProcessPlugins() {
                return processPlugins;
            }

            @Override
            public List<RemoteRepository> getRepositories() {
                return repositories;
            }

            @Override
            public boolean equals(Object o) {
                return o instanceof DefaultProjectBuilderRequest that
                        && allowStubModel == that.allowStubModel
                        && recursive == that.recursive
                        && processPlugins == that.processPlugins
                        && Objects.equals(path, that.path)
                        && Objects.equals(source, that.source)
                        && Objects.equals(repositories, that.repositories);
            }

            @Override
            public int hashCode() {
                return Objects.hash(path, source, allowStubModel, recursive, processPlugins, repositories);
            }

            @Override
            public String toString() {
                return "ProjectBuilderRequest[" + "path="
                        + path + ", source="
                        + source + ", allowStubModel="
                        + allowStubModel + ", recursive="
                        + recursive + ", processPlugins="
                        + processPlugins + ", repositories="
                        + repositories + ']';
            }
        }
    }
}
