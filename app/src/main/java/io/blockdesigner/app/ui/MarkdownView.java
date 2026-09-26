package io.blockdesigner.app.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders the Markdown that release notes use as JavaFX nodes (no web view): headings, paragraphs, bullet and numbered
 * lists (nested by indentation), tables, horizontal rules, and inline <b>bold</b>, {@code code} and links. Anything
 * else shows as plain text.
 */
final class MarkdownView {
    private MarkdownView() {
    }

    /** Bold, inline code or a link, in the order they appear. */
    private static final Pattern INLINE = Pattern.compile("\\*\\*(.+?)\\*\\*|`([^`]+)`|\\[([^\\]]+)]\\(([^)\\s]+)\\)");
    private static final Pattern BULLET = Pattern.compile("^(\\s*)([-*+]|\\d+[.)])\\s+(.*)$");

    /** The rendered document; links open through {@code openLink}. */
    static VBox render(String markdown, Consumer<String> openLink) {
        VBox out = new VBox(6);
        out.getStyleClass().add("markdown");
        List<String> lines = List.of(markdown.replace("\r", "").split("\n", -1));
        StringBuilder para = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i), t = line.strip();
            if (t.isEmpty()) {
                flush(para, out, openLink);
                continue;
            }
            if (t.startsWith("#")) {
                flush(para, out, openLink);
                int level = 0;
                while (level < t.length() && t.charAt(level) == '#') level++;
                TextFlow h = inline(t.substring(level).strip(), openLink);
                h.getStyleClass().add("md-h" + Math.min(level, 3));
                if (!out.getChildren().isEmpty()) VBox.setMargin(h, new Insets(level <= 2 ? 10 : 6, 0, 0, 0));
                out.getChildren().add(h);
                continue;
            }
            if (t.matches("-{3,}|\\*{3,}|_{3,}")) {
                flush(para, out, openLink);
                out.getChildren().add(new Separator());
                continue;
            }
            if (t.startsWith("|")) {
                flush(para, out, openLink);
                List<String> rows = new ArrayList<>();
                while (i < lines.size() && lines.get(i).strip().startsWith("|")) rows.add(lines.get(i++).strip());
                i--;
                out.getChildren().add(table(rows, openLink));
                continue;
            }
            Matcher b = BULLET.matcher(line);
            if (b.matches()) {
                flush(para, out, openLink);
                // A list: this item, its continuation lines, and the items after it (blank lines inside are allowed
                // while the next line is still indented or another item).
                VBox list = new VBox(3);
                while (i < lines.size()) {
                    Matcher m = BULLET.matcher(lines.get(i));
                    if (!m.matches()) break;
                    int indent = m.group(1).replace("\t", "  ").length() / 2;
                    StringBuilder item = new StringBuilder(m.group(3).strip());
                    int j = i + 1;
                    while (j < lines.size()) {
                        String next = lines.get(j);
                        if (next.isBlank() || BULLET.matcher(next).matches() || !Character.isWhitespace(next.charAt(0))) break;
                        item.append(' ').append(next.strip());
                        j++;
                    }
                    list.getChildren().add(bullet(m.group(2), item.toString(), indent, openLink));
                    i = j;
                    // Skip blank lines between items of the same list.
                    while (i < lines.size() && lines.get(i).isBlank() && i + 1 < lines.size() && BULLET.matcher(lines.get(i + 1)).matches()) i++;
                    if (i < lines.size() && lines.get(i).isBlank()) break;
                }
                i--;
                out.getChildren().add(list);
                continue;
            }
            if (!para.isEmpty()) para.append(' ');
            para.append(t);
        }
        flush(para, out, openLink);
        return out;
    }

    private static void flush(StringBuilder para, VBox out, Consumer<String> openLink) {
        if (para.isEmpty()) return;
        out.getChildren().add(inline(para.toString(), openLink));
        para.setLength(0);
    }

    private static Node bullet(String marker, String text, int indent, Consumer<String> openLink) {
        Label dot = new Label(marker.matches("\\d+[.)]") ? marker : "•");
        dot.getStyleClass().add("md-bullet");
        dot.setMinWidth(16);
        TextFlow body = inline(text, openLink);
        HBox.setHgrow(body, Priority.ALWAYS);
        HBox row = new HBox(4, dot, body);
        row.setPadding(new Insets(0, 0, 0, 4 + indent * 18));
        return row;
    }

    private static Node table(List<String> rows, Consumer<String> openLink) {
        GridPane g = new GridPane();
        g.getStyleClass().add("md-table");
        g.setHgap(14);
        g.setVgap(4);
        int r = 0;
        for (String row : rows) {
            String inner = row.replaceAll("^\\|", "").replaceAll("\\|$", "");
            // The |---|---| line under the header.
            if (inner.matches("[\\s:|-]+")) continue;
            String[] cells = inner.split("\\|", -1);
            for (int c = 0; c < cells.length; c++) {
                TextFlow cell = inline(cells[c].strip(), openLink);
                if (r == 0) cell.getStyleClass().add("md-th");
                cell.setMaxWidth(380);
                g.add(cell, c, r);
            }
            r++;
        }
        return g;
    }

    /** One line of text with **bold**, `code` and [links](url). */
    static TextFlow inline(String s, Consumer<String> openLink) {
        TextFlow f = new TextFlow();
        f.getStyleClass().add("md-p");
        Matcher m = INLINE.matcher(s);
        int at = 0;
        while (m.find()) {
            if (m.start() > at) f.getChildren().add(text(s.substring(at, m.start()), "md-text"));
            if (m.group(1) != null) {
                // Bold may itself hold code or links.
                // (A copy: adding a node to this flow takes it out of the other one.)
                for (Node n : List.copyOf(inline(m.group(1), openLink).getChildren())) {
                    n.getStyleClass().add("md-bold");
                    f.getChildren().add(n);
                }
            } else if (m.group(2) != null) {
                f.getChildren().add(text(m.group(2), "md-code"));
            } else {
                Hyperlink link = new Hyperlink(m.group(3));
                String url = m.group(4);
                link.getStyleClass().add("md-link");
                link.setOnAction(e -> openLink.accept(url));
                f.getChildren().add(link);
            }
            at = m.end();
        }
        if (at < s.length()) f.getChildren().add(text(s.substring(at), "md-text"));
        return f;
    }

    private static Text text(String s, String style) {
        Text t = new Text(s);
        t.getStyleClass().addAll("md-text", style);
        return t;
    }
}
