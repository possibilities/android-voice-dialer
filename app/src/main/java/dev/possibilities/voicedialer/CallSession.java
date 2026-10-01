package dev.possibilities.voicedialer;

import com.google.gson.*;
import java.util.*;
import java.util.concurrent.*;

/** One durable thread, many disposable calls. No remote endpoint or credentials. */
public final class CallSession {
    public static final String ENDPOINT = "ws://127.0.0.1:8765";
    public enum State { IDLE, CONNECTING, CALLING, LIVE, STOPPING, ERROR }
    public interface Transport { void open(); void send(String data); void close(); }
    public interface Store { String thread(); boolean uncertain(); void beginCreate(); void saveThread(String id); }
    public interface Audio { void prepare(java.util.function.Consumer<String> offer); void answer(String sdp); void stop(); }
    public interface Listener { void changed(State state, String message); void transcript(String role, String text); }
    private final Transport wire; private final Store store; private final Audio audio; private final Listener ui;
    private final ScheduledExecutorService timer; private final Map<Integer,String> pending = new HashMap<>();
    private State state = State.IDLE; private int sequence; private String thread; private boolean started, accepted, requested, connected;
    private ScheduledFuture<?> deadline; private long deadlineGeneration;
    public CallSession(Transport wire, Store store, Audio audio, Listener ui, ScheduledExecutorService timer) {
        this.wire=wire; this.store=store; this.audio=audio; this.ui=ui; this.timer=timer;
    }
    public synchronized State state() { return state; }
    public synchronized void dial() {
        if (state!=State.IDLE && state!=State.ERROR) return;
        if (store.uncertain() && store.thread().isEmpty()) { fail("An earlier thread creation was interrupted. Recover its thread ID in Setup before calling again."); return; }
        thread=store.thread(); started=false; accepted=false; requested=false; connected=false; pending.clear();
        change(State.CONNECTING,"Connecting to Codex on this phone…");
        timeout(15000,"Local Codex did not answer. Start app-server in Termux, then try again."); try { wire.open(); } catch(Exception e) { fail("Local connection could not start. Check pairing and Termux."); }
    }
    public synchronized void opened() {
        if (state!=State.CONNECTING) { wire.close(); return; }
        request("initialize", obj("clientInfo",obj("name","android_local_voice","title","Local Voice","version","0.1.0"),"capabilities",obj("experimentalApi",true)));
    }
    public synchronized void receive(String raw) {
        if (state==State.IDLE || state==State.ERROR) return;
        try {
            JsonObject message=JsonParser.parseString(raw).getAsJsonObject();
            if (message.has("method") && message.has("id")) {
                // Never interpret speech or a generic yes as consent to a tool action.
                wire.send(obj("id",message.get("id"),"error",obj("code",-32601,"message","This voice client cannot approve or execute tool requests. Use a full Codex client.")).toString());
                ui.transcript("system","A tool or approval request needs a full Codex client; no approval was given."); return;
            }
            if (message.has("id")) {
                int id=message.get("id").getAsInt(); String method=pending.remove(id); if(method==null) return;
                if(message.has("error")) {
                    String error=message.getAsJsonObject("error").get("message").getAsString();
                    if (method.equals("thread/realtime/stop") && state==State.STOPPING) { finish("Call ended. Thread kept."); return; }
                    fail(method+": "+error); return;
                }
                JsonObject result=message.has("result") && message.get("result").isJsonObject()?message.getAsJsonObject("result"):new JsonObject();
                switch(method) {
                    case "initialize":
                        notify("initialized",new JsonObject());
                        if(state==State.STOPPING) { finish("Call cancelled. Thread kept."); break; }
                        request("thread/realtime/listVoices",new JsonObject()); break;
                    case "thread/realtime/listVoices":
                        if(state==State.STOPPING) { finish("Call cancelled. Thread kept."); break; }
                        if(thread.isEmpty()) { store.beginCreate(); request("thread/start",obj("ephemeral",false)); }
                        else request("thread/resume",obj("threadId",thread));
                        break;
                    case "thread/start": case "thread/resume":
                        JsonObject t=result.getAsJsonObject("thread");
                        if(t==null || !t.has("id") || (t.has("ephemeral") && t.get("ephemeral").getAsBoolean())) { fail("Codex returned no durable thread. Refusing to start audio."); break; }
                        String returned=t.get("id").getAsString();
                        if (!thread.isEmpty() && !thread.equals(returned)) { fail("Codex returned a different thread. Your saved conversation was not replaced."); break; }
                        thread=returned; store.saveThread(thread);
                        if(state==State.STOPPING) { finish("Call cancelled. Thread kept."); break; }
                        change(State.CALLING,"Preparing your voice call…");
                        audio.prepare(this::offerReady);
                        timeout(30000,"Codex realtime did not start. Check your account's realtime access and Termux logs."); break;
                    case "thread/realtime/start":
                        accepted=true;
                        if(state==State.STOPPING) stopRemote(); else maybeLive(); break;
                    case "thread/realtime/stop": finish("Call ended. Thread kept."); break;
                }
                return;
            }
            String method=string(message,"method"); JsonObject p=message.has("params")?message.getAsJsonObject("params"):new JsonObject();
            if(!thread.equals(string(p,"threadId"))) return;
            switch(method) {
                case "thread/realtime/started": started=true; if(state==State.STOPPING) stopRemote(); else maybeLive(); break;
                case "thread/realtime/sdp": if(state==State.CALLING) audio.answer(string(p,"sdp")); break;
                case "thread/realtime/transcript/done": ui.transcript(string(p,"role"),string(p,"text")); break;
                case "thread/realtime/error": fail("Codex realtime: "+string(p,"message")); break;
                case "thread/realtime/closed": finish("Call ended. Thread kept."); break;
            }
        } catch(Exception e) { fail("Incompatible Codex response: "+e.getClass().getSimpleName()); }
    }
    private void maybeLive() {
        if(state!=State.CALLING || !started || !accepted || !connected) return;
        cancelTimeout();
        change(State.LIVE,"Connected · your ongoing conversation");
    }
    private synchronized void offerReady(String sdp) {
        if(state!=State.CALLING) { audio.stop(); return; }
        requested=true;
        request("thread/realtime/start",obj("threadId",thread,"outputModality","audio","transport",obj("type","webrtc","sdp",sdp)));
    }
    public synchronized void mediaConnected() { connected=true; maybeLive(); }
    public synchronized void hangup() {
        if(state==State.IDLE || state==State.ERROR || state==State.STOPPING) return;
        audio.stop(); change(State.STOPPING,"Ending call…");
        if(requested && accepted) stopRemote();
        else if(!requested && pending.values().stream().noneMatch(x->x.equals("thread/start") || x.equals("thread/resume"))) { finish("Call cancelled. Thread kept."); return; }
        if(state==State.STOPPING)timeout(30000,"Call disconnected. The thread is kept; check Termux if voice cleanup was interrupted.");
    }
    private void stopRemote() { request("thread/realtime/stop",obj("threadId",thread)); }
    public synchronized void disconnected(String reason) { if(state==State.IDLE||state==State.ERROR)return; fail("Connection lost. Your saved thread is kept. "+reason); }
    public synchronized void fail(String error) {
        audio.stop(); cancelTimeout();
        if(requested && thread!=null && !thread.isEmpty()) try { wire.send(obj("id",++sequence,"method","thread/realtime/stop","params",obj("threadId",thread)).toString()); } catch(Exception ignored) {}
        change(State.ERROR,error); pending.clear(); wire.close();
    }
    private void finish(String message) { audio.stop(); cancelTimeout(); pending.clear(); change(State.IDLE,message); wire.close(); }
    private void request(String method,JsonObject params) { int id=++sequence; pending.put(id,method); try { wire.send(obj("id",id,"method",method,"params",params).toString()); } catch(Exception e) { pending.remove(id); fail("Local connection closed. Your saved thread is kept."); } }
    private void notify(String method,JsonObject params) { wire.send(obj("method",method,"params",params).toString()); }
    private void change(State next,String message) { state=next; ui.changed(next,message); }
    private void timeout(long ms,String reason) { cancelTimeout(); long generation=deadlineGeneration; deadline=timer.schedule(()->{synchronized(this){if(generation!=deadlineGeneration)return;if(state==State.STOPPING)finish(reason);else fail(reason);}},ms,TimeUnit.MILLISECONDS); }
    private void cancelTimeout() { ++deadlineGeneration; if(deadline!=null)deadline.cancel(false); deadline=null; }
    static String string(JsonObject object,String key) { return object.has(key)&&!object.get(key).isJsonNull()?object.get(key).getAsString():""; }
    static JsonObject obj(Object... pairs) { JsonObject o=new JsonObject(); for(int i=0;i<pairs.length;i+=2){Object v=pairs[i+1];o.add(pairs[i].toString(),v instanceof JsonElement?(JsonElement)v:new Gson().toJsonTree(v));}return o; }
}
