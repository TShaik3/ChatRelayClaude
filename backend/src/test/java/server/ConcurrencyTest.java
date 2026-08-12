package server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import packet.ActionType;
import packet.Packet;
import packet.Status;
import support.TestConnection;
import support.TestDatabase;
import support.TestServerHarness;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static support.TestConnection.args;

/**
 * Concurrency tests (TEST_PLAN.md section 7). CNC-5 (kill -9 the server
 * mid-write and inspect the on-disk file) is not automated here: it needs
 * an actual out-of-process kill with adversarial timing, which doesn't fit
 * a fast, deterministic unit-test run. Exercise it manually if needed.
 */
class ConcurrencyTest {

    private TestDatabase testDb;
    private TestServerHarness harness;

    @BeforeEach
    void startServer() throws Exception {
        testDb = TestDatabase.createSchema();
        harness = new TestServerHarness(testDb.dataSource());
    }

    @AfterEach
    void stopServer() {
        harness.close();
        testDb.close();
    }

    // CNC-1
    // NOTE: AbstractUser's id counter is a JVM-wide static, shared across every DBManager
    // created anywhere in this test run -- so admin's id is NOT reliably "0" once other test
    // methods/classes have run earlier in the same process. Assert internal consistency
    // (every login returns the same id as every other) rather than a hardcoded literal.
    @Test
    void manySimultaneousLoginsAllSucceedOrFailCorrectly() throws Exception {
        int clientCount = 20;
        String expectedId = harness.connect().login("admin", "admin");

        ExecutorService pool = Executors.newFixedThreadPool(clientCount);
        CountDownLatch ready = new CountDownLatch(clientCount);
        CountDownLatch go = new CountDownLatch(1);

        List<Future<String>> futures = IntStream.range(0, clientCount)
                .mapToObj(i -> pool.submit(() -> {
                    TestConnection conn = harness.connect();
                    ready.countDown();
                    go.await();
                    return conn.login("admin", "admin");
                }))
                .collect(Collectors.toList());

        ready.await();
        go.countDown();

        for (Future<String> future : futures) {
            assertEquals(expectedId, future.get(10, TimeUnit.SECONDS));
        }
        pool.shutdown();
    }

    // CNC-2 (scaled for test speed: 5 chatters x 10 messages each = 50 total)
    @Test
    void concurrentMessagesFromMultipleChattersAllPersistAndBroadcast() throws Exception {
        TestConnection admin = harness.connect();
        String adminId = admin.login("admin", "admin");

        int chatterCount = 5;
        int messagesPerChatter = 10;
        int totalMessages = chatterCount * messagesPerChatter;

        // Create every user first, all via the one already-connected `admin` connection, before
        // any of them logs in -- otherwise an already-connected chatter would also receive (and
        // never drain) the NEW_USER_BROADCAST for chatters created after it.
        for (int i = 0; i < chatterCount; i++) {
            admin.send(Status.NONE, ActionType.CREATE_USER, args("chatter" + i, "pw", "C", "" + i, "false", "false"), adminId);
            admin.recv();
        }

        List<String> memberIds = new ArrayList<>();
        List<TestConnection> members = new ArrayList<>();
        for (int i = 0; i < chatterCount; i++) {
            TestConnection conn = harness.connect();
            String id = conn.login("chatter" + i, "pw");
            memberIds.add(id);
            members.add(conn);
        }

        admin.send(Status.NONE, ActionType.CREATE_CHAT, args(String.join("/", memberIds), "group", "false"), adminId);
        String chatId = admin.recv().getActionArguments().get(0).split("/")[0];
        for (TestConnection member : members) {
            member.recv(); // NEW_CHAT_BROADCAST
        }

        ExecutorService pool = Executors.newFixedThreadPool(chatterCount);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> senders = new ArrayList<>();
        for (int i = 0; i < chatterCount; i++) {
            TestConnection member = members.get(i);
            String memberId = memberIds.get(i);
            senders.add(pool.submit(() -> {
                go.await();
                for (int m = 0; m < messagesPerChatter; m++) {
                    member.send(Status.NONE, ActionType.SEND_MESSAGE, args("msg-" + memberId + "-" + m, chatId), memberId);
                }
                return null;
            }));
        }
        go.countDown();
        for (Future<?> f : senders) {
            f.get(10, TimeUnit.SECONDS);
        }
        pool.shutdown();

        // Every chatter (including admin, a member too) must see exactly totalMessages broadcasts.
        drainExactly(admin, totalMessages);
        for (TestConnection member : members) {
            drainExactly(member, totalMessages);
        }

        long persistedRows = testDb.countRows("messages");
        assertEquals(totalMessages, persistedRows, "no lost/corrupted writes to messages under concurrent senders");
    }

