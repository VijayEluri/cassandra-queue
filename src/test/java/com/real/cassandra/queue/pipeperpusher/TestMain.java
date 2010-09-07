package com.real.cassandra.queue.pipeperpusher;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.cassandra.thrift.ConsistencyLevel;
import org.scale7.cassandra.pelops.Pelops;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.real.cassandra.queue.CassQMsg;
import com.real.cassandra.queue.pipeperpusher.utils.EnvProperties;
import com.real.cassandra.queue.pipeperpusher.utils.CassQueueUtils;

/**
 * Unit tests for {@link CassQueueImpl}.
 * 
 * @author Todd Burruss
 */
public class TestMain {
    private static Logger logger = LoggerFactory.getLogger(TestMain.class);

    private static CassQueueFactoryImpl cqFactory;
    private static QueueRepositoryImpl qRepos;
    private static EnvProperties envProps;
    private static CassQueueImpl cq;

    public static void main(String[] args) throws Exception {
        logger.info("setting up app properties");
        parseAppProperties();

        logger.info("setting queuing system");
        setupQueueSystem();

        //
        // start a set of pushers and poppers
        //

        logger.info("starting pushers/poppers after 2 sec pause : " + envProps.getNumPushers() + "/"
                + envProps.getNumPoppers());

        Queue<CassQMsg> popQ = new ConcurrentLinkedQueue<CassQMsg>();
        List<PushPopAbstractBase> pusherSet = CassQueueUtils.startPushers(cq, envProps);
        List<PushPopAbstractBase> popperSet = CassQueueUtils.startPoppers(cq, popQ, envProps);

        CassQueueUtils.monitorPushersPoppers(popQ, pusherSet, popperSet, null, null);

        shutdownQueueMgrAndPool();
    }

    // -----------------------

    private static void parseAppProperties() throws FileNotFoundException, IOException {
        File appPropsFile = new File("conf/app.properties");
        Properties props = new Properties();
        props.load(new FileReader(appPropsFile));
        envProps = new EnvProperties(props);

        logger.info("using hosts : " + props.getProperty("hosts"));
        logger.info("using thrift port : " + props.getProperty("thriftPort"));
    }

    private static void setupQueueSystem() throws Exception {
        qRepos = CassQueueUtils.createQueueRepository(envProps, ConsistencyLevel.QUORUM);
        cqFactory = new CassQueueFactoryImpl(qRepos, new PipeDescriptorFactory(qRepos), new PipeLockerImpl());
        cq =
                cqFactory.createInstance(envProps.getQName(), envProps.getMaxPushTimePerPipe(),
                        envProps.getMaxPushesPerPipe(), envProps.getMaxPopWidth(), envProps.getPopPipeRefreshDelay(),
                        false);
        if (envProps.getTruncateQueue()) {
            cq.truncate();
            Thread.sleep(2000);
        }
    }

    private static void shutdownQueueMgrAndPool() {
        cq.shutdown();
        Pelops.shutdown();
    }

}