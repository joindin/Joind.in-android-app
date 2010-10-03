package com.noxlogic.joindin;

/**
 * Taken from fedorvlasov.lazylist 
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Stack;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.ImageView;

public class ImageLoader {
    protected HashMap<String, Bitmap> _cache=new HashMap<String, Bitmap>();
    protected File _cacheDir;
    protected String _cacheDirName;
    
    public ImageLoader(Context context, String cacheDir){
        //Make the background thead low priority. This way it will not affect the UI performance
        photoLoaderThread.setPriority(Thread.NORM_PRIORITY-1);
     
        //Find the dir to save cached images
        if (android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED)) {
        	_cacheDir=new File (context.getCacheDir(), cacheDir);
        	// @TODO: Somehow the external cache dir is not writeable on the 1.6 emulator?
        	//_cacheDir=new File(android.os.Environment.getExternalStorageDirectory(),"/Android/data/com.noxlogic.joindin/cache/"+cacheDir);
        } else {
            _cacheDir=new File (context.getCacheDir(), cacheDir);
        }
        
        // Create cache dir if it doesn't exists
        if (!_cacheDir.exists()) _cacheDir.mkdirs();
    }
    
    public void displayImage(String url, String filename, Activity activity, ImageView imageView) {
    	// Check if we are allowed to load images. If not, just return.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity.getBaseContext());
        boolean loadimages = prefs.getBoolean("loadimages", true);
        if (! loadimages) return;
        	
    	// Check if the file is present in the cache, if so, display it directly without loading from the web.
        if (_cache.containsKey(filename)) {
            imageView.setImageBitmap(_cache.get(filename));
            imageView.setVisibility(View.VISIBLE);
        } else {
        	// Queue this image so it will be loaded.
            _queuePhoto(url, filename, activity, imageView);
        }    
    }
        
    private void _queuePhoto(String url, String filename, Activity activity, ImageView imageView) {
        //This ImageView may be used for other images before. So there may be some old tasks in the queue. We need to discard them. 
        photosQueue.Clean(imageView);
        PhotoToLoad p=new PhotoToLoad(url, filename, imageView);
        synchronized (photosQueue.photosToLoad){
            photosQueue.photosToLoad.push(p);
            photosQueue.photosToLoad.notifyAll();
        }
        
        //start thread if it's not started yet
        if (photoLoaderThread.getState()==Thread.State.NEW) {
            photoLoaderThread.start();
        }
    }
    
    private Bitmap getBitmap(String url, String filename) {
    	// Open file and see if we can decode the file into a bitmap
        File f = new File(_cacheDir, filename);
        Bitmap b = decodeFile(f);
        if (b != null) return b;
        
        // Fetch from the web
        try {
            Bitmap bitmap=null;
            InputStream is=new URL(url+filename).openStream();
            OutputStream os = new FileOutputStream(f);
            copyStream(is, os);
            os.close();
            bitmap = decodeFile(f);
            return bitmap;
        } catch (Exception ex){
           ex.printStackTrace();
           return null;
        }
    }

    //decodes image and scales it to reduce memory consumption
    private Bitmap decodeFile(File f) {
        try {
            //decode image size
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(new FileInputStream(f),null,o);
            
            //Find the correct scale value. It should be the power of 2.
            final int REQUIRED_SIZE=70;
            int width_tmp=o.outWidth, height_tmp=o.outHeight;
            int scale=1;
            while (true) {
                if (width_tmp/2<REQUIRED_SIZE || height_tmp/2<REQUIRED_SIZE) break;
                width_tmp/=2;
                height_tmp/=2;
                scale++;
            }
            
            //decode with inSampleSize
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize=scale;
            return BitmapFactory.decodeStream(new FileInputStream(f), null, o2);
        } catch (FileNotFoundException e) {}
        return null;
    }
    
    //Task for the queue
    private class PhotoToLoad {
        public String url;
        public String file;
        public ImageView imageView;
        public PhotoToLoad(String u, String f, ImageView i) {
            url=u; 
            file=f;
            imageView=i;
        }
    }
    
    PhotosQueue photosQueue=new PhotosQueue();
    
    public void stopThread() {
        photoLoaderThread.interrupt();
    }
    
    //stores list of photos to download
    class PhotosQueue {
        private Stack<PhotoToLoad> photosToLoad=new Stack<PhotoToLoad>();
        
        //removes all instances of this ImageView
        public void Clean(ImageView image) {
            for (int j=0 ;j<photosToLoad.size();) {
                if(photosToLoad.get(j).imageView==image) {
                    photosToLoad.remove(j);
                } else {
                    ++j;
                }
            }
        }
    }
    
    class PhotosLoader extends Thread {
        public void run() {
            try {
                while(true)
                {
                    //thread waits until there are any images to load in the queue
                    if(photosQueue.photosToLoad.size()==0)
                        synchronized(photosQueue.photosToLoad){
                            photosQueue.photosToLoad.wait();
                        }
                    if(photosQueue.photosToLoad.size()!=0)
                    {
                        PhotoToLoad photoToLoad;
                        synchronized(photosQueue.photosToLoad){
                            photoToLoad=photosQueue.photosToLoad.pop();
                        }
                        Bitmap bmp=getBitmap(photoToLoad.url, photoToLoad.file);
                        if (bmp == null) break;
                        
                        _cache.put(photoToLoad.file, bmp);
                        String tag = (String)photoToLoad.imageView.getTag();
                        if ((tag).equals(photoToLoad.file)) {
                            BitmapDisplayer bd = new BitmapDisplayer(bmp, photoToLoad.imageView);
                            Activity a = (Activity)photoToLoad.imageView.getContext();
                            a.runOnUiThread(bd);
                        }
                    }
                    if(Thread.interrupted())
                        break;
                }
            } catch (InterruptedException e) {
                //allow thread to exit
            }
        }
    }
    
    PhotosLoader photoLoaderThread=new PhotosLoader();
    
    //Used to display bitmap in the UI thread
    class BitmapDisplayer implements Runnable {
        Bitmap bitmap;
        ImageView imageView;
        public BitmapDisplayer(Bitmap b, ImageView i) { bitmap=b; imageView=i; }
        public void run() {
            if (bitmap != null) {
            	imageView.setImageBitmap(bitmap);
            	imageView.setVisibility(View.VISIBLE);
            }
        }
    }

    public void clearCache() {
        //clear memory cache
        _cache.clear();
        
        //clear SD cache
        File[] files = _cacheDir.listFiles();
        for (File f:files) f.delete();
    }
    
    
    public static void copyStream(InputStream is, OutputStream os) {
        final int buffer_size = 1024;
        try {
            byte[] bytes = new byte[buffer_size];
            for(;;) {
              int count = is.read(bytes, 0, buffer_size);
              if (count == -1) break;
              os.write(bytes, 0, count);
            }
        }
        catch(Exception ex) {}
    }    

}

