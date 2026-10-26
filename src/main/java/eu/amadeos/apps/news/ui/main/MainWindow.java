package eu.amadeos.apps.news.ui.main;

import eu.amadeos.apps.news.bootstrap.AmadeNewsApplication;
import eu.amadeos.apps.news.catalog.Catalog;
import eu.amadeos.apps.news.domain.Article;
import eu.amadeos.apps.news.domain.Settings;
import eu.amadeos.apps.news.domain.Source;
import eu.amadeos.apps.news.images.ImageCache;
import eu.amadeos.apps.news.ui.common.Flags;
import eu.amadeos.apps.news.ui.dialogs.AddFeedDialog;
import eu.amadeos.apps.news.ui.dialogs.CatalogDialog;
import eu.amadeos.apps.news.ui.dialogs.PreferencesDialog;
import eu.amadeos.apps.news.update.FeedFinder;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.swing.EdtExecutor;
import eu.amadeos.commons.swing.StandardGlyph;
import eu.amadeos.commons.swing.ToolButtons;
import eu.amadeos.commons.swing.ToolIcon;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * The window: toolbar; smart views, folders and feeds on the left; the articles in the middle; the selected article
 * on the right; a status line.
 */
public final class MainWindow extends JFrame implements MainView {
    static final int THUMB_WIDTH = 72;
    static final int THUMB_HEIGHT = 48;

