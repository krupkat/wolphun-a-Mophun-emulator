package com.manedsoftware.wolphun;

/** Dependency-free geometry regression tests. Run android/tests/run-viewport-tests.sh. */
public final class GameViewportTest {
    private static int checks;

    private static void check(boolean condition, String message){
        checks++;
        if(!condition) throw new AssertionError(message);
    }

    private static void expect(GameViewport v, int left, int top, int width, int height){
        check(v.left==left && v.top==top && v.width==width && v.height==height,
            "Expected " + left + "," + top + " " + width + "x" + height
                + "; got " + v.left + "," + v.top + " " + v.width + "x" + v.height);
    }

    public static void main(String[] args){
        GameViewport v = new GameViewport();
        v.fit(1080, 1794, 101, 80, .66f, true);
        expect(v, 35, 0, 1010, 800);   // Minigolf: 10x, not a fractional fit to 1080 pixels.
        v.fit(1080, 1794, 128, 160, .66f, true);
        expect(v, 92, 0, 896, 1120);   // 7x, limited by the portrait game-area height.
        v.fit(1080, 1794, 128, 160, 1f, true);
        expect(v, 28, 0, 1024, 1280);  // 8x when more height is available.
        v.fit(1920, 1080, 128, 160, .5f, true);
        expect(v, 576, 60, 768, 960);  // Landscape ignores the portrait height option.
        v.fit(1920, 1080, 128, 160, 1f, true);
        expect(v, 576, 60, 768, 960);
        v.fit(1080, 1794, 101, 80, .66f, false);
        expect(v, 0, 0, 1080, 855);    // Disabling pixel-perfect restores fractional fitting.
        v.fit(80, 60, 101, 80, .66f, true);
        expect(v, -10, -10, 101, 80); // Never shrink fractionally: clip a centered 1x image.
        v.fit(80, 100, 101, 80, .5f, true);
        expect(v, -10, 0, 101, 80);
        check(v.availableHeight==50, "Portrait clipping keeps the controls' reserved space");
        v.fit(0, 100, 101, 80, .66f, true);
        expect(v, 0, 0, 0, 0);
        check(v.availableHeight==0, "Empty surfaces clear previous geometry");

        int[][] surfaces = {{1080,1794}, {1080,1920}, {1794,1080}, {1920,1080},
            {720,1280}, {1280,720}, {360,640}, {240,320}, {160,120}, {80,100}, {1,1}};
        int[][] games = {{101,80}, {128,160}, {176,208}, {208,320}, {320,240}, {176,220}};
        for(int[] surface: surfaces) for(int[] game: games) for(float fraction: new float[]{.5f,.66f,1f}){
            int vw=surface[0], vh=surface[1], gw=game[0], gh=game[1];
            v.fit(vw, vh, gw, gh, fraction, true);
            int scale = Math.max(1, Math.min(vw/gw, v.availableHeight/gh));
            check(v.width==gw*scale && v.height==gh*scale, "Largest whole-number native scale");
            check(v.width%gw==0 && v.height%gh==0, "Both dimensions remain integer multiples");
            check(v.left==(vw-v.width)/2, "Horizontal centering uses integer pixel coordinates");
            check(v.top==(vh>vw ? 0 : (vh-v.height)/2), "Portrait top / landscape center alignment");
            check(v.availableHeight>=1 && v.availableHeight<=vh, "Clipping area stays inside the surface");
            check((scale+1)*gw>vw || (scale+1)*gh>v.availableHeight, "Next integer scale cannot fit");
            v.fit(vw, vh, gw, gh, fraction, false);
            check(v.width>=1 && v.width<=vw && v.height>=1 && v.height<=v.availableHeight,
                "Fractional fitting stays inside the available area");
        }
        System.out.println("PASS: " + checks + " viewport checks (integer/fractional scaling, orientations, height limits, clipping).");
    }
}
