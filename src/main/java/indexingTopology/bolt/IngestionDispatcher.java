package indexingTopology.bolt;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import indexingTopology.DataSchema;
import indexingTopology.streams.Streams;
import indexingTopology.util.BalancedPartition;
import indexingTopology.util.Histogram;

import java.io.IOException;
import java.util.*;


/**
 * Created by parijatmazumdar on 14/09/15.
 */
public class IngestionDispatcher extends BaseRichBolt {
    OutputCollector collector;

    private final DataSchema schema;

    private List<Integer> targetTasks;

    private Double lowerBound;

    private Double upperBound;

    private BalancedPartition balancedPartition;

    private boolean enableLoadBalance;

    private int numberOfPartitions;

    private boolean generateTimeStamp;

    public IngestionDispatcher(DataSchema schema, Double lowerBound, Double upperBound, boolean enableLoadBalance,
                               boolean generateTimeStamp) {
        this.schema = schema;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.enableLoadBalance = enableLoadBalance;
        this.generateTimeStamp = generateTimeStamp;
    }

    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        collector = outputCollector;

        Set<String> componentIds = topologyContext.getThisTargets()
                .get(Streams.IndexStream).keySet();
        targetTasks = new ArrayList<Integer>();

        for (String componentId : componentIds) {
            targetTasks.addAll(topologyContext.getComponentTasks(componentId));
        }

        numberOfPartitions = targetTasks.size();

        balancedPartition = new BalancedPartition(numberOfPartitions, lowerBound, upperBound, enableLoadBalance);

    }

    public void execute(Tuple tuple) {
        if (tuple.getSourceStreamId().equals(Streams.IndexStream)){

            Double indexValue = tuple.getDoubleByField(schema.getIndexField());

//                updateBound(indexValue);
            balancedPartition.record(indexValue);

            int partitionId = balancedPartition.getPartitionId(indexValue);

            int taskId = targetTasks.get(partitionId);

            Values values = new Values(tuple.getValues());
            if (generateTimeStamp)
                values.add(System.currentTimeMillis());

            collector.emitDirect(taskId, Streams.IndexStream, tuple, values);
            collector.ack(tuple);

        } else if (tuple.getSourceStreamId().equals(Streams.IntervalPartitionUpdateStream)){
            Map<Integer, Integer> intervalToPartitionMapping = (Map) tuple.getValueByField("newIntervalPartition");
            balancedPartition.setIntervalToPartitionMapping(intervalToPartitionMapping);
        } else if (tuple.getSourceStreamId().equals(Streams.StaticsRequestStream)){

            collector.emit(Streams.StatisticsReportStream,
                    new Values(new Histogram(balancedPartition.getIntervalDistribution().getHistogram())));

            balancedPartition.clearHistogram();
        }
    }

    private void updateBound(Double indexValue) {
        if (indexValue > upperBound) {
            upperBound = indexValue;
        }

        if (indexValue < lowerBound) {
            lowerBound = indexValue;
        }
    }

    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        List<String> fields = schema.getFieldsObject().toList();
        fields.add("timeStamp");

        declarer.declareStream(Streams.IndexStream, new Fields(fields));

        declarer.declareStream(Streams.StatisticsReportStream, new Fields("statistics"));

    }


}
