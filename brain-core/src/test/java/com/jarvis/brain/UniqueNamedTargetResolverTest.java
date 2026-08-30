package com.jarvis.brain;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

/** Executable ambiguity fixtures for any human-name -> consequential-action target lookup. */
public final class UniqueNamedTargetResolverTest {
    public static void main(String[] args) throws Exception {
        Class<?> resolver;
        Class<?> candidate;
        try {
            resolver = Class.forName("com.jarvis.brain.UniqueNamedTargetResolver");
            candidate = Class.forName("com.jarvis.brain.UniqueNamedTargetResolver$Candidate");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("shared exact-unique named-target resolver must exist", missing);
        }

        Constructor<?> candidateCtor = candidate.getConstructor(String.class, String.class);
        Method resolve = resolver.getMethod("resolve", String.class, List.class);

        check(resolve(resolve, candidateCtor, "Mom", List.of(
                row(candidateCtor, "Mom", "mom@example.com"),
                row(candidateCtor, "Mom Work", "other@example.com")))
                        .equals(Optional.of("mom@example.com")),
                "one exact display-name target must resolve even when partial-name rows exist");

        check(resolve(resolve, candidateCtor, "Mom", List.of(
                row(candidateCtor, "Mom Work", "work@example.com"),
                row(candidateCtor, "Mom Cell", "cell@example.com")))
                        .isEmpty(),
                "partial-name rows alone must never resolve a consequential target");

        check(resolve(resolve, candidateCtor, "Alex", List.of(
                row(candidateCtor, "Alex", "alex.one@example.com"),
                row(candidateCtor, "Alex", "alex.two@example.com")))
                        .isEmpty(),
                "two contacts sharing one exact display name with different targets must fail closed");

        check(resolve(resolve, candidateCtor, "Alex", List.of(
                row(candidateCtor, "Alex", "+13035550123"),
                row(candidateCtor, "Alex", "+13035550123")))
                        .equals(Optional.of("+13035550123")),
                "duplicate provider rows for the same exact target must dedupe rather than create false ambiguity");

        check(resolve(resolve, candidateCtor, "MOM", List.of(
                row(candidateCtor, "mom", "mom@example.com")))
                        .equals(Optional.of("mom@example.com")),
                "exact display-name matching must remain case-insensitive");

        check(resolve(resolve, candidateCtor, "Mom", List.of(
                row(candidateCtor, "Mom", " "),
                row(candidateCtor, null, "bad@example.com"),
                row(candidateCtor, "Mom", null)))
                        .isEmpty(),
                "blank targets and nameless rows must not resolve");

        System.out.println("UniqueNamedTargetResolverTest: PASS");
    }

    private static Object row(Constructor<?> ctor, String name, String target) throws Exception {
        return ctor.newInstance(name, target);
    }

    @SuppressWarnings("unchecked")
    private static Optional<String> resolve(Method method, Constructor<?> ctor, String requested, List<Object> rows)
            throws Exception {
        Object result = method.invoke(null, requested, rows);
        if (!(result instanceof Optional<?> optional)) {
            throw new AssertionError("resolver must return Optional<String>");
        }
        return (Optional<String>) optional;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
