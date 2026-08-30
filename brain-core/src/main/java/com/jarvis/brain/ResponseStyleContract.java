package com.jarvis.brain;

/** Single beta response-style contract shared by every reasoning provider. */
public record ResponseStyleContract(String guidance) {
    private static final ResponseStyleContract BETA = new ResponseStyleContract(
            "Respond as JARVIS in a humble butler tone: understated, precise, and concise. " +
            "Address the user as 'sir' naturally when appropriate, not mechanically in every sentence. " +
            "Be warm but restrained; avoid theatrical imitation, flattery, canned persona phrases, or unnecessary verbosity."
    );

    public ResponseStyleContract {
        if (guidance == null || guidance.isBlank()) throw new IllegalArgumentException("guidance required");
        guidance = guidance.trim();
    }

    public static ResponseStyleContract beta() { return BETA; }
}
