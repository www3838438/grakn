/*
 * Grakn - A Distributed Semantic Database
 * Copyright (C) 2016-2018 Grakn Labs Limited
 *
 * Grakn is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Grakn is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Grakn. If not, see <http://www.gnu.org/licenses/gpl.txt>.
 */

package ai.grakn.graql.internal.analytics;

import ai.grakn.concept.ConceptId;
import ai.grakn.exception.GraqlQueryException;
import ai.grakn.util.Schema;
import org.apache.tinkerpop.gremlin.process.computer.Memory;
import org.apache.tinkerpop.gremlin.process.computer.MemoryComputeKey;
import org.apache.tinkerpop.gremlin.process.computer.Messenger;
import org.apache.tinkerpop.gremlin.process.computer.VertexComputeKey;
import org.apache.tinkerpop.gremlin.process.traversal.Operator;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import java.util.Collections;
import java.util.Set;

import static ai.grakn.graql.internal.analytics.ConnectedComponentsVertexProgram.CLUSTER_LABEL;

/**
 * The vertex program for computing connected components of a give instance.
 *
 * @author Jason Liu
 */

public class ConnectedComponentVertexProgram extends GraknVertexProgram<Boolean> {

    private static final int MAX_ITERATION = 100;

    private static final String VOTE_TO_HALT = "connectedComponentVertexProgram.voteToHalt";
    private static final String SOURCE = "connectedComponentVertexProgram.source";

    private static final Boolean MESSAGE = true; // just a random message

    private static final Set<MemoryComputeKey> MEMORY_COMPUTE_KEYS =
            Collections.singleton(MemoryComputeKey.of(VOTE_TO_HALT, Operator.and, false, true));

    // for internal use
    public ConnectedComponentVertexProgram() {
    }

    public ConnectedComponentVertexProgram(ConceptId sourceId) {
        this.persistentProperties.put(SOURCE, sourceId.getValue());
    }

    @Override
    public Set<VertexComputeKey> getVertexComputeKeys() {
        return Collections.singleton(VertexComputeKey.of(CLUSTER_LABEL, false));
    }

    @Override
    public Set<MemoryComputeKey> getMemoryComputeKeys() {
        return MEMORY_COMPUTE_KEYS;
    }

    @Override
    public void setup(final Memory memory) {
        LOGGER.debug("ConnectedComponentVertexProgram Started !!!!!!!!");
        memory.set(VOTE_TO_HALT, true);
    }

    @Override
    public void safeExecute(final Vertex vertex, Messenger<Boolean> messenger, final Memory memory) {
        if (memory.isInitialIteration()) {
            if (vertex.<String>value(Schema.VertexProperty.ID.name()).equals(persistentProperties.get(SOURCE))) {
                update(vertex, messenger, memory, (String) persistentProperties.get(SOURCE));
            }
        } else {
            if (messenger.receiveMessages().hasNext() && !vertex.property(CLUSTER_LABEL).isPresent()) {
                update(vertex, messenger, memory, (String) persistentProperties.get(SOURCE));
            }
        }
    }

    private static void update(Vertex vertex, Messenger<Boolean> messenger, Memory memory, String label) {
        messenger.sendMessage(messageScopeIn, MESSAGE);
        messenger.sendMessage(messageScopeOut, MESSAGE);
        vertex.property(CLUSTER_LABEL, label);
        memory.add(VOTE_TO_HALT, false);
    }

    @Override
    public boolean terminate(final Memory memory) {
        LOGGER.debug("Finished Iteration " + memory.getIteration());
        if (memory.isInitialIteration()) return false;
        if (memory.<Boolean>get(VOTE_TO_HALT)) {
            return true;
        }
        if (memory.getIteration() == MAX_ITERATION) {
            LOGGER.debug("Reached Max Iteration: " + MAX_ITERATION + " !!!!!!!!");
            throw GraqlQueryException.maxIterationsReached(this.getClass());
        }

        memory.set(VOTE_TO_HALT, true);
        return false;
    }

}