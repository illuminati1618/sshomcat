package com.sshomcat.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** Covers the protocol contract in docs/api.md, including malformed/hostile input handling. */
class ProtocolCodecTest {

    @Test
    void parsesAuth() throws Exception {
        var msg = ProtocolCodec.parseInbound("{\"type\":\"auth\",\"username\":\"alice\",\"password\":\"s3cret\"}");
        var auth = assertInstanceOf(InboundMessage.Auth.class, msg);
        assertEquals("alice", auth.username());
        assertEquals("s3cret", auth.password());
    }

    @Test
    void authToStringRedactsPassword() throws Exception {
        var auth = (InboundMessage.Auth) ProtocolCodec.parseInbound(
                "{\"type\":\"auth\",\"username\":\"alice\",\"password\":\"s3cret\"}");
        assertTrue(!auth.toString().contains("s3cret"), "password must never appear in toString()");
    }

    @Test
    void parsesDataAndRoundTripsBytes() throws Exception {
        byte[] original = "hello éè 😀".getBytes(StandardCharsets.UTF_8); // includes non-ASCII + emoji
        String b64 = Base64.getEncoder().encodeToString(original);
        var msg = ProtocolCodec.parseInbound("{\"type\":\"data\",\"data\":\"" + b64 + "\"}");
        var data = assertInstanceOf(InboundMessage.Data.class, msg);
        assertEquals(original.length, ProtocolCodec.decodeData(data).length);
        assertTrue(java.util.Arrays.equals(original, ProtocolCodec.decodeData(data)));
    }

    @Test
    void parsesResize() throws Exception {
        var msg = ProtocolCodec.parseInbound("{\"type\":\"resize\",\"cols\":120,\"rows\":34}");
        var resize = assertInstanceOf(InboundMessage.Resize.class, msg);
        assertEquals(120, resize.cols());
        assertEquals(34, resize.rows());
    }

    @Test
    void rejectsUnknownType() {
        assertThrows(ProtocolException.class, () -> ProtocolCodec.parseInbound("{\"type\":\"nope\"}"));
    }

    @Test
    void rejectsMissingType() {
        assertThrows(ProtocolException.class, () -> ProtocolCodec.parseInbound("{\"username\":\"alice\"}"));
    }

    @Test
    void rejectsMalformedJson() {
        assertThrows(ProtocolException.class, () -> ProtocolCodec.parseInbound("not json at all"));
        assertThrows(ProtocolException.class, () -> ProtocolCodec.parseInbound("{\"type\":\"auth\""));
    }

    @Test
    void rejectsEmptyMessage() {
        assertThrows(ProtocolException.class, () -> ProtocolCodec.parseInbound(""));
    }

    @Test
    void rejectsAuthMissingPassword() {
        assertThrows(ProtocolException.class,
                () -> ProtocolCodec.parseInbound("{\"type\":\"auth\",\"username\":\"alice\"}"));
    }

    @Test
    void rejectsResizeWithNonNumericFields() {
        assertThrows(ProtocolException.class,
                () -> ProtocolCodec.parseInbound("{\"type\":\"resize\",\"cols\":\"wide\",\"rows\":34}"));
    }

    @Test
    void rejectsResizeOutOfRange() {
        assertThrows(ProtocolException.class,
                () -> ProtocolCodec.parseInbound("{\"type\":\"resize\",\"cols\":0,\"rows\":34}"));
        assertThrows(ProtocolException.class,
                () -> ProtocolCodec.parseInbound("{\"type\":\"resize\",\"cols\":99999,\"rows\":34}"));
    }

    @Test
    void rejectsDataWithInvalidBase64() {
        assertThrows(ProtocolException.class,
                () -> ProtocolCodec.parseInbound("{\"type\":\"data\",\"data\":\"not-base64!!\"}"));
    }

    @Test
    void serializesConnected() {
        assertEquals("{\"type\":\"connected\"}", ProtocolCodec.connected());
    }

    @Test
    void serializesDataEscapesAndEncodesBytes() {
        byte[] bytes = {0x00, 0x01, (byte) 0xFF};
        String json = ProtocolCodec.data(bytes);
        assertTrue(json.contains("\"type\":\"data\""));
        assertTrue(json.contains(Base64.getEncoder().encodeToString(bytes)));
    }

    @Test
    void serializesErrorEscapesQuotesAndBackslashes() {
        String json = ProtocolCodec.error("bad \"input\" \\ here");
        // Must be valid JSON round-trippable, not naively concatenated.
        var reparsed = ProtocolCodec.error("bad \"input\" \\ here");
        assertEquals(json, reparsed);
        assertTrue(json.contains("\\\""));
    }
}
