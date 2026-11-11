package com.manedsoftware.wolphun;

/** Display geometry in physical screen pixels, based on the game's original logical resolution. */
final class GameViewport {
    int left, top, width, height, availableHeight;

    void fit(int viewWidth, int viewHeight, int gameWidth, int gameHeight,
             float portraitFraction, boolean pixelPerfect){
        if(viewWidth<=0 || viewHeight<=0 || gameWidth<=0 || gameHeight<=0){
            left=top=width=height=availableHeight=0;
            return;
        }
        boolean portrait = viewHeight > viewWidth;
        availableHeight = portrait
            ? Math.max(1, Math.min(viewHeight, Math.round(viewHeight * portraitFraction)))
            : viewHeight;
        if(pixelPerfect){
            int scale = Math.max(1, Math.min(viewWidth/gameWidth, availableHeight/gameHeight));
            width = gameWidth * scale;
            height = gameHeight * scale;
            // If even 1x cannot fit, the renderer clips rather than using fractional downscaling.
        } else {
            float scale = Math.min((float)viewWidth/gameWidth, (float)availableHeight/gameHeight);
            width = Math.max(1, Math.round(gameWidth * scale));
            height = Math.max(1, Math.round(gameHeight * scale));
        }
        left = (viewWidth-width)/2;
        top = portrait ? 0 : (viewHeight-height)/2;
    }
}
