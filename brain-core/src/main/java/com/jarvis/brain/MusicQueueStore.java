package com.jarvis.brain;

import java.util.*;

/** Queue/playback state backing the Music UI; actual audio transport remains platform-owned. */
public final class MusicQueueStore {
    private final List<MusicTrack> queue = new ArrayList<>();
    private final MusicQueueStorePersistence persistence;
    private int index = -1;
    private boolean playing;
    private boolean shuffle;
    private boolean repeat;
    private int volume = 70;
    private long positionSeconds;

    public MusicQueueStore(){ this(MusicQueueStorePersistence.none()); }
    public MusicQueueStore(MusicQueueStorePersistence persistence){
        this.persistence = persistence == null ? MusicQueueStorePersistence.none() : persistence;
        restore();
    }

    public synchronized void add(MusicTrack track){
        if(track==null)throw new IllegalArgumentException("track required");
        queue.add(track);if(index<0)index=0;persist();
    }
    public synchronized boolean remove(String id){
        int i=find(id);if(i<0)return false;
        queue.remove(i);
        if(queue.isEmpty()){index=-1;playing=false;positionSeconds=0;}
        else if(index>=queue.size())index=queue.size()-1;
        persist();return true;
    }
    public synchronized List<MusicTrack> queue(){return List.copyOf(queue);}
    public synchronized Optional<MusicTrack> current(){return index>=0&&index<queue.size()?Optional.of(queue.get(index)):Optional.empty();}
    public synchronized void play(String id){int i=find(id);if(i<0)throw new IllegalArgumentException("unknown track: "+id);index=i;playing=true;positionSeconds=0;persist();}
    public synchronized void togglePlay(){if(index>=0){playing=!playing;persist();}}
    public synchronized void next(){if(queue.isEmpty())return;index=(index+1)%queue.size();positionSeconds=0;persist();}
    public synchronized void previous(){if(queue.isEmpty())return;index=(index-1+queue.size())%queue.size();positionSeconds=0;persist();}
    public synchronized void seek(long seconds){long max=current().map(MusicTrack::durationSeconds).orElse(0L);positionSeconds=Math.max(0,Math.min(seconds,max));persist();}
    public synchronized void setVolume(int value){volume=Math.max(0,Math.min(100,value));persist();}
    public synchronized void setShuffle(boolean value){shuffle=value;persist();}
    public synchronized void setRepeat(boolean value){repeat=value;persist();}
    public synchronized PlaybackState state(){return new PlaybackState(current().orElse(null),playing,shuffle,repeat,volume,positionSeconds);}
    public record PlaybackState(MusicTrack current,boolean playing,boolean shuffle,boolean repeat,int volume,long positionSeconds){}

    private void restore(){
        MusicQueueStorePersistence.Snapshot snapshot;
        try{snapshot=persistence.load();}catch(RuntimeException ignored){return;}
        if(snapshot==null)return;
        queue.clear();queue.addAll(snapshot.queue());
        index=find(snapshot.currentId());
        if(index<0 && !queue.isEmpty())index=0;
        playing=index>=0 && snapshot.playing();
        shuffle=snapshot.shuffle();repeat=snapshot.repeat();volume=snapshot.volume();
        long max=current().map(MusicTrack::durationSeconds).orElse(0L);
        positionSeconds=Math.max(0,Math.min(snapshot.positionSeconds(),max));
    }
    private void persist(){
        String currentId=current().map(MusicTrack::id).orElse("");
        MusicQueueStorePersistence.Snapshot snapshot=new MusicQueueStorePersistence.Snapshot(
                queue,currentId,playing,shuffle,repeat,volume,positionSeconds);
        try{persistence.save(snapshot);}catch(RuntimeException ignored){}
    }
    private int find(String id){String q=id==null?"":id.trim();for(int i=0;i<queue.size();i++)if(queue.get(i).id().equals(q))return i;return -1;}
}
