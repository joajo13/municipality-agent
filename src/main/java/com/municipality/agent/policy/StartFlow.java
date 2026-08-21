package com.municipality.agent.policy;

import com.municipality.agent.router.Action;
import com.municipality.agent.router.Domain;
import com.municipality.agent.router.EntityType;

import java.util.Map;

/**
 * Everything the procedure needs is there. Run it.
 *
 * <p>This says which procedure and with what, not how to carry it out. Filing a
 * complaint against the municipality's systems is somebody else's job — and plain code,
 * not a model: the number a resident is given has to be the number that was really
 * created.
 *
 * @param entities what is known about the resident, ready for whoever runs this
 */
public record StartFlow(Domain domain, Action action, Map<EntityType, String> entities) implements Decision {

    public StartFlow {
        if (domain == null) throw new IllegalArgumentException("domain is required");
        if (action == null) throw new IllegalArgumentException("action is required");
        if (entities == null) throw new IllegalArgumentException("entities is required");

        entities = Map.copyOf(entities);
    }
}
