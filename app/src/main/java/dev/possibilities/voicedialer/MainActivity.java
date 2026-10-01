package dev.possibilities.voicedialer;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public final class MainActivity extends Activity implements CallSession.Listener {
    private static final int INK=Color.rgb(23,37,30),GREEN=Color.rgb(47,113,82),CREAM=Color.rgb(244,241,235),MUTED=Color.rgb(106,117,108);
    private TextView status,threadLabel,transcript,setupStatus;private Button call,mute,speaker;private LinearLayout setup;
    private CallService service;private boolean bound;private CallSession.State state=CallSession.State.IDLE;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){refreshSetup();handler.postDelayed(this,1500);}};
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder binder){service=((CallService.LocalBinder)binder).service();bound=true;service.observe(MainActivity.this);}
        public void onServiceDisconnected(ComponentName name){service=null;changed(CallSession.State.IDLE,"Call disconnected. Your thread is saved.");}
    };
    @Override public void onCreate(Bundle saved){super.onCreate(saved);build();bound=bindService(new Intent(this,CallService.class),connection,Context.BIND_AUTO_CREATE);}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable background(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));d.setStroke(dp(1),Color.rgb(217,222,212));return d;}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(6),0,dp(6));return t;}
    private Button button(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextColor(INK);b.setBackground(background(Color.WHITE,28));b.setMinHeight(dp(52));b.setOnClickListener(v->action.run());return b;}
    private void addButton(LinearLayout layout,String label,Runnable action){Button b=button(label,action);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(52));lp.setMargins(0,dp(8),0,0);layout.addView(b,lp);}
    private void build(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(CREAM);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(24),dp(36),dp(24),dp(30));scroll.addView(root);setContentView(scroll);
        root.setOnApplyWindowInsetsListener((v,insets)->{if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(dp(24),bars.top+dp(24),dp(24),bars.bottom+dp(24));}else{v.setPadding(dp(24),insets.getSystemWindowInsetTop()+dp(24),dp(24),insets.getSystemWindowInsetBottom()+dp(24));}return insets;});
        TextView brand=text("LOCAL VOICE",13,GREEN);brand.setLetterSpacing(.18f);brand.setTypeface(null,Typeface.BOLD);root.addView(brand);
        TextView headline=text("One thread.\nEvery call.",36,INK);headline.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));root.addView(headline);
        root.addView(text("Pick up where you left off with Codex on this phone.",16,MUTED));
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setGravity(Gravity.CENTER);card.setPadding(dp(20),dp(28),dp(20),dp(24));card.setBackground(background(Color.WHITE,28));LinearLayout.LayoutParams cardLp=new LinearLayout.LayoutParams(-1,-2);cardLp.setMargins(0,dp(24),0,dp(20));root.addView(card,cardLp);
        TextView avatar=text("C",40,GREEN);avatar.setGravity(Gravity.CENTER);avatar.setBackground(background(Color.rgb(229,237,228),50));card.addView(avatar,new LinearLayout.LayoutParams(dp(92),dp(92)));
        TextView name=text("Codex",28,INK);name.setTypeface(null,Typeface.BOLD);card.addView(name);
        card.addView(text("THIS DEVICE · TERMUX",11,MUTED));
        status=text("Ready when you are",15,GREEN);status.setGravity(Gravity.CENTER);card.addView(status);
        threadLabel=text("Your first call starts a conversation",12,MUTED);threadLabel.setGravity(Gravity.CENTER);card.addView(threadLabel);
        call=button("Call Codex",this::toggleCall);call.setTextColor(Color.WHITE);call.setBackground(background(GREEN,40));LinearLayout.LayoutParams callLp=new LinearLayout.LayoutParams(-1,dp(64));callLp.setMargins(0,dp(24),0,dp(12));card.addView(call,callLp);
        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);mute=button("Mute",()->{if(service!=null){service.mute();mute.setText(service.isMuted()?"Unmute":"Mute");}});speaker=button("Speaker",()->{if(service!=null){service.speaker();speaker.setText(service.isSpeaker()?"Speaker":"Earpiece");}});controls.addView(mute,new LinearLayout.LayoutParams(0,dp(52),1));controls.addView(speaker,new LinearLayout.LayoutParams(0,dp(52),1));card.addView(controls);mute.setEnabled(false);speaker.setEnabled(false);
        transcript=text("Your call disconnects when you hang up. Your conversation stays.",14,MUTED);root.addView(transcript);
        addButton(root,"Setup & connection",()->setup.setVisibility(setup.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
        setup=new LinearLayout(this);setup.setOrientation(LinearLayout.VERTICAL);setup.setVisibility(TermuxBridge.installed(this)?View.GONE:View.VISIBLE);root.addView(setup);
        setupStatus=text("",14,INK);setup.addView(setupStatus);
        setup.addView(text("1 · Termux",19,INK));
        addButton(setup,"Get Termux from F-Droid",()->openUrl("https://f-droid.org/en/packages/com.termux/"));
        addButton(setup,"Open Termux",()->{if(TermuxBridge.installed(this))TermuxBridge.open(this);else explain("Install Termux first","Use the F-Droid page. Android will ask before installation. Keep all Termux add-ons from the same source.");});
        setup.addView(text("2 · Codex runtime",19,INK));
        setup.addView(text("Community port: DioNanos/codex-termux. Android 10+ and ARM64. Realtime still depends on account access. Installation and sign-in happen in Termux.",14,MUTED));
        addButton(setup,"Review Codex Termux project",()->openUrl("https://github.com/DioNanos/codex-termux"));
        addButton(setup,"Review & copy install commands",()->new AlertDialog.Builder(this).setTitle("Community software").setMessage("This installs @mmmbuto/codex-cli-termux 0.156.1-termux.1, a third-party Codex port. It can access your Termux files and Codex login. Review its source before installing. These commands are copied, never run automatically.\n\n"+TermuxBridge.SETUP).setNegativeButton("Cancel",null).setPositiveButton("Copy commands",(d,w)->copy(TermuxBridge.SETUP)).show());
        addButton(setup,"Check installed Codex",()->runTermux(false));
        setup.addView(text("3 · Start the local server",19,INK));
        setup.addView(text("Pair this app with Termux to require a local capability token. Control stays at 127.0.0.1:8765; audio and inference still use OpenAI over the internet. Stop the server in Termux when finished.",14,MUTED));
        addButton(setup,"Pair this app with Termux",this::pair);
        addButton(setup,"Retry saved pairing",()->{if(state!=CallSession.State.IDLE&&state!=CallSession.State.ERROR){explain("End the call first","Pairing cannot be retried during a call.");return;}if(!new ControlCredential(this).exists()){explain("No pairing saved","Create a pairing first.");return;}new AlertDialog.Builder(this).setTitle("Retry the saved pairing?").setMessage("This reuses the same saved capability. No new credential is generated. Wait for any running setup command to finish, and stop any old app-server in Termux first.").setNegativeButton("Cancel",null).setPositiveButton("Retry",(d,w)->{try{TermuxBridge.provision(this);}catch(Exception e){explain("Pairing needs attention",e.getMessage());}}).show();});
        addButton(setup,"Copy paired server command",()->copy(TermuxBridge.START));
        addButton(setup,"Start server in Termux",()->new AlertDialog.Builder(this).setTitle("Start local Codex?").setMessage("This starts the installed Codex app-server on 127.0.0.1:8765 using your existing local pairing. It requires a capability token. Stop any old server in Termux first. It is not exposed to your Wi-Fi network.").setNegativeButton("Cancel",null).setPositiveButton("Start",(d,w)->runTermux(true)).show());
        addButton(setup,"Optional Termux command access",this::termuxAccess);
        setup.addView(text("Android can stop either app. Active calls use a microphone notification; there is no automatic microphone restart. Call again to resume the same thread after restarting Termux.",14,MUTED));
        addButton(setup,"Open Termux app settings",()->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:com.termux"))));
        addButton(setup,"Recover existing thread ID",this::recover);
        refreshSetup();
    }
    private void toggleCall(){
        if(state!=CallSession.State.IDLE&&state!=CallSession.State.ERROR){if(service!=null)service.hangup();return;}
        if(!new ControlCredential(this).ready()){setup.setVisibility(View.VISIBLE);explain("Pair with Termux first","Setup creates local control access only after your explicit approval. No microphone or connection has started.");return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){new AlertDialog.Builder(this).setTitle("Allow your voice for this call?").setMessage("Local Voice needs your microphone during calls. Audio is sent to OpenAI through Codex realtime. It stops when you hang up.").setNegativeButton("Cancel",null).setPositiveButton("Continue",(d,w)->requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},20)).show();return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!getPreferences(0).getBoolean("askedNotifications",false)){getPreferences(0).edit().putBoolean("askedNotifications",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},21);return;}
        startForegroundService(new Intent(this,CallService.class));
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] result){super.onRequestPermissionsResult(request,permissions,result);if(request==20){if(result.length>0&&result[0]==PackageManager.PERMISSION_GRANTED)toggleCall();else explain("Microphone is off","No call started. You can enable microphone access in Android app settings when you're ready.");}else if(request==21)toggleCall();else if(request==22)refreshSetup();}
    private void runTermux(boolean start){try{if(!TermuxBridge.installed(this)){explain("Termux is missing","Install Termux using the F-Droid button first.");return;}if(checkSelfPermission(TermuxBridge.PERMISSION)!=PackageManager.PERMISSION_GRANTED){termuxAccess();return;}TermuxBridge.run(this,start);getSharedPreferences("setup",0).edit().putString("result",start?"Server launch requested. Open Termux to check its output, then call.":"Checking Codex…").apply();}catch(Exception e){explain("Termux access needed","Enable optional command access, or copy the command and run it yourself in Termux. "+e.getMessage());}}
    private void termuxAccess(){new AlertDialog.Builder(this).setTitle("Optional command access").setMessage("Android can grant Local Voice permission to run commands inside Termux. This is broad access to Termux files and login state. This app only checks Codex or starts its local server. You can instead copy commands manually.\n\nTermux also requires allow-external-apps=true. Copy the configuration command, run it in Termux, then grant access.").setNegativeButton("Cancel",null).setNeutralButton("Copy configuration",(d,w)->copy(TermuxBridge.ENABLE)).setPositiveButton("Grant access",(d,w)->requestPermissions(new String[]{TermuxBridge.PERMISSION},22)).show();}
    private void pair(){
        if(state!=CallSession.State.IDLE&&state!=CallSession.State.ERROR){explain("End the call first","Pairing cannot change during a call.");return;}
        if(new ControlCredential(this).pending()){explain("Pairing is pending","Wait for Termux to finish. If the earlier attempt was interrupted, use Retry saved pairing to reuse its capability.");return;}
        if(!TermuxBridge.installed(this)){explain("Install Termux first","Get Termux from the setup link before pairing.");return;}
        if(checkSelfPermission(TermuxBridge.PERMISSION)!=PackageManager.PERMISSION_GRANTED){termuxAccess();return;}
        new AlertDialog.Builder(this).setTitle("Create persistent local access?").setMessage("This creates a random local capability for this app to control Codex in Termux. It is stored encrypted on Android and in a private Termux file, and is separate from your OpenAI login. The token is passed directly to Termux without showing it or putting it on the clipboard. This starts the paired server. Stop any old server first. Re-pairing replaces the previous capability. No LAN access is enabled. Only pair with a Termux installation and apps you trust. Keep Termux verbose/debug logging off; those logs can expose command input.").setNegativeButton("Cancel",null).setPositiveButton("Pair & start",(d,w)->{try{new ControlCredential(this).create();TermuxBridge.provision(this);getSharedPreferences("setup",0).edit().putString("result","Pairing request sent. Wait for confirmation, then open Termux and call.").apply();refreshSetup();}catch(Exception e){explain("Pairing not complete",e.getMessage());}}).show();
    }
    private void recover(){if(state!=CallSession.State.IDLE&&state!=CallSession.State.ERROR){explain("End the call first","Your current call is using the saved conversation.");return;}EditText input=new EditText(this);input.setSingleLine();input.setHint("Existing Codex thread ID");new AlertDialog.Builder(this).setTitle("Recover your conversation").setMessage("Use the exact existing thread ID from Codex. This changes only the saved pointer; it does not delete or create a conversation.").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{String id=input.getText().toString().trim();if(!id.matches("[A-Za-z0-9_-]{8,200}")){explain("Invalid thread ID","Paste the existing Codex thread ID.");return;}new SessionStore(this).saveThread(id);refreshSetup();}).show();}
    private void copy(String value){((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Local Voice setup",value));Toast.makeText(this,"Copied. Paste it in Termux to review and run.",Toast.LENGTH_LONG).show();}
    private void openUrl(String url){startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
    private void explain(String title,String detail){new AlertDialog.Builder(this).setTitle(title).setMessage(detail).setPositiveButton("OK",null).show();}
    private void refreshSetup(){SessionStore store=new SessionStore(this);String id=store.thread();threadLabel.setText(id.isEmpty()?"Your first call starts a conversation":"Continuing your saved conversation");setupStatus.setText((TermuxBridge.installed(this)?"Termux detected":"Termux is not installed")+(new ControlCredential(this).exists()?" · Pairing saved":" · Not paired")+"\n"+getSharedPreferences("setup",0).getString("result","Codex has not been checked. Use Check installed Codex, or start it manually."));}
    @Override public void changed(CallSession.State state,String message){this.state=state;status.setText(message);boolean active=state!=CallSession.State.IDLE&&state!=CallSession.State.ERROR;call.setText(active?"Hang up":"Call Codex");call.setBackground(background(active?Color.rgb(174,67,55):GREEN,40));call.setEnabled(state!=CallSession.State.STOPPING);mute.setEnabled(state==CallSession.State.LIVE);speaker.setEnabled(state==CallSession.State.LIVE);if(service!=null){mute.setText(service.isMuted()?"Unmute":"Mute");speaker.setText(service.isSpeaker()?"Speaker":"Earpiece");}refreshSetup();}
    @Override public void transcript(String role,String text){transcript.setText((role.isEmpty()?"":role+": ")+text);}
    @Override public void onResume(){super.onResume();handler.post(refresh);}
    @Override public void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    @Override public void onDestroy(){if(service!=null)service.observe(null);if(bound)unbindService(connection);super.onDestroy();}
}
