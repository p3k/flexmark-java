package com.vladsch.flexmark.util.html;

import com.vladsch.flexmark.util.Utils;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class HtmlFormattingAppendableBaseTest {

    @Test
    public void test_basic() throws Exception {
        final HtmlFormattingAppendableBase fa = new HtmlFormattingAppendableBase(2, LineFormattingAppendable.F_FORMAT_ALL);

        fa.tagIndent("ul", () -> fa.withCondIndent().tagLine("li", () -> fa.text("item1")));
        assertEquals("<ul>\n  <li>item1</li>\n</ul>\n", fa.toString(0));
    }

    @Test
    public void test_basic1() throws Exception {
        final HtmlFormattingAppendableBase fa1 = new HtmlFormattingAppendableBase(2, LineFormattingAppendable.F_FORMAT_ALL);

        fa1.tagIndent("ul", () -> fa1.withCondIndent().tagLine("li", () -> {
            fa1.text("item1");
            fa1.tagIndent("ul", () -> fa1.withCondIndent().tagLine("li", () -> fa1.text("item1")));
        }));

        assertEquals("<ul>\n  <li>item1\n    <ul>\n      <li>item1</li>\n    </ul>\n  </li>\n</ul>\n", fa1.toString(0));
    }

    @Test
    public void test_basic2() throws Exception {
        final HtmlFormattingAppendableBase fa2 = new HtmlFormattingAppendableBase(2, LineFormattingAppendable.F_FORMAT_ALL);

        fa2.withCondLineOnChildText().withCondIndent().tag("tbody", () -> {
        });

        assertEquals("<tbody></tbody>\n", fa2.toString(0));
    }

    @Test
    public void test_basic3() throws Exception {
        final HtmlFormattingAppendableBase fa = new HtmlFormattingAppendableBase(2, LineFormattingAppendable.F_FORMAT_ALL);

        fa.tagIndent("ul", () -> fa.withCondIndent().tagLine("li", () -> fa.text("item1\ntwo line text")));
        assertEquals("" +
                "<ul>\n" +
                "  <li>item1\n" +
                "    two line text</li>\n" +
                "</ul>\n" +
                "", fa.toString(0));
    }

    @Test
    public void test_basic4() throws Exception {
        final HtmlFormattingAppendableBase fa = new HtmlFormattingAppendableBase(2, LineFormattingAppendable.F_FORMAT_ALL);

        fa.tagIndent("ul", () -> {
            fa.withCondIndent().tagLine("li", () -> fa.text("item1\ntwo line text"));
            fa.withCondIndent().tagLine("li", () -> fa.text("item1"));
        });

        assertEquals("" +
                "<ul>\n" +
                "  <li>item1\n" +
                "    two line text</li>\n" +
                "  <li>item1</li>\n" +
                "</ul>\n" +
                "", fa.toString(0));
    }

    // test tag tracking

    @Test
    public void test_tagList() throws Exception {
        final HtmlFormattingAppendableBase fa = new HtmlFormattingAppendableBase(2, LineFormattingAppendable.F_FORMAT_ALL);

        fa.tag("span", false);
        fa.tagIndent("ul", () -> fa.withCondIndent().tagLine("li", () -> {
            final List<String> tagsAfterLast = fa.getOpenTagsAfterLast("span");
            String tags = Utils.splice(tagsAfterLast.toArray(new String[tagsAfterLast.size()]), ", ");
            assertEquals("ul, li", tags);
            fa.text("item1");
        }));

        fa.closeTag("span");

        assertEquals("<span>\n<ul>\n  <li>item1</li>\n</ul>\n</span>\n", fa.toString(0));

        final HtmlFormattingAppendableBase fa1 = new HtmlFormattingAppendableBase(2, LineFormattingAppendable.F_FORMAT_ALL);

        fa1.tagIndent("ul", () -> fa1.withCondIndent().tagLine("li", () -> {
            final List<String> tagsAfterLast = fa.getOpenTagsAfterLast("span");
            String tags = Utils.splice(tagsAfterLast.toArray(new String[tagsAfterLast.size()]), ", ");
            assertEquals("", tags);
            fa1.text("item1");
            fa1.tagIndent("ul", () -> fa1.withCondIndent().tagLine("li", () -> fa1.text("item1")));
        }));

        assertEquals("<ul>\n  <li>item1\n    <ul>\n      <li>item1</li>\n    </ul>\n  </li>\n</ul>\n", fa1.toString(0));
    }
}
