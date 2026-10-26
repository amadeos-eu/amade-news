package eu.amadeos.apps.news.ui.dialogs;

import eu.amadeos.apps.news.ui.main.MainView;
import eu.amadeos.apps.news.update.FeedFinder;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

/**
 * Add Feed: a feed address or just a website; the feeds behind it are looked up in the background and listed with
 * their names, so the user picks one, names it and chooses a folder.
 */
public final class AddFeedDialog extends JDialog {
    private final Messages msg;
    private final Function<String, CompletableFuture<List<FeedFinder.Candidate>>> finder;
    private final JTextField address = new JTextField(32);
    private final JButton find = new JButton();
    private final JLabel status = new JLabel(" ");
    private final JPanel results = new JPanel();
    private final JTextField name = new JTextField(26);
    private final JComboBox<String> folder = new JComboBox<>();
    private final JButton subscribe = new JButton();
    private final List<FeedFinder.Candidate> candidates = new ArrayList<>();
    private final List<JRadioButton> radios = new ArrayList<>();
    private Optional<MainView.AddFeedChoice> choice = Optional.empty();
    private int generation;

    AddFeedDialog(Window owner, Messages msg, String initial, List<String> folders,
                  Function<String, CompletableFuture<List<FeedFinder.Candidate>>> finder) {
        super(owner, msg.get("add.title"), ModalityType.APPLICATION_MODAL);
        this.msg = msg;
        this.finder = finder;
        setName("add-feed");

        JPanel top = new JPanel(new BorderLayout(6, 0));
        JLabel label = Mnemonics.apply(new JLabel(), msg.get("add.address"));
        label.setLabelFor(address);
        address.setName("add.address");
        address.setText(initial == null ? "" : initial);
        Mnemonics.apply(find, msg.get("add.find"));
        find.setName("add.find");
        find.addActionListener(e -> search());
        address.addActionListener(e -> search());
        top.add(label, BorderLayout.WEST);
        top.add(address, BorderLayout.CENTER);
        top.add(find, BorderLayout.EAST);

        status.setName("add.status");
        status.setForeground(AmadeUI.theme().textInactive());
        results.setLayout(new BoxLayout(results, BoxLayout.Y_AXIS));
        results.setName("add.results");

        name.setName("add.name");
        folder.setName("add.folder");
        folder.setEditable(true);
        folder.addItem(msg.get("dialog.move.none"));
        folders.forEach(folder::addItem);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new TitledBorder(msg.get("add.subscribe-box")));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 6, 3, 6);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.gridy = 0;
        form.add(labelFor("add.name", name), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(name, c);
        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        form.add(labelFor("add.folder", folder), c);
        c.gridx = 1;
        form.add(folder, c);

        JPanel middle = new JPanel(new BorderLayout(0, 6));
        middle.add(status, BorderLayout.NORTH);
        middle.add(results, BorderLayout.CENTER);
        middle.add(form, BorderLayout.SOUTH);

        Mnemonics.apply(subscribe, msg.get("add.subscribe"));
        subscribe.setName("add.subscribe");
        subscribe.setEnabled(false);
        subscribe.addActionListener(e -> accept());
        JButton cancel = Mnemonics.apply(new JButton(), msg.get("add.cancel"));
        cancel.setName("add.cancel");
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(cancel);
        buttons.add(subscribe);

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(top, BorderLayout.NORTH);
        root.add(middle, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().setDefaultButton(find);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        pack();
        setSize(Math.max(getWidth(), Math.round(560 * AmadeUI.fontScale())), getHeight());
        setLocationRelativeTo(owner);
        if (!address.getText().isBlank()) search();
    }

    private JLabel labelFor(String key, Component field) {
        JLabel l = Mnemonics.apply(new JLabel(), msg.get(key));
        l.setLabelFor(field);
        return l;
    }

    /** Shows the dialog and waits; empty if cancelled. */
    public static Optional<MainView.AddFeedChoice> show(Window owner, Messages msg, String initial, List<String> folders,
                                                        Function<String, CompletableFuture<List<FeedFinder.Candidate>>> finder) {
        AddFeedDialog dialog = new AddFeedDialog(owner, msg, initial, folders, finder);
        dialog.setVisible(true);
        return dialog.choice;
    }

    void search() {
        String input = address.getText().strip();
        if (input.isEmpty()) return;
        int mine = ++generation;
        candidates.clear();
        radios.clear();
        results.removeAll();
        subscribe.setEnabled(false);
        find.setEnabled(false);
        status.setText(msg.format("add.searching", input));
        finder.apply(input).whenComplete((found, error) -> {
            if (mine != generation) return;
            find.setEnabled(true);
            if (error != null) {
                Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                status.setText(cause.getMessage());
            } else {
                show(found);
            }
            pack();
        });
    }

    private void show(List<FeedFinder.Candidate> found) {
        status.setText(found.size() == 1 ? msg.get("add.found-one") : msg.format("add.found", found.size()));
        ButtonGroup group = new ButtonGroup();
        for (FeedFinder.Candidate c : found) {
            JRadioButton radio = new JRadioButton();
            radio.setName("add.result." + candidates.size());
            radio.setOpaque(false);
            group.add(radio);
            radio.addActionListener(e -> choose(c));
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setBorder(BorderFactory.createCompoundBorder(AmadeUI.raised(), BorderFactory.createEmptyBorder(5, 6, 5, 8)));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.add(radio, BorderLayout.WEST);
            JPanel text = new JPanel(new GridLayout(2, 1));
            text.setOpaque(false);
            JLabel title = new JLabel(c.title().isEmpty() ? c.url() : c.title());
            title.setFont(title.getFont().deriveFont(Font.BOLD));
            JLabel details = new JLabel(details(c));
            details.setForeground(AmadeUI.theme().textInactive());
            text.add(title);
            text.add(details);
            row.add(text, BorderLayout.CENTER);
            results.add(row);
            results.add(Box.createVerticalStrut(4));
            candidates.add(c);
            radios.add(radio);
        }
        if (!radios.isEmpty()) {
            radios.getFirst().setSelected(true);
            choose(candidates.getFirst());
        }
        results.revalidate();
    }

    private String details(FeedFinder.Candidate c) {
        List<String> parts = new ArrayList<>();
        parts.add(c.format());
        if (!c.language().isEmpty()) parts.add(Locale.forLanguageTag(c.language()).getDisplayLanguage(msg.locale()));
        parts.add(msg.format("add.articles", c.articles()));
        if (c.newest() != null) {
            parts.add(msg.format("add.newest", DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                    .withLocale(msg.locale()).format(c.newest().atZone(ZoneId.systemDefault()))));
        }
        return String.join("  ·  ", parts);
    }

    private void choose(FeedFinder.Candidate c) {
        name.setText(c.title().isEmpty() ? FeedFinder.normalize(c.url()) : c.title());
        subscribe.setEnabled(true);
        getRootPane().setDefaultButton(subscribe);
    }

    private void accept() {
        int index = -1;
        for (int i = 0; i < radios.size(); i++) {
            if (radios.get(i).isSelected()) index = i;
        }
        if (index < 0) return;
        String chosenFolder = String.valueOf(folder.getSelectedItem()).strip();
        if (chosenFolder.equals(msg.get("dialog.move.none"))) chosenFolder = "";
        String title = name.getText().strip();
        FeedFinder.Candidate c = candidates.get(index);
        choice = Optional.of(new MainView.AddFeedChoice(c, title.isEmpty() ? c.title() : title, chosenFolder));
        dispose();
    }

    // ---------------------------------------------------------------- for tests

    JTextField addressField() {
        return address;
    }

    JTextField nameField() {
        return name;
    }

    JComboBox<String> folderBox() {
        return folder;
    }

    String statusText() {
        return status.getText();
    }

    List<JRadioButton> radios() {
        return radios;
    }

    JButton subscribeButton() {
        return subscribe;
    }

    Optional<MainView.AddFeedChoice> choice() {
        return choice;
    }

    void pressSubscribe() {
        accept();
    }
}
