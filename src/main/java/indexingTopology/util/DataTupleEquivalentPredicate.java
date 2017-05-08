package indexingTopology.util;

import indexingTopology.data.DataSchema;
import indexingTopology.data.DataTuple;

import java.io.Serializable;
import java.util.function.Predicate;

/**
 * Created by Robert on 5/8/17.
 */
public class DataTupleEquivalentPredicate implements Predicate<DataTuple>, Serializable{
    public String column;
    public Object value;
    DataSchema schema;

    public DataTupleEquivalentPredicate(String column, Object value) {
        this.column = column;
        this.value = value;
    }

    @Override
    public boolean test(DataTuple objects) {
        return schema.getValue(column, objects).equals(value);
    }
}
