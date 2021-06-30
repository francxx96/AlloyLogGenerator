package intervals;

import core.exceptions.GenerationException;
import core.models.declare.data.EnumeratedDataImpl;
import core.models.declare.data.FloatDataImpl;
import core.models.declare.data.IntegerDataImpl;
import core.models.declare.data.NumericDataImpl;
import core.models.intervals.*;
import declare.DeclareParserException;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;

/**
 * Created by Vasiliy on 2017-11-08.
 */
public class NumericDataTest {
    @Test
    public void EnumeratedDataTest() throws DeclareParserException {
        EnumeratedDataImpl data = new EnumeratedDataImpl("data", Arrays.asList("v1", "v2"), true);
        data.addValue("v3");
        Assert.assertEquals(data.getType(), "data");
        Assert.assertEquals(data.getValues().size(), 3);
    }

    @Test
    public void IntegerDataNoValuesTest() {
        NumericDataImpl data = new IntegerDataImpl("idata", 0, 100, 1, null, true);
        Assert.assertEquals(data.getType(), "idata");
        Assert.assertEquals(data.getValues().size(), 1);
        Assert.assertTrue(data.getMapping().containsKey(data.getValues().get(0)));
        Assert.assertTrue(data.getMapping().get(data.getValues().get(0)) instanceof IntegerInterval);
    }

    @Test
    public void IntegerDataTest() throws DeclareParserException, GenerationException {
        NumericDataImpl data = new IntegerDataImpl("idata", 0, 100, 1, null, true);
        data.addSplit(new IntervalSplit("30"));
        data.addSplit(new IntervalSplit("60"));
        Assert.assertEquals(data.getType(), "idata");
        Assert.assertEquals(data.getValues().size(), 5);
        Assert.assertTrue(data.getValues().stream().anyMatch(i -> data.getMapping().get(i) instanceof IntegerValue));
        Assert.assertTrue(data.getValues().stream().allMatch(i -> data.getMapping().get(i) instanceof IntegerInterval));
    }

    @Test
    public void IntegerDataTest2() throws DeclareParserException, GenerationException {
        int increment = 10_000;
        NumericDataImpl data = new IntegerDataImpl("idata", -2000000 + 1, 2000000 - 1, 1, null, true);  // -1 and +1 as min and max values in constructor are included in range
        for (int i = 0; i < 100; ++i) {
            data.addSplit(new IntervalSplit(String.valueOf(increment * i)));
            data.addSplit(new IntervalSplit(String.valueOf(-increment * i)));
        }

        Assert.assertEquals(data.getType(), "idata");
        Assert.assertEquals(data.getValues().size(), 399); // 199 (200 minus duplicate zero) values + 200 intervals
        Assert.assertTrue(data.getValues().stream().anyMatch(i -> data.getMapping().get(i) instanceof IntegerValue));
        Assert.assertTrue(data.getValues().stream().allMatch(i -> data.getMapping().get(i) instanceof IntegerInterval));
        for (String key : data.getMapping().keySet()) {
            Interval intl = data.getMapping().get(key);
            if (intl instanceof IntegerInterval) {
                IntegerInterval ii = (IntegerInterval) intl;
                Assert.assertEquals(ii.getMin() % increment, 0);
                Assert.assertEquals(ii.getMax() % increment, 0);
            } else {
                IntegerValue iv = (IntegerValue) intl;
                Assert.assertEquals(iv.getMin() % increment, 0);
            }
        }
    }

    @Test
    public void IntegerDataSplitsTest() throws DeclareParserException {
        NumericDataImpl data = new IntegerDataImpl("idata", -2000000 + 1, 2000000 - 1, 1000, null, true);
        for (String key : data.getMapping().keySet()) {
            Interval intl = data.getMapping().get(key);
            if (intl instanceof IntegerInterval) {
                IntegerInterval ii = (IntegerInterval) intl;
                if (ii.getMin() != -2000000)    //once, edge case
                    Assert.assertEquals((ii.getMin() + 1) % 4000, 0);
                Assert.assertEquals(ii.getMax() % 4000, 0);
            }
        }
    }

    @Test
    public void FloatDataNoValuesTest() {
        NumericDataImpl data = new FloatDataImpl("idata", 0, 100, 1, null, true);
        Assert.assertEquals(data.getType(), "idata");
        Assert.assertEquals(data.getValues().size(), 1);
        Assert.assertTrue(data.getMapping().containsKey(data.getValues().get(0)));
        Assert.assertTrue(data.getMapping().get(data.getValues().get(0)) instanceof FloatInterval);
    }

    @Test
    public void FloatDataTest() throws DeclareParserException, GenerationException {
        NumericDataImpl data = new FloatDataImpl("idata", 0, 100, 1, null, true);
        data.addSplit(new IntervalSplit("30"));
        data.addSplit(new IntervalSplit("60"));
        Assert.assertEquals(data.getType(), "idata");
        Assert.assertEquals(data.getValues().size(), 5);
        Assert.assertTrue(data.getValues().stream().anyMatch(i -> data.getMapping().get(i) instanceof FloatValue));
        Assert.assertTrue(data.getValues().stream().anyMatch(i -> data.getMapping().get(i) instanceof FloatInterval));
    }
}
