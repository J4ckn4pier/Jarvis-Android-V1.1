package com.jarvis.brain;

/** Incoming-call screening only; answering/placing calls is deliberately outside this port. */
@FunctionalInterface
public interface CallScreeningPort {
    CallerScreeningResult screen(IncomingCaller incoming);
}
