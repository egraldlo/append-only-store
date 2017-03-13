package indexingTopology;

import indexingTopology.bolt.InputStreamReceiver;
import indexingTopology.bolt.InputStreamReceiverServer;
import indexingTopology.bolt.QueryCoordinator;
import indexingTopology.bolt.QueryCoordinatorWithQueryReceiverServer;
import indexingTopology.client.*;
import indexingTopology.data.DataSchema;
import indexingTopology.data.DataTuple;
import indexingTopology.util.DataTuplePredicate;
import indexingTopology.util.TopologyGenerator;
import indexingTopology.util.texi.City;
import indexingTopology.util.texi.TrajectoryGenerator;
import indexingTopology.util.texi.TrajectoryUniformGenerator;
import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.StormSubmitter;
import org.apache.storm.generated.KillOptions;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.topology.TopologyBuilder;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;

/**
 * Created by robert on 10/3/17.
 */
public class TopologyOverallTest {

    @Test
    public void testTopologyDouble() {
        final int ingestionPort = 10000;
        final int queryPort = 10001;
        final String topologyName = "test_1";

        DataSchema schema = new DataSchema();
        schema.addDoubleField("f1");
        schema.addDoubleField("f2");
        schema.addDoubleField("f3");
        schema.addLongField("timestamp");
        schema.setPrimaryIndexField("f1");


        Double lowerBound = 0.0;
        Double upperBound = 5000.0;

        final boolean enableLoadBalance = false;


        InputStreamReceiver dataSource = new InputStreamReceiverServer(schema, ingestionPort);
        QueryCoordinator<Double> queryCoordinator = new QueryCoordinatorWithQueryReceiverServer<>(lowerBound, upperBound, queryPort);

        TopologyGenerator<Double> topologyGenerator = new TopologyGenerator<>();

        StormTopology topology = topologyGenerator.generateIndexingTopology(schema, lowerBound, upperBound, enableLoadBalance, dataSource, queryCoordinator);

        Config conf = new Config();
        conf.setDebug(false);
        conf.setNumWorkers(1);

        conf.put(Config.WORKER_CHILDOPTS, "-Xmx2048m");
//        conf.put(Config.SUPERVISOR_CHILDOPTS, "-Xmx2048m");
        conf.put(Config.WORKER_HEAP_MEMORY_MB, 2048);

        LocalCluster cluster = new LocalCluster();
        cluster.submitTopology(topologyName, conf, topology);


        double start = 0;
        double step = 1.5;
        int numberOfTuples = 50;

        try {
//            Thread.sleep(10000);
            IngestionClient ingestionClient = new IngestionClient("localhost", ingestionPort);
            ingestionClient.connectWithTimeout(10000);
            for (int i = 0; i < numberOfTuples; i++) {
                ingestionClient.append(new DataTuple(start + step * i, 0.0, 0.0, System.currentTimeMillis()));
            }

            Thread.sleep(1000);

            QueryClient queryClient = new QueryClient("localhost", queryPort);
            queryClient.connectWithTimeout(10000);
            QueryResponse response = queryClient.temporalRangeQuery(0.0, 10000.0, 0, Long.MAX_VALUE);

            assertEquals(numberOfTuples, response.dataTuples.size());
            KillOptions killOptions = new KillOptions();
            killOptions.set_wait_secs(0);
            cluster.killTopology(topologyName);
//            Thread.sleep(5000);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Test
    public void testTopologyIntegerFilter() {

        final int ingestionPort = 10010;
        final int queryPort = 10011;
        final String topologyName = "test_2";

        DataSchema schema = new DataSchema();
        schema.addIntField("f1");
        schema.addIntField("f2");
        schema.addIntField("f3");
        schema.addLongField("timestamp");
        schema.setPrimaryIndexField("f1");


        Integer lowerBound = 0;
        Integer upperBound = 5000;

        final boolean enableLoadBalance = false;

        InputStreamReceiver dataSource = new InputStreamReceiverServer(schema, ingestionPort);
        QueryCoordinator<Integer> queryCoordinator = new QueryCoordinatorWithQueryReceiverServer<>(lowerBound, upperBound, queryPort);

        TopologyGenerator<Integer> topologyGenerator = new TopologyGenerator<>();

        StormTopology topology = topologyGenerator.generateIndexingTopology(schema, lowerBound, upperBound, enableLoadBalance, dataSource, queryCoordinator);

        Config conf = new Config();
        conf.setDebug(false);
        conf.setNumWorkers(1);

        conf.put(Config.WORKER_CHILDOPTS, "-Xmx2048m");
//        conf.put(Config.SUPERVISOR_CHILDOPTS, "-Xmx2048m");
        conf.put(Config.WORKER_HEAP_MEMORY_MB, 2048);

        LocalCluster cluster = new LocalCluster();
        cluster.submitTopology(topologyName, conf, topology);


        int start = 0;
        int step = 1;
        int numberOfTuples = 50;

        try {
//            Thread.sleep(10000);
            IngestionClient ingestionClient = new IngestionClient("localhost", ingestionPort);
            ingestionClient.connectWithTimeout(10000);
            for (int i = 0; i < numberOfTuples; i++) {
                ingestionClient.append(new DataTuple(start + step * i, 0, 0, System.currentTimeMillis()));
            }

            Thread.sleep(1000);

            QueryClient queryClient = new QueryClient("localhost", queryPort);
            queryClient.connectWithTimeout(10000);
            QueryResponse response = queryClient.temporalRangeQuery(0, 40, 0, Long.MAX_VALUE);

            assertEquals(41, response.dataTuples.size());
            KillOptions killOptions = new KillOptions();
            killOptions.set_wait_secs(0);
            cluster.killTopology(topologyName);
            Thread.sleep(5000);


        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    @Test
    public void testTopologyDoubleFilter() {

        final int ingestionPort = 10020;
        final int queryPort = 10021;
        final String topologyName = "test_3";

        DataSchema schema = new DataSchema();
        schema.addDoubleField("f1");
        schema.addDoubleField("f2");
        schema.addDoubleField("f3");
        schema.addLongField("timestamp");
        schema.setPrimaryIndexField("f1");


        Double lowerBound = 0.0;
        Double upperBound = 5000.0;

        final boolean enableLoadBalance = false;

        InputStreamReceiver dataSource = new InputStreamReceiverServer(schema, ingestionPort);
        QueryCoordinator<Double> queryCoordinator = new QueryCoordinatorWithQueryReceiverServer<>(lowerBound, upperBound, queryPort);

        TopologyGenerator<Double> topologyGenerator = new TopologyGenerator<>();

        StormTopology topology = topologyGenerator.generateIndexingTopology(schema, lowerBound, upperBound, enableLoadBalance, dataSource, queryCoordinator);

        Config conf = new Config();
        conf.setDebug(false);
        conf.setNumWorkers(1);

        conf.put(Config.WORKER_CHILDOPTS, "-Xmx2048m");
//        conf.put(Config.SUPERVISOR_CHILDOPTS, "-Xmx2048m");
        conf.put(Config.WORKER_HEAP_MEMORY_MB, 2048);

        LocalCluster cluster = new LocalCluster();
        cluster.submitTopology(topologyName, conf, topology);


        double start = 0;
        double step = 2.0;
        int numberOfTuples = 50;

        try {
//            Thread.sleep(10000);
            IngestionClient ingestionClient = new IngestionClient("localhost", ingestionPort);
            ingestionClient.connectWithTimeout(10000);
            for (int i = 0; i < numberOfTuples; i++) {
                ingestionClient.append(new DataTuple(start + step * i, 0.0, 0.0, System.currentTimeMillis()));
            }

            Thread.sleep(1000);

            QueryClient queryClient = new QueryClient("localhost", queryPort);
            queryClient.connectWithTimeout(10000);
            QueryResponse response = queryClient.temporalRangeQuery(0.0, 9.5, 0, Long.MAX_VALUE);

            assertEquals(5, response.dataTuples.size());
            KillOptions killOptions = new KillOptions();
            killOptions.set_wait_secs(0);
            cluster.killTopology(topologyName);
            Thread.sleep(5000);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Test
    public void testTopologyDoubleFilterWithPredicate() {

        final int ingestionPort = 10030;
        final int queryPort = 10031;
        final String topologyName = "test_4";

        DataSchema schema = new DataSchema();
        schema.addDoubleField("f1");
        schema.addDoubleField("f2");
        schema.addDoubleField("f3");
        schema.addLongField("timestamp");
        schema.setPrimaryIndexField("f1");


        Double lowerBound = 0.0;
        Double upperBound = 5000.0;

        final boolean enableLoadBalance = false;

        InputStreamReceiver dataSource = new InputStreamReceiverServer(schema, ingestionPort);
        QueryCoordinator<Double> queryCoordinator = new QueryCoordinatorWithQueryReceiverServer<>(lowerBound, upperBound, queryPort);

        TopologyGenerator<Double> topologyGenerator = new TopologyGenerator<>();

        StormTopology topology = topologyGenerator.generateIndexingTopology(schema, lowerBound, upperBound, enableLoadBalance, dataSource, queryCoordinator);

        Config conf = new Config();
        conf.setDebug(false);
        conf.setNumWorkers(1);

        conf.put(Config.WORKER_CHILDOPTS, "-Xmx2048m");
//        conf.put(Config.SUPERVISOR_CHILDOPTS, "-Xmx2048m");
        conf.put(Config.WORKER_HEAP_MEMORY_MB, 2048);

        LocalCluster cluster = new LocalCluster();
        cluster.submitTopology(topologyName, conf, topology);


        double start = 0;
        double step = 2.0;
        int numberOfTuples = 50;

        try {
//            Thread.sleep(10000);
            IngestionClient ingestionClient = new IngestionClient("localhost", ingestionPort);
            ingestionClient.connectWithTimeout(10000);
            for (int i = 0; i < numberOfTuples; i++) {
                ingestionClient.append(new DataTuple(start + step * i, 0.0, 0.0, System.currentTimeMillis()));
            }

            Thread.sleep(1000);

            QueryClient queryClient = new QueryClient("localhost", queryPort);
            queryClient.connectWithTimeout(10000);
//            QueryResponse response = queryClient.temporalRangeQuery(0.0, 9.5, 0, Long.MAX_VALUE);
            QueryResponse response = queryClient.query(new QueryRequest<>(0.0, 9.5, 0, Long.MAX_VALUE, t -> (Double)schema.getValue("f1", t) > 5, null));

            assertEquals(2, response.dataTuples.size());


            KillOptions killOptions = new KillOptions();
            killOptions.set_wait_secs(0);
            cluster.killTopology(topologyName);
            Thread.sleep(5000);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
