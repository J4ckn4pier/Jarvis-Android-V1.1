package com.jarvis.brain;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Durable-memory semantics independent of storage backend. History is preserved; retention only closes hot validity. */
public final class LongTermMemoryStore {
    private final Map<String, List<RichMemory>> byKey = new HashMap<>();
    private final LongTermMemoryPersistence persistence;

    public LongTermMemoryStore() { this(LongTermMemoryPersistence.none()); }

    public LongTermMemoryStore(LongTermMemoryPersistence persistence) {
        this.persistence = persistence == null ? LongTermMemoryPersistence.none() : persistence;
        restore();
    }

    public synchronized void put(RichMemory memory) {
        if (memory == null) throw new IllegalArgumentException("memory required");
        List<RichMemory> list = byKey.computeIfAbsent(memory.key(), k -> new ArrayList<>());
        if (memory.type() != MemoryType.EPISODE) for (int i=0;i<list.size();i++) {
            RichMemory existing=list.get(i);
            if (existing.validUntil()==null && existing.validFrom().isBefore(memory.validFrom()) && !existing.content().equals(memory.content()))
                list.set(i, existing.closeAt(memory.validFrom()));
        }
        list.add(memory);
        list.sort(Comparator.comparing(RichMemory::validFrom));
        persist();
    }

    /** Explicit user removal archives the current value while preserving audit/history. */
    public synchronized boolean archive(String key, Instant when) {
        List<RichMemory> list=byKey.get(key);
        if(list==null||list.isEmpty()) return false;
        Instant at=when==null?Instant.now():when;
        boolean changed=false;
        for(int i=0;i<list.size();i++){
            RichMemory m=list.get(i);
            if(m.validUntil()==null){list.set(i,m.closeAt(at));changed=true;}
        }
        if(changed) persist();
        return changed;
    }

    public synchronized void observeRoutine(String key,String content,Set<String> tags,Instant observedAt) {
        List<RichMemory> list=byKey.computeIfAbsent(key,k->new ArrayList<>());
        for(int i=0;i<list.size();i++){
            RichMemory existing=list.get(i);
            if(existing.type()==MemoryType.ROUTINE&&existing.validUntil()==null&&existing.content().equals(content)){
                int count=existing.evidenceCount()+1;
                double confidence=Math.min(0.98,0.45+0.09*count);
                list.set(i,existing.withEvidence(count,confidence));
                persist();
                return;
            }
        }
        list.add(new RichMemory(key,MemoryType.ROUTINE,content,"observed",0.54,0.72,observedAt,null,tags,1));
        persist();
    }

    public synchronized Optional<RichMemory> current(String key,Instant when){return byKey.getOrDefault(key,List.of()).stream().filter(m->m.validAt(when)).max(Comparator.comparing(RichMemory::validFrom).thenComparingDouble(RichMemory::confidence));}
    public synchronized Optional<RichMemory> currentTrusted(String key,Instant when,double minConfidence){return current(key,when).filter(m->m.confidence()>=minConfidence&&!m.source().equals("inferred-unconfirmed"));}
    public synchronized List<RichMemory> history(String key){return List.copyOf(byKey.getOrDefault(key,List.of()));}
    public synchronized List<RichMemory> snapshotAll(){List<RichMemory> out=new ArrayList<>();for(List<RichMemory> list:byKey.values())out.addAll(list);out.sort(Comparator.comparing(RichMemory::key).thenComparing(RichMemory::validFrom));return List.copyOf(out);}

    public synchronized int prune(MemoryRetentionPolicy policy, Instant now) {
        if (policy==null||now==null) return 0;
        int archived=0;
        for(List<RichMemory> list:byKey.values()) for(int i=0;i<list.size();i++) {
            RichMemory memory=list.get(i);
            if(memory.validUntil()==null && policy.shouldPrune(memory,now)) {
                list.set(i,memory.closeAt(now));
                archived++;
            }
        }
        if(archived>0) persist();
        return archived;
    }

    public synchronized List<RichMemory> searchHistory(String query,int limit){Set<String> terms=terms(query);List<Scored> scored=new ArrayList<>();for(List<RichMemory> list:byKey.values())for(RichMemory m:list){double overlap=overlap(terms,memoryTerms(m));if(overlap>0)scored.add(new Scored(m,overlap));}scored.sort(Comparator.comparingDouble(Scored::score).reversed());return scored.stream().limit(Math.max(0,limit)).map(Scored::memory).toList();}

