package com.real.cassandra.queue.pipeperpusher;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.real.cassandra.queue.CassQMsg;

public class PusherImplTest extends PipePerPusherTestBase {
    private CassQueueImpl cq;
    private CassQueueFactoryImpl cqFactory;

    @Test
    public void testSinglePusherSingleMsg() throws Exception {
        PusherImpl pusher = cqFactory.createPusher(cq);
        String msgData = "the data-" + System.currentTimeMillis();

        CassQMsg qMsg = pusher.push(msgData);

        assertEquals("stats should show only one msg", 1, qMsg.getPipeDescriptor().getMsgCount());
        assertNotNull(qMsg.getMsgId());
        assertNotNull(qMsg.getPipeDescriptor().getPipeId());
        assertNotNull(cq.getName(), qMsg.getPipeDescriptor().getQName());
        assertEquals(msgData, qMsg.getMsgData());

        CassQMsg qMsgNew = qRepos.getMsg(cq.getName(), qMsg.getPipeDescriptor().getPipeId(), qMsg.getMsgId());

        assertNotNull("should have created new message in the expected queue and pipe", qMsgNew);
        assertEquals("didn't seem to write the correct data to the correct place", qMsg.getMsgData(),
                qMsgNew.getMsgData());
    }

    @Test
    public void testMultiplePushers() throws Exception {
        int numMsgs = 10;
        ArrayList<CassQMsg> pushList1 = new ArrayList<CassQMsg>(numMsgs);
        ArrayList<CassQMsg> pushList2 = new ArrayList<CassQMsg>(numMsgs);

        PusherImpl pusher1 = cqFactory.createPusher(cq);
        PusherImpl pusher2 = cqFactory.createPusher(cq);

        for (int i = 0; i < numMsgs; i++) {
            pushList1.add(pusher1.push("push1-" + System.currentTimeMillis() + i));
            pushList2.add(pusher2.push("push2-" + System.currentTimeMillis() + i));
        }

        assertNotSame(pusher1.getPipeDesc(), pusher2.getPipeDesc());
        assertEquals("should have inserted " + numMsgs + " into pipe", numMsgs,
                qRepos.getPipeDescriptor(cq.getName(), pusher1.getPipeDesc().getPipeId()).getMsgCount());
        assertEquals("should have inserted " + numMsgs + " into pipe", numMsgs,
                qRepos.getPipeDescriptor(cq.getName(), pusher2.getPipeDesc().getPipeId()).getMsgCount());

        for (int i = 0; i < numMsgs; i++) {
            CassQMsg qMsg = pushList1.get(i);
            CassQMsg qMsgNew = qRepos.getMsg(cq.getName(), qMsg.getPipeDescriptor().getPipeId(), qMsg.getMsgId());
            assertEquals(qMsg.getMsgData(), qMsgNew.getMsgData());
        }

        for (int i = 0; i < numMsgs; i++) {
            CassQMsg qMsg = pushList2.get(i);
            CassQMsg qMsgNew = qRepos.getMsg(cq.getName(), qMsg.getPipeDescriptor().getPipeId(), qMsg.getMsgId());
            assertEquals(qMsg.getMsgData(), qMsgNew.getMsgData());
        }
    }

    @Test
    public void testRollAfterMaxPushes() throws Exception {
        int numMsgs = 30;
        ArrayList<CassQMsg> pushList = new ArrayList<CassQMsg>(numMsgs);

        PusherImpl pusher = cqFactory.createPusher(cq);

        for (int i = 0; i < numMsgs; i++) {
            pushList.add(pusher.push("push-" + System.currentTimeMillis() + i));
        }

        Set<UUID> pipeSet = new HashSet<UUID>();
        UUID lastPipeId = null;
        for (int i = 0; i < numMsgs; i++) {
            CassQMsg qMsg = pushList.get(i);
            CassQMsg qMsgNew = qRepos.getMsg(cq.getName(), qMsg.getPipeDescriptor().getPipeId(), qMsg.getMsgId());
            assertEquals(qMsg.getMsgData(), qMsgNew.getMsgData());
            lastPipeId = qMsg.getPipeDescriptor().getPipeId();
            pipeSet.add(lastPipeId);
        }

        assertEquals("should have created exactly " + numMsgs / cq.getMaxPushesPerPipe(),
                numMsgs / cq.getMaxPushesPerPipe(), pipeSet.size());

        for (UUID pipeId : pipeSet) {
            PipeDescriptorImpl pipeDesc = qRepos.getPipeDescriptor(cq.getName(), pipeId);
            assertEquals(numMsgs / pipeSet.size(), pipeDesc.getMsgCount());
            if (!pipeDesc.getPipeId().equals(lastPipeId)) {
                assertFalse("pipe should not be active", pipeDesc.isActive());
            }
            else {
                assertTrue("pipe should still be active", pipeDesc.isActive());
            }
        }
    }

    @Test
    public void testShutdownInProgress() throws Exception {
        PusherImpl pusher = cqFactory.createPusher(cq);
        int numMsgs = 45;
        Set<UUID> pipeSet = new HashSet<UUID>();
        for (int i = 0; i < numMsgs; i++) {
            CassQMsg qMsg = pusher.push("push-" + System.currentTimeMillis() + i);
            pipeSet.add(qMsg.getPipeDescriptor().getPipeId());
        }

        pusher.shutdown();

        try {
            pusher.push("push-" + System.currentTimeMillis() + numMsgs);
            fail("pusher should not have allowed a new message since in shutdown mode");
        }
        catch (IllegalStateException e) {
            // all good, expected
        }

        for (UUID pipeId : pipeSet) {
            PipeDescriptorImpl pipeDesc = qRepos.getPipeDescriptor(cq.getName(), pipeId);
            assertFalse("all pipes should be inactive because in shutdown mode", pipeDesc.isActive());
        }
    }

    // -------------------------

    @Before
    public void setupTest() throws Exception {
        PipeDescriptorFactory pipeDescFactory = new PipeDescriptorFactory();
        cqFactory = new CassQueueFactoryImpl(qRepos, pipeDescFactory);
        cq = cqFactory.createQueueInstance("test_" + System.currentTimeMillis(), 20000, 10, false);
    }
}
