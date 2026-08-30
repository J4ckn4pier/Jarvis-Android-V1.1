package com.jarvis.brain;
import java.net.URI;
/** Non-secret user-entered metadata for a web-link connector. Tokens/credentials never belong here. */
public record WebLinkConnectionProfile(String connectionId,String url,String accountNote){
 public WebLinkConnectionProfile{
  if(connectionId==null||connectionId.isBlank())throw new IllegalArgumentException("connection id required");
  if(url==null||url.isBlank())throw new IllegalArgumentException("https link required");
  URI u;try{u=URI.create(url.trim());}catch(IllegalArgumentException e){throw new IllegalArgumentException("valid https link required",e);}
  if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||u.getHost().isBlank())throw new IllegalArgumentException("valid https link required");
  url=u.toString();accountNote=accountNote==null?"":accountNote.trim();
 }
}
