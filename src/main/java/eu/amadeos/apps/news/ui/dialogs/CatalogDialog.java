package eu.amadeos.apps.news.ui.dialogs;

import eu.amadeos.apps.news.catalog.Catalog;
import eu.amadeos.apps.news.ui.common.Flags;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Europe's News: the countries with their flags on the left, the feeds of the chosen country on the right, each
 * with a box to subscribe. Feeds in the reader's languages come first.
 */
public final class CatalogDialog extends JDialog {
    private final Messages msg;
    private final List<Catalog.Country> countries;
    private final List<String> languages;
    private final Set<String> chosen;
    private final DefaultListModel<Catalog.Country> countryModel = new DefaultListModel<>();
    private final JList<Catalog.Country> countryList = new JList<>(countryModel) {
        // Long names are cut at the list's edge instead of asking for a horizontal scroll bar.
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }
    };
    private final JTextField filter = new JTextField(14);
    private final JLabel heading = new JLabel();
    private final JPanel feeds = new JPanel();
    private final JLabel summary = new JLabel();
    private final List<JCheckBox> boxes = new ArrayList<>();
    private Optional<Set<String>> result = Optional.empty();

    /** Builds the dialog without showing it; {@link #show} is the usual way, the screenshots build it directly. */
    public CatalogDialog(Window owner, Messages msg, Catalog catalog, Set<String> subscribed, List<String> languages) {
        super(owner, msg.get("catalog.title"), ModalityType.APPLICATION_MODAL);
        this.msg = msg;
        this.countries = catalog.withFeeds(msg.locale());
        this.languages = languages;
        this.chosen = new LinkedHashSet<>(subscribed);
        setName("catalog");

        JLabel intro = new JLabel(msg.get("catalog.intro"));

        filter.setName("catalog.filter");
        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filter();
            }
        });
        countryList.setName("catalog.countries");
        countryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        countryList.setCellRenderer((list, country, index, selected, focus) -> {
            Locale locale = msg.locale();
            String local = country.localName(locale);
            String own = country.name().equals(local) || country.name().contains(local) ? "" : "  (" + country.name() + ")";
            long ticked = country.feeds().stream().filter(f -> chosen.contains(f.url())).count();
            JLabel l = new JLabel("<html>" + (ticked > 0 ? "<b>" + html(local) + "</b>" : html(local))
                    + "<font color='#" + String.format("%06x", AmadeUI.theme().textInactive().getRGB() & 0xFFFFFF) + "'>"
                    + html(own) + "</font></html>");
            l.setIcon(Flags.icon(country.code(), Math.round(14 * AmadeUI.fontScale())).orElse(null));
            l.setIconTextGap(8);
            l.setOpaque(true);
            l.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            l.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            l.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            return l;
        });
        countryList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showCountry(countryList.getSelectedValue());
        });
        JLabel filterLabel = Mnemonics.apply(new JLabel(), msg.get("catalog.filter"));
        filterLabel.setLabelFor(filter);
        JPanel filterRow = new JPanel(new BorderLayout(6, 0));
        filterRow.add(filterLabel, BorderLayout.WEST);
        filterRow.add(filter, BorderLayout.CENTER);
        JPanel left = new JPanel(new BorderLayout(0, 4));
        left.add(filterRow, BorderLayout.NORTH);
        JScrollPane listScroll = new JScrollPane(countryList, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        listScroll.setPreferredSize(new Dimension(Math.round(300 * AmadeUI.fontScale()), Math.round(420 * AmadeUI.fontScale())));
        left.add(listScroll, BorderLayout.CENTER);

        heading.setName("catalog.heading");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, heading.getFont().getSize2D() * 1.3f));
        heading.setIconTextGap(8);
        feeds.setLayout(new BoxLayout(feeds, BoxLayout.Y_AXIS));
        feeds.setName("catalog.feeds");
        JPanel right = new JPanel(new BorderLayout(0, 8));
        right.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
        right.add(heading, BorderLayout.NORTH);
        JPanel feedsTop = new JPanel(new BorderLayout());
        feedsTop.add(feeds, BorderLayout.NORTH);
        right.add(feedsTop, BorderLayout.CENTER);
        right.setPreferredSize(new Dimension(Math.round(440 * AmadeUI.fontScale()), 10));

        summary.setName("catalog.summary");
        summary.setForeground(AmadeUI.theme().textInactive());
        JButton ok = Mnemonics.apply(new JButton(), msg.get("catalog.ok"));
        ok.setName("catalog.ok");
        ok.addActionListener(e -> {
            result = Optional.of(Set.copyOf(chosen));
            dispose();
        });
        JButton cancel = Mnemonics.apply(new JButton(), msg.get("catalog.cancel"));
        cancel.setName("catalog.cancel");
        cancel.addActionListener(e -> dispose());
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(summary, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(cancel);
        buttons.add(ok);
        bottom.add(buttons, BorderLayout.EAST);

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(intro, BorderLayout.NORTH);
        root.add(left, BorderLayout.WEST);
        root.add(right, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().setDefaultButton(ok);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        filter();
        selectFirst();
        updateSummary();
        pack();
        setLocationRelativeTo(owner);
    }

    /** Shows the dialog and waits: the addresses that should be subscribed, or empty if cancelled. */
    public static Optional<Set<String>> show(Window owner, Messages msg, Catalog catalog, Set<String> subscribed,
                                             List<String> languages) {
        CatalogDialog dialog = new CatalogDialog(owner, msg, catalog, subscribed, languages);
        dialog.setVisible(true);
        return dialog.result;
    }

    /** Starts at the reader's own country if it has feeds, otherwise at the first country. */
    private void selectFirst() {
        String country = msg.locale().getCountry().toLowerCase(Locale.ROOT);
        for (int i = 0; i < countryModel.size(); i++) {
            if (countryModel.get(i).code().equals(country)) {
                countryList.setSelectedIndex(i);
                countryList.ensureIndexIsVisible(i);
                return;
            }
        }
        if (!countryModel.isEmpty()) countryList.setSelectedIndex(0);
    }

    private void filter() {
        String q = filter.getText().strip().toLowerCase(msg.locale());
        Catalog.Country selected = countryList.getSelectedValue();
        countryModel.clear();
        for (Catalog.Country c : countries) {
            if (q.isEmpty() || c.localName(msg.locale()).toLowerCase(msg.locale()).contains(q)
                    || c.name().toLowerCase(msg.locale()).contains(q) || c.code().equals(q)) {
                countryModel.addElement(c);
            }
        }
        if (selected != null && countryModel.contains(selected)) {
            countryList.setSelectedValue(selected, true);
        } else if (!countryModel.isEmpty()) {
            countryList.setSelectedIndex(0);
        }
    }

    private void showCountry(Catalog.Country country) {
        feeds.removeAll();
        boxes.clear();
        if (country == null) {
            heading.setText(" ");
            heading.setIcon(null);
        } else {
            heading.setText(country.localName(msg.locale()));
            heading.setIcon(Flags.icon(country.code(), Math.round(20 * AmadeUI.fontScale())).orElse(null));
            for (Catalog.Entry e : Catalog.ordered(country, languages)) feeds.add(row(e));
        }
        feeds.revalidate();
        feeds.repaint();
    }

    private JComponent row(Catalog.Entry e) {
        JCheckBox box = new JCheckBox("", chosen.contains(e.url()));
        box.setName("catalog.feed." + boxes.size());
        box.setOpaque(false);
        box.addActionListener(a -> {
            if (box.isSelected()) chosen.add(e.url());
            else chosen.remove(e.url());
            updateSummary();
            countryList.repaint();
        });
        boxes.add(box);
        JLabel title = new JLabel(e.title());
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        String language = Locale.forLanguageTag(e.language()).getDisplayLanguage(msg.locale());
        JLabel details = new JLabel(language + "  ·  " + msg.get("catalog.kind." + e.kind().name().toLowerCase(Locale.ROOT)));
        details.setForeground(AmadeUI.theme().textInactive());
        JPanel text = new JPanel(new GridLayout(2, 1));
        text.setOpaque(false);
        text.add(title);
        text.add(details);
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBorder(BorderFactory.createCompoundBorder(AmadeUI.raised(), BorderFactory.createEmptyBorder(5, 6, 5, 8)));
        row.add(box, BorderLayout.WEST);
        row.add(text, BorderLayout.CENTER);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        // A click anywhere on the row ticks the box.
        row.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent m) {
                box.doClick();
            }
        });
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        wrap.add(row);
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, wrap.getPreferredSize().height));
        return wrap;
    }

    private void updateSummary() {
        long feedCount = 0;
        long countryCount = 0;
        for (Catalog.Country c : countries) {
            long n = c.feeds().stream().filter(f -> chosen.contains(f.url())).count();
            feedCount += n;
            if (n > 0) countryCount++;
        }
        summary.setText(msg.format("catalog.summary", feedCount, countryCount));
    }

    private static String html(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;");
    }

    // ---------------------------------------------------------------- for tests

    JList<Catalog.Country> countryList() {
        return countryList;
    }

    JTextField filterField() {
        return filter;
    }

    List<JCheckBox> boxes() {
        return boxes;
    }

    String headingText() {
        return heading.getText();
    }

    String summaryText() {
        return summary.getText();
    }

    Set<String> chosen() {
        return chosen;
    }
}
