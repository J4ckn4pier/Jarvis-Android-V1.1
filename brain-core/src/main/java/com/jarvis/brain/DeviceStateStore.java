package com.jarvis.brain;

import java.util.*;

/** Vendor-neutral editable device state for UI projection and action adapters. */
public final class DeviceStateStore {
    private final Map<String,DeviceState> devices = new LinkedHashMap<>();
    private final DeviceStateStorePersistence persistence;

    public DeviceStateStore(){ this(DeviceStateStorePersistence.none()); }

    public DeviceStateStore(DeviceStateStorePersistence persistence){
        this.persistence = persistence == null ? DeviceStateStorePersistence.none() : persistence;
        restore();
    }

    public synchronized void upsert(DeviceState state){
        if(state==null)throw new IllegalArgumentException("device required");
        devices.put(state.id(),state);
        persist(state);
    }

    public synchronized Optional<DeviceState> get(String id){ return Optional.ofNullable(devices.get(clean(id))); }
    public synchronized List<DeviceState> all(){ return List.copyOf(devices.values()); }

    public synchronized boolean remove(String id){
        String clean = clean(id);
        boolean removed = devices.remove(clean) != null;
        if(removed){
            try{ persistence.remove(clean); }catch(RuntimeException ignored){}
        }
        return removed;
    }

    public synchronized DeviceState setPower(String id,boolean on){
        DeviceState state=require(id).withPower(on);
        devices.put(state.id(),state);
        persist(state);
        return state;
    }

    public synchronized DeviceState setAttribute(String id,String key,String value){
        DeviceState state=require(id).withAttribute(clean(key),value==null?"":value.trim());
        devices.put(state.id(),state);
        persist(state);
        return state;
    }

    private void persist(DeviceState state){
        try{ persistence.put(state); }catch(RuntimeException ignored){}
    }

    private void restore(){
        Map<String,DeviceState> restored;
        try{ restored=persistence.load(); }catch(RuntimeException ignored){ return; }
        if(restored==null)return;
        for(DeviceState state:restored.values()){
            if(state!=null)devices.put(state.id(),state);
        }
    }

    private DeviceState require(String id){return get(id).orElseThrow(()->new IllegalArgumentException("unknown device: "+id));}
    private static String clean(String v){return v==null?"":v.trim();}
}
