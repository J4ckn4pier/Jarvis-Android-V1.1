package com.jarvis.brain;

import java.time.Clock;
import java.util.Locale;
import java.util.Map;

/** Provider-neutral dynamic context: current time plus normalized device state relevant to the request. */
public final class RuntimeEnvironmentContextSource implements AssistantContextSource {
    private final Clock clock;
    private final DeviceStateStore devices;

    public RuntimeEnvironmentContextSource(Clock clock, DeviceStateStore devices) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.devices = devices == null ? new DeviceStateStore() : devices;
    }

    @Override
    public String contextFor(String utterance) {
        String normalized = utterance == null ? "" : utterance.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder("Current time: ").append(clock.instant());
        for (DeviceState device : devices.all()) {
            if (!relevant(normalized, device)) continue;
            out.append("\nDevice: ").append(device.name())
                    .append(" [type=").append(device.type())
                    .append(", on=").append(device.on());
            for (Map.Entry<String,String> attribute : device.attributes().entrySet()) {
                out.append(", ").append(attribute.getKey()).append('=').append(attribute.getValue());
            }
            out.append(']');
        }
        return out.toString();
    }

    private static boolean relevant(String utterance, DeviceState device) {
        return containsPhraseOrToken(utterance, device.name())
                || containsPhraseOrToken(utterance, device.id())
                || containsPhraseOrToken(utterance, device.type());
    }

    private static boolean containsPhraseOrToken(String utterance, String candidate) {
        if (candidate == null || candidate.isBlank()) return false;
        String normalized = candidate.toLowerCase(Locale.ROOT).replace('-', ' ').trim();
        if (utterance.contains(normalized)) return true;
        for (String token : normalized.split("\\s+")) {
            if (token.length() >= 4 && utterance.contains(token)) return true;
        }
        return false;
    }
}
