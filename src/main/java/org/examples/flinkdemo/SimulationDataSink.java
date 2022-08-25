package org.examples.flinkdemo;

import java.util.HashMap;
import java.util.Map;
import org.apache.flink.api.java.utils.ParameterTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.types.Row;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.DistributionMode;
import org.apache.iceberg.Schema;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.flink.CatalogLoader;
import org.apache.iceberg.flink.FlinkSchemaUtil;
import org.apache.iceberg.flink.TableLoader;
import org.apache.iceberg.flink.sink.FlinkSink;

public class SimulationDataSink {
  private static final Logger LOGGER = LoggerFactory.getLogger(SimulationDataSink.class);

  public static void main(String[] args) throws Exception {
    Configuration hadoopConf = new Configuration();
    hadoopConf.set("fs.s3a.access.key", "admin");
    hadoopConf.set("fs.s3a.secret.key", "password");
    hadoopConf.set("aws.region", "us-east-1");
    hadoopConf.set("fs.s3a.endpoint", "http://minio:9000");
    hadoopConf.set("fs.s3a.path.style.access", "true");

    Map<String, String> catalogProperties = new HashMap<>();
    catalogProperties.put("uri", "jdbc:postgresql://postgres:5432/demo_catalog");
    catalogProperties.put("jdbc.user", "admin");
    catalogProperties.put("jdbc.password", "password");
    catalogProperties.put("io-impl", "org.apache.iceberg.hadoop.HadoopFileIO");
    catalogProperties.put("warehouse", "s3a://warehouse");
    CatalogLoader catalogLoader =
        CatalogLoader.custom(
            "demo",
            catalogProperties,
            hadoopConf,
            "org.apache.iceberg.jdbc.JdbcCatalog");
    Catalog catalog = catalogLoader.loadCatalog();
    // Note: This table must already exist in the data warehouse before running this application
    TableIdentifier outputTable = TableIdentifier.of(
        "transit",
        "simulations"
    );  // Ideally read from an application configuration file
    Schema schema = catalog.loadTable(outputTable).schema();
    StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
    env.enableCheckpointing(10000);
    // Add the ViewerMatchSource
    ParameterTool parameters = ParameterTool.fromArgs(args);
    TransitSimulationSource source = new TransitSimulationSource();
    source.setStarRadius(Float.parseFloat(parameters.getRequired("starRadius")));
    DataStream<Row> stream =
        env.addSource(source)
            .returns(TypeInformation.of(Map.class)).map(s -> {
              Row row = new Row(3);
              row.setField(0, s.get("planet_id"));
              row.setField(1, s.get("flux"));
              row.setField(2, s.get("event_time"));
              return row;
            });
    // Configure row-based append
    FlinkSink.forRow(stream, FlinkSchemaUtil.toSchema(schema))
        .tableLoader(TableLoader.fromCatalog(catalogLoader, outputTable))
        .distributionMode(DistributionMode.HASH)
        .writeParallelism(1)
        .append();
    // Execute the flink app
    env.execute();
  }

  public static class IncrementMapFunction implements MapFunction<Long, Long> {
    // common processing usecase, maybe add this as a bonus?
    // writing to a partitioned table by event_time OR processing_time
    // event_time in particular is a use case powered by Iceberg because of hidden partitioningx
    @Override
    public Long map(Long record) throws Exception {
      return record + 1;
    }
  }
}