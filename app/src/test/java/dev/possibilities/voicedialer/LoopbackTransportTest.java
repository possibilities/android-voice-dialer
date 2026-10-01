package dev.possibilities.voicedialer;
import org.junit.*;
import static org.junit.Assert.*;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import java.net.InetAddress;
import java.util.concurrent.*;

public class LoopbackTransportTest {
    static class ClosingObserver extends WebSocketListener { @Override public void onClosing(WebSocket ws,int code,String reason){ws.close(code,reason);} }
    private MockWebServer server;private LoopbackTransport wire;private CallSession session;
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
    private final CountDownLatch failed=new CountDownLatch(1);
    @Before public void setup()throws Exception{server=new MockWebServer();server.start(InetAddress.getByName("127.0.0.1"),8765);}
    private void start(String token){wire=new LoopbackTransport(()->token);session=new CallSession(wire,new CallSessionTest.FakeStore(),new CallSessionTest.FakeAudio(),new CallSession.Listener(){public void changed(CallSession.State s,String m){if(s==CallSession.State.ERROR)failed.countDown();}public void transcript(String r,String t){}},timer);wire.session=session;session.dial();}
    @After public void cleanup()throws Exception{if(session!=null)session.hangup();if(wire!=null)wire.destroy();timer.shutdownNow();server.shutdown();}
    @Test public void probesWithoutCredentialThenAuthenticates()throws Exception{
        CountDownLatch initialized=new CountDownLatch(1);
        server.enqueue(new MockResponse().setResponseCode(401));
        server.enqueue(new MockResponse().withWebSocketUpgrade(new ClosingObserver(){public void onMessage(WebSocket ws,String text){if(text.contains("initialize"))initialized.countDown();}}));
        start("fixture-local-capability");assertTrue(initialized.await(5,TimeUnit.SECONDS));
        RecordedRequest probe=server.takeRequest(1,TimeUnit.SECONDS),authenticated=server.takeRequest(1,TimeUnit.SECONDS);
        assertNotNull(probe);assertNull(probe.getHeader("Authorization"));assertNull(probe.getHeader("Origin"));
        assertEquals("Bearer fixture-local-capability",authenticated.getHeader("Authorization"));assertNull(authenticated.getHeader("Origin"));
    }
    @Test public void refusesUnauthenticatedServerWithoutRevealingToken()throws Exception{
        server.enqueue(new MockResponse().withWebSocketUpgrade(new ClosingObserver(){}));
        start("fixture-local-capability");assertTrue(failed.await(5,TimeUnit.SECONDS));
        assertNull(server.takeRequest(1,TimeUnit.SECONDS).getHeader("Authorization"));assertEquals(1,server.getRequestCount());
    }
    @Test public void refusesWrongPairing()throws Exception{
        server.enqueue(new MockResponse().setResponseCode(401));server.enqueue(new MockResponse().setResponseCode(401));
        start("fixture-wrong-capability");assertTrue(failed.await(5,TimeUnit.SECONDS));assertEquals(CallSession.State.ERROR,session.state());assertEquals(2,server.getRequestCount());
    }
    @Test public void unpairedCallDoesNotConnect()throws Exception{start("");assertTrue(failed.await(1,TimeUnit.SECONDS));assertEquals(0,server.getRequestCount());}
}
