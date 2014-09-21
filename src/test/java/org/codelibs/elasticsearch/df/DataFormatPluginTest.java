package org.codelibs.elasticsearch.df;

import static org.codelibs.elasticsearch.runner.ElasticsearchClusterRunner.newConfigs;

import java.io.InputStream;

import junit.framework.TestCase;

import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.codelibs.elasticsearch.runner.ElasticsearchClusterRunner;
import org.codelibs.elasticsearch.runner.net.Curl;
import org.codelibs.elasticsearch.runner.net.CurlResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.settings.ImmutableSettings.Builder;
import org.elasticsearch.node.Node;

public class DataFormatPluginTest extends TestCase {

    private ElasticsearchClusterRunner runner;

    @Override
    protected void setUp() throws Exception {
        // create runner instance
        runner = new ElasticsearchClusterRunner();
        // create ES nodes
        runner.onBuild(new ElasticsearchClusterRunner.Builder() {
            @Override
            public void build(final int number, final Builder settingsBuilder) {
            }
        }).build(newConfigs().ramIndexStore().numOfNode(1));

        // wait for yellow status
        runner.ensureYellow();
    }

    @Override
    protected void tearDown() throws Exception {
        // close runner
        runner.close();
        // delete all files
        runner.clean();
    }

    public void test_runCluster() throws Exception {

        final String index = "dataset";
        final String type = "item";

        // create an index
        runner.createIndex(index, null);

        if (!runner.indexExists(index)) {
            fail();
        }

        // create 1000 documents
        for (int i = 1; i <= 1000; i++) {
            final IndexResponse indexResponse1 = runner
                    .insert(index,
                            type,
                            String.valueOf(i),
                            "{\"aaa\":\"test "
                                    + i
                                    + "\", \"bbb\":"
                                    + i
                                    + ", \"ccc\":\"2012-01-01:00:00.000Z\", \"eee\":{\"fff\":\"TEST "
                                    + i + "\", \"ggg\":" + i
                                    + ", \"hhh\":\"2013-01-01:00:00.000Z\"}}");
            assertTrue(indexResponse1.isCreated());
        }
        runner.refresh();

        // search 1000 documents
        {
            final SearchResponse searchResponse = runner.search(index, type,
                    null, null, 0, 10);
            assertEquals(1000, searchResponse.getHits().getTotalHits());
        }

        Node node = runner.node();

        // Download All as CSV
        try (CurlResponse curlResponse = Curl.get(node, "/dataset/item/_data")
                .param("format", "csv").execute()) {
            String content = curlResponse.getContentAsString();
            String[] lines = content.split("\n");
            assertEquals(1001, lines.length);
            assertEquals(
                    "\"eee.ggg\",\"aaa\",\"eee.fff\",\"ccc\",\"bbb\",\"eee.hhh\"",
                    lines[0]);
        }

        // Download All as JSON
        try (CurlResponse curlResponse = Curl.get(node, "/dataset/item/_data")
                .param("format", "json").execute()) {
            String content = curlResponse.getContentAsString();
            String[] lines = content.split("\n");
            assertEquals(2000, lines.length);
            assertTrue(lines[0].startsWith("{\"index\""));
            assertTrue(lines[1].startsWith("{\"aaa\""));
        }

        // Download All as Excel
        try (CurlResponse curlResponse = Curl.get(node, "/dataset/item/_data")
                .param("format", "xls").execute()) {
            try (InputStream is = curlResponse.getContentAsStream()) {
                POIFSFileSystem fs = new POIFSFileSystem(is);
                HSSFWorkbook book = new HSSFWorkbook(fs);
                HSSFSheet sheet = book.getSheetAt(0);
                assertEquals(1000, sheet.getLastRowNum());
            }
        }

        // Download All as CSV with Fields
        try (CurlResponse curlResponse = Curl.get(node, "/dataset/item/_data")
                .param("format", "csv").param("fl", "aaa,eee.ggg").execute()) {
            String content = curlResponse.getContentAsString();
            String[] lines = content.split("\n");
            assertEquals(1001, lines.length);
            assertEquals("\"aaa\",\"eee.ggg\"", lines[0]);
        }

        // Download All as JSON with Fields
        try (CurlResponse curlResponse = Curl.get(node, "/dataset/item/_data")
                .param("format", "json").param("fl", "aaa,eee.ggg").execute()) {
            String content = curlResponse.getContentAsString();
            String[] lines = content.split("\n");
            assertEquals(2000, lines.length);
            assertTrue(lines[0].startsWith("{\"index\"")); // TODO
            assertTrue(lines[1].startsWith("{\"aaa\"")); // TODO
        }

        // Download All as Excel with Fields
        try (CurlResponse curlResponse = Curl.get(node, "/dataset/item/_data")
                .param("format", "xls").param("fl", "aaa,eee.ggg").execute()) {
            try (InputStream is = curlResponse.getContentAsStream()) {
                POIFSFileSystem fs = new POIFSFileSystem(is);
                HSSFWorkbook book = new HSSFWorkbook(fs);
                HSSFSheet sheet = book.getSheetAt(0);
                assertEquals(1000, sheet.getLastRowNum());
                HSSFRow row = sheet.getRow(0);
                assertEquals("aaa", row.getCell(0).toString());
                assertEquals("eee.ggg", row.getCell(1).toString());
            }
        }

        String query = "{\"query\":{\"bool\":{\"must\":[{\"range\":{\"item.bbb\":{\"from\":\"100\",\"to\":\"199\"}}}],\"must_not\":[],\"should\":[]}},\"sort\":[\"bbb\"]}";

        // Download All as CSV with Query
        try (CurlResponse curlResponse = Curl.get(node, "/dataset/item/_data")
                .param("format", "csv").body(query).execute()) {
            String content = curlResponse.getContentAsString();
            String[] lines = content.split("\n");
            assertEquals(101, lines.length);
            assertEquals(
                    "\"eee.ggg\",\"aaa\",\"eee.fff\",\"ccc\",\"bbb\",\"eee.hhh\"",
                    lines[0]);
            assertTrue(lines[1].startsWith("\"100\","));
        }

        // Download All as JSON with Query
        try (CurlResponse curlResponse = Curl.get(node, "/dataset/item/_data")
                .param("format", "json").body(query).execute()) {
            String content = curlResponse.getContentAsString();
            String[] lines = content.split("\n");
            assertEquals(200, lines.length);
            assertTrue(lines[0].startsWith("{\"index\""));
            assertTrue(lines[1].startsWith("{\"aaa\":\"test 100\","));
        }

        // Download All as Excel with Query
        try (CurlResponse curlResponse = Curl.get(node, "/dataset/item/_data")
                .param("format", "xls").body(query).execute()) {
            try (InputStream is = curlResponse.getContentAsStream()) {
                POIFSFileSystem fs = new POIFSFileSystem(is);
                HSSFWorkbook book = new HSSFWorkbook(fs);
                HSSFSheet sheet = book.getSheetAt(0);
                assertEquals(100, sheet.getLastRowNum());
                assertEquals("eee.ggg", sheet.getRow(0).getCell(0).toString());
                assertEquals("aaa", sheet.getRow(0).getCell(1).toString());
                assertEquals("100", sheet.getRow(1).getCell(0).toString());
                assertEquals("test 100", sheet.getRow(1).getCell(1).toString());
            }
        }
    }
}