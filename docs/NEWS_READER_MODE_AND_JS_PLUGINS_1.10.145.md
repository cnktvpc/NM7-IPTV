# NM7 Mobile 1.10.145 — Native Article Reader + JS Comic/Novel Plugins

Date: 2026-10-22  
Branch: fix/mobile-news-reader-20261022  
Baseline: stable/mobile-1.10.142 (1.10.142 / code 158; unchanged)  
Candidate: 1.10.145 / versionCode 161

## Scope and decision

This work responds to the video 228126.mp4. The top and bottom tab positions were reported as correct and remain unchanged. The remaining issue was that tapping an article still loaded the publisher's full website and only then attempted a JavaScript DOM rewrite. That left site chrome visible and could leave dark text against a dark page when the rewrite failed.

The candidate now uses a different data flow: fetch the article HTML on a worker thread, parse and extract the article body on the native side with jsoup, sanitize the body and then render a fresh local document in the WebView. The publisher page's styles/scripts are not inserted as a full page and JavaScript stays disabled in the reader WebView. Its typography is controlled by NM7: near-black background, light text, readable line spacing, centered article width, metadata, lead image, summary, images/captions and a font-size control. If extraction fails, NM7 shows a clean fallback with the title/summary and a button to open the original page in a browser; it does not switch back to the hard-to-read full website inside the reader.

## Comic and novel sources

The old manager only contained fixed RSS source cards for Báo; the Truyện chữ and Truyện tranh tabs returned an empty-state message. Those two tabs now open a dedicated source manager that supports:
- installing a JavaScript .js source from a URL or a local file selected from the device;
- validating its ID, type, base URL and required functions before installation;
- listing installed sources, opening/browsing, enable/disable and remove;
- searching or viewing popular items, opening an item details screen, listing chapters, and reading pages/content;
- a continuous image page layout for comics and a sanitized, dark text reader for novels.

Plugin files and metadata are stored in app-private files; the source runtime is a JavaScript interpreter with a class-access shutter, bounded script size and a controlled NM7 HTTP/DOM facade. It does not expose Android classes directly. Users should install only trusted source scripts, since a plugin is still allowed to make HTTP requests to fetch its content.

### NM7 source-plugin contract

The plugin's exported object is NM7_SOURCE, or it can be supplied through module.exports or registerSource(NM7_SOURCE):

    var NM7_SOURCE = {
      id: "my-comic", name: "Example Comic", type: "comic",
      baseUrl: "https://example.com", version: "1.0.0",
      description: "Example source",
      popular: function(page) { return []; },
      search: function(keyword, page) { return []; },
      details: function(url) { return {title: "Comic title"}; },
      chapters: function(url) { return [{title: "Chapter 1", url: "https://example.com/chapter/1"}]; },
      pages: function(chapterUrl) { return ["https://example.com/images/1.jpg"]; }
    };

For type comic, pages(chapterUrl) is required. For type novel, use chapter(chapterUrl) to return HTML or plain text instead. At least one of search or popular and one of details or chapters is required. The runtime exposes NM7.http.get(url), NM7.dom.text(html, baseUrl, selector), NM7.dom.attr(html, baseUrl, selector, attribute) and NM7.dom.select(html, baseUrl, selector, fieldsJson); dom.select returns a JSON string to parse with JSON.parse.

This is an NM7-defined JS contract, not a claim of drop-in compatibility with an undocumented/proprietary Super OK script format. If exact Super OK plugin compatibility is required, the actual source file/manifest format needs to be inspected against a sample; it cannot be verified from the video alone.

## News list constraint

The fixed news catalogue and existing RSS feed URLs were not extended. No additional newspaper source was added. The custom .js installer is only exposed for Truyện chữ and Truyện tranh, not the news list.

## Automated verification

- ReaderArticleExtractorTest: verifies clean article typography, title/body/image extraction, ad/recommendation removal and graceful fallback.
- ReaderPluginRuntimeTest: verifies JS manifest validation, helper-based DOM extraction, normalized URLs and rejection of incomplete plugins.
- Static Reader guards: native article rendering, disabled page JavaScript, dark typography, existing inset/tab geometry and unchanged fixed news catalogue.
- Static plugin guards: required source UI, .js import paths, dependency/runtime wiring and private storage.
- Existing Live feed and fullscreen server regression guards are retained.
- Candidate APK is built as 1.10.145 / code 161 on the feature branch; stable/mobile-1.10.142 is not moved.

## Validation limits

CI tests compile the app and run unit tests but do not install the APK on the user's phone. Article markup varies by publisher, so the extractor tries publisher-specific article selectors and a text-only paragraph fallback; some anti-bot or dynamically generated pages may only provide title/summary and need a source-specific extractor. Actual run-time reading and compatibility with any actual Super OK .js plugin file still require a device test and, for plugin compatibility, a sample source file.
