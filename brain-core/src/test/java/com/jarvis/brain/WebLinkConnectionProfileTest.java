package com.jarvis.brain;
public final class WebLinkConnectionProfileTest{
 public static void main(String[]a){WebLinkConnectionProfile p=new WebLinkConnectionProfile("opentable","https://www.opentable.com/profile","Charles");check(p.url().startsWith("https://"),"https retained");check(p.accountNote().equals("Charles"),"note retained");reject("");reject("http://example.com");reject("javascript:alert(1)");reject("https:///missing-host");System.out.println("WebLinkConnectionProfileTest passed");}
 private static void reject(String u){try{new WebLinkConnectionProfile("x",u,"");throw new AssertionError("should reject "+u);}catch(IllegalArgumentException expected){}}
 private static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
}
