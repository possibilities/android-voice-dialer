package dev.possibilities.voicedialer;
import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Created only from the user's explicit pairing confirmation, never on app launch. */
final class ControlCredential {
    private static final String ALIAS="localvoice-control-v1";
    private final SharedPreferences prefs;
    ControlCredential(Context c){prefs=c.getSharedPreferences("local-control",Context.MODE_PRIVATE);}
    boolean pending(){return prefs.getBoolean("pending",false);}
    boolean ready(){return prefs.getBoolean("ready",false)&&!pending();}
    void provisioning(){prefs.edit().putBoolean("pending",true).putBoolean("ready",false).commit();}
    void completed(boolean success){prefs.edit().putBoolean("pending",false).putBoolean("ready",success).commit();}
    boolean exists(){return prefs.contains("sealed");}
    String load(){
        if(!exists())return "";
        try{
            KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,store.getKey(ALIAS,null),new GCMParameterSpec(128,Base64.decode(prefs.getString("iv",""),Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(prefs.getString("sealed",""),Base64.NO_WRAP)),StandardCharsets.UTF_8);
        }catch(Exception e){throw new IllegalStateException("Local pairing cannot be opened. Pair again in Setup.");}
    }
    void create(){
        if(pending())throw new IllegalStateException("Pairing is still pending. Use Retry saved pairing instead of creating a new capability.");
        try{
            byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);
            String token=Base64.encodeToString(bytes,Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);
            KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
            SecretKey key;
            if(store.containsAlias(ALIAS))key=(SecretKey)store.getKey(ALIAS,null);
            else {KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());key=generator.generateKey();}
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key);
            String sealed=Base64.encodeToString(cipher.doFinal(token.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
            if(!prefs.edit().putString("sealed",sealed).putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).putBoolean("pending",true).putBoolean("ready",false).commit())throw new IllegalStateException();
        }catch(Exception e){throw new IllegalStateException("Could not create the local pairing. Nothing was sent to Termux.");}
    }
}
