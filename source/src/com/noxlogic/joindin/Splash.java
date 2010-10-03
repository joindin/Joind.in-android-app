package com.noxlogic.joindin;

/*
 * Displays a splash screen. There is no real need for it, but it looks nice.
 */

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.Window;

public class Splash extends JIActivity {
    int SPLASH_DISPLAY_LENGHT = 5000;           // milliseconds the splash screen stays visible

     /** Called when the activity is first created. */
     public void onCreate(Bundle savedInstanceState) {
          super.onCreate(savedInstanceState);

          // Do not display title bar, this makes the splash screen really full screen
          requestWindowFeature(Window.FEATURE_NO_TITLE);
          
          // Check if we need to display the splash screen. If no, go directly to the main intent
          SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
          boolean showscreen = prefs.getBoolean("showsplashscreen", true);
          if (! showscreen) {
        	  gotoMainIntent();
        	  return;
          }
          

          // Set the layout
          setContentView(R.layout.splash);

          // This will run after a specified time (SPLASH_DISPLAY_LENGHT milliseconds)
          new Handler().postDelayed(new Runnable(){
               public void run() {
            	   gotoMainIntent();
               }
          }, SPLASH_DISPLAY_LENGHT);
     }
     
     
     /**
      * Start the main activity and finish the splash screen
      */
     void gotoMainIntent() {
         // Intenting to start the activity "main".
         Intent mainIntent = new Intent(this, Main.class);
         this.startActivity(mainIntent);
         this.finish();   // Splash screen is done now...   Needed, otherwise when closing the main activity will result
                          // in coming back to the splashscreen.
     }
}