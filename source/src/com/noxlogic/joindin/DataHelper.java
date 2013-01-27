package com.noxlogic.joindin;

/*
 * Data helper for the SQLite database.
 *
 * All data is stored in a database so we do not have to call the joind.in api
 * to display data. Instead of filtering out all JSON data, we just store some
 * standard data (event_id, talk_id) and for the rest we just store the actual
 * received JSON data. This is because we not going to seek or query data anyway.
 *
 * The helper functions here take care of everything, so no need to query anything
 * outside this class.
 */

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.widget.ArrayAdapter;


public final class DataHelper {
    private static DataHelper DHinstance = null;

    private static final String DATABASE_NAME = "joindin.db";
    private static final int DATABASE_VERSION = 10;  // Increasing this version number will result in automatic call to onUpgrade()
    
    public static final int ORDER_DATE_ASC 		= 1;
    public static final int ORDER_DATE_DESC 	= 2;
    public static final int ORDER_TITLE_ASC 	= 3;
    public static final int ORDER_TITLE_DESC 	= 4;

    private SQLiteDatabase db = null;

    private DataHelper(Context context) {
        OpenHelper openHelper = new OpenHelper (context);
        this.db = openHelper.getWritableDatabase();
    }

    public static DataHelper createInstance (Context context) {
        if (DHinstance == null) DHinstance = new DataHelper (context);

        return DHinstance;
    }

    public static DataHelper getInstance () {
        if (DHinstance == null) return null;
        return DHinstance;
    }

    // Updates a event
    public long updateEvent(int eventRowID, JSONObject event) {
        ContentValues values = new ContentValues();
        values.put("json", event.toString());
        return db.update("events", values, "_rowid_=?", new String[] {Integer.toString(eventRowID)});
    }

    // insert a new event to specified event_type (hot, pending, past etc)
    public long insertEvent(String event_type, JSONObject event) {
        ContentValues values = new ContentValues();
        values.put("event_uri", event.optString("uri"));
        values.put("event_start", event.optInt("start_date"));
        values.put("event_title", event.optString("name"));
        values.put("event_type", event_type);
        values.put("json", event.toString());

        db.delete("events", "event_uri=?", new String[] {event.optString("event_uri")});
        return db.insert("events", "", values);
    }

    // load event
    public JSONObject getEvent(int eventRowID) {
        Cursor c = this.db.rawQuery("SELECT json FROM events WHERE _rowid_ = " + eventRowID, null);
        JSONObject json;
        try {
            c.moveToFirst();
            json = new JSONObject (c.getString (0));
        } catch (Exception e) {
            json = new JSONObject();
        }
        return json;
    }

    // Insert a new talk
    public long insertTalk (JSONObject talk) {
        ContentValues values = new ContentValues();
        values.put("event_id", talk.optInt("event_id"));
        
        
        int track_id = -1;	// Defaul track id (none) 
        int talk_id = talk.optInt("ID");
        
        try {
			if (talk.getJSONArray("tracks").length() > 0) {
				// There are tracks. This talk is inside the 1st track (could there be more tracks??) 
				track_id = talk.getJSONArray("tracks").getJSONObject(0).optInt("ID");                 
			}
		} catch (JSONException e) {
			// Ignore
		}
        
        values.put("talk_id", talk_id);
        values.put("track_id", track_id);
        values.put("json", talk.toString());
        
        db.delete("talks", "talk_id=?", new String[] {Integer.toString(talk_id)});
        return db.insert("talks", "", values);
    }

    // Insert a new talk comment
    public long insertTalkComment (JSONObject talkComment) {
        ContentValues values = new ContentValues();
        values.put("talk_id", talkComment.optInt("talk_id"));
        values.put("json", talkComment.toString());        
        return db.insert("tcomments", "", values);
    }

    // Insert a new event comment
    public long insertEventComment (JSONObject eventComment) {
        ContentValues values = new ContentValues();
        values.put("event_id", eventComment.optInt("event_id"));
        values.put("json", eventComment.toString());
        return db.insert("ecomments", "", values);
    }
    
    // Add event to favorite list
    public long addToFavorites (int event_id) {
        ContentValues values = new ContentValues();
        values.put("event_id", event_id);
        db.delete("favlist", "event_id=?", new String[] {Integer.toString(event_id)});
        return db.insert("favlist", "", values);
    }
    
    // Remove from favorite list
    public void removeFromFavorites (int event_id) {
    	db.delete("favlist", "event_id=?", new String[] {Integer.toString(event_id)});	
    }
        

    // Removes all events for specified event type (hot, pending, past etc)
    public void deleteAllEventsFromType(String event_type) {
        db.delete ("events", "event_type=?", new String[] {event_type});
    }

    // Removes all talks from specified event
    public void deleteTalksFromEvent(int event_id) {
        db.delete("talks", "event_id=?", new String[] {Integer.toString(event_id)});
    }

    // Removes all comments from specified event
    public void deleteCommentsFromEvent(int event_id) {
        db.delete("ecomments", "event_id=?", new String[] {Integer.toString(event_id)});
    }

    // Removes all comments from specified talk
    public void deleteCommentsFromTalk(int talk_id) {
        db.delete("tcomments", "talk_id=?", new String[] {Integer.toString(talk_id)});
    }