    public synchronized List<RichMemory> retrieve(String query,Instant when,int limit){
        Set<String> q=terms(query);
        List<Scored> scored=new ArrayList<>();
        boolean touchedAny=false;
        for(Map.Entry<String,List<RichMemory>> entry:byKey.entrySet()){
            List<RichMemory> list=entry.getValue();
            int currentIndex=currentIndex(list,when);
            if(currentIndex<0)continue;
            RichMemory m=list.get(currentIndex);
            double semantic=overlap(q,memoryTerms(m));
            if(semantic<=0)continue;
            RichMemory touched=m.touch(when);
            if(touched!=m){list.set(currentIndex,touched);m=touched;touchedAny=true;}
            double sourceTrust=(m.source().equals("user-stated")||m.source().equals("manual-user-edit"))?1.0:m.type()==MemoryType.INFERENCE?m.confidence():0.82;
            double stability=switch(m.type()){case PREFERENCE,FACT,RELATIONSHIP,PROCEDURE,GOAL->1.0;case ROUTINE->Math.min(1.0,0.55+0.08*m.evidenceCount());case EPISODE->0.55;case INFERENCE->0.50;};
            long ageDays=Math.max(0,Duration.between(m.validFrom(),when).toDays());
            double recency=1.0/(1.0+ageDays/120.0);
            double score=0.42*semantic+0.24*m.importance()+0.15*m.confidence()+0.10*sourceTrust+0.06*stability+0.03*recency;
            scored.add(new Scored(m,score));
        }
        if(touchedAny) persist();
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        return scored.stream().limit(Math.max(0,limit)).map(Scored::memory).toList();
    }

    public synchronized String memoryPack(String query,Instant when,int limit){StringBuilder out=new StringBuilder();for(RichMemory m:retrieve(query,when,limit)){if(out.length()>0)out.append('\n');out.append('[').append(m.type()).append("; confidence=").append(String.format(Locale.ROOT,"%.2f",m.confidence())).append("] ").append(m.content());}return out.toString();}

    private void restore() {
        List<RichMemory> restored;
        try { restored = persistence.load(); } catch (RuntimeException ignored) { return; }
        if(restored==null) return;
        for(RichMemory memory:restored) {
            if(memory==null) continue;
            byKey.computeIfAbsent(memory.key(), k -> new ArrayList<>()).add(memory);
        }
        for(List<RichMemory> list:byKey.values()) list.sort(Comparator.comparing(RichMemory::validFrom));
    }

    private void persist() {
        try { persistence.save(snapshotAll()); } catch (RuntimeException ignored) { }
    }

    private static int currentIndex(List<RichMemory> list,Instant when){int best=-1;for(int i=0;i<list.size();i++){RichMemory candidate=list.get(i);if(!candidate.validAt(when))continue;if(best<0){best=i;continue;}RichMemory existing=list.get(best);int timeCompare=candidate.validFrom().compareTo(existing.validFrom());if(timeCompare>0||(timeCompare==0&&candidate.confidence()>existing.confidence()))best=i;}return best;}
    private record Scored(RichMemory memory,double score){}
    private static Set<String> memoryTerms(RichMemory m){Set<String> out=new HashSet<>(terms(m.key()+" "+m.content()));for(String tag:m.tags())out.addAll(terms(tag));return out;}
    private static Set<String> terms(String text){Set<String> stop=Set.of("the","a","an","is","are","to","for","of","my","we","i","you","should","where","what","do","me","find","some","with");Set<String> out=new HashSet<>();if(text==null)return out;for(String token:text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]"," ").split("\\s+"))if(token.length()>=3&&!stop.contains(token))out.add(token);return out;}
    private static double overlap(Set<String>a,Set<String>b){if(a.isEmpty()||b.isEmpty())return 0;int hit=0;for(String t:a)if(b.contains(t)||related(t,b))hit++;return Math.min(1.0,hit/(double)Math.max(1,a.size()));}
    private static boolean related(String term,Set<String> memory){if(term.equals("eat")&&(memory.contains("food")||memory.contains("dinner")||memory.contains("restaurant")))return true;if(term.equals("restaurant")&&(memory.contains("dinner")||memory.contains("food")))return true;if(term.equals("dinner")&&(memory.contains("food")||memory.contains("restaurant")))return true;return false;}
}
