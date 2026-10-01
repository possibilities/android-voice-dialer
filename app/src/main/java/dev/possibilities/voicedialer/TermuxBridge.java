package dev.possibilities.voicedialer;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

final class TermuxBridge {
    static final String PERMISSION="com.termux.permission.RUN_COMMAND";
    static final String SETUP="pkg update\npkg install nodejs-lts\n# Community fork; inspect the project and package before installing.\nnpm install -g @mmmbuto/codex-cli-termux@0.156.1-termux.1\ncodex login\n";
    static final String START="codex -c 'realtime.type=\"conversational\"' app-server --listen ws://127.0.0.1:8765 --ws-auth capability-token --ws-token-file /data/data/com.termux/files/home/.localvoice/control.token";
    static final String ENABLE="mkdir -p ~/.termux\nprintf '\\nallow-external-apps=true\\n' >> ~/.termux/termux.properties\ntermux-reload-settings";
    static boolean installed(Context c){try{c.getPackageManager().getPackageInfo("com.termux",0);return true;}catch(PackageManager.NameNotFoundException e){return false;}}
    static void open(Context c){Intent launch=c.getPackageManager().getLaunchIntentForPackage("com.termux");if(launch!=null)c.startActivity(launch);}
    static void provision(Context c){
        if(c.checkSelfPermission(PERMISSION)!=PackageManager.PERMISSION_GRANTED)throw new SecurityException("Termux permission is not granted");
        String token=new ControlCredential(c).load();if(token.isEmpty())throw new SecurityException("Pairing not created");
        Intent result=new Intent(c,TermuxResultReceiver.class).setData(Uri.parse("localvoice://termux/pair"));
        int flags=PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_ONE_SHOT;if(Build.VERSION.SDK_INT>=31)flags|=PendingIntent.FLAG_MUTABLE;
        PendingIntent callback=PendingIntent.getBroadcast(c,12,result,flags);
        Intent intent=new Intent("com.termux.RUN_COMMAND").setClassName("com.termux","com.termux.app.RunCommandService")
            .putExtra("com.termux.RUN_COMMAND_PATH","/data/data/com.termux/files/usr/bin/bash")
            .putExtra("com.termux.RUN_COMMAND_ARGUMENTS",new String[]{"-c","umask 077; mkdir -p /data/data/com.termux/files/home/.localvoice && cat > /data/data/com.termux/files/home/.localvoice/control.token"})
            .putExtra("com.termux.RUN_COMMAND_BACKGROUND",true)
            .putExtra("com.termux.RUN_COMMAND_STDIN",token+"\n")
            .putExtra("com.termux.RUN_COMMAND_BACKGROUND_CUSTOM_LOG_LEVEL","0")
            .putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL","Pair Local Voice control")
            .putExtra("com.termux.RUN_COMMAND_PENDING_INTENT",callback);
        c.startService(intent);
    }
    static void run(Context c,boolean start){
        if(c.checkSelfPermission(PERMISSION)!=PackageManager.PERMISSION_GRANTED)throw new SecurityException("Termux permission is not granted");
        String token=start?new ControlCredential(c).load():"";
        if(start&&token.isEmpty())throw new SecurityException("Pair this app first");
        String command=start?"exec "+START:"if command -v codex >/dev/null 2>&1; then codex --version; else printf 'CODEX_MISSING\\n'; exit 127; fi";
        Intent result=new Intent(c,TermuxResultReceiver.class).setData(Uri.parse("localvoice://termux/"+(start?"start":"check")));
        int flags=PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_ONE_SHOT;
        if(Build.VERSION.SDK_INT>=31)flags|=PendingIntent.FLAG_MUTABLE;
        PendingIntent callback=PendingIntent.getBroadcast(c,start?11:10,result,flags);
        Intent intent=new Intent("com.termux.RUN_COMMAND").setClassName("com.termux","com.termux.app.RunCommandService")
            .putExtra("com.termux.RUN_COMMAND_PATH","/data/data/com.termux/files/usr/bin/bash")
            .putExtra("com.termux.RUN_COMMAND_ARGUMENTS",new String[]{"-l","-c",command})
            .putExtra("com.termux.RUN_COMMAND_WORKDIR","/data/data/com.termux/files/home")
            .putExtra("com.termux.RUN_COMMAND_BACKGROUND",!start)
            .putExtra("com.termux.RUN_COMMAND_SESSION_ACTION","0")
            .putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL",start?"Local Voice app-server":"Check Codex installation")
            .putExtra("com.termux.RUN_COMMAND_PENDING_INTENT",callback);
        c.startService(intent);
    }
}
