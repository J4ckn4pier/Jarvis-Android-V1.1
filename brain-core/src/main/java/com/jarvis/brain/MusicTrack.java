package com.jarvis.brain;

public record MusicTrack(String id,String title,String artist,long durationSeconds){
 public MusicTrack{if(id==null||id.isBlank())throw new IllegalArgumentException("id required");if(title==null||title.isBlank())throw new IllegalArgumentException("title required");artist=artist==null?"":artist.trim();if(durationSeconds<0)throw new IllegalArgumentException("duration must be >=0");}
}
