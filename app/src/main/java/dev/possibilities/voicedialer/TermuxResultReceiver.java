package dev.possibilities.voicedialer;
import android.content.*;
import android.os.Bundle;
public final class TermuxResultReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        Bundle result=intent.getBundleExtra("result");if(result==null)return;
        if(intent.getData()!=null&&"/pair".equals(intent.getData().getPath()))new ControlCredential(context).completed();
        String out=result.getString("stdout","");String err=result.getString("errmsg",result.getString("stderr",""));
        String message;
        if(result.getInt("err",android.app.Activity.RESULT_OK)!=android.app.Activity.RESULT_OK)message="Termux could not run the command. "+err;
        else if(result.getInt("exitCode",0)==127&&out.contains("CODEX_MISSING"))message="Codex is missing. Review the community package and install it in Termux.";
        else if(result.getInt("exitCode",0)!=0)message="Termux command ended with an error. "+err;
        else if(intent.getData()!=null&&"/pair".equals(intent.getData().getPath())){
            try{TermuxBridge.run(context,true);message="Pairing configured; server launch requested. Open Termux to view its output.";}catch(Exception e){message="Pairing configured. Tap Start server after returning to the app.";}
        } else message=out.trim().isEmpty()?"Termux command ended.":out.trim();
        context.getSharedPreferences("setup",Context.MODE_PRIVATE).edit().putString("result",message.substring(0,Math.min(message.length(),500))).apply();
    }
}
