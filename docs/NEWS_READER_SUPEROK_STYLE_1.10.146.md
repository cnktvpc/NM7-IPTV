# NM7 Mobile 1.10.146 — News Reader recovery and JS news sources

Date: 2026-10-22  
Branch: fix/mobile-news-reader-20261022  
Candidate: 1.10.146 / versionCode 162  
Baseline: stable/mobile-1.10.142 / versionCode 158 (untouched)

## Problem found from the supplied video

The existing news screen used fixed RSS feed URLs as the only feed data source. When a feed returned HTTP errors or no parsed item/entry elements, NM7 showed an empty list and displayed an "open the publisher" escape hatch. That created a cycle: no native articles appeared, so the user had to open the website and read there. A live check also returned 403 for the 24h RSS endpoint; an RSS-only design cannot provide a dependable list for that source.

The previous source manager only covered the pre-bundled catalogue. It did not let the user add a custom news feed/provider.

## Feed loading recovery

For each built-in or custom URL source:
1. Try the configured RSS/Atom feed and parse title, link, summary, image and publish date.
2. If that fails or parses zero articles, request the mapped category/home page and extract article cards with Jsoup.
3. Normalize relative article/image links against the page URL, reject navigation/assets/category links, deduplicate articles and keep the user inside the NM7 list.
4. If both data paths fail, show retry and source-manager buttons in the app. It does not automatically open the source website as a fallback.

Built-in 24h, VnExpress, Dân Trí, Tuổi Trẻ, Thanh Niên and VTV categories have explicit HTML category fallbacks; other built-ins try their source home page. Some publishers may still require a site-specific scraper if their markup or anti-bot policy changes.

## Reading flow

- Feed items invoke the NM7 reader, not an external browser.
- The reader requests and sanitizes the article HTML and renders title, metadata, lead image, summary and article body with NM7-owned typography.
- At the end, up to eight other items from the loaded category are shown as "Bài viết liên quan". The links use an internal nm7://related/<index> route, intercepted by the WebView client to open that item in the same Reader.
- Left/right swipes on the feed switch the selected category in the source's category list. The horizontal category strip scrolls the selected tab into view.
- External links embedded within the article are still available through the external-browser handler; article cards and related-story cards do not use that path.

## Add a news source

The News source manager now supports:
- adding an RSS/Atom URL or a regular news-list page URL (try RSS, then scrape HTML);
- importing a .js news provider from a URL or the Android document picker;
- enable/disable and remove for custom RSS sources and installed JS providers.

A news provider may be shaped like this:

    var NM7_SOURCE = {
      id: "my-news",
      name: "My News",
      type: "news",
      baseUrl: "https://news.example",
      description: "Custom news source",
      categories: function () {
        return [
          { name: "Trang chủ", key: "home" },
          { name: "Thế giới", key: "world" }
        ];
      },
      articles: function (category, page) {
        var html = NM7.http.get(this.baseUrl + "/");
        var fields = JSON.stringify({
          title: { selector: "a.title", attr: "text" },
          url: { selector: "a.title", attr: "href" },
          summary: { selector: ".summary", attr: "text" },
          imageUrl: { selector: "img", attr: "src" }
        });
        return JSON.parse(NM7.dom.select(html, this.baseUrl, "article", fields));
      }
    };

The type "news" must define articles(category,page) or popular(category,page). Optional categories() may return strings or {name,key} objects. Article objects should include title and url, and may include summary, imageUrl/image, and date. The DOM helper resolves relative href/src values where possible. Install only trusted .js source files; the interpreter hides Java class access and limits script size, but intentionally allows NM7.http.get so a provider can fetch its site's feed/HTML.

## Super OK comparison and compatibility

The supplied recording establishes the user-visible behavior to reproduce, but I could not locate a public Super OK provider specification or source code that proves its exact proprietary JavaScript API. This implementation therefore follows the same workflow—source/provider-driven news, category tabs, in-app article reader and related-story navigation—using a documented NM7 contract. It does not claim that every private Super OK .js file can be installed without adapting its method names/schema.

## Automated verification

The branch adds tests for:
- JS news-provider manifest validation, categories and article data return;
- related links included in the in-app article document;
- HTML feed fallback, swipe-to-category wiring, and custom RSS/JS source manager paths.

The build must still complete successfully in GitHub Actions before the APK is considered ready. A successful CI run does not replace testing the source feeds and reader on the actual phone.
