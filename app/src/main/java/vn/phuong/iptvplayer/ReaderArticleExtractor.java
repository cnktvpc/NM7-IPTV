package vn.phuong.iptvplayer;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.parser.Parser;
import org.jsoup.safety.Safelist;

import java.io.IOException;
import java.util.Locale;

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

    public static String fetchAndRender(String url, String requestedTitle, String summary,
                                        String imageUrl, String sourceName, String publishedDate,
                                        int fontScale) throws IOException {
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
                sourceName, publishedDate, fontScale, "");
    }

    public static String fallback(String url, String title, String summary, String imageUrl,
                                  String sourceName, String publishedDate, int fontScale,
                                  String message) {
        return render(Jsoup.parse("", url), url, title, summary, imageUrl,
                sourceName, publishedDate, fontScale, message);
    }

    public static String renderForTest(String rawHtml, String baseUrl, String title,
                                       String summary, String imageUrl, String sourceName,
                                       String publishedDate, int fontScale) {
        return render(Jsoup.parse(rawHtml, baseUrl), baseUrl, title, summary, imageUrl,
                sourceName, publishedDate, fontScale, "");
    }

    private static String render(Document document, String baseUrl, String requestedTitle,
                                 String requestedSummary, String requestedImage,
                                 String sourceName, String publishedDate, int fontScale,
                                 String fallbackMessage) {
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
        String hero = firstNonBlank(requestedImage,
                meta(document, "og:image"),
                meta(document, "twitter:image"));

        Element article = findArticleBody(document);
        String articleHtml = "";
        if (article != null) {
            Element clone = article.clone();
            clone.select(REMOVE_SELECTOR).remove();
            for (Element image : clone.select("img")) {
                String src = firstNonBlank(
                        image.absUrl("data-src"), image.absUrl("data-original"),
                        image.absUrl("data-lazy-src"), image.absUrl("data-original-src"),
                        image.absUrl("src"));
                if (src.isEmpty()) {
                    image.remove();
                    continue;
                }
                image.attr("src", src);
                image.removeAttr("srcset");
                image.removeAttr("data-srcset");
                image.removeAttr("data-src");
                image.removeAttr("data-original");
                image.removeAttr("data-lazy-src");
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
                    .addAttributes("img", "src", "alt", "title")
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

        int scale = Math.max(85, Math.min(fontScale, 145));
        int bodyPx = Math.round(19f * scale / 100f);
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
                .append(".body p{font-size:1em;line-height:").append(lineHeight).append(";margin:0 0 1.12em;color:").append(TEXT).append(";}")
                .append(".body h2,.body h3,.body h4{color:#fff;line-height:1.4;margin:1.45em 0 .65em;font-weight:700;}")
                .append(".body blockquote{margin:1.1em 0;padding:3px 0 3px 14px;border-left:3px solid #ff8a2a;color:#c8cad0;}")
                .append(".body img,.body video{display:block;max-width:100%;height:auto;margin:16px auto;border-radius:9px;}")
                .append(".body figure{margin:18px 0;}.body figcaption{font-size:.76em;line-height:1.5;color:").append(MUTED).append(";text-align:center;margin-top:6px;}")
                .append(".body a{color:#ffad65;text-decoration:none;}.body ul,.body ol{padding-left:1.4em;}")
                .append(".body li{margin:.5em 0;}.body table{width:100%;border-collapse:collapse;display:block;overflow-x:auto;font-size:.84em;}")
                .append(".body th,.body td{border:1px solid #3b3c43;padding:7px;text-align:left;}")
                .append(".notice{font-size:13px;color:").append(MUTED).append(";border-top:1px solid #303138;padding-top:14px;margin-top:24px;}")
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
        html.append("</article></body></html>");
        return html.toString();
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
