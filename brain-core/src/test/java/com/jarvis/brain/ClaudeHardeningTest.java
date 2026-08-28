package com.jarvis.brain;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/** Regression gates from Claude's memory/security/endpointing review. */
public final class ClaudeHardeningTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        richMemoryCanPersistEncryptedAtRest(); lowConfidenceSpeechCannotBecomeTrustedFact();
        retentionPolicyArchivesStaleLowImportanceButProtectsHistoryAndImportantMemory();
        endpointingDerivesIncompleteVerbsFromToolRegistry();
        System.out.println("ClaudeHardeningTest: " + checks + " assertions passed");
    }
    private static void richMemoryCanPersistEncryptedAtRest() throws Exception {
        LongTermMemoryStore store=new LongTermMemoryStore(); Instant now=Instant.parse("2026-08-27T00:00:00Z");
        store.put(new RichMemory("home.city",MemoryType.FACT,"Castle Rock","user-stated",1.0,0.95,now,null,Set.of("home","city")));
        byte[] key="0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8); MemoryCipher cipher=new AesGcmMemoryCipher(key);
        var file=Files.createTempDirectory("jarvis-encrypted-memory").resolve("memory.jrm"); RichMemoryPersistence.saveEncrypted(store,file,cipher);
        byte[] raw=Files.readAllBytes(file); check(!new String(raw,StandardCharsets.UTF_8).contains("Castle Rock"),"encrypted persistence must not expose user memory in plaintext");
        LongTermMemoryStore loaded=RichMemoryPersistence.loadEncrypted(file,cipher); check(loaded.current("home.city",now.plusSeconds(60)).orElseThrow().content().equals("Castle Rock"),"encrypted memory should round-trip losslessly");
    }
    private static void lowConfidenceSpeechCannotBecomeTrustedFact(){MemoryConsolidator c=new MemoryConsolidator(new RuleMemoryExtractor(),new LongTermMemoryStore());Instant now=Instant.parse("2026-08-27T00:00:00Z");c.ingestUserTurn("I prefer Thai food for dinner",0.55,now);RichMemory uncertain=c.store().retrieve("Thai dinner",now.plusSeconds(1),1).get(0);check(!uncertain.source().equals("user-stated"),"low-STT-confidence extraction must remain unconfirmed/inferred");check(c.store().currentTrusted(uncertain.key(),now.plusSeconds(1),0.75).isEmpty(),"low-confidence speech must not be returned as trusted memory");c.ingestUserTurn("I prefer Thai food for dinner",0.98,now.plusSeconds(10));RichMemory trusted=c.store().retrieve("Thai dinner",now.plusSeconds(11),1).get(0);check(trusted.source().equals("user-stated")&&trusted.confidence()>=0.95,"clear direct statement should promote to trusted user-stated memory");}
    private static void retentionPolicyArchivesStaleLowImportanceButProtectsHistoryAndImportantMemory(){LongTermMemoryStore store=new LongTermMemoryStore();Instant now=Instant.parse("2026-08-27T00:00:00Z");store.put(new RichMemory("noise.old",MemoryType.EPISODE,"Saw a random blue car","observed",0.7,0.08,now.minus(Duration.ofDays(400)),null,Set.of("car")));store.put(new RichMemory("identity.goal",MemoryType.GOAL,"Build JARVIS","user-stated",1.0,1.0,now.minus(Duration.ofDays(400)),null,Set.of("goal","jarvis")));MemoryRetentionPolicy policy=new MemoryRetentionPolicy(Duration.ofDays(120),0.25,0.80);int archived=store.prune(policy,now);check(archived==1,"retention should archive only stale low-value memory in this case");check(store.history("noise.old").size()==1,"stale low-importance episode must remain in history");check(store.current("noise.old",now).isEmpty(),"archived low-value episode must leave current retrieval");check(!store.history("identity.goal").isEmpty(),"high-importance goal must survive age-based retention");}
    private static void endpointingDerivesIncompleteVerbsFromToolRegistry(){ToolRegistry registry=ToolRegistry.standard();registry.register(new ToolSpec("email_contact",true,Set.of("email"),Set.of("recipient","message"),"Send an email"),(args,ctx)->ToolResult.success("sent"));EndpointingPolicy policy=new EndpointingPolicy(registry);check(!policy.shouldCommit("email",1100),"required-argument tool alias should automatically extend endpointing wait");check(policy.shouldCommit("email",2500),"hard silence ceiling should still eventually commit incomplete-looking speech");}
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
}
