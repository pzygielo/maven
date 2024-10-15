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
package org.apache.maven.cling;

import java.io.IOException;

import org.apache.maven.api.cli.Invoker;
import org.apache.maven.api.cli.ParserException;
import org.apache.maven.api.cli.ParserRequest;
import org.apache.maven.api.cli.mvnenc.EncryptInvokerRequest;
import org.apache.maven.api.cli.mvnenc.EncryptOptions;
import org.apache.maven.cling.invoker.ProtoLogger;
import org.apache.maven.cling.invoker.ProtoLookup;
import org.apache.maven.cling.invoker.mvnenc.DefaultEncryptInvoker;
import org.apache.maven.cling.invoker.mvnenc.DefaultEncryptParser;
import org.apache.maven.jline.JLineMessageBuilderFactory;
import org.apache.maven.jline.MessageUtils;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

/**
 * Maven encrypt CLI "new-gen".
 */
public class MavenEncCling extends ClingSupport<EncryptOptions, EncryptInvokerRequest> {
    /**
     * "Normal" Java entry point. Note: Maven uses ClassWorld Launcher and this entry point is NOT used under normal
     * circumstances.
     */
    public static void main(String[] args) throws IOException {
        int exitCode = new MavenEncCling().run(args);
        System.exit(exitCode);
    }

    /**
     * ClassWorld Launcher "enhanced" entry point: returning exitCode and accepts Class World.
     */
    public static int main(String[] args, ClassWorld world) throws IOException {
        return new MavenEncCling(world).run(args);
    }

    private Terminal terminal;

    public MavenEncCling() {
        super();
    }

    public MavenEncCling(ClassWorld classWorld) {
        super(classWorld);
    }

    @Override
    public int run(String[] args) throws IOException {
        terminal = TerminalBuilder.builder().build();
        MessageUtils.systemInstall(terminal);
        MessageUtils.registerShutdownHook();
        try {
            return super.run(args);
        } finally {
            MessageUtils.systemUninstall();
        }
    }

    @Override
    protected Invoker<EncryptInvokerRequest> createInvoker() {
        return new DefaultEncryptInvoker(ProtoLookup.builder()
                .addMapping(ClassWorld.class, classWorld)
                .addMapping(Terminal.class, terminal)
                .build());
    }

    @Override
    protected EncryptInvokerRequest parseArguments(String[] args) throws ParserException, IOException {
        return new DefaultEncryptParser()
                .parse(ParserRequest.mvnenc(args, new ProtoLogger(), new JLineMessageBuilderFactory())
                        .build());
    }
}