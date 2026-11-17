package com.manedsoftware.wolphun;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class GameInputTest {
    private static void expect(List<String> actual, String... expected){
        if(!actual.equals(Arrays.asList(expected))) throw new AssertionError(actual.toString());
        actual.clear();
    }

    public static void main(String[] args){
        List<String> events = new ArrayList<>();
        GameInput input = new GameInput((code, down) -> events.add(code + ":" + down));
        input.set("touch:0", 16);
        input.set("pad:1:key:96", 16);
        input.set("pad:1:key:96", 16); // repeated down is idempotent
        input.set("touch:0", 0);
        expect(events, "16:true");
        input.set("pad:1:key:96", 0);
        expect(events, "16:false");

        input.set("pad:1:hat:x", 4);
        input.set("pad:1:hat:y", 1); // diagonal
        expect(events, "4:true", "1:true");
        input.set("pad:1:hat:x", 8); // direction change releases old direction
        expect(events, "4:false", "8:true");
        input.removeDevice(1);
        if(events.size() != 2 || !events.contains("8:false") || !events.contains("1:false"))
            throw new AssertionError(events.toString());
        events.clear();

        int numeric2 = 0x10000 | '2';
        input.set("touch:1", numeric2);
        input.set("pad:2:key:102", numeric2);
        input.set("pad:20:key:102", numeric2);
        expect(events, numeric2 + ":true");
        input.removeDevice(2); // must not remove device 20 or touch ownership
        input.set("touch:1", 0);
        expect(events);
        input.removeDevice(20);
        expect(events, numeric2 + ":false");

        input.set("touch:0", 16);
        input.set("pad:1:key:96", 16);
        expect(events, "16:true");
        input.clear();
        input.clear();
        input.set("pad:1:key:96", 0); // late key-up after focus loss
        expect(events, "16:false");
        if(GameInput.hat(-1, 4, 8) != 4 || GameInput.hat(1, 4, 8) != 8
                || GameInput.hat(0, 4, 8) != 0 || GameInput.hat(0.1f, 4, 8) != 0)
            throw new AssertionError("hat mapping");
        System.out.println("GameInput tests passed");
    }
}
