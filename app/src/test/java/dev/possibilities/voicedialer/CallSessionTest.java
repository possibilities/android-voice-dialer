package dev.possibilities.voicedialer;
import org.junit.*;
import static org.junit.Assert.*;
import com.google.gson.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class CallSessionTest {
    static final class TestTimer extends ScheduledThreadPoolExecutor { Runnable task; TestTimer(){super(1);} @Override public ScheduledFuture<?> schedule(Runnable r,long delay,TimeUnit unit){task=r;return super.schedule(r,1,TimeUnit.DAYS);} }
    final TestTimer timer=new TestTimer();
    final FakeWire wire=new FakeWire();final FakeStore store=new FakeStore();final FakeAudio audio=new FakeAudio();
    CallSession session;String last="";
    @Before public void setup(){session=new CallSession(wire,store,audio,new CallSession.Listener(){public void changed(CallSession.State s,String m){last=m;}public void transcript(String r,String t){}},timer);}
    @After public void cleanup(){timer.shutdownNow();}
    static final class FakeWire implements CallSession.Transport {List<JsonObject> sent=new ArrayList<>();boolean closed;int opens;boolean throwOnSend;public void open(){opens++;}public void close(){closed=true;}public void send(String s){if(throwOnSend)throw new IllegalStateException("Closed socket");sent.add(JsonParser.parseString(s).getAsJsonObject());}JsonObject last(){return sent.get(sent.size()-1);}String method(){return last().get("method").getAsString();}}
    static final class FakeStore implements CallSession.Store {String id="";boolean pending;public String thread(){return id;}public boolean uncertain(){return pending;}public void beginCreate(){pending=true;}public void saveThread(String s){id=s;pending=false;}}
    static final class FakeAudio implements CallSession.Audio {Consumer<String> offer;int stops;String answer;public void prepare(Consumer<String> o){offer=o;}public void answer(String s){answer=s;}public void stop(){stops++;}}
    void reply(JsonObject result){int id=wire.last().get("id").getAsInt();session.receive(CallSession.obj("id",id,"result",result).toString());}
    void initialized(){session.dial();session.opened();assertEquals("initialize",wire.method());reply(new JsonObject());assertEquals("thread/realtime/listVoices",wire.method());reply(new JsonObject());}
    void threadReady(){initialized();reply(CallSession.obj("thread",CallSession.obj("id","thread_123456","ephemeral",false)));}
    void offered(){threadReady();audio.offer.accept("offer-sdp");assertEquals("thread/realtime/start",wire.method());}
    void event(String method,JsonObject params){session.receive(CallSession.obj("method",method,"params",params).toString());}
    void live(){offered();reply(new JsonObject());event("thread/realtime/started",CallSession.obj("threadId",store.id));session.mediaConnected();assertEquals(CallSession.State.LIVE,session.state());}
    @Test public void firstCallCreatesAndPersistsDurableThreadBeforeAudio(){threadReady();assertEquals("thread_123456",store.id);assertFalse(store.pending);assertNotNull(audio.offer);}
    @Test public void subsequentCallResumesWithoutCreating(){store.id="thread_123456";initialized();assertEquals("thread/resume",wire.method());assertEquals(store.id,wire.last().getAsJsonObject("params").get("threadId").getAsString());}
    @Test public void hangupKeepsThreadAndStopsOnlyRealtime(){live();session.hangup();assertEquals("thread/realtime/stop",wire.method());reply(new JsonObject());assertEquals("thread_123456",store.id);assertEquals(CallSession.State.IDLE,session.state());assertTrue(audio.stops>0);}
    @Test public void callTwiceCannotCreateDuplicateThreads(){session.dial();session.dial();assertEquals(1,wire.opens);}
    @Test public void noConnectedUiUntilMediaConnected(){offered();reply(new JsonObject());event("thread/realtime/started",CallSession.obj("threadId",store.id));assertEquals(CallSession.State.CALLING,session.state());session.mediaConnected();assertEquals(CallSession.State.LIVE,session.state());}
    @Test public void asyncAnswerIsApplied(){offered();event("thread/realtime/sdp",CallSession.obj("threadId",store.id,"sdp","answer-sdp"));assertEquals("answer-sdp",audio.answer);}
    @Test public void mismatchedThreadEventsAreIgnored(){offered();event("thread/realtime/sdp",CallSession.obj("threadId","wrong","sdp","bad"));assertNull(audio.answer);}
    @Test public void hangupDuringCreateStillSavesLateThread(){initialized();session.hangup();reply(CallSession.obj("thread",CallSession.obj("id","thread_123456")));assertEquals("thread_123456",store.id);assertEquals(CallSession.State.IDLE,session.state());assertNull(audio.offer);}
    @Test public void hangupDuringOfferNeverStartsVoice(){threadReady();session.hangup();audio.offer.accept("late-sdp");assertEquals(CallSession.State.IDLE,session.state());assertTrue(wire.sent.stream().noneMatch(x->"thread/realtime/start".equals(CallSession.string(x,"method"))));}
    @Test public void hangupDuringStartStopsLateSession(){offered();session.hangup();reply(new JsonObject());assertEquals("thread/realtime/stop",wire.method());assertNotEquals(CallSession.State.LIVE,session.state());}
    @Test public void uncertainCreateCannotSilentlyCreateAgain(){store.pending=true;session.dial();assertEquals(0,wire.opens);assertEquals(CallSession.State.ERROR,session.state());}
    @Test public void resumeFailurePreservesExistingThread(){store.id="thread_123456";initialized();int id=wire.last().get("id").getAsInt();session.receive(CallSession.obj("id",id,"error",CallSession.obj("message","Thread missing")).toString());assertEquals("thread_123456",store.id);assertEquals(CallSession.State.ERROR,session.state());}
    @Test public void ephemeralThreadIsRejected(){initialized();reply(CallSession.obj("thread",CallSession.obj("id","ephemeral","ephemeral",true)));assertEquals(CallSession.State.ERROR,session.state());assertEquals("",store.id);}
    @Test public void approvalRequestsNeverAutoApprove(){live();session.receive(CallSession.obj("id",900,"method","item/commandExecution/requestApproval","params",new JsonObject()).toString());assertTrue(wire.last().has("error"));assertFalse(wire.last().has("result"));}
    @Test public void obsoleteTimeoutCannotEndNewCall(){session.dial();Runnable old=timer.task;session.hangup();session.dial();old.run();assertEquals(CallSession.State.CONNECTING,session.state());}
    @Test public void hangupAfterSocketLossDoesNotThrow(){live();wire.throwOnSend=true;session.hangup();assertEquals(CallSession.State.ERROR,session.state());assertTrue(audio.stops>0);}
    @Test public void offerAfterSocketLossDoesNotThrow(){threadReady();wire.throwOnSend=true;audio.offer.accept("sdp");assertEquals(CallSession.State.ERROR,session.state());}
    @Test public void initializeSendFailureIsVisible(){session.dial();wire.throwOnSend=true;session.opened();assertEquals(CallSession.State.ERROR,session.state());}
    @Test public void backendAddressIsUnchangeableLoopback(){assertEquals("ws://127.0.0.1:8765",CallSession.ENDPOINT);}
    @Test public void reconnectReusesSameThread(){live();session.hangup();reply(new JsonObject());session.dial();session.opened();reply(new JsonObject());reply(new JsonObject());assertEquals("thread/resume",wire.method());}
}
