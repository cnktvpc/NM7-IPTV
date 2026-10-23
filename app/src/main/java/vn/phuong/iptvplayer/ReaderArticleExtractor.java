package vn.phuong.iptvplayer;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.parser.Parser;
import org.jsoup.safety.Safelist;

import java.io.IOException;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fetches an article once, extracts the editorial body, and renders it into NM7's own
 * reader document. The publisher's HTML/CSS/JavaScript is never shown as the article UI.
 */
public final class ReaderArticleExtractor {
    private static final String DARK = "#101114";
    private static final String TEXT = "#e9e9ed";
    private static final String MUTED = "#aeb1bb";

    private static final String[] ARTICLE_SELECTORS = {
            "[itemprop=articleBody]", ".fck_detail", ".dt-news__content",
            ".singular-content", ".article-content", ".article__body", ".article-body",
            ".detail-content", ".content-detail", ".detail__content", "#article-content",
            ".article-content-body", ".post-content", ".entry-content", ".news-content",
            ".story-content", ".article__content", ".content-news", ".content_article",
            ".main-content article", "article", "main"
    };

    private static final String REMOVE_SELECTOR =
            "script,style,noscript,iframe:not([src*=youtube]):not([src*=vimeo])," +
            "form,nav,aside,button,select,textarea,svg,canvas," +
            "[class*=advert],[class*=adsense],[class*=ad-container],[class*=adslot]," +
            "[class*=ad-slot],[class*=banner-ad],[class*=sponsor],[class*=sponsored]," +
            "[id^=ad-],[id*=-ad-],[id*=_ad_],[id*=ads-],[id*=ads_]," +
            "[data-ad],[data-ad-unit],[data-ad-slot],[aria-label*=advertisement]," +
            "[class*=related],[id*=related],[class*=recommend],[id*=recommend]," +
            "[class*=most-viewed],[class*=social-share],[class*=share-box]," +
            "[class*=comment],[id*=comment],[class*=sidebar],[class*=trending]," +
            "[class*=breadcrumb],[class*=pagination],[class*=author-box]";

    private ReaderArticleExtractor() { }

    public static final class RelatedArticle {
        public final String title, url, summary, imageUrl, source;
        public RelatedArticle(String title, String url, String summary, String imageUrl, String source) {
            this.title = title == null ? "" : title;
            this.url = url == null ? "" : url;
            this.summary = summary == null ? "" : summary;
            this.imageUrl = imageUrl == null ? "" : imageUrl;
            this.source = source == null ? "" : source;
        }
    }

    public static String fetchAndRender(String url, String requestedTitle, String summary,
                                        String imageUrl, String sourceName, String publishedDate,
                                        int fontScale) throws IOException {
        return fetchAndRender(url, requestedTitle, summary, imageUrl, sourceName, publishedDate,
                Collections.emptyList(), fontScale);
    }

    public static String fetchAndRender(String url, String requestedTitle, String summary,
                                        String imageUrl, String sourceName, String publishedDate,
                                        List<RelatedArticle> related, int fontScale) throws IOException {
        Document document = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                .header("Accept-Language", "vi-VN,vi;q=0.9,en;q=0.5")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .referrer("https://www.google.com/")
                .timeout(16000)
                .maxBodySize(6 * 1024 * 1024)
                .followRedirects(true)
                .get();
        return render(document, url, requestedTitle, summary, imageUrl,
                sourceName, publishedDate, related, fontScale, "");
    }

    public static String fallback(String url, String title, String summary, String imageUrl,
                                  String sourceName, String publishedDate, int fontScale,
                                  String message) {
        return fallback(url, title, summary, imageUrl, sourceName, publishedDate,
                Collections.emptyList(), fontScale, message);
    }

    public static String fallback(String url, String title, String summary, String imageUrl,
                                  String sourceName, String publishedDate, List<RelatedArticle> related,
                                  int fontScale, String message) {
        return render(Jsoup.parse("", url), url, title, summary, imageUrl,
                sourceName, publishedDate, related, fontScale, message);
    }

