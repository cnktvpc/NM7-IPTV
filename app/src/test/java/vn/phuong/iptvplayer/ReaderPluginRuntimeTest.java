package vn.phuong.iptvplayer;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class ReaderPluginRuntimeTest {
    private static final String SCRIPT = """
            var NM7_SOURCE = {
              id: "sample-comic",
              name: "Sample Comic",
              type: "comic",
              baseUrl: "https://example.test/",
              description: "Test plugin",
              version: "1.0.0",
              popular: function(page) {
                var html = "<div class='card'><a href='/comic/1'>Sky Story</a><img src='/cover.jpg'></div>";
                return JSON.parse(NM7.dom.select(html, "https://example.test/",
                  ".card", JSON.stringify({
                    title: {selector:"a", attr:"text"},
                    url: {selector:"a", attr:"href"},
                    cover: {selector:"img", attr:"src"}
                  })));
              },
              search: function(query, page) { return this.popular(page); },
              details: function(url) { return {title:"Sky Story", description:"A sample"}; },
              chapters: function(url) { return [{title:"Chapter 1", url:"https://example.test/chapter/1"}]; },
              pages: function(chapterUrl) { return ["https://example.test/pages/1.jpg"]; }
            };
            """;

    @Test public void validatesPluginManifestAndRunsDomHelper() throws Exception {
        JSONObject info = ReaderPluginRuntime.inspect(SCRIPT);
        assertEquals("sample-comic", info.getString("id"));
        assertEquals("comic", info.getString("type"));
        assertEquals("Sample Comic", info.getString("name"));

        JSONArray args = new JSONArray().put(1);
        Object raw = ReaderPluginRuntime.invoke(SCRIPT, "popular", args);
        assertTrue(raw instanceof JSONArray);
        JSONObject row = ((JSONArray) raw).getJSONObject(0);
        assertEquals("Sky Story", row.getString("title"));
        assertEquals("https://example.test/comic/1", row.getString("url"));
        assertEquals("https://example.test/cover.jpg", row.getString("cover"));

        Object pages = ReaderPluginRuntime.invoke(SCRIPT, "pages",
                new JSONArray().put("https://example.test/chapter/1"));
        assertEquals("https://example.test/pages/1.jpg", ((JSONArray) pages).getString(0));
    }

    @Test public void validatesAndRunsNewsSourcePlugin() throws Exception {
        String script = "var NM7_SOURCE = {"
                + "id:'sample-news',name:'Sample News',type:'news',baseUrl:'https://news.example',"
                + "categories:function(){return [{name:'Trang chủ',key:'home'},{name:'Thế giới',key:'world'}];},"
                + "articles:function(category,page){return [{title:'Tin thử nghiệm',url:'/story/1',summary:'Tóm tắt',imageUrl:'/cover.jpg'}];}"
                + "};";
        JSONObject info = ReaderPluginRuntime.inspect(script);
        assertEquals("news", info.getString("type"));
        assertEquals("sample-news", info.getString("id"));
        Object raw = ReaderPluginRuntime.invoke(script, "articles",
                new JSONArray().put("world").put(1));
        assertTrue(raw instanceof JSONArray);
        JSONObject article = ((JSONArray) raw).getJSONObject(0);
        assertEquals("Tin thử nghiệm", article.getString("title"));
        assertEquals("/story/1", article.getString("url"));
        Object categories = ReaderPluginRuntime.invoke(script, "categories", new JSONArray());
        assertTrue(categories instanceof JSONArray);
        assertEquals("Thế giới", ((JSONArray) categories).getJSONObject(1).getString("name"));
    }

    @Test public void rejectsSourceWithNoRequiredReaderMethods() throws Exception {
        String invalid = "var NM7_SOURCE={id:'bad',name:'Bad',type:'comic',baseUrl:'https://example.test'};";
        try {
            ReaderPluginRuntime.inspect(invalid);
            fail("Expected invalid plugin to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Plugin") || expected.getMessage().contains("Nguồn"));
        }
    }
}
