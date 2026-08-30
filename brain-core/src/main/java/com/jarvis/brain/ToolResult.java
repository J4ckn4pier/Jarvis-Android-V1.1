package com.jarvis.brain;

public record ToolResult(Status status, String output) {
    public enum Status { SUCCESS, RETRYABLE_FAILURE, FAILURE }
    public static ToolResult success(String output) { return new ToolResult(Status.SUCCESS, output); }
    public static ToolResult retryableFailure(String output) { return new ToolResult(Status.RETRYABLE_FAILURE, output); }
    public static ToolResult failure(String output) { return new ToolResult(Status.FAILURE, output); }
}
