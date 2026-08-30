package com.jarvis.brain;

/** Installed-app resolution/launch boundary. Android adapter should prefer normal intent resolution over broad package visibility. */
@FunctionalInterface
public interface AppLauncherPort {
    ToolResult launch(String appName);
}
