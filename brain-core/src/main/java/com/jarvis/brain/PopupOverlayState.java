package com.jarvis.brain;
public record PopupOverlayState(boolean visible,String title,String detail){
 public PopupOverlayState{title=title==null?"":title.trim();detail=detail==null?"":detail.trim();}
 public static PopupOverlayState hidden(){return new PopupOverlayState(false,"","");}
}
