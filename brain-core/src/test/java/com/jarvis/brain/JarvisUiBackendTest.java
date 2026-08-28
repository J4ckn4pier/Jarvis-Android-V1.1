package com.jarvis.brain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class JarvisUiBackendTest {
    private static int checks;
    public static void main(String[] args){
        JarvisUiBackend ui=new JarvisUiBackend(new LongTermMemoryStore(),ToolRegistry.standard(),new ConnectionRegistry());
        listSurfacesAreRealCrud(ui);
        routinesAreEditableAndExecutableData(ui);
        deviceControlsRetainTypedState(ui);
        musicControlsRetainPlaybackState(ui);
        settingsAndDefaultsPersistInBackendState(ui);
        memoryAndSkillsExposeBrainTruth(ui);
        activityIsAuditState(ui);
        System.out.println("JarvisUiBackendTest: "+checks+" assertions passed");
    }
    private static void listSurfacesAreRealCrud(JarvisUiBackend ui){
        for(UiSection section:List.of(UiSection.TASKS,UiSection.PROJECTS,UiSection.CALENDAR,UiSection.MESSAGES,UiSection.BROWSER,UiSection.SKILLS)){
            String id=section.name().toLowerCase()+"-1";
            ui.lists().upsert(section,new UiListItem(id,"Test "+section,"details",false,Map.of("tag","alpha")));
            check(ui.lists().get(section,id).isPresent(),section+" add");
            check(ui.lists().search(section,"alpha").size()==1,section+" search");
            ui.lists().upsert(section,new UiListItem(id,"Edited "+section,"changed",true,Map.of()));
            check(ui.lists().get(section,id).orElseThrow().completed(),section+" edit");
            check(ui.lists().remove(section,id),section+" remove");
        }
    }
    private static void routinesAreEditableAndExecutableData(JarvisUiBackend ui){
        RoutineDefinition r=new RoutineDefinition("leave-work","Leaving work","phrase",Map.of("phrase","I'm leaving work"),new Plan("Text Mom",List.of(new PlanStep("send_message",Map.of("recipient","Mom","message","I'm on my way"),true))),true);
        ui.routines().upsert(r);check(ui.routines().matching("phrase").size()==1,"routine matches trigger");ui.routines().setEnabled("leave-work",false);check(ui.routines().matching("phrase").isEmpty(),"disabled routine not executable");
    }
    private static void deviceControlsRetainTypedState(JarvisUiBackend ui){
        ui.devices().upsert(new DeviceState("lights","Bedroom Lights","light",true,Map.of("brightness","70","color","blue")));
        ui.devices().setAttribute("lights","brightness","42");ui.devices().setPower("lights",false);DeviceState s=ui.devices().get("lights").orElseThrow();check(!s.on(),"device power state");check("42".equals(s.attributes().get("brightness")),"device attribute state");
    }
    private static void musicControlsRetainPlaybackState(JarvisUiBackend ui){
        ui.music().add(new MusicTrack("a","Track A","Artist",200));ui.music().add(new MusicTrack("b","Track B","Artist",180));ui.music().play("a");ui.music().next();ui.music().seek(50);ui.music().setVolume(33);check("b".equals(ui.music().state().current().id()),"music next");check(ui.music().state().positionSeconds()==50,"music seek");check(ui.music().state().volume()==33,"music volume");
    }
    private static void settingsAndDefaultsPersistInBackendState(JarvisUiBackend ui){
        ui.settings().put("wake_word","Computer");ui.settings().put("location_permission","true");ui.defaultApps().set("maps","google-maps");check("Computer".equals(ui.settings().get("wake_word")),"wake setting");check(ui.settings().bool("location_permission"),"permission toggle");check("google-maps".equals(ui.defaultApps().get("maps").orElseThrow()),"default app");
    }
    private static void memoryAndSkillsExposeBrainTruth(JarvisUiBackend ui){
        Instant t=Instant.parse("2026-08-28T19:00:00Z");ui.addManualMemory("pref:tea",MemoryType.PREFERENCE,"likes matcha",t);check(ui.memories().stream().anyMatch(m->m.content().contains("matcha")),"memory screen reads real memory store");check(ui.skills().stream().anyMatch(s->s.name().equals("send_message")),"skills screen reads tool registry");
    }
    private static void activityIsAuditState(JarvisUiBackend ui){
        ui.activity().append(new ActivityRecord("x",Instant.parse("2026-08-28T19:00:00Z"),"Reservation",ActivityRecord.Status.NEEDS_INPUT,"7 unavailable",Map.of("alternatives","6:00,6:45")));check(ui.activity().needsAttention().size()==1,"activity attention filter");
    }
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
}
