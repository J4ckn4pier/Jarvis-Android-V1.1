package com.jarvis.brain;

import java.util.*;

/** Queue/playback state backing the Music UI; actual audio transport remains platform-owned. */
public final class MusicQueueStore {
    private final List<MusicTrack> queue = new ArrayList<>();
    private int index = -1;
    private boolean playing;
    private boolean shuffle;
    private boolean repeat;
    private int volume = 70;
    private long positionSeconds;

    public synchronized void add(MusicTrack track){ if(track==null)throw new IllegalArgumentException("track required"); queue.add(track);if(index<0)index=0; }
    public synchronized boolean remove(String id){int i=find(id);if(i<0)return false;queue.remove(i);if(queue.isEmpty()){index=-1;playing=false;positionSeconds=0;}else if(index>=queue.size())index=queue.size()-1;return true;}
    public synchronized List<MusicTrack> queue(){return List.copyOf(queue);}
    public synchronized Optional<MusicTrack> current(){return index>=0&&index<queue.size()?Optional.of(queue.get(index)):Optional.empty();}
    public synchronized void play(String id){int i=find(id);if(i<0)throw new IllegalArgumentException("unknown track: "+id);index=i;playing=true;positionSeconds=0;}
    public synchronized void togglePlay(){if(index>=0)playing=!playing;}
    public synchronized void next(){if(queue.isEmpty())return;index=(index+1)%queue.size();positionSeconds=0;}
    public synchronized void previous(){if(queue.isEmpty())return;index=(index-1+queue.size())%queue.size();positionSeconds=0;}
    public synchronized void seek(long seconds){long max=current().map(MusicTrack::durationSeconds).orElse(0L);positionSeconds=Math.max(0,Math.min(seconds,max));}
    public synchronized void setVolume(int value){volume=Math.max(0,Math.min(100,value));}
    public synchronized void setShuffle(boolean value){shuffle=value;}
    public synchronized void setRepeat(boolean value){repeat=value;}
    public synchronized PlaybackState state(){return new PlaybackState(current().orElse(null),playing,shuffle,repeat,volume,positionSeconds);}
    public record PlaybackState(MusicTrack current,boolean playing,boolean shuffle,boolean repeat,int volume,long positionSeconds){}
    private int find(String id){String q=id==null?"":id.trim();for(int i=0;i<queue.size();i++)if(queue.get(i).id().equals(q))return i;return -1;}
}
