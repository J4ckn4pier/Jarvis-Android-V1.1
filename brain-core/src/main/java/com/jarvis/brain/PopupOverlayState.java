package com.jarvis.brain;
public record PopupOverlayState(boolean visible,String title,String detail,boolean expanded){
 public PopupOverlayState{title=title==null?"":title.trim();detail=detail==null?"":detail.trim();if(!visible)expanded=false;}
 public static PopupOverlayState hidden(){return new PopupOverlayState(false,"","",false);}
}