    // Removes EVERYTHING
    public void deleteAll () {
        db.delete("events", null, null);
        db.delete("talks", null, null);
        db.delete("ecomments", null, null);
        db.delete("tcomments", null, null);
    }

        
    // Populates an event adapter and return the number of items populated
    public int populateEvents(String event_type, JIEventAdapter m_eventAdapter, int order) {
    	// Different handling for favorite list
    	if (event_type.equals("favorites")) {
            Cursor c = this.db.rawQuery("SELECT json FROM events INNER JOIN favlist ON favlist.event_id = events._rowid_", null);
            int count = c.getCount();
            populate (c, m_eventAdapter);
            return count;    		
    	}
    	
    	
    	String order_sql = "";
    	
    	switch (order) {
    		case ORDER_DATE_ASC :
    			order_sql = "ORDER BY event_start ASC";
    			break;
    		case ORDER_DATE_DESC :
    			order_sql = "ORDER BY event_start DESC";
    			break;
    		case ORDER_TITLE_ASC :
    			order_sql = "ORDER BY event_title ASC";
    			break;
    		case ORDER_TITLE_DESC :
    			order_sql = "ORDER BY event_title DESC";
    			break;
    	}

        Cursor c = this.db.rawQuery("SELECT json,events._rowid_ FROM events WHERE event_type = '"+event_type+"' "+order_sql, null);

        int count = c.getCount();
        populate (c, m_eventAdapter);
        return count;
    }

    public int populateTracks(int event_id, JITrackAdapter m_trackAdapter) {
    	int trackCount = 0;
        Cursor c = this.db.rawQuery("SELECT json FROM events WHERE event_id = "+event_id, null);
        
        if (c.getCount() == 0) {
        	c.close();
        	return 0;
        }
        
        c.moveToFirst();
        try {
            JSONObject json = new JSONObject(c.getString(0));
            JSONArray json_tracks = json.getJSONArray ("tracks");
            trackCount = json_tracks.length();

            for (int i=0; i!=json_tracks.length(); i++) {
                m_trackAdapter.add(json_tracks.getJSONObject(i));
            }
        } catch (JSONException e) { 
            android.util.Log.e("JoindInApp", "Could not add item to list", e); 
        }

        c.close ();
        return trackCount;
    }

    // Populates a talk adapter and returns the number of items populated
    public int populateTalks(int event_id, int track_id, JITalkAdapter m_talkAdapter) {
        Cursor c;
        
        if (track_id == -1) {
            c = this.db.rawQuery("SELECT json FROM talks WHERE event_id = "+event_id, null);
        } else {
            c = this.db.rawQuery("SELECT json FROM talks WHERE event_id = "+event_id+" and track_id = "+track_id, null);
        }
        int count = c.getCount();
        populate (c, m_talkAdapter);
        return count;
    }

    // Populates a talk comment adapter and returns the number of items populated
    public int populateTalkComments(int talk_id, JITalkCommentAdapter m_talkCommentAdapter) {
        Cursor c = this.db.rawQuery("SELECT json FROM tcomments WHERE talk_id = "+talk_id, null);
        int count = c.getCount();
        populate (c, m_talkCommentAdapter);
        return count;
    }

    // Populates an event comment adapter and returns the number of items populated
    public int populateEventComments(int event_id, JIEventCommentAdapter m_eventCommentAdapter) {
        Cursor c = this.db.rawQuery("SELECT json FROM ecomments WHERE event_id = "+event_id, null);
        int count = c.getCount();
        populate (c, m_eventCommentAdapter);
        return count;
    }

    // Populates specified adapter with a query (should be JSON data column)
    private void populate (Cursor c, ArrayAdapter<JSONObject> adapter) {
        // No cursor given, do not do anything
        if (c == null) return;

        // Start with first item
        if (c.moveToFirst()) {
            do {
                try {
                    // Add JSON data to the adapter
                    JSONObject eventData = new JSONObject(c.getString(0));
                    eventData.put("eventRowID", c.getInt(1));
                    adapter.add(eventData);
                } catch (JSONException e) {
                    android.util.Log.e("JoindInApp", "Could not add item to list", e);
                }
            } while (c.moveToNext());
        }
        // Close cursor
        if (c != null && !c.isClosed()) c.close();
    }


    private static class OpenHelper extends SQLiteOpenHelper {
        OpenHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        // Create new database (if needed)
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE [events]    ([event_uri] VARCHAR COLLATE NOCASE, [event_type] VARCHAR, [event_title] VARCHAR COLLATE NOCASE, [event_start] INTEGER, [json] VARCHAR)");
            db.execSQL("CREATE TABLE [talks]     ([event_id] INTEGER, [talk_id] INTEGER, [track_id] INTEGER, [json] VARCHAR)");
            db.execSQL("CREATE TABLE [ecomments] ([event_id] INTEGER, [json] VARCHAR)");
            db.execSQL("CREATE TABLE [tcomments] ([talk_id] INTEGER, [json] VARCHAR)");
            db.execSQL("CREATE TABLE [favlist]   ([event_id] INTEGER)");
        }

        // Upgrade database. Drop everything and call onCreate.. We do not care for old data anyway
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // Drop everything
            db.execSQL("DROP TABLE IF EXISTS events");
            db.execSQL("DROP TABLE IF EXISTS talks");
            db.execSQL("DROP TABLE IF EXISTS ecomments");
            db.execSQL("DROP TABLE IF EXISTS tcomments");
            db.execSQL("DROP TABLE IF EXISTS favlist");

            // Create new database
            onCreate(db);
        }
    }

}

