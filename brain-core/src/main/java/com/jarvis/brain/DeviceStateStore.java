package com.jarvis.brain;

import java.util.*;

/** Vendor-neutral editable device state for UI projection and action adapters. */
public final class DeviceStateStore {
    private final Map<String,DeviceState> devices = new LinkedHashMap<>();
    public synchronized void upsert(DeviceState state){ if(state==null)throw new IllegalArgumentException("device required"); devices.put(state.id(),state); }
    public synchronized Optional<DeviceState> get(String id){ return Optional.ofNullable(devices.get(clean(id))); }
    public synchronized List<DeviceState> all(){ return List.copyOf(devices.values()); }
    public synchronized boolean remove(String id){ return devices.remove(clean(id)) != null; }
    public synchronized DeviceState setPower(String id,boolean on){ DeviceState s=require(id).withPower(on);devices.put(s.id(),s);return s; }
    public synchronized DeviceState setAttribute(String id,String key,String value){ DeviceState s=require(id).withAttribute(clean(key),value==null?"":value.trim());devices.put(s.id(),s);return s; }
    private DeviceState require(String id){return get(id).orElseThrow(()->new IllegalArgumentException("unknown device: "+id));}
    private static String clean(String v){return v==null?"":v.trim();}
}
