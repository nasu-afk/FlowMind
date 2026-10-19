package com.flowmind.api.credential;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public class EnvelopeCrypto {
    private static final int NONCE_BYTES=12; private static final int TAG_BITS=128;
    private final SecretKey masterKey; private final SecureRandom random=new SecureRandom(); private final String keyVersion;
    public EnvelopeCrypto(String encodedMasterKey,String keyVersion){byte[] raw=Base64.getDecoder().decode(encodedMasterKey);if(raw.length!=32)throw new IllegalArgumentException("Master key must be 32 bytes");this.masterKey=new SecretKeySpec(raw,"AES");this.keyVersion=keyVersion;}
    public Encrypted encrypt(byte[] plaintext,String context){try{KeyGenerator generator=KeyGenerator.getInstance("AES");generator.init(256);SecretKey dataKey=generator.generateKey();return new Encrypted(seal(plaintext,dataKey,context),seal(dataKey.getEncoded(),masterKey,"key:"+context),keyVersion);}catch(Exception error){throw new IllegalStateException("Credential encryption failed",error);}}
    public byte[] decrypt(Encrypted encrypted,String context){try{byte[] rawKey=open(encrypted.encryptedDataKey(),masterKey,"key:"+context);return open(encrypted.ciphertext(),new SecretKeySpec(rawKey,"AES"),context);}catch(Exception error){throw new IllegalStateException("Credential decryption failed",error);}}
    private byte[] seal(byte[] input,SecretKey key,String aad)throws Exception{byte[] nonce=new byte[NONCE_BYTES];random.nextBytes(nonce);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(TAG_BITS,nonce));cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));byte[] encrypted=cipher.doFinal(input);return ByteBuffer.allocate(nonce.length+encrypted.length).put(nonce).put(encrypted).array();}
    private byte[] open(byte[] input,SecretKey key,String aad)throws Exception{ByteBuffer buffer=ByteBuffer.wrap(input);byte[] nonce=new byte[NONCE_BYTES];buffer.get(nonce);byte[] ciphertext=new byte[buffer.remaining()];buffer.get(ciphertext);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(TAG_BITS,nonce));cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));return cipher.doFinal(ciphertext);}
    public record Encrypted(byte[] ciphertext,byte[] encryptedDataKey,String keyVersion){}
}

