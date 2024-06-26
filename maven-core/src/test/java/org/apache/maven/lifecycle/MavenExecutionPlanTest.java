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
package org.apache.maven.lifecycle;

import java.util.Set;

import org.apache.maven.lifecycle.internal.ExecutionPlanItem;
import org.apache.maven.lifecycle.internal.stub.LifecycleExecutionPlanCalculatorStub;
import org.apache.maven.model.Plugin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 */
class MavenExecutionPlanTest {

    @Test
    void testFindLastInPhase() throws Exception {
        MavenExecutionPlan plan = LifecycleExecutionPlanCalculatorStub.getProjectAExecutionPlan();

        ExecutionPlanItem expected = plan.findLastInPhase("package");
        assertNotNull(expected);
    }

    @Test
    void testThreadSafeMojos() throws Exception {
        MavenExecutionPlan plan = LifecycleExecutionPlanCalculatorStub.getProjectAExecutionPlan();
        final Set<Plugin> unSafePlugins = plan.getNonThreadSafePlugins();
        // There is only a single threadsafe plugin here...
        assertEquals(plan.size() - 1, unSafePlugins.size());
    }

    @Test
    void testFindLastWhenFirst() throws Exception {
        MavenExecutionPlan plan = LifecycleExecutionPlanCalculatorStub.getProjectAExecutionPlan();

        ExecutionPlanItem beerPhase = plan.findLastInPhase(
                LifecycleExecutionPlanCalculatorStub.VALIDATE.getPhase()); // Beer comes straight after package in stub
        assertNull(beerPhase);
    }

    @Test
    void testFindLastInPhaseMisc() throws Exception {
        MavenExecutionPlan plan = LifecycleExecutionPlanCalculatorStub.getProjectAExecutionPlan();

        assertNull(plan.findLastInPhase("pacXkage"));
        // Beer comes straight after package in stub, much like real life.
        assertNotNull(plan.findLastInPhase(LifecycleExecutionPlanCalculatorStub.INITIALIZE.getPhase()));
    }
}
