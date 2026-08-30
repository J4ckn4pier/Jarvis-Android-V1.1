package com.jarvis.brain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Fail-closed resolver for consequential actions addressed by a human display name.
 * Only one exact case-insensitive display name with one unique non-blank target resolves.
 */
public final class UniqueNamedTargetResolver {
    private UniqueNamedTargetResolver() { }

    public record Candidate(String displayName, String target) { }

    public static Optional<String> resolve(String requestedName, List<Candidate> candidates) {
        if (requestedName == null || requestedName.isBlank() || candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }

        String requested = requestedName.trim();
        Set<String> exactTargets = new LinkedHashSet<>();

        for (Candidate candidate : candidates) {
            if (candidate == null || candidate.displayName() == null || candidate.target() == null) continue;
            String displayName = candidate.displayName().trim();
            String target = candidate.target().trim();
            if (displayName.isEmpty() || target.isEmpty()) continue;
            if (!displayName.equalsIgnoreCase(requested)) continue;

            exactTargets.add(target);
            if (exactTargets.size() > 1) return Optional.empty();
        }

        return exactTargets.stream().findFirst();
    }
}
