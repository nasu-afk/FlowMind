package com.flowmind.api.identity;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;

@Component
public class TotpService {
    private static final String ALPHABET="ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private final SecureRandom random=new SecureRandom();

    public String generateSecret() {
        byte[] bytes=new byte[20]; random.nextBytes(bytes); return encodeBase32(bytes);
    }

    public boolean verify(String secret,String code,Instant now) {
        if(code==null||!code.matches("\\d{6}")) return false;
        long counter=now.getEpochSecond()/30;
        for(long offset=-1;offset<=1;offset++) if(MessageDigest.isEqual(code.getBytes(),generate(secret,counter+offset).getBytes())) return true;
        return false;
    }

    String generate(String secret,long counter) {
        try {
            Mac mac=Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decodeBase32(secret),"HmacSHA1"));
            byte[] hash=mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset=hash[hash.length-1]&15;
            int binary=((hash[offset]&127)<<24)|((hash[offset+1]&255)<<16)|((hash[offset+2]&255)<<8)|(hash[offset+3]&255);
            return String.format("%06d",binary%1_000_000);
        } catch(Exception failure) { throw new IllegalStateException("TOTP generation failed",failure); }
    }

    private String encodeBase32(byte[] input) {
        StringBuilder result=new StringBuilder(); int buffer=0,bits=0;
        for(byte value:input){buffer=(buffer<<8)|(value&255);bits+=8;while(bits>=5){result.append(ALPHABET.charAt((buffer>>(bits-5))&31));bits-=5;}}
        if(bits>0)result.append(ALPHABET.charAt((buffer<<(5-bits))&31));
        return result.toString();
    }

    private byte[] decodeBase32(String input) {
        String normalized=input.replace("=","").replace(" ","").toUpperCase();
        byte[] output=new byte[normalized.length()*5/8]; int buffer=0,bits=0,index=0;
        for(char character:normalized.toCharArray()){
            int value=ALPHABET.indexOf(character);if(value<0)throw new IllegalArgumentException("Invalid Base32 secret");
            buffer=(buffer<<5)|value;bits+=5;if(bits>=8){output[index++]=(byte)((buffer>>(bits-8))&255);bits-=8;}
        }
        return output;
    }
}
