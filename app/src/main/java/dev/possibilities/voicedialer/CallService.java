package dev.possibilities.voicedialer;
import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import java.util.concurrent.*;

public final class CallService extends Service implements CallSession.Listener {
    public static final String HANGUP="dev.possibilities.voicedialer.HANGUP";
    private final IBinder binder=new LocalBinder();
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private CallSession session; private LoopbackTransport transport; private NativeAudio audio;
    private CallSession.Listener observer; private String message="Ready when you are"; private String transcript="";
    private CallSession.State state=CallSession.State.IDLE; private boolean muted; private boolean speaker=true;
    public final class LocalBinder extends Binder { CallService service(){return CallService.this;} }
    @Override public void onCreate(){super.onCreate();transport=new LoopbackTransport(()->new ControlCredential(this).load());audio=new NativeAudio(this);session=new CallSession(transport,new SessionStore(this),audio,this,timer);transport.session=session;audio.session=session;}
    @Override public IBinder onBind(Intent intent){return binder;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&HANGUP.equals(intent.getAction()))session.hangup();
        else { foreground();session.dial(); }
        return START_NOT_STICKY; // Never resurrect a microphone after process death.
    }
    void observe(CallSession.Listener listener){observer=listener;if(listener!=null){listener.changed(state,message);if(!transcript.isEmpty())listener.transcript("",transcript);}}
    void hangup(){session.hangup();}
    boolean isMuted(){return muted;}
    boolean isSpeaker(){return speaker;}
    void mute(){muted=!muted;audio.mute(muted);}
    void speaker(){speaker=!speaker;audio.speaker(speaker);}
    @Override public void changed(CallSession.State state,String message){main.post(()->{this.state=state;this.message=message;if(state==CallSession.State.IDLE||state==CallSession.State.ERROR){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}else updateNotification(message);if(observer!=null)observer.changed(state,message);});}
    @Override public void transcript(String role,String text){main.post(()->{transcript=(role.isEmpty()?"":role+": ")+text;if(observer!=null)observer.transcript(role,text);});}
    private void foreground(){
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("call","Voice calls",NotificationManager.IMPORTANCE_LOW));
        if(Build.VERSION.SDK_INT>=30)startForeground(1,notification("Connecting to local Codex"),ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);else startForeground(1,notification("Connecting to local Codex"));
    }
    private Notification notification(String title){
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent end=PendingIntent.getService(this,1,new Intent(this,CallService.class).setAction(HANGUP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,"call").setSmallIcon(dev.possibilities.voicedialer.R.drawable.ic_phone).setContentTitle("Local Voice").setContentText(title).setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"Hang up",end).build()).setCategory(Notification.CATEGORY_CALL).build();
    }
    private void updateNotification(String text){getSystemService(NotificationManager.class).notify(1,notification(text));}
    @Override public void onDestroy(){if(session!=null)session.hangup();if(audio!=null)audio.destroy();if(transport!=null)transport.destroy();timer.shutdownNow();super.onDestroy();}
}
