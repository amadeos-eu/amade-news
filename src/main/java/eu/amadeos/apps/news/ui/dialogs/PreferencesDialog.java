package eu.amadeos.apps.news.ui.dialogs;

import eu.amadeos.apps.news.domain.Settings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Optional;

/** Preferences: how often to update, how long to keep articles, text size and pictures. */
public final class PreferencesDialog extends JDialog {
    private final Messages msg;
    private final JComboBox<String> update = new JComboBox<>();
    private final JCheckBox atStart = new JCheckBox();
    private final JComboBox<String> keep = new JComboBox<>();
    private final JComboBox<String> textSize = new JComboBox<>();
    private final JCheckBox pictures = new JCheckBox();
    private final JCheckBox markRead = new JCheckBox();
    private Optional<Settings> result = Optional.empty();

    PreferencesDialog(Window owner, Messages msg, Settings current) {
        super(owner, msg.get("prefs.title"), ModalityType.APPLICATION_MODAL);
        this.msg = msg;
        setName("preferences");
        for (int minutes : Settings.UPDATE_CHOICES) {
            update.addItem(minutes == 0 ? msg.get("prefs.update.manual")
                    : minutes < 60 ? msg.format("prefs.update.minutes", minutes) : msg.format("prefs.update.hours", minutes / 60));
        }
        select(update, Settings.UPDATE_CHOICES, current.updateMinutes());
        for (int days : Settings.KEEP_CHOICES) keep.addItem(msg.format("prefs.keep.days", days));
        select(keep, Settings.KEEP_CHOICES, current.keepDays());
        for (String size : List.of("small", "normal", "large", "larger")) textSize.addItem(msg.get("prefs.text." + size));
        select(textSize, Settings.TEXT_SIZES, current.textSize());
        Mnemonics.apply(atStart, msg.get("prefs.update.at-start"));
        atStart.setSelected(current.updateAtStart());
        Mnemonics.apply(pictures, msg.get("prefs.pictures"));
        pictures.setSelected(current.showPictures());
        Mnemonics.apply(markRead, msg.get("prefs.mark-read"));
        markRead.setSelected(current.markReadOnShow());
        update.setName("prefs.update");
        keep.setName("prefs.keep");
        textSize.setName("prefs.text");
        atStart.setName("prefs.at-start");
        pictures.setName("prefs.pictures");
        markRead.setName("prefs.mark-read");

        JPanel updating = form("prefs.box.updating",
                "prefs.update", update, null, atStart, "prefs.keep", keep);
        JPanel reading = form("prefs.box.reading",
                "prefs.text", textSize, null, pictures, null, markRead);
        JLabel network = new JLabel(msg.get("prefs.network"));
        JPanel net = new JPanel(new BorderLayout());
        net.setBorder(BorderFactory.createCompoundBorder(new TitledBorder(msg.get("prefs.box.network")),
                BorderFactory.createEmptyBorder(3, 6, 3, 6)));
        net.add(network);

        JButton ok = Mnemonics.apply(new JButton(), msg.get("prefs.ok"));
        ok.setName("prefs.ok");
        ok.addActionListener(e -> {
            result = Optional.of(settings(current));
            dispose();
        });
        JButton cancel = Mnemonics.apply(new JButton(), msg.get("prefs.cancel"));
        cancel.setName("prefs.cancel");
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(cancel);
        buttons.add(ok);

        JPanel boxes = new JPanel();
        boxes.setLayout(new BoxLayout(boxes, BoxLayout.Y_AXIS));
        for (JComponent c : List.of(updating, reading, net)) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            boxes.add(c);
            boxes.add(javax.swing.Box.createVerticalStrut(8));
        }
        JPanel root = new JPanel(new BorderLayout(0, 4));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(boxes, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().setDefaultButton(ok);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        pack();
        setLocationRelativeTo(owner);
    }

    public static Optional<Settings> show(Window owner, Messages msg, Settings current) {
        PreferencesDialog dialog = new PreferencesDialog(owner, msg, current);
        dialog.setVisible(true);
        return dialog.result;
    }

    Settings settings(Settings current) {
        return new Settings(Settings.UPDATE_CHOICES.get(update.getSelectedIndex()), atStart.isSelected(),
                Settings.KEEP_CHOICES.get(keep.getSelectedIndex()), Settings.TEXT_SIZES.get(textSize.getSelectedIndex()),
                pictures.isSelected(), markRead.isSelected());
    }

    /** Selects the choice nearest to the value, so a hand-edited file still shows something sensible. */
    private static void select(JComboBox<String> box, List<Integer> choices, int value) {
        int best = 0;
        for (int i = 0; i < choices.size(); i++) {
            if (Math.abs(choices.get(i) - value) < Math.abs(choices.get(best) - value) || choices.get(i) == value) best = i;
        }
        box.setSelectedIndex(best);
    }

    /** A titled box of rows: a label key and its field, or null and a field that spans both columns. */
    private JPanel form(String title, Object... rows) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new TitledBorder(msg.get(title)));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 6, 3, 6);
        c.anchor = GridBagConstraints.WEST;
        for (int i = 0, y = 0; i < rows.length; i += 2, y++) {
            c.gridy = y;
            c.gridx = 0;
            c.weightx = 0;
            Component field = (Component) rows[i + 1];
            if (rows[i] == null) {
                c.gridwidth = 2;
                p.add(field, c);
                c.gridwidth = 1;
                continue;
            }
            JLabel label = Mnemonics.apply(new JLabel(), msg.get((String) rows[i]));
            label.setLabelFor(field);
            p.add(label, c);
            c.gridx = 1;
            c.weightx = 1;
            p.add(field, c);
        }
        return p;
    }

    // ---------------------------------------------------------------- for tests

    JComboBox<String> updateBox() {
        return update;
    }

    JComboBox<String> textSizeBox() {
        return textSize;
    }

    JCheckBox picturesBox() {
        return pictures;
    }
}
