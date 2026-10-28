package com.example.bookkeeping;

import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

public final class WechatCrypto {
    private WechatCrypto() {}

    public static String signature(String... parts) {
        try {
            String[] sorted = parts.clone();
            Arrays.sort(sorted);
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(String.join("", sorted).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    public static boolean validSignature(String received, String... parts) {
        if (received == null || !received.matches("[a-fA-F0-9]{40}")) return false;
        return MessageDigest.isEqual(java.util.HexFormat.of().parseHex(received.toLowerCase()), java.util.HexFormat.of().parseHex(signature(parts)));
    }

    public static Document xml(String content) {
        try {
            if (content.length() > 65536) throw new IllegalArgumentException("消息过大");
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(content)));
        } catch (Exception error) { throw new IllegalArgumentException("微信 XML 消息无效", error); }
    }

    public static String tag(Document document, String name) {
        var nodes = document.getElementsByTagName(name);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent();
    }

    public static String decrypt(String encrypted, String encodingKey, String expectedAppId) {
        try {
            byte[] key = Base64.getDecoder().decode(encodingKey + "=");
            if (key.length != 32) throw new IllegalArgumentException("EncodingAESKey 不正确");
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(key, 0, 16));
            byte[] all = cipher.doFinal(Base64.getDecoder().decode(encrypted));
            int pad = all[all.length - 1] & 0xff;
            if (pad < 1 || pad > 32 || pad > all.length) throw new IllegalArgumentException("消息填充不正确");
            for (int i = all.length - pad; i < all.length; i++) if ((all[i] & 0xff) != pad) throw new IllegalArgumentException("消息填充不正确");
            int unpadded = all.length - pad;
            if (unpadded < 20) throw new IllegalArgumentException("消息长度不正确");
            int length = ByteBuffer.wrap(all, 16, 4).getInt();
            if (length < 0 || 20L + length > unpadded) throw new IllegalArgumentException("消息长度不正确");
            String appId = new String(all, 20 + length, unpadded - 20 - length, StandardCharsets.UTF_8);
            if (!appId.equals(expectedAppId)) throw new IllegalArgumentException("消息 AppID 不匹配");
            return new String(all, 20, length, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException error) { throw error; }
        catch (Exception error) { throw new IllegalArgumentException("微信消息解密失败", error); }
    }
}