    public static String renderForTest(String rawHtml, String baseUrl, String title,
                                       String summary, String imageUrl, String sourceName,
                                       String publishedDate, int fontScale) {
        return renderForTestWithRelated(rawHtml, baseUrl, title, summary, imageUrl, sourceName,
                publishedDate, Collections.emptyList(), fontScale);
    }

    public static String renderForTestWithRelated(String rawHtml, String baseUrl, String title,
                                                  String summary, String imageUrl, String sourceName,
                                                  String publishedDate, List<RelatedArticle> related,
                                                  int fontScale) {
        return render(Jsoup.parse(rawHtml, baseUrl), baseUrl, title, summary, imageUrl,
                sourceName, publishedDate, related, fontScale, "");
    }

    private static String render(Document document, String baseUrl, String requestedTitle,
                                 String requestedSummary, String requestedImage,
                                 String sourceName, String publishedDate,
                                 List<RelatedArticle> related, int fontScale, String fallbackMessage) {
        if (document == null) document = Jsoup.parse("", baseUrl);

        String title = firstNonBlank(requestedTitle, meta(document, "og:title"),
                textOf(document, "h1.title-detail,h1.title,[itemprop=headline],.article-title,h1"),
                document.title());
        String summary = firstNonBlank(requestedSummary, meta(document, "og:description"),
                meta(document, "description"),
                textOf(document, ".description,.sapo,.detail-sapo,[itemprop=description],.article-summary"));
        String date = firstNonBlank(publishedDate,
                meta(document, "article:published_time"),
                textOf(document, "time,.date-time,.publish-date,[itemprop=datePublished]"));
        String hero = firstHttpUrl(baseUrl, requestedImage,
                meta(document, "og:image"),
                meta(document, "twitter:image"));

        Element article = findArticleBody(document);
        String articleHtml = "";
        if (article != null) {
            Element clone = article.clone();
            clone.select(REMOVE_SELECTOR).remove();
            for (Element image : clone.select("img")) {
                String src = firstHttpUrl(baseUrl,
                        firstSrcsetUrl(image.attr("data-lazy-srcset")),
                        firstSrcsetUrl(image.attr("data-srcset")),
                        firstSrcsetUrl(image.attr("srcset")),
                        image.attr("data-ks-lazyload"),
                        image.attr("data-lazy-src"),
                        image.attr("data-original-src"),
                        image.attr("data-original"),
                        image.attr("data-actualsrc"),
                        image.attr("data-echo"),
                        image.attr("data-fallback-src"),
                        image.attr("data-image"),
                        image.attr("data-url"),
                        image.attr("data-src"),
                        image.attr("src"));
                if (src.isEmpty()) {
                    Element picture = image.parent();
                    if (picture != null && "picture".equalsIgnoreCase(picture.tagName())) {
                        for (Element source : picture.select("source[srcset]")) {
                            src = firstHttpUrl(baseUrl, firstSrcsetUrl(source.attr("srcset")));
                            if (!src.isEmpty()) break;
                        }
                    }
                }
                if (src.isEmpty()) {
                    image.remove();
                    continue;
                }
                image.attr("src", src);
                image.attr("loading", "eager");
                image.attr("decoding", "async");
                image.removeAttr("srcset");
                image.removeAttr("data-lazy-srcset");
                image.removeAttr("data-srcset");
                image.removeAttr("data-ks-lazyload");
                image.removeAttr("data-lazy-src");
                image.removeAttr("data-original-src");
                image.removeAttr("data-original");
                image.removeAttr("data-actualsrc");
                image.removeAttr("data-echo");
                image.removeAttr("data-fallback-src");
                image.removeAttr("data-image");
                image.removeAttr("data-url");
                image.removeAttr("data-src");
                image.removeAttr("width");
                image.removeAttr("height");
            }
            for (Element link : clone.select("a[href]")) {
                String target = link.absUrl("href");
                if (!target.isEmpty()) link.attr("href", target);
                link.removeAttr("target");
                link.removeAttr("onclick");
            }
            clone.select("[style],[onclick],[onload],[onerror],[id]").removeAttr("style");
            Safelist allow = Safelist.relaxed()
                    .addTags("figure", "figcaption", "picture", "source", "video",
                            "audio", "h2", "h3", "h4", "blockquote", "table", "thead",
                            "tbody", "tr", "th", "td", "hr", "br")
                    .addAttributes("img", "src", "alt", "title", "loading", "decoding")
                    .addAttributes("a", "href", "title")
                    .addAttributes("source", "src", "srcset", "type")
                    .addAttributes("video", "src", "poster", "controls")
                    .addAttributes("audio", "src", "controls")
                    .preserveRelativeLinks(false);
            articleHtml = Jsoup.clean(clone.html(), baseUrl, allow);
        }

        if (articleHtml.trim().isEmpty()) {
            String fallbackParagraphs = extractParagraphFallback(document);
            if (!fallbackParagraphs.isEmpty()) articleHtml = fallbackParagraphs;
        }

        int bodyPx = 19; // WebView textZoom is the single source of font scaling; avoid double scaling.
        String lineHeight = "1.82";
        StringBuilder html = new StringBuilder(10000);
        html.append("<!doctype html><html lang=\"vi\"><head>")
                .append("<meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=5\">")
                .append("<meta name=\"color-scheme\" content=\"dark\">")
                .append("<style>")
                .append("*{box-sizing:border-box;-webkit-text-size-adjust:100%;}")
                .append("html,body{margin:0;min-height:100%;background:").append(DARK)
                .append(";color:").append(TEXT).append(";}")
                .append("body{font-family:Roboto,'Noto Sans',Arial,sans-serif;font-size:")
                .append(bodyPx).append("px;line-height:1.82;padding:0 0 36px;}")
                .append(".article{max-width:900px;margin:0 auto;padding:14px 18px 30px;overflow-wrap:anywhere;}")
                .append("h1{color:#fff;font-size:1.42em;line-height:1.34;font-weight:750;letter-spacing:-.25px;margin:2px 0 12px;}")
                .append(".byline{font-size:13px;line-height:1.55;color:").append(MUTED)
                .append(";margin:0 0 17px;}")
                .append(".summary{font-size:1.03em;line-height:1.65;color:#f3f3f5;font-weight:550;margin:0 0 18px;}")
                .append(".hero{width:100%;height:auto;display:block;margin:14px auto 8px;border-radius:9px;}")
                .append(".body{color:").append(TEXT).append(";}")
                .append(".body p,.body li,.body blockquote,.body td{font-family:Roboto,'Noto Sans',Arial,sans-serif;font-size:").append(bodyPx).append("px;font-weight:400;font-style:normal;line-height:").append(lineHeight).append(";}")
                .append(".body p *,.body li *,.body blockquote *,.body td *{font-family:inherit!important;font-size:inherit!important;font-weight:400!important;font-style:normal!important;line-height:inherit!important;}")
                .append(".body p{margin:0 0 1.12em;color:").append(TEXT).append(";}")
                .append(".body h2,.body h3,.body h4{color:#fff;line-height:1.4;margin:1.45em 0 .65em;font-weight:700;}")
                .append(".body blockquote{margin:1.1em 0;padding:3px 0 3px 14px;border-left:3px solid #ff8a2a;color:#c8cad0;}")
                .append(".body img,.body video{display:block;max-width:100%;height:auto;margin:16px auto;border-radius:9px;}")
                .append(".body figure{margin:18px 0;}.body figcaption{font-size:.76em;line-height:1.5;color:").append(MUTED).append(";text-align:center;margin-top:6px;}")
                .append(".body a{color:#ffad65;text-decoration:none;}.body ul,.body ol{padding-left:1.4em;}")
                .append(".body li{margin:.5em 0;}.body table{width:100%;border-collapse:collapse;display:block;overflow-x:auto;font-size:.84em;}")
                .append(".body th,.body td{border:1px solid #3b3c43;padding:7px;text-align:left;}")
                .append(".notice{font-size:13px;color:").append(MUTED).append(";border-top:1px solid #303138;padding-top:14px;margin-top:24px;}")
                .append(".related{border-top:1px solid #303138;margin-top:26px;padding-top:18px}.related h2{font-size:18px;color:#fff;margin:0 0 12px}.rel-card{display:flex;gap:12px;padding:11px 0;border-bottom:1px solid #292a32;text-decoration:none;color:").append(TEXT).append(";}.rel-card img{width:108px;height:76px;object-fit:cover;border-radius:7px;flex-shrink:0}.rel-copy{display:block;min-width:0}.rel-title{display:block;font-size:14px;font-weight:650;line-height:1.45;color:").append(TEXT).append(";}.rel-meta{display:block;font-size:11px;color:").append(MUTED).append(";margin-top:5px}.rel-summary{display:block;font-size:12px;line-height:1.4;color:#bfc1ca;margin-top:5px}")
                .append("</style></head><body><article class=\"article\">");

        html.append("<h1>").append(escape(title)).append("</h1>");
        String byline = joinMeta(sourceName, date);
        if (!byline.isEmpty()) html.append("<div class=\"byline\">").append(escape(byline)).append("</div>");
        if (isHttpUrl(hero)) {
            html.append("<img class=\"hero\" src=\"").append(escape(hero)).append("\" alt=\"Ảnh bài viết\">");
        }
        if (!summary.isEmpty() && !summary.equalsIgnoreCase(title)) {
            html.append("<p class=\"summary\">").append(escape(summary)).append("</p>");
        }
        if (!articleHtml.isEmpty()) {
            html.append("<div class=\"body\">").append(articleHtml).append("</div>");
        } else {
            html.append("<div class=\"notice\">")
                    .append(escape(fallbackMessage.isEmpty()
                            ? "Trang báo chưa cung cấp phần nội dung có thể trích xuất. Nội dung tóm tắt phía trên là phần dữ liệu hiện có."
                            : fallbackMessage))
                    .append("</div>");
        }
        appendRelatedCards(html, related);
        html.append("</article></body></html>");
        return html.toString();
    }

