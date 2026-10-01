package dev.possibilities.voicedialer;
import okhttp3.*;
import java.util.concurrent.TimeUnit;
final class LoopbackTransport implements CallSession.Transport {
    private final OkHttpClient client=new OkHttpClient.Builder().proxy(java.net.Proxy.NO_PROXY).followRedirects(false).followSslRedirects(false).connectTimeout(5,TimeUnit.SECONDS).readTimeout(0,TimeUnit.SECONDS).pingInterval(15,TimeUnit.SECONDS).build();
    private final java.util.function.Supplier<String> credential;
    LoopbackTransport(java.util.function.Supplier<String> credential){this.credential=credential;}
    volatile CallSession session; private volatile WebSocket socket;
    public void open() {
        String token=credential.get(); if(token.isEmpty()){session.fail("Pair this app with Termux in Setup before calling.");return;}
        socket=client.newWebSocket(new Request.Builder().url(CallSession.ENDPOINT).header("Authorization","Bearer "+token).build(),new WebSocketListener(){
            public void onOpen(WebSocket ws,Response response){synchronized(session){if(ws!=socket){ws.cancel();return;}session.opened();}}
            public void onMessage(WebSocket ws,String text){synchronized(session){if(ws==socket)session.receive(text);}}
            public void onClosed(WebSocket ws,int code,String reason){synchronized(session){if(ws==socket)session.disconnected(reason);}}
            public void onFailure(WebSocket ws,Throwable t,Response response){synchronized(session){if(ws==socket)session.disconnected(response!=null&&response.code()==401?"Pairing was rejected. Stop the old server and start the paired server in Setup.":"Open Termux and check the paired local app-server.");}}
        });
    }
    public void send(String data){WebSocket ws=socket;if(ws==null||ws.queueSize()>1024*1024||!ws.send(data))throw new IllegalStateException("Local WebSocket unavailable or overloaded");}
    public void close(){WebSocket ws=socket;socket=null;if(ws!=null)ws.close(1000,"Audio disconnected; thread retained");}
    void destroy(){close();client.dispatcher().executorService().shutdown();client.connectionPool().evictAll();}
}
