package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** web_search(query) must preserve the query through a typed Android search adapter. */
public final class AndroidWebSearchToolBindingContractTest {
    public static void main(String[] args) throws Exception {
        String registry = Files.readString(Path.of("src/main/java/com/jarvis/brain/ToolRegistry.java"));
        String factory = Files.readString(Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidToolRegistryFactory.java"));
        Path actionPath = Path.of("../android/app/src/main/java/com/jarvis/mobile/actions/AndroidWebSearchActions.java");
        check(registry.contains("r.register(spec(\"web_search\""), "shared brain registry must expose web_search");
        check(Files.exists(actionPath), "Android production must provide a typed web-search adapter");
        String action = Files.readString(actionPath);
        check(factory.contains("args -> web.search(args.get(\"query\"))"), "Android registry must bind web_search to typed query data");
        check(action.contains("Intent.ACTION_WEB_SEARCH"), "typed search must prefer Android's web-search intent");
        check(action.contains("SearchManager.QUERY, clean"), "typed search must preserve the exact query in structured intent data");
        check(action.contains("https://duckduckgo.com/?q="), "typed search must have a browser fallback without a paid dependency");
        check(action.contains("Tell me what you want me to search for."), "blank search queries must fail closed");
        System.out.println("AndroidWebSearchToolBindingContractTest passed");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
