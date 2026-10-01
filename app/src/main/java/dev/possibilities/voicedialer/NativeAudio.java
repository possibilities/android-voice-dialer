package dev.possibilities.voicedialer;

import android.content.Context;
import android.media.*;
import org.webrtc.*;
import org.webrtc.audio.JavaAudioDeviceModule;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Audio stays in a microphone foreground service, never in an Activity/WebView. */
final class NativeAudio implements CallSession.Audio {
    private final Context context;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AudioManager manager;
    private PeerConnectionFactory factory; private JavaAudioDeviceModule module;
    private PeerConnection peer; private AudioSource source; private org.webrtc.AudioTrack track; private DataChannel events;
    private AudioFocusRequest focus;
    private volatile int epoch; private volatile boolean muted; private volatile boolean loudspeaker=true; private volatile boolean destroyed; private boolean routeAcquired; private int oldMode; private boolean oldSpeaker;
    volatile CallSession session;
    NativeAudio(Context context){this.context=context;manager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);}
    public void prepare(Consumer<String> offer){
        int generation=++epoch;
        dispatch(()->{
            if(generation!=epoch)return;
            try {
                focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).setOnAudioFocusChangeListener(change->{if(change<0)current(generation,()->session.hangup());}).build();
                if(manager.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED)throw new IllegalStateException("Another app is using call audio");
                oldMode=manager.getMode();oldSpeaker=manager.isSpeakerphoneOn();routeAcquired=true;manager.setMode(AudioManager.MODE_IN_COMMUNICATION);manager.setSpeakerphoneOn(loudspeaker);
                PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).setEnableInternalTracer(false).createInitializationOptions());
                module=JavaAudioDeviceModule.builder(context).setUseHardwareAcousticEchoCanceler(true).setUseHardwareNoiseSuppressor(true).createAudioDeviceModule();
                factory=PeerConnectionFactory.builder().setAudioDeviceModule(module).createPeerConnectionFactory();
                PeerConnection.RTCConfiguration configuration=new PeerConnection.RTCConfiguration(Collections.emptyList());
                configuration.sdpSemantics=PeerConnection.SdpSemantics.UNIFIED_PLAN;
                peer=factory.createPeerConnection(configuration,new PeerConnection.Observer(){
                    public void onSignalingChange(PeerConnection.SignalingState state){}
                    public void onIceConnectionChange(PeerConnection.IceConnectionState state){
                        if(generation!=epoch)return;
                        if(state==PeerConnection.IceConnectionState.CONNECTED||state==PeerConnection.IceConnectionState.COMPLETED)current(generation,()->session.mediaConnected());
                        else if(state==PeerConnection.IceConnectionState.FAILED||state==PeerConnection.IceConnectionState.DISCONNECTED)current(generation,()->session.fail("Voice connection failed. Check internet access, then call again."));
                    }
                    public void onIceConnectionReceivingChange(boolean receiving){}
                    public void onIceGatheringChange(PeerConnection.IceGatheringState state){}
                    public void onIceCandidate(IceCandidate candidate){}
                    public void onIceCandidatesRemoved(IceCandidate[] candidates){}
                    public void onAddStream(MediaStream stream){}
                    public void onRemoveStream(MediaStream stream){}
                    public void onDataChannel(DataChannel channel){}
                    public void onRenegotiationNeeded(){}
                    public void onAddTrack(RtpReceiver receiver,MediaStream[] streams){}
                });
                if(peer==null)throw new IllegalStateException("WebRTC unavailable");
                source=factory.createAudioSource(new MediaConstraints());track=factory.createAudioTrack("microphone",source);track.setEnabled(!muted);
                peer.addTrack(track,Collections.singletonList("voice"));
                events=peer.createDataChannel("oai-events",new DataChannel.Init());
                events.registerObserver(new DataChannel.Observer(){
                    public void onBufferedAmountChange(long old){}
                    public void onStateChange(){}
                    public void onMessage(DataChannel.Buffer buffer){
                        if(buffer.binary||generation!=epoch||buffer.data.remaining()>65536)return;
                        ByteBuffer data=buffer.data;byte[] bytes=new byte[data.remaining()];data.get(bytes);
                        // SDP transport drives audio; app-server remains the authoritative control stream.
                    }
                });
                peer.createOffer(new Observer(generation){
                    public void onCreateSuccess(SessionDescription description){dispatch(()->{
                        if(generation!=epoch||peer==null)return;
                        peer.setLocalDescription(new Observer(generation){public void onSetSuccess(){current(generation,()->offer.accept(description.description));}},description);
                    });}
                },new MediaConstraints());
            }catch(Exception e){current(generation,()->session.fail("Microphone setup failed: "+e.getMessage()));}
        });
    }
    public void answer(String sdp){int generation=epoch;dispatch(()->{if(generation!=epoch||peer==null)return;peer.setRemoteDescription(new Observer(generation){},new SessionDescription(SessionDescription.Type.ANSWER,sdp));});}
    void mute(boolean value){muted=value;dispatch(()->{if(track!=null)track.setEnabled(!value);});}
    void speaker(boolean value){loudspeaker=value;dispatch(()->{if(routeAcquired)manager.setSpeakerphoneOn(value);});}
    public void stop(){++epoch;dispatch(this::release);}
    private void release(){
        if(track!=null)track.setEnabled(false);
        if(events!=null){events.unregisterObserver();events.close();events.dispose();events=null;}
        if(peer!=null){peer.close();peer.dispose();peer=null;}
        if(track!=null){track.dispose();track=null;}
        if(source!=null){source.dispose();source=null;}
        if(factory!=null){factory.dispose();factory=null;}
        if(module!=null){module.release();module=null;}
        if(routeAcquired){routeAcquired=false;manager.setSpeakerphoneOn(oldSpeaker);manager.setMode(oldMode);}
        if(focus!=null){manager.abandonAudioFocusRequest(focus);focus=null;}
    }
    void destroy(){++epoch;destroyed=true;try{worker.execute(this::release);}catch(RejectedExecutionException ignored){}worker.shutdown();}
    private void current(int generation,Runnable action){synchronized(session){if(generation==epoch&&!destroyed)action.run();}}
    private void dispatch(Runnable action){if(destroyed)return;try{worker.execute(()->{if(!destroyed)action.run();});}catch(RejectedExecutionException ignored){/* Service teardown won the callback race. */}}
    private class Observer implements SdpObserver {
        private final int generation; Observer(int generation){this.generation=generation;}
        public void onCreateSuccess(SessionDescription description){}
        public void onSetSuccess(){}
        public void onCreateFailure(String error){current(generation,()->session.fail("Voice offer failed: "+error));}
        public void onSetFailure(String error){current(generation,()->session.fail("Voice negotiation failed: "+error));}
    }
}
