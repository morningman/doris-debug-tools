package com.test.mcwrite;

import com.aliyun.odps.Column;
import com.aliyun.odps.Odps;
import com.aliyun.odps.OdpsException;
import com.aliyun.odps.Table;
import com.aliyun.odps.TableSchema;
import com.aliyun.odps.Tables;
import com.aliyun.odps.account.AliyunAccount;
import com.aliyun.odps.table.DataSchema;
import com.aliyun.odps.table.TableIdentifier;
import com.aliyun.odps.table.configuration.ArrowOptions;
import com.aliyun.odps.table.configuration.ArrowOptions.TimestampUnit;
import com.aliyun.odps.table.configuration.RestOptions;
import com.aliyun.odps.table.configuration.WriterOptions;
import com.aliyun.odps.table.enviroment.Credentials;
import com.aliyun.odps.table.enviroment.EnvironmentSettings;
import com.aliyun.odps.table.write.BatchWriter;
import com.aliyun.odps.table.write.TableBatchWriteSession;
import com.aliyun.odps.table.write.TableWriteSessionBuilder;
import com.aliyun.odps.table.write.WriterAttemptId;
import com.aliyun.odps.table.write.WriterCommitMessage;
import com.aliyun.odps.type.TypeInfoFactory;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * Standalone test program to verify MaxCompute large STRING column write behavior.
 *
 * This program:
 * 1. Creates a MaxCompute table with an INT id column and a STRING content column.
 * 2. Generates test data with large string values (configurable size) and writes them
 *    to the table using the MaxCompute Storage API (Arrow-based).
 *
 * Usage:
 *   java -jar mc-string-write-test.jar \
 *       --endpoint <mc_endpoint> \
 *       --project <mc_project> \
 *       --accessKey <access_key> \
 *       --secretKey <secret_key> \
 *       [--table <table_name>]           (default: mc_large_string_test) \
 *       [--rows <num_rows>]              (default: 10) \
 *       [--stringSize <bytes_per_row>]   (default: 1048576, i.e. 1MB) \
 *       [--batchRows <rows_per_batch>]   (default: 1) \
 *       [--quota <quota_name>]           (default: pay-as-you-go) \
 *       [--skipCreate]                   (skip table creation if it already exists)
 */
public class McStringWriteTest {

    private static final Logger LOG = LoggerFactory.getLogger(McStringWriteTest.class);

    // --- Config fields ---
    private String endpoint;
    private String project;
    private String accessKey;
    private String secretKey;
    private String tableName = "mc_large_string_test";
    private int numRows = 10;
    private int stringSize = 1024 * 1024; // 1MB
    private int batchRows = 1;
    private String quota = "pay-as-you-go";
    private boolean skipCreate = false;

    public static void main(String[] args) throws Exception {
        McStringWriteTest test = new McStringWriteTest();
        test.parseArgs(args);
        test.run();
    }

