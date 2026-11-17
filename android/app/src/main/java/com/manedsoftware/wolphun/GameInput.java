package com.manedsoftware.wolphun;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** UI-thread input ownership: releasing one source must not release another's key. */
final class GameInput {
    interface Sink { void set(int code, boolean down); }
    private final Sink sink;
    private final Map<String, Integer> held = new HashMap<>();

    GameInput(Sink sink){ this.sink = sink; }

    void set(String source, int code){
        Integer previous = held.get(source);
        if(previous != null && previous == code) return;
        if(previous != null){
            held.remove(source);
            if(!held.containsValue(previous)) sink.set(previous, false);
        }
        if(code != 0){
            boolean alreadyDown = held.containsValue(code);
            held.put(source, code);
            if(!alreadyDown) sink.set(code, true);
        }
    }

    void removeDevice(int deviceId){
        String prefix = "pad:" + deviceId + ":";
        for(String source : new HashSet<>(held.keySet()))
            if(source.startsWith(prefix)) set(source, 0);
    }

    void clear(){
        Set<Integer> codes = new HashSet<>(held.values());
        held.clear();
        for(int code : codes) sink.set(code, false);
    }

    static int hat(float value, int negative, int positive){
        return value < -0.5f ? negative : value > 0.5f ? positive : 0;
    }
}
