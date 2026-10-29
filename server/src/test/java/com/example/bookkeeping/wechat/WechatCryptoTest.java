package com.example.bookkeeping.wechat;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class WechatCryptoTest {
    @Test void verifiesSignatureWithoutTrustingMalformedInput() {
        String signed = WechatCrypto.signature("token", "123", "456");
        assertTrue(WechatCrypto.validSignature(signed, "token", "123", "456"));
        assertFalse(WechatCrypto.validSignature("oops", "token", "123", "456"));
    }

    @Test void decryptsWechatMessageWithThirtyTwoBytePadding() throws Exception {
        byte[] key = new byte[32]; Arrays.fill(key, (byte) 7);
        String appId = "wx-test";
        byte[] xml = "<xml><Content>午饭 20元</Content></xml>".getBytes(StandardCharsets.UTF_8);
        byte[] suffix = appId.getBytes(StandardCharsets.UTF_8);
        ByteBuffer plain = ByteBuffer.allocate(16 + 4 + xml.length + suffix.length);
        plain.put(new byte[16]).putInt(xml.length).put(xml).put(suffix);
        int pad = 32 - plain.array().length % 32;
        byte[] padded = Arrays.copyOf(plain.array(), plain.array().length + pad);
        Arrays.fill(padded, plain.array().length, padded.length, (byte) pad);
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(key, 0, 16));
        String encrypted = Base64.getEncoder().encodeToString(cipher.doFinal(padded));
        String encodingKey = Base64.getEncoder().withoutPadding().encodeToString(key);
        assertEquals(new String(xml, StandardCharsets.UTF_8), WechatCrypto.decrypt(encrypted, encodingKey, appId));
        assertThrows(IllegalArgumentException.class, () -> WechatCrypto.decrypt(encrypted, encodingKey, "other-app"));
    }

    @Test void rejectsDoctype() {
        assertThrows(IllegalArgumentException.class, () -> WechatCrypto.xml("<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]><x>&e;</x>"));
    }
}