    private static void appendRelatedCards(StringBuilder html, List<RelatedArticle> related) {
        if (related == null || related.isEmpty()) return;
        int count = 0;
        html.append("<section class=\"related\"><h2>Bài viết liên quan</h2>");
        for (RelatedArticle item : related) {
            if (item == null || !isHttpUrl(item.url) || item.title.trim().isEmpty()) continue;
            html.append("<a class=\"rel-card\" href=\"nm7://related/").append(count).append("\">");
            if (isHttpUrl(item.imageUrl)) {
                html.append("<img loading=\"lazy\" src=\"").append(escape(item.imageUrl)).append("\" alt=\"\">");
            }
            html.append("<span class=\"rel-copy\"><span class=\"rel-title\">")
                    .append(escape(item.title)).append("</span>");
            String meta = joinMeta(item.source, item.summary);
            if (!meta.isEmpty()) html.append("<span class=\"rel-summary\">").append(escape(meta.length() > 170 ? meta.substring(0, 167) + "..." : meta)).append("</span>");
            html.append("</span></a>");
            if (++count >= 8) break;
        }
        html.append("</section>");
    }

    private static Element findArticleBody(Document document) {
        for (String selector : ARTICLE_SELECTORS) {
            Element node = document.selectFirst(selector);
            if (usable(node)) return node;
        }

        Element best = null;
        int bestScore = 0;
        for (Element node : document.select("article,main,section,div")) {
            if (hasExcludedAncestor(node)) continue;
            String text = node.text();
            int length = text.length();
            int paragraphs = node.select("p").size();
            if (length < 260 || paragraphs < 2 || length > 50000) continue;
            String descriptor = (node.className() + " " + node.id()).toLowerCase(Locale.ROOT);
            if (descriptor.matches(".*(sidebar|related|recommend|comment|header|footer|menu|nav|hot-news|popular).*")) continue;
            int score = Math.min(length, 9000) + Math.min(paragraphs, 30) * 110;
            if (descriptor.matches(".*(article|content|detail|body|story|fck|news|post).*")) score += 1000;
            if (node.select("img").size() > 0) score += 120;
            if (node.select("a").size() > paragraphs * 3) score -= 900;
            if (score > bestScore) { best = node; bestScore = score; }
        }
        return usable(best) ? best : null;
    }

