package com.noxlogic.joindin;

/*
 * Displays a splash screen. There is no real need for it, but it looks nice.
 */

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.Window;

public class Splash extends JIActivity {
    int SPLASH_DISPLAY_LENGHT = 5000;           // milliseconds the splash screen stays visible

     /** Called when the activity is first created. */
     public void onCreate(Bundle savedInstanceState) {
          super.onCreate(savedInstanceState);

          // Do not display title bar, this makes the splash screen really full screen
          requestWindowFeature(Window.FEATURE_NO_TITLE);

          // Set the layout
          setContentView(R.layout.splash);

          // This will run after a specified time (SPLASH_DISPLAY_LENGHT milliseconds)
          new Handler().postDelayed(new Runnable(){
               public void run() {
                    // Intenting to start the activity "main".
                    Intent mainIntent = new Intent(Splash.this, main.class);
                    Splash.this.startActivity(mainIntent);
                    Splash.this.finish();   // Splash screen is done now...
               }
          }, SPLASH_DISPLAY_LENGHT);
     }
}