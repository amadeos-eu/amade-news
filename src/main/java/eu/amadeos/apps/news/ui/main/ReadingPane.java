package eu.amadeos.apps.news.ui.main;

import eu.amadeos.apps.news.domain.Article;
import eu.amadeos.apps.news.images.ImageCache;
import eu.amadeos.apps.news.ui.common.Flags;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.event.HyperlinkEvent;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The article on the right: title, feed and date, picture, text. Without an article it shows a short text with
 * links instead (the welcome text before the first subscription).
 */
final class ReadingPane extends JPanel implements Scrollable {
    static final int PICTURE_WIDTH = 520;
    static final int PICTURE_HEIGHT = 300;

    private final Messages msg;
    private final ImageCache images;
    private final CardLayout cards = new CardLayout();
    private final JPanel article = new JPanel();
    private final JTextArea title = text();
    private final JLabel meta = new JLabel();
    private final JLabel picture = new JLabel();
    private final JTextArea body = text();
    private final JLabel more = new JLabel();
    private final JEditorPane placeholder = new JEditorPane();
    private Optional<ArticleRow> shown = Optional.empty();
    private boolean pictures = true;
    private int textSize = 13;

    ReadingPane(Messages msg, ImageCache images, Consumer<String> linkClicked) {
        this.msg = msg;
        this.images = images;
        setLayout(cards);
        setName("reading");

        article.setLayout(new BoxLayout(article, BoxLayout.Y_AXIS));
        article.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        title.setName("reading.title");
        meta.setName("reading.meta");
        meta.setForeground(AmadeUI.theme().textInactive());
        meta.setIconTextGap(6);
        picture.setName("reading.picture");
        body.setName("reading.body");
        more.setName("reading.more");
        more.setForeground(AmadeUI.theme().textInactive());
        for (JComponent c : List.of(title, meta, picture, body, more)) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        article.add(title);
        article.add(Box.createVerticalStrut(4));
        article.add(meta);
        article.add(Box.createVerticalStrut(10));
        article.add(picture);
        article.add(Box.createVerticalStrut(10));
        article.add(body);
        article.add(Box.createVerticalStrut(12));
        article.add(more);
        article.add(Box.createVerticalGlue());

        placeholder.setName("reading.placeholder");
        placeholder.setContentType("text/html");
        placeholder.setEditable(false);
        placeholder.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        placeholder.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                linkClicked.accept(e.getURL() != null ? e.getURL().toString() : e.getDescription());
            }
        });
        add(article, "article");
        add(placeholder, "placeholder");
        applyColours();
        applyFonts();
    }

    private static JTextArea text() {
        // Never taller than its text, so a short article stays at the top instead of being spread out.
        JTextArea t = new JTextArea() {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        t.setEditable(false);
        t.setLineWrap(true);
        t.setWrapStyleWord(true);
        t.setOpaque(false);
        t.setBorder(null);
        return t;
    }

    void setOptions(int newTextSize, boolean showPictures) {
        textSize = newTextSize;
        pictures = showPictures;
        applyFonts();
        show(shown, "");
    }

    private void applyFonts() {
        Font base = new JLabel().getFont();
        float size = AmadeUI.scaled(textSize);
        title.setFont(base.deriveFont(Font.BOLD, size * 1.45f));
        body.setFont(base.deriveFont(Font.PLAIN, size));
        meta.setFont(base.deriveFont(Font.PLAIN, size * 0.9f));
        more.setFont(base.deriveFont(Font.ITALIC, size * 0.9f));
    }

    private void applyColours() {
        setBackground(AmadeUI.theme().fieldBackground());
        article.setBackground(AmadeUI.theme().fieldBackground());
        placeholder.setBackground(AmadeUI.theme().fieldBackground());
    }

    void show(Optional<ArticleRow> row, String placeholderHtml) {
        shown = row;
        if (row.isEmpty()) {
            placeholder.setText("<html><body style='font-family:sans-serif; font-size:" + Math.round(AmadeUI.scaled(textSize))
                    + "pt'>" + placeholderHtml + "</body></html>");
            cards.show(this, "placeholder");
            return;
        }
        ArticleRow r = row.get();
        Article a = r.article();
        title.setText(a.title().isEmpty() ? msg.get("article.untitled") : a.title());
        List<String> parts = new ArrayList<>();
        parts.add(r.feedTitle());
        parts.add(r.fullDate());
        if (!a.author().isEmpty()) parts.add(a.author());
        meta.setText(String.join("  ·  ", parts));
        meta.setIcon(Flags.icon(r.flag(), Math.round(AmadeUI.scaled(11))).orElse(null));
        body.setText(a.text().isEmpty() ? msg.get("article.no-text") : a.text());
        more.setText(a.link().isEmpty() ? "" : msg.get("article.more"));
        more.setVisible(!a.link().isEmpty());
        showPicture(a);
        cards.show(this, "article");
        revalidate();
        repaint();
        scrollRectToVisible(new Rectangle(0, 0, 1, 1));
    }

    private void showPicture(Article a) {
        picture.setIcon(null);
        picture.setVisible(false);
        if (!pictures || a.imageUrl().isEmpty()) return;
        int w = Math.round(PICTURE_WIDTH * AmadeUI.fontScale());
        int h = Math.round(PICTURE_HEIGHT * AmadeUI.fontScale());
        Optional<BufferedImage> image = images.get(a.imageUrl(), w, h, false, () -> {
            if (shown.isPresent() && shown.get().article().key().equals(a.key())) showPicture(a);
        });
        image.ifPresent(i -> {
            picture.setIcon(new ImageIcon(i));
            picture.setVisible(true);
            revalidate();
        });
    }

    // ---------------------------------------------------------------- Scrollable: wrap to the viewport's width

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        return Math.round(16 * AmadeUI.fontScale());
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return orientation == SwingConstants.VERTICAL ? visible.height - 32 : visible.width;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent() != null && getPreferredSize().height < getParent().getHeight();
    }

    // ---------------------------------------------------------------- for tests

    String titleText() {
        return title.getText();
    }

    String bodyText() {
        return body.getText();
    }

    String metaText() {
        return meta.getText();
    }

    String placeholderText() {
        return placeholder.getText();
    }

    boolean showsPicture() {
        return picture.isVisible() && picture.getIcon() != null;
    }

    boolean showsPlaceholder() {
        return shown.isEmpty();
    }
}