    private static boolean usable(Element element) {
        if (element == null) return false;
        int textLength = element.text().length();
        int paragraphs = element.select("p").size();
        return textLength >= 180 && (paragraphs >= 1 || element.select("img").size() >= 2);
    }

    private static boolean hasExcludedAncestor(Element node) {
        for (Element parent = node.parent(); parent != null; parent = parent.parent()) {
            if (isExcludedName(parent.tagName(), parent.className(), parent.id())) return true;
        }
        return false;
    }

    private static boolean isExcludedName(String tag, String cssClass, String id) {
        String value = (tag + " " + cssClass + " " + id).toLowerCase(Locale.ROOT);
        return value.contains("header") || value.contains("footer") || value.contains("sidebar")
                || value.contains("related") || value.contains("recommend") || value.contains("comment")
                || value.contains("breadcrumb") || value.contains("menu") || value.contains("advert")
                || value.contains("ad-container") || value.contains("social-share");
    }

    private static String extractParagraphFallback(Document document) {
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (Element p : document.select("body p")) {
            if (hasExcludedAncestor(p)) continue;
            String text = p.text().trim();
            if (text.length() < 35 || text.length() > 3500) continue;
            out.append("<p>").append(escape(text)).append("</p>");
            if (++count >= 18) break;
        }
        return out.toString();
    }

