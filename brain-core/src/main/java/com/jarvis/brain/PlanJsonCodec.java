package com.jarvis.brain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Strict minimal JSON codec for the model-plan contract. It intentionally accepts only
 * the schema JARVIS expects: {goal:string,steps:[{tool:string,arguments:object,consequential:boolean}]}. */
public final class PlanJsonCodec {
    private PlanJsonCodec() {}

    public static String encode(Plan plan) {
        StringBuilder out = new StringBuilder();
        out.append("{\"goal\":\"").append(escape(plan.goal())).append("\",\"steps\":[");
        for (int i = 0; i < plan.steps().size(); i++) {
            if (i > 0) out.append(',');
            PlanStep step = plan.steps().get(i);
            out.append("{\"tool\":\"").append(escape(step.tool())).append("\",\"arguments\":{");
            int j = 0;
            for (Map.Entry<String, String> entry : step.arguments().entrySet()) {
                if (j++ > 0) out.append(',');
                out.append('\"').append(escape(entry.getKey())).append("\":\"")
                        .append(escape(entry.getValue())).append('\"');
            }
            out.append("},\"consequential\":").append(step.consequential()).append('}');
        }
        return out.append("]}").toString();
    }

    public static Plan decode(String json) {
        Parser p = new Parser(json);
        p.expect('{');
        p.expectString("goal"); p.expect(':'); String goal = p.string(); p.expect(',');
        p.expectString("steps"); p.expect(':'); p.expect('[');
        List<PlanStep> steps = new ArrayList<>();
        if (!p.peek(']')) {
            do { steps.add(parseStep(p)); } while (p.consume(','));
        }
        p.expect(']'); p.expect('}'); p.finish();
        return new Plan(goal, List.copyOf(steps));
    }

    private static PlanStep parseStep(Parser p) {
        p.expect('{');
        p.expectString("tool"); p.expect(':'); String tool = p.string(); p.expect(',');
        p.expectString("arguments"); p.expect(':'); Map<String, String> args = p.stringMap(); p.expect(',');
        p.expectString("consequential"); p.expect(':'); boolean consequential = p.bool();
        p.expect('}');
        return new PlanStep(tool, Map.copyOf(args), consequential);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static final class Parser {
        private final String s; private int i;
        Parser(String s) { this.s = s == null ? "" : s; }
        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        boolean peek(char c) { ws(); return i < s.length() && s.charAt(i) == c; }
        void expect(char c) { ws(); if (i >= s.length() || s.charAt(i) != c) fail("Expected " + c); i++; }
        boolean consume(char c) { if (peek(c)) { i++; return true; } return false; }
        void expectString(String expected) { String got = string(); if (!expected.equals(got)) fail("Expected key " + expected); }
        String string() {
            ws(); expectRaw('"'); StringBuilder out = new StringBuilder(); boolean esc = false;
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (esc) { out.append(switch (c) { case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t'; case '"' -> '"'; case '\\' -> '\\'; default -> c; }); esc = false; }
                else if (c == '\\') esc = true;
                else if (c == '"') return out.toString();
                else out.append(c);
            }
            fail("Unterminated string"); return "";
        }
        Map<String, String> stringMap() {
            expect('{'); Map<String, String> map = new LinkedHashMap<>();
            if (!peek('}')) {
                do { String k = string(); expect(':'); String v = string(); map.put(k, v); } while (consume(','));
            }
            expect('}'); return map;
        }
        boolean bool() {
            ws();
            if (s.startsWith("true", i)) { i += 4; return true; }
            if (s.startsWith("false", i)) { i += 5; return false; }
            fail("Expected boolean"); return false;
        }
        void finish() { ws(); if (i != s.length()) fail("Trailing content"); }
        void expectRaw(char c) { if (i >= s.length() || s.charAt(i) != c) fail("Expected " + c); i++; }
        void fail(String m) { throw new IllegalArgumentException(m + " at offset " + i); }
    }
}
