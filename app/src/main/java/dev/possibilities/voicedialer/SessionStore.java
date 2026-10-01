package dev.possibilities.voicedialer;
import android.content.*;
final class SessionStore implements CallSession.Store {
    private final SharedPreferences prefs;
    SessionStore(Context context) { prefs=context.getSharedPreferences("conversation",Context.MODE_PRIVATE); }
    public String thread(){return prefs.getString("thread","");}
    public boolean uncertain(){return prefs.getBoolean("creating",false);}
    public void beginCreate(){if(!prefs.edit().putBoolean("creating",true).commit())throw new IllegalStateException("Cannot persist conversation state");}
    public void saveThread(String id){if(id.trim().isEmpty()||id.length()>200)throw new IllegalArgumentException("Invalid thread ID");if(!prefs.edit().putString("thread",id).putBoolean("creating",false).commit())throw new IllegalStateException("Cannot save thread ID");}
}
