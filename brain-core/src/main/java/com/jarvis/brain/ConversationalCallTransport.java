package com.jarvis.brain;

/** Provider-independent duplex media transport for JARVIS-owned conversational calls. */
public interface ConversationalCallTransport {
    Session connect(String destination) throws Exception;

    interface Session extends AutoCloseable {
        void speak(String text) throws Exception;
        String awaitRemoteSpeech() throws Exception;
        @Override void close() throws Exception;
    }
}
