package packet;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketTest {

    private ArrayList<String> args(String... values) {
        ArrayList<String> list = new ArrayList<>();
        for (String v : values) list.add(v);
        return list;
    }

    // PKT-1
    @Test
    void gettersReturnConstructorValues() {
        ArrayList<String> a = args("one", "two");
        Packet p = new Packet(Status.SUCCESS, ActionType.LOGIN, a, "sender-1");

        assertEquals(Status.SUCCESS, p.getStatus());
        assertEquals(ActionType.LOGIN, p.getActionType());
        assertEquals(a, p.getActionArguments());
        assertEquals("sender-1", p.getSenderId());
        assertTrue(p.getTimeCreated() != null);
    }

    // PKT-2
    @Test
    void idsAreDistinctAndIncreasing() {
        Packet first = new Packet(Status.NONE, ActionType.LOGIN, args(), "s");
        Packet second = new Packet(Status.NONE, ActionType.LOGIN, args(), "s");

        assertNotEquals(first.getId(), second.getId());
        assertTrue(Integer.parseInt(second.getId()) > Integer.parseInt(first.getId()));
    }

    // PKT-3
    @Test
    void sanitizeThenUnsanitizeRoundTrips() {
        String original = "a/b/c";
        String sanitized = Packet.sanitize(original);
        assertNotEquals(original, sanitized);
        assertEquals(original, Packet.unsanitize(sanitized));
    }

    // PKT-4
    @Test
    void sanitizeAndUnsanitizeHandleNull() {
        assertNull(Packet.sanitize(null));
        assertNull(Packet.unsanitize(null));
    }

    // PKT-5
    @Test
    void sanitizeIsNoOpWithoutSlash() {
        assertEquals("plain text", Packet.sanitize("plain text"));
    }

    // PKT-6
    @Test
    void packetSurvivesSerializationRoundTrip() throws Exception {
        Packet original = new Packet(Status.ERROR, ActionType.ERROR, args("bad", "request"), "sender-9");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(original);
        }

        Packet copy;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            copy = (Packet) in.readObject();
        }

        assertEquals(original.getId(), copy.getId());
        assertEquals(original.getStatus(), copy.getStatus());
        assertEquals(original.getActionType(), copy.getActionType());
        assertEquals(original.getActionArguments(), copy.getActionArguments());
        assertEquals(original.getSenderId(), copy.getSenderId());
    }
}