    private void parseArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--endpoint":
                    endpoint = args[++i];
                    break;
                case "--project":
                    project = args[++i];
                    break;
                case "--accessKey":
                    accessKey = args[++i];
                    break;
                case "--secretKey":
                    secretKey = args[++i];
                    break;
                case "--table":
                    tableName = args[++i];
                    break;
                case "--rows":
                    numRows = Integer.parseInt(args[++i]);
                    break;
                case "--stringSize":
                    stringSize = Integer.parseInt(args[++i]);
                    break;
                case "--batchRows":
                    batchRows = Integer.parseInt(args[++i]);
                    break;
                case "--quota":
                    quota = args[++i];
                    break;
                case "--skipCreate":
                    skipCreate = true;
                    break;
                default:
                    System.err.println("Unknown argument: " + args[i]);
                    printUsage();
                    System.exit(1);
            }
        }

        if (endpoint == null || project == null || accessKey == null || secretKey == null) {
            System.err.println("ERROR: --endpoint, --project, --accessKey, --secretKey are required.");
            printUsage();
            System.exit(1);
        }
    }

    private void printUsage() {
        System.err.println("Usage: java -jar mc-string-write-test.jar \\");
        System.err.println("    --endpoint <mc_endpoint> \\");
        System.err.println("    --project <mc_project> \\");
        System.err.println("    --accessKey <access_key> \\");
        System.err.println("    --secretKey <secret_key> \\");
        System.err.println("    [--table <table_name>]           (default: mc_large_string_test)");
        System.err.println("    [--rows <num_rows>]              (default: 10)");
        System.err.println("    [--stringSize <bytes_per_row>]   (default: 1048576, 1MB)");
        System.err.println("    [--batchRows <rows_per_batch>]   (default: 1)");
        System.err.println("    [--quota <quota_name>]           (default: pay-as-you-go)");
        System.err.println("    [--skipCreate]                   (skip table creation)");
    }

    private void run() throws Exception {
        LOG.info("====== MaxCompute Large String Write Test ======");
        LOG.info("Endpoint:    {}", endpoint);
        LOG.info("Project:     {}", project);
        LOG.info("Table:       {}", tableName);
        LOG.info("Rows:        {}", numRows);
        LOG.info("String size: {} bytes ({} KB)", stringSize, stringSize / 1024);
        LOG.info("Batch rows:  {}", batchRows);
        LOG.info("Quota:       {}", quota);

        // 1. Create Odps client
        Odps odps = createOdpsClient();

        // 2. Create table (if not skipping)
        if (!skipCreate) {
            createTable(odps);
        } else {
            LOG.info("Skipping table creation (--skipCreate).");
        }

        // 3. Write data using Storage API
        writeData(odps);

        LOG.info("====== Test completed successfully ======");
    }

    // ===================== Create Odps Client =====================

    private Odps createOdpsClient() {
        AliyunAccount account = new AliyunAccount(accessKey, secretKey);
        Odps odps = new Odps(account);
        odps.setDefaultProject(project);
        odps.setEndpoint(endpoint);
        LOG.info("Odps client created.");
        return odps;
    }

    // ===================== Create Table =====================

    private void createTable(Odps odps) throws OdpsException {
        LOG.info("Creating table: {}.{} ...", project, tableName);

        // Build schema: id BIGINT, content STRING
        TableSchema schema = new TableSchema();
        schema.addColumn(new Column("id", TypeInfoFactory.BIGINT, "Row ID"));
        schema.addColumn(new Column("content", TypeInfoFactory.STRING, "Large string content"));

        Tables tables = odps.tables();
        try {
            // Check if table already exists
            if (tables.exists(tableName)) {
                LOG.info("Table {} already exists, dropping it first ...", tableName);
                tables.delete(tableName);
                LOG.info("Table {} dropped.", tableName);
            }
        } catch (OdpsException e) {
            LOG.warn("Failed to check/drop existing table: {}", e.getMessage());
        }

        // Create the table
        tables.create(project, tableName, schema, "Test table for large string write", false);
        LOG.info("Table {}.{} created successfully.", project, tableName);

        // Verify
        Table table = tables.get(tableName);
        table.reload();
        LOG.info("Table schema verification:");
        for (Column col : table.getSchema().getColumns()) {
            LOG.info("  Column: {} -> {}", col.getName(), col.getTypeInfo());
        }
    }

    // ===================== Write Data =====================

    private void writeData(Odps odps) throws Exception {
        LOG.info("Starting data write with Storage API ...");

        // 1. Build environment settings (same pattern as MaxComputeJniWriter)
        Credentials credentials = Credentials.newBuilder()
                .withAccount(odps.getAccount())
                .withAppAccount(odps.getAppAccount())
                .build();

        RestOptions restOptions = RestOptions.newBuilder()
                .withConnectTimeout(10)
                .withReadTimeout(120)
                .withRetryTimes(4)
                .build();

        EnvironmentSettings settings = EnvironmentSettings.newBuilder()
                .withCredentials(credentials)
                .withServiceEndpoint(odps.getEndpoint())
                .withQuotaName(quota)
                .withRestOptions(restOptions)
                .build();

        // 2. Create a new write session
        LOG.info("Creating write session ...");
        TableBatchWriteSession writeSession = new TableWriteSessionBuilder()
                .identifier(TableIdentifier.of(project, tableName))
                .withSettings(settings)
                .withArrowOptions(ArrowOptions.newBuilder()
                        .withDatetimeUnit(TimestampUnit.MILLI)
                        .withTimestampUnit(TimestampUnit.MILLI)
                        .build())
                .buildBatchWriteSession();

        String sessionId = writeSession.getId();
        LOG.info("Write session created: {}", sessionId);

        // 3. Get schema info
        DataSchema dataSchema = writeSession.requiredSchema();
        LOG.info("Required schema columns: {}", dataSchema.getColumns().size());
        for (com.aliyun.odps.Column col : dataSchema.getColumns()) {
            LOG.info("  {} -> {}", col.getName(), col.getTypeInfo());
        }

        // 4. Create Arrow writer for block 0
        WriterOptions writerOptions = WriterOptions.newBuilder()
                .withSettings(settings)
                .build();

        BatchWriter<VectorSchemaRoot> batchWriter = writeSession.createArrowWriter(
                0, WriterAttemptId.of(0), writerOptions);
        LOG.info("Arrow BatchWriter created for block 0.");

        // 5. Generate and write data in batches
        Random random = new Random(42);
        int rowsWritten = 0;

        while (rowsWritten < numRows) {
            int currentBatchSize = Math.min(batchRows, numRows - rowsWritten);

            VectorSchemaRoot root = batchWriter.newElement();
            root.setRowCount(currentBatchSize);

            // Fill id column (BIGINT -> BigIntVector)
            BigIntVector idVec = (BigIntVector) root.getVector(0);
            idVec.allocateNew(currentBatchSize);
            for (int i = 0; i < currentBatchSize; i++) {
                idVec.set(i, rowsWritten + i);
            }
            idVec.setValueCount(currentBatchSize);

            // Fill content column (STRING -> VarCharVector)
            VarCharVector contentVec = (VarCharVector) root.getVector(1);
            contentVec.allocateNew(currentBatchSize);
            for (int i = 0; i < currentBatchSize; i++) {
                byte[] bigString = generateLargeString(stringSize, rowsWritten + i, random);
                LOG.info("Row {}: generating string of {} bytes (actual UTF-8 size: {} bytes)",
                        rowsWritten + i, stringSize, bigString.length);
                contentVec.setSafe(i, bigString);
            }
            contentVec.setValueCount(currentBatchSize);

            // Write the batch
            long startMs = System.currentTimeMillis();
            batchWriter.write(root);
            long elapsed = System.currentTimeMillis() - startMs;

            rowsWritten += currentBatchSize;
            LOG.info("Batch written: {} rows (total: {}/{}), elapsed: {} ms",
                    currentBatchSize, rowsWritten, numRows, elapsed);
        }

        // 6. Commit the writer
        LOG.info("Committing writer ...");
        WriterCommitMessage commitMessage = batchWriter.commit();
        LOG.info("Writer committed. CommitMessage: {}", commitMessage);

        // 7. Commit the session
        LOG.info("Committing write session ...");
        writeSession.commit(new WriterCommitMessage[]{commitMessage});
        LOG.info("Write session committed successfully. Total rows written: {}", rowsWritten);
    }

    // ===================== Generate Large String =====================

    /**
     * Generates a large string of the specified byte size.
     * The content is a repeating pattern with an identifiable prefix for each row.
     */
    private byte[] generateLargeString(int sizeBytes, int rowId, Random random) {
        StringBuilder sb = new StringBuilder(sizeBytes);

        // Header: identifiable prefix
        String header = String.format("[ROW-%06d] ", rowId);
        sb.append(header);

        // Fill with random printable ASCII characters
        // Use a mix of alphanumeric chars to simulate realistic text data
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 .,;:!?-_\n";
        int remaining = sizeBytes - header.length();
        for (int i = 0; i < remaining; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }

        byte[] result = sb.toString().getBytes(StandardCharsets.UTF_8);

        // Trim or pad to exact byte size since some chars might produce different byte lengths
        if (result.length > sizeBytes) {
            byte[] trimmed = new byte[sizeBytes];
            System.arraycopy(result, 0, trimmed, 0, sizeBytes);
            return trimmed;
        }
        return result;
    }
}
