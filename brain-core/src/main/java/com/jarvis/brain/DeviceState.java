package com.jarvis.brain;

import java.util.Map;

/** Normalized smart-device state backing the Devices UI without vendor coupling. */
public record DeviceState(String id, String name, String type, boolean on, Map<String,String> attributes) {
    public DeviceState {
        id = require(id,"id"); name = require(name,"name"); type = type == null ? "generic" : type.trim();
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
    public DeviceState withPower(boolean value){ return new DeviceState(id,name,type,value,attributes); }
    public DeviceState withAttribute(String key,String value){ java.util.Map<String,String> copy=new java.util.LinkedHashMap<>(attributes); copy.put(key,value); return new DeviceState(id,name,type,on,copy); }
    private static String require(String v,String label){String s=v==null?"":v.trim();if(s.isBlank())throw new IllegalArgumentException(label+" required");return s;}
}