    private void drainExactly(TestConnection conn, int expectedCount) throws Exception {
        for (int i = 0; i < expectedCount; i++) {
            Packet p = conn.recv();
            assertEquals(ActionType.NEW_MESSAGE_BROADCAST, p.getActionType());
        }
    }

    // CNC-3
    // NOTE: Server.clients is keyed by userId (see SRV-5 in ServerProtocolTest), so N connections
    // all logging in as the same account would collapse to a single broadcast-routing slot and
    // most of them would never see their own reply. Give every worker its own distinct admin
    // account instead, so each has an independent slot in the registry; and rather than trying
    // to match each concurrent CREATE_USER's reply back to "its own" sender (broadcasts from
    // concurrent creations interleave arbitrarily across all connected admins), just fire all the
    // sends concurrently and collect the resulting N broadcasts from one fixed observer connection.
    @Test
    void concurrentUserCreationFromDistinctAdminConnectionsProducesDistinctUsers() throws Exception {
        int count = 30;
        TestConnection bootstrap = harness.connect();
        String bootstrapId = bootstrap.login("admin", "admin");

        List<String> adminIds = new ArrayList<>();
        List<TestConnection> adminConns = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            bootstrap.send(Status.NONE, ActionType.CREATE_USER,
                    args("conc-admin" + i, "pw", "A", "" + i, "false", "true"), bootstrapId);
            bootstrap.recv(); // NEW_USER_BROADCAST for this admin account's own creation
            TestConnection conn = harness.connect();
            adminIds.add(conn.login("conc-admin" + i, "pw"));
            adminConns.add(conn);
        }

        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch go = new CountDownLatch(1);

        List<Future<?>> sends = IntStream.range(0, count)
                .mapToObj(i -> pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    adminConns.get(i).send(Status.NONE, ActionType.CREATE_USER,
                            args("bulk" + i, "pw", "B", "" + i, "false", "false"), adminIds.get(i));
                    return null;
                }))
                .collect(Collectors.toList());

        ready.await();
        go.countDown();
        for (Future<?> f : sends) {
            f.get(10, TimeUnit.SECONDS);
        }

        // bootstrap is connected throughout and sends nothing itself during this phase, so it
        // will observe exactly `count` NEW_USER_BROADCAST packets, one per concurrent creation.
        Set<String> createdIds = new HashSet<>();
        for (int i = 0; i < count; i++) {
            Packet broadcast = bootstrap.recv();
            assertEquals(ActionType.NEW_USER_BROADCAST, broadcast.getActionType());
            createdIds.add(broadcast.getActionArguments().get(0).split("/")[0]);
        }
        pool.shutdown();

        assertEquals(count, createdIds.size(), "all concurrently created users must have distinct ids");
    }

    // CNC-4
    // NOTE: the two connections used here must be distinct accounts (see SRV-5) -- otherwise the
    // second login would evict the first connection's registry slot and `admin.recv()` below
    // would hang waiting for a broadcast that now routes to the other connection instead.
    @Test
    void concurrentChatMutationsOnDifferentChatsDoNotCorruptEachOther() throws Exception {
        TestConnection admin = harness.connect();
        String adminId = admin.login("admin", "admin");
        admin.send(Status.NONE, ActionType.CREATE_USER, args("mover", "pw", "M", "O", "false", "false"), adminId);
        admin.recv();
        String moverId = harness.server().getDBManager().getUserByUsername("mover").getId();

        admin.send(Status.NONE, ActionType.CREATE_USER, args("otherCreator", "pw", "O", "C", "false", "false"), adminId);
        admin.recv();

        admin.send(Status.NONE, ActionType.CREATE_CHAT, args("", "existing-chat", "false"), adminId);
        String existingChatId = admin.recv().getActionArguments().get(0).split("/")[0];

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);

        Future<Packet> createFuture = pool.submit(() -> {
            TestConnection creator = harness.connect();
            String creatorId = creator.login("otherCreator", "pw");
            go.await();
            creator.send(Status.NONE, ActionType.CREATE_CHAT, args("", "brand-new-chat", "false"), creatorId);
            return creator.recv();
        });
        Future<Packet> addFuture = pool.submit(() -> {
            go.await();
            admin.send(Status.NONE, ActionType.ADD_USER_TO_CHAT, args(moverId, existingChatId), adminId);
            return admin.recv();
        });

        go.countDown();
        Packet createResult = createFuture.get(10, TimeUnit.SECONDS);
        Packet addResult = addFuture.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(ActionType.NEW_CHAT_BROADCAST, createResult.getActionType());
        assertEquals(ActionType.ADD_USER_TO_CHAT_BROADCAST, addResult.getActionType());

        long chatRows = testDb.countRows("chats");
        assertEquals(2, chatRows, "both the pre-existing and newly created chat must be present, no corruption");
    }
}
