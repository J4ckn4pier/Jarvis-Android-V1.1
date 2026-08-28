package com.jarvis.brain;
/** Platform adapter for beginning/ending native binding or web OAuth. No credentials cross this core interface. */
public interface ConnectionPort {
 ConnectionType type();
 ToolResult beginConnect(String connectionId);
 ToolResult disconnect(String connectionId);
}