    private final Messages msg;
    private final LocaleSettings locales;
    private final ImageCache images;
    private final DefaultListModel<SourceItem> sourceModel = new DefaultListModel<>();
    private final JList<SourceItem> sourceList = new JList<>(sourceModel);
    private final DefaultListModel<ArticleRow> articleModel = new DefaultListModel<>();
    private final JList<ArticleRow> articleList = new JList<>(articleModel) {
        // The rows wrap to the list's width instead of asking for a horizontal scroll bar.
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }
    };
    private final JTextField search = new JTextField(16);
    private final JLabel status = new JLabel(" ");
    private final Map<String, Action> actions = new HashMap<>();
    private MainPresenter presenter;
    private MainMenuBar menuBar;
    private ReadingPane reading;
    private JScrollPane readingScroll;
    private Timer ticker;
    private Settings settings = Settings.DEFAULT;
    private boolean updating;

    public MainWindow(Messages msg, LocaleSettings locales, ImageCache images) {
        super(msg.get("window.title"));
        EdtExecutor.requireEdt();
        this.msg = msg;
        this.locales = locales;
        this.images = images;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setIconImage(AppIcon.image());
    }

    public void bind(MainPresenter newPresenter) {
        EdtExecutor.requireEdt();
        presenter = newPresenter;
        menuBar = new MainMenuBar(presenter, msg, locales);
        setJMenuBar(menuBar);
        reading = new ReadingPane(msg, images, presenter::linkClicked);

        sourceList.setName("sources");
        sourceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sourceList.setCellRenderer(sourceRenderer());
        sourceList.addListSelectionListener(e -> {
            if (!updating && !e.getValueIsAdjusting() && sourceList.getSelectedValue() != null) {
                presenter.selectSource(sourceList.getSelectedValue().source());
            }
        });
        sourceList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                popup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                popup(e);
            }
        });
        JScrollPane sources = new JScrollPane(sourceList);
        sources.setPreferredSize(scaled(230, 400));

        articleList.setName("articles");
        articleList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        articleList.setCellRenderer(articleRenderer());
        articleList.addListSelectionListener(e -> {
            if (!updating && !e.getValueIsAdjusting() && articleList.getSelectedValue() != null) {
                presenter.selectArticle(articleList.getSelectedValue().key());
            }
        });
        articleList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && articleList.locationToIndex(e.getPoint()) >= 0) presenter.openInWeb();
            }
        });
        keys(articleList);
        JScrollPane articles = new JScrollPane(articleList, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        articles.setPreferredSize(scaled(360, 400));
        // The rows wrap their titles to the list's width, so their heights change with it.
        articles.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                articleList.setFixedCellHeight(10);
                articleList.setFixedCellHeight(-1);
            }
        });

        readingScroll = new JScrollPane(reading);
        readingScroll.getViewport().setBackground(AmadeUI.theme().fieldBackground());
        readingScroll.setPreferredSize(scaled(520, 400));
        readingScroll.getVerticalScrollBar().setUnitIncrement(16);
        JPanel right = new JPanel(new BorderLayout());
        right.add(readingScroll, BorderLayout.CENTER);
        right.add(articleButtons(), BorderLayout.SOUTH);

        JSplitPane inner = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, articles, right);
        inner.setBorder(null);
        inner.setResizeWeight(0.4);
        inner.setContinuousLayout(true);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sources, inner);
        split.setBorder(null);
        split.setResizeWeight(0.0);
        split.setContinuousLayout(true);
        JPanel centre = new JPanel(new BorderLayout());
        centre.setBorder(BorderFactory.createEmptyBorder(4, 6, 2, 6));
        centre.add(split, BorderLayout.CENTER);

        status.setName("status");
        status.setForeground(AmadeUI.theme().textInactive());
        status.setBorder(BorderFactory.createEmptyBorder(2, 8, 4, 8));

        add(toolBar(), BorderLayout.NORTH);
        add(centre, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                presenter.exit();
            }
        });
        ticker = new Timer(60_000, e -> presenter.tick());
        ticker.start();
        pack();
        Dimension wanted = scaled(1200, 720);
        setSize(Math.max(getWidth(), wanted.width), Math.max(getHeight(), wanted.height));
        setMinimumSize(new Dimension(760, 460));
        setLocationRelativeTo(null);
    }

    private static Dimension scaled(int w, int h) {
        return new Dimension(Math.round(w * AmadeUI.fontScale()), Math.round(h * AmadeUI.fontScale()));
    }

    // ---------------------------------------------------------------- toolbar and buttons

    private JToolBar toolBar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.add(ToolButtons.button(action("update", StandardGlyph.REFRESH, presenter::updateAll)));
        bar.add(ToolButtons.button(action("add", NewsGlyph.ADD_FEED, presenter::addFeed)));
        bar.add(ToolButtons.button(action("catalog", NewsGlyph.EUROPE, presenter::openCatalog)));
        bar.addSeparator();
        bar.add(ToolButtons.button(action("all-read", NewsGlyph.MARK_READ, presenter::markAllRead)));
        bar.addSeparator();
        bar.add(ToolButtons.button(action("star", NewsGlyph.STAR, presenter::toggleStar)));
        bar.add(ToolButtons.button(action("later", NewsGlyph.LATER, presenter::toggleLater)));
        bar.add(ToolButtons.button(action("open", NewsGlyph.WEB, presenter::openInWeb)));
        bar.add(Box.createHorizontalGlue());
        search.setName("search");
        search.setMaximumSize(scaled(220, 28));
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                presenter.search(search.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                presenter.search(search.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                presenter.search(search.getText());
            }
        });
        search.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clear");
        search.getActionMap().put("clear", run(() -> search.setText("")));
        search.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "list");
        search.getActionMap().put("list", run(() -> {
            articleList.requestFocusInWindow();
            if (articleList.getSelectedIndex() < 0 && articleModel.size() > 0) articleList.setSelectedIndex(0);
        }));
        JLabel label = new JLabel(msg.get("search.label") + " ");
        label.setLabelFor(search);
        bar.add(label);
        bar.add(search);
        bar.add(Box.createHorizontalStrut(6));
        return bar;
    }

    private JPanel articleButtons() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        for (String key : List.of("open", "star", "later", "copy")) {
            JButton b = new JButton();
            Mnemonics.apply(b, msg.get("button." + key));
            b.setName("button." + key);
            b.addActionListener(e -> {
                switch (key) {
                    case "open" -> presenter.openInWeb();
                    case "star" -> presenter.toggleStar();
                    case "later" -> presenter.toggleLater();
                    default -> presenter.copyLink();
                }
            });
            buttons.put(key, b);
            row.add(b);
        }
        return row;
    }

    private final Map<String, JButton> buttons = new HashMap<>();

    private Action action(String key, ToolIcon.Glyph glyph, Runnable run) {
        Action action = run(run);
        action.putValue(Action.ACTION_COMMAND_KEY, "tool." + key);
        action.putValue(Action.SHORT_DESCRIPTION, msg.get("tool." + key));
        action.putValue(Action.LARGE_ICON_KEY, new ToolIcon(glyph));
        actions.put(key, action);
        return action;
    }

    private static Action run(Runnable run) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                run.run();
            }
        };
    }

    /** Single keys in the article list, like in other feed readers. */
    private void keys(JList<?> list) {
        Object[][] keys = {
                {KeyEvent.VK_J, (Runnable) presenter::next}, {KeyEvent.VK_K, (Runnable) presenter::previous},
                {KeyEvent.VK_N, (Runnable) presenter::nextUnread}, {KeyEvent.VK_SPACE, (Runnable) presenter::nextUnread},
                {KeyEvent.VK_S, (Runnable) presenter::toggleStar}, {KeyEvent.VK_L, (Runnable) presenter::toggleLater},
                {KeyEvent.VK_M, (Runnable) presenter::toggleRead}, {KeyEvent.VK_O, (Runnable) presenter::openInWeb},
                {KeyEvent.VK_ENTER, (Runnable) presenter::openInWeb},
        };
        for (Object[] k : keys) {
            String name = "key." + k[0];
            list.getInputMap().put(KeyStroke.getKeyStroke((Integer) k[0], 0), name);
            list.getActionMap().put(name, run((Runnable) k[1]));
        }
    }

    // ---------------------------------------------------------------- context menu on the left

    private void popup(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        int index = sourceList.locationToIndex(e.getPoint());
        if (index < 0 || !sourceList.getCellBounds(index, index).contains(e.getPoint())) return;
        sourceList.setSelectedIndex(index);
        JPopupMenu menu = popupFor(sourceModel.get(index));
        if (menu != null) menu.show(sourceList, e.getX(), e.getY());
    }

    JPopupMenu popupFor(SourceItem item) {
        JPopupMenu menu = new JPopupMenu();
        switch (item.source()) {
            case Source.OneFeed feed -> {
                menu.add(popupItem("popup.update", presenter::updateSelected));
                menu.add(popupItem("popup.website", () -> presenter.openWebsite(feed.feedId())));
                menu.addSeparator();
                menu.add(popupItem("popup.rename", () -> presenter.renameFeed(feed.feedId())));
                menu.add(popupItem("popup.move", () -> presenter.moveFeed(feed.feedId())));
                menu.add(popupItem("popup.all-read", presenter::markAllRead));
                menu.addSeparator();
                menu.add(popupItem("popup.unsubscribe", () -> presenter.unsubscribe(feed.feedId())));
            }
            case Source.Folder folder -> {
                menu.add(popupItem("popup.update", presenter::updateSelected));
                menu.add(popupItem("popup.rename-folder", () -> presenter.renameFolder(folder.name())));
                menu.add(popupItem("popup.all-read", presenter::markAllRead));
            }
            default -> {
                menu.add(popupItem("popup.all-read", presenter::markAllRead));
            }
        }
        return menu;
    }

    private JMenuItem popupItem(String key, Runnable action) {
        JMenuItem item = Mnemonics.apply(new JMenuItem(), msg.get(key));
        item.setName(key);
        item.addActionListener(e -> action.run());
        return item;
    }

    // ---------------------------------------------------------------- renderers

    private ListCellRenderer<SourceItem> sourceRenderer() {
        return (list, item, index, selected, focus) -> {
            boolean bold = item.unread() > 0 || item.source() instanceof Source.Folder;
            String count = item.unread() > 0 || item.source() instanceof Source.Starred
                    || item.source() instanceof Source.Later ? Integer.toString(item.unread()) : "";
            String warn = item.error().isEmpty() ? "" : " ⚠";
            int width = Math.max(80, list.getWidth() - Math.round(56 * AmadeUI.fontScale()));
            String colour = selected ? "" : String.format(" color='#%06x'", AmadeUI.theme().textInactive().getRGB() & 0xFFFFFF);
            String name = (item.source() instanceof Source.Folder ? "▾ " : "") + html(item.label()) + warn;
            JLabel l = new JLabel("<html><table cellpadding=0 cellspacing=0 width=" + width + "><tr><td>"
                    + (bold ? "<b>" + name + "</b>" : name) + "</td><td align=right><font" + colour + ">" + count
                    + "</font></td></tr></table></html>");
            l.setName("source." + index);
            l.setIcon(sourceIcon(item));
            l.setIconTextGap(6);
            l.setOpaque(true);
            l.setToolTipText(item.error().isEmpty() ? null : item.error());
            int left = item.indent() ? 18 : 6;
            l.setBorder(BorderFactory.createEmptyBorder(item.source() instanceof Source.Folder ? 8 : 3, left, 3, 6));
            l.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            l.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            return l;
        };
    }

    private Icon sourceIcon(SourceItem item) {
        int size = Math.round(16 * AmadeUI.fontScale());
        return switch (item.source()) {
            case Source.Starred s -> new ToolIcon(NewsGlyph.STAR, size);
            case Source.Later l -> new ToolIcon(NewsGlyph.LATER, size);
            case Source.Unread u -> new ToolIcon(NewsGlyph.MARK_READ, size);
            case Source.All a -> new ToolIcon(StandardGlyph.NEW, size);
            case Source.Folder f -> null;
            case Source.OneFeed f -> Flags.icon(item.flag(), Math.round(12 * AmadeUI.fontScale()))
                    .orElseGet(() -> new ToolIcon(NewsGlyph.FEED, size));
        };
    }

    private static final int ROW_PADDING = 5;

    private ListCellRenderer<ArticleRow> articleRenderer() {
        int tw = Math.round(THUMB_WIDTH * AmadeUI.fontScale());
        int th = Math.round(THUMB_HEIGHT * AmadeUI.fontScale());
        return (list, row, index, selected, focus) -> {
            Article a = row.article();
            String colour = selected ? "" : String.format(" color='#%06x'", AmadeUI.theme().textInactive().getRGB() & 0xFFFFFF);
            String mark = (a.read() ? "" : "● ") + (a.starred() ? "★ " : "") + (a.later() ? "◷ " : "");
            String title = html(a.title().isEmpty() ? msg.get("article.untitled") : a.title());
            String meta = html(row.feedTitle() + "  ·  " + row.when());
            JLabel l = new JLabel("<html>" + (a.read() ? mark + title : "<b>" + mark + title + "</b>")
                    + "<br><font" + colour + ">" + meta + "</font></html>");
            l.setName("article." + index);
            l.setOpaque(true);
            l.setVerticalAlignment(JLabel.TOP);
            l.setIconTextGap(8);
            int textWidth = list.getWidth() - 2 * ROW_PADDING;
            if (settings.showPictures()) {
                Optional<BufferedImage> thumb = a.imageUrl().isEmpty() ? Optional.empty()
                        : images.get(a.imageUrl(), tw, th, true, list::repaint);
                l.setIcon(thumb.<Icon>map(ImageIcon::new).orElseGet(() -> new Blank(tw, th, !a.imageUrl().isEmpty())));
                textWidth -= tw + l.getIconTextGap();
            }
            // The text wraps at the width the list really has, so it never runs under the scroll bar. Measured a
            // little narrower than it is painted, so a line that only just fits never needs more height than given.
            View view = (View) l.getClientProperty(BasicHTML.propertyKey);
            view.setSize(Math.max(80, textWidth - 6), 0);
            int textHeight = (int) Math.ceil(view.getPreferredSpan(View.Y_AXIS)) + 6;
            int height = Math.max(textHeight, settings.showPictures() ? th : 0) + 2 * ROW_PADDING + 1;
            l.setPreferredSize(new Dimension(Math.max(1, list.getWidth()), height));
            l.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, AmadeUI.theme().shadow()),
                    BorderFactory.createEmptyBorder(ROW_PADDING, ROW_PADDING, ROW_PADDING, ROW_PADDING)));
            l.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            l.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            return l;
        };
    }

    /** The room of a thumbnail: an empty sunken frame while the picture loads, nothing for articles without one. */
    private record Blank(int width, int height, boolean frame) implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            if (!frame) return;
            g.setColor(AmadeUI.theme().sunkenBg());
            g.fillRect(x, y, width, height);
            AmadeUI.paintBevel(g, x, y, width, height, false, 1);
        }

        @Override
        public int getIconWidth() {
            return width;
        }

        @Override
        public int getIconHeight() {
            return height;
        }
    }

    private static String html(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ---------------------------------------------------------------- MainView

    @Override
    public void showSettings(Settings newSettings) {
        settings = newSettings;
        reading.setOptions(newSettings.textSize(), newSettings.showPictures());
        articleList.setFixedCellHeight(10);
        articleList.setFixedCellHeight(-1);
        articleList.repaint();
    }

    @Override
    public void showSources(List<SourceItem> sources, int selected) {
        updating = true;
        try {
            sourceModel.clear();
            sourceModel.addAll(sources);
            sourceList.setSelectedIndex(selected);
        } finally {
            updating = false;
        }
    }

    @Override
    public void showArticles(List<ArticleRow> rows, Optional<String> selectedKey, String text) {
        updating = true;
        try {
            if (!rows.equals(java.util.Collections.list(articleModel.elements()))) {
                articleModel.clear();
                articleModel.addAll(rows);
            }
            int index = -1;
            for (int i = 0; i < rows.size() && selectedKey.isPresent(); i++) {
                if (rows.get(i).key().equals(selectedKey.get())) index = i;
            }
            if (index >= 0) {
                articleList.setSelectedIndex(index);
                articleList.ensureIndexIsVisible(index);
            } else {
                articleList.clearSelection();
            }
            if (!updatingFeeds) status.setText(text);
            listStatus = text;
        } finally {
            updating = false;
        }
        enableArticle(selectedKey.isPresent());
    }

    private void enableArticle(boolean selected) {
        menuBar.enableArticle(selected);
        for (String key : List.of("star", "later", "open")) actions.get(key).setEnabled(selected);
        for (JButton b : buttons.values()) b.setEnabled(selected);
    }

    @Override
    public void showArticle(Optional<ArticleRow> row, String placeholderHtml) {
        reading.show(row, placeholderHtml);
        enableArticle(row.isPresent());
        if (row.isPresent()) {
            Mnemonics.apply(buttons.get("star"), msg.get(row.get().article().starred() ? "button.unstar" : "button.star"));
            Mnemonics.apply(buttons.get("later"), msg.get(row.get().article().later() ? "button.not-later" : "button.later"));
            buttons.get("open").setEnabled(!row.get().article().link().isEmpty());
            buttons.get("copy").setEnabled(!row.get().article().link().isEmpty());
        }
        SwingUtilities.invokeLater(() -> readingScroll.getVerticalScrollBar().setValue(0));
    }

    @Override
    public void showUnreadTotal(int unread) {
        setTitle(unread > 0 ? msg.format("window.title.unread", unread) : msg.get("window.title"));
    }

    private boolean updatingFeeds;
    private String listStatus = " ";

    @Override
    public void showUpdating(boolean running, String text) {
        updatingFeeds = running;
        actions.get("update").setEnabled(!running);
        menuBar.item("menu.news.update").setEnabled(!running);
        menuBar.item("menu.news.update-selected").setEnabled(!running);
        status.setText(running ? text : listStatus + "   ·   " + text);
    }

    @Override
    public Optional<AddFeedChoice> addFeed(String address, List<String> folders,
                                           Function<String, CompletableFuture<List<FeedFinder.Candidate>>> finder) {
        return AddFeedDialog.show(this, msg, address, folders, finder);
    }

    @Override
    public Optional<Set<String>> chooseFromCatalog(Catalog catalog, Set<String> subscribed, List<String> languages) {
        return CatalogDialog.show(this, msg, catalog, subscribed, languages);
    }

    @Override
    public Optional<Settings> editPreferences(Settings current) {
        return PreferencesDialog.show(this, msg, current);
    }

    @Override
    public Optional<String> askText(String title, String label, String initial) {
        Object answer = JOptionPane.showInputDialog(this, label, title, JOptionPane.PLAIN_MESSAGE, null, null, initial);
        return Optional.ofNullable(answer).map(Object::toString).map(String::strip);
    }

    @Override
    public Optional<String> askFolder(String feedTitle, List<String> folders, String current) {
        String none = msg.get("dialog.move.none");
        JComboBox<String> box = new JComboBox<>();
        box.addItem(none);
        folders.forEach(box::addItem);
        box.setEditable(true);
        box.setSelectedItem(current.isEmpty() ? none : current);
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(new JLabel(msg.format("dialog.move.label", feedTitle)), BorderLayout.NORTH);
        panel.add(box, BorderLayout.CENTER);
        int answer = JOptionPane.showConfirmDialog(this, panel, msg.get("dialog.move.title"), JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) return Optional.empty();
        String chosen = String.valueOf(box.getSelectedItem()).strip();
        return Optional.of(chosen.equals(none) ? "" : chosen);
    }

    @Override
    public boolean confirm(String title, String message) {
        return JOptionPane.showConfirmDialog(this, plain(message), title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;
    }

    @Override
    public Optional<Path> chooseImportFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(msg.get("dialog.import.title"));
        chooser.setFileFilter(new FileNameExtensionFilter(msg.get("filter.opml"), "opml", "xml"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return Optional.empty();
        return Optional.of(chooser.getSelectedFile().toPath());
    }

    @Override
    public Optional<Path> chooseExportFile(String suggestedName) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(msg.get("dialog.export.title"));
        chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), suggestedName));
        chooser.setFileFilter(new FileNameExtensionFilter(msg.get("filter.opml"), "opml"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return Optional.empty();
        File file = chooser.getSelectedFile();
        if (!file.getName().contains(".")) file = new File(file.getParentFile(), file.getName() + ".opml");
        if (file.exists() && !confirm(msg.get("dialog.export.title"), msg.format("dialog.export.replace", file.getName()))) {
            return Optional.empty();
        }
        return Optional.of(file.toPath());
    }

    @Override
    public void copyText(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    @Override
    public void focusSearch() {
        search.requestFocusInWindow();
        search.selectAll();
    }

    @Override
    public void showError(String message) {
        JOptionPane.showMessageDialog(this, plain(message), msg.get("dialog.error.title"), JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void showInfo(String message) {
        JOptionPane.showMessageDialog(this, plain(message), msg.get("window.title"), JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void showAbout() {
        JOptionPane.showMessageDialog(this,
                "<html><center><h2>amade-news " + AmadeNewsApplication.version() + "</h2>"
                        + "<p>" + msg.get("dialog.about.text") + "</p><br>"
                        + "<small>" + msg.get("dialog.about.flags") + "</small><br><br>"
                        + "<small>&copy; 2026 datazuul.com &bull; EUPL-1.2</small></center></html>",
                msg.get("dialog.about.title"), JOptionPane.INFORMATION_MESSAGE, AppIcon.icon(48));
    }

    @Override
    public void close() {
        if (ticker != null) ticker.stop();
        dispose();
    }

    private static JComponent plain(String message) {
        JTextArea text = new JTextArea(message);
        text.setEditable(false);
        text.setOpaque(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setColumns(40);
        return text;
    }

    // ---------------------------------------------------------------- for tests

    MainMenuBar menu() {
        return menuBar;
    }

    ReadingPane reading() {
        return reading;
    }

    JList<ArticleRow> articleList() {
        return articleList;
    }

    JList<SourceItem> sourceList() {
        return sourceList;
    }

    JTextField searchField() {
        return search;
    }

    String statusText() {
        return status.getText();
    }

    Action toolAction(String key) {
        return actions.get(key);
    }

    JButton button(String key) {
        return buttons.get(key);
    }
}