    private static String meta(Document document, String key) {
        if (key == null || key.isEmpty()) return "";
        String safeKey = key.replace("\\", "\\\\").replace("\"", "\\\"");
        Element e = document.selectFirst("meta[property=\"" + safeKey + "\"],meta[name=\"" + safeKey + "\"],meta[itemprop=\"" + safeKey + "\"]");
        if (e == null) return "";
        return e.attr("content").trim();
    }

    private static String textOf(Document document, String selector) {
        Element e = document.selectFirst(selector);
        return e == null ? "" : e.text().trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) if (value != null && !value.trim().isEmpty()) return value.trim();
        return "";
    }

    private static String firstHttpUrl(String baseUrl, String... candidates) {
        if (candidates == null) return "";
        for (String candidate : candidates) {
            if (candidate == null) continue;
            String value = candidate.trim().replace("&amp;", "&");
            if (value.isEmpty() || value.startsWith("data:") || value.startsWith("blob:")) continue;
            if (value.startsWith("//")) value = "https:" + value;
            try {
                java.net.URL resolved = new java.net.URL(new java.net.URL(baseUrl), value);
                String protocol = resolved.getProtocol();
                if (("http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol))
                        && resolved.getHost() != null && !resolved.getHost().isEmpty()) return resolved.toExternalForm();
            } catch (Exception ignored) { }
        }
        return "";
    }

    private static String firstSrcsetUrl(String srcset) {
        if (srcset == null || srcset.trim().isEmpty()) return "";
        String best = "";
        long bestWidth = -1;
        for (String entry : srcset.split(",")) {
            String[] parts = entry.trim().split("\\s+");
            if (parts.length == 0 || parts[0].trim().isEmpty()) continue;
            long width = 0;
            if (parts.length > 1) {
                String descriptor = parts[1].trim().toLowerCase(Locale.ROOT);
                try {
                    if (descriptor.endsWith("w")) width = Long.parseLong(descriptor.substring(0, descriptor.length() - 1));
                    else if (descriptor.endsWith("x")) width = Math.round(Double.parseDouble(descriptor.substring(0, descriptor.length() - 1)) * 1000);
                } catch (Exception ignored) { }
            }
            if (width >= bestWidth) { bestWidth = width; best = parts[0].trim(); }
        }
        return best;
    }

    private static String joinMeta(String source, String date) {
        if (source == null || source.trim().isEmpty()) return date == null ? "" : date.trim();
        if (date == null || date.trim().isEmpty()) return source.trim();
        return source.trim() + "  •  " + date.trim();
    }

    private static String escape(String value) {
        return Entities.escape(value == null ? "" : value);
    }

    private static boolean isHttpUrl(String value) {
        return value != null && (value.startsWith("https://") || value.startsWith("http://"));
    }
}
