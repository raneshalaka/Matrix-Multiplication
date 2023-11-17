import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.*;

class Element implements Writable {
    char m; //tag
    int key;  //index
    double value;
    public Element() {

    }

    public Element(char m, int key, double value) {
        this.m = m;
        this.key = key;
        this.value = value;
    }

    public char getM() {
        return m;
    }

    public int getKey() {
        return key;
    }

    public double getValue() {
        return value;
    }

    @Override
    public void readFields (DataInput input) throws IOException {
        m = input.readChar();
        key = input.readInt();
        value = input.readDouble();
    }

    @Override
    public void write(DataOutput output) throws IOException {
        output.writeChar(m);
        output.writeInt(key);
        output.writeDouble(value);
    }
}

class Pair implements WritableComparable<Pair> {
    public int i;
    public int j;

    Pair() {}

    Pair (int i, int j) {
        this.i = i;
        this.j = j;
    }

    @Override
    public String toString() {
        return i+","+j+",";
    }

    @Override
    public int compareTo(Pair p) {
        if(this.i<p.i) {
            return -1;
        } else if (this.i>p.i) {
            return 1;
        } else {
            if(this.j<p.j) {
                return -1;
            } else if (this.j > p.j) {
                return 1;
            } else {
                return 0;
            }
        }
    }

    @Override
    public boolean equals(Object object) {
        if(object instanceof Pair) {
            Pair pair = (Pair) object;
            if(this.compareTo(pair)==0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void readFields (DataInput input) throws IOException {
        i = input.readInt();
        j = input.readInt();
    }

    @Override
    public void write (DataOutput output) throws IOException {
        output.writeInt(i);
        output.writeInt(j);
    }
}

public class Multiply extends Configured implements Tool {

    @Override
    public int run(String[] strings) throws Exception {
        return 0;
    }

    // Mapper class for Matrix A
    public static class MatrixMapperA extends Mapper<Object, Text, IntWritable, Element> {

        @Override
        public void map(Object key1, Text row, Context context) throws IOException, InterruptedException {
            // Split the input value by tab delimiter
            Scanner sc = new Scanner(row.toString()).useDelimiter(",");

            int i = sc.nextInt();
            int j = sc.nextInt();

            double v = sc.nextDouble();

            context.write(new IntWritable(j), new Element('A', i, v));

            sc.close();

        }
    }

    // Mapper class for Matrix B
    public static class MatrixMapperB extends Mapper<Object, Text, IntWritable, Element> {

        @Override
        public void map(Object key1, Text row, Context context) throws IOException, InterruptedException {
            // Splitting the input value by tab delimiter
            Scanner sc = new Scanner(row.toString()).useDelimiter(",");

            int i = sc.nextInt();
            int j = sc.nextInt();

            double v = sc.nextDouble();

            context.write(new IntWritable(i), new Element('B', j, v));
            sc.close();
        }
    }

    // Reducer class for the first job
    public static class MatrixReducer1 extends Reducer<IntWritable, Element, Pair, DoubleWritable> {

        @Override
        public void reduce(IntWritable key1, Iterable<Element> values, Context context) throws IOException, InterruptedException {

            List<Element> matrixA = new ArrayList<>();
            List<Element> matrixB = new ArrayList<>();
            // Iterate through the values and populate the matrices
            for (Element element : values) {
                if(element.getM()=='A') {
                    matrixA.add(new Element(element.m, element.key, element.value));
                }

                if(element.getM()=='B') {
                    matrixB.add(new Element(element.m, element.key, element.value));
                }

            }

            for(Element a: matrixA) {
                for(Element b: matrixB) {
                    context.write(new Pair(a.getKey(), b.getKey()), new DoubleWritable(a.getValue()*b.getValue()));
                }
            }
        }
    }

    public static class MatrixMapperAxB extends Mapper<Object, Text, Pair, DoubleWritable> {
        @Override
        public void map (Object key1, Text row, Context context) throws IOException, InterruptedException {
            String[] arr = row.toString().split("[,]");
            int i = Integer.parseInt(arr[0].trim());
            int j = Integer.parseInt(arr[1].trim());
            double value = Double.parseDouble(arr[2].trim());

            context.write(new Pair(i, j), new DoubleWritable(value));
        }
    }

    // Reducer class for the second job
    public static class MatrixReducer2 extends Reducer<Pair, DoubleWritable, Pair, DoubleWritable> {

        @Override
        public void reduce(Pair key1, Iterable<DoubleWritable> values, Context context) throws IOException, InterruptedException {
            // Calculate the sum of products for each key
            double sum = 0.0;
            for (DoubleWritable value : values) {
                sum += value.get();
            }

            // Emit the final key-value pair
            context.write(key1, new DoubleWritable(sum));
        }
    }

    // Main method
    public static void main(String[] args) throws IOException, ClassNotFoundException, InterruptedException {
        //Configuration conf = new Configuration();

        // First MapReduce job configuration
        Job job1 = Job.getInstance();
        job1.setJarByClass(Multiply.class);
        job1.setOutputKeyClass(Pair.class);
        job1.setOutputValueClass(DoubleWritable.class);
        job1.setMapOutputKeyClass(IntWritable.class);
        job1.setMapOutputValueClass(Element.class);
        job1.setReducerClass(MatrixReducer1.class);
        job1.setOutputFormatClass(TextOutputFormat.class);
        MultipleInputs.addInputPath(job1, new Path(args[0]), TextInputFormat.class, MatrixMapperA.class);
        MultipleInputs.addInputPath(job1, new Path(args[1]), TextInputFormat.class, MatrixMapperB.class);
        FileOutputFormat.setOutputPath(job1, new Path(args[2]));
        job1.waitForCompletion(true);

        // Second MapReduce job configuration
        Job job2 = Job.getInstance();
        job2.setJobName("a2");
        job2.setJarByClass(Multiply.class);
        job2.setOutputKeyClass(Pair.class);
        job2.setOutputValueClass(DoubleWritable.class);
        job2.setMapOutputKeyClass(Pair.class);
        job2.setMapOutputValueClass(DoubleWritable.class);
        job2.setReducerClass(MatrixReducer2.class);
        MultipleInputs.addInputPath(job2, new Path(args[2]), TextInputFormat.class, MatrixMapperAxB.class);
        FileOutputFormat.setOutputPath(job2, new Path(args[3]));
        job2.waitForCompletion(true);
    }
}

