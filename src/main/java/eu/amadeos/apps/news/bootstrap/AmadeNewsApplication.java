package eu.amadeos.apps.news.bootstrap;

import eu.amadeos.apps.news.ui.main.MainPresenter;
import eu.amadeos.apps.news.ui.main.MainWindow;
import eu.amadeos.commons.app.AmadeApplication;
import eu.amadeos.commons.app.AppInfo;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public final class AmadeNewsApplication {
    public static final AppInfo APP = new AppInfo("amade-news", "News", "news");

    private AmadeNewsApplication() {
    }

    /** The version from the jar's manifest; "dev" when run from the IDE. */
    public static String version() {
        String version = AmadeNewsApplication.class.getPackage().getImplementationVersion();
        return version == null ? "dev" : version;
    }

    // --8<-- [start:main]
    public static void main(String[] args) {
        if (args.length > 0 && (args[0].equals("--version") || args[0].equals("-v"))) {
            System.out.println("amade-news " + version());
            return;
        }
        List<String> open = arguments(args);
        AmadeApplication.launch(APP, injector -> {
            MainWindow window = injector.getInstance(MainWindow.class);
            MainPresenter presenter = injector.getInstance(MainPresenter.class);
            window.bind(presenter);
            window.setVisible(true);
            presenter.start(open);
        }, new NewsModule());
    }
    // --8<-- [end:main]

    /**
     * Feed addresses ({@code https:}, {@code feed:}) and OPML files from the command line; the desktop entry passes
     * them with %U. {@code file:} URIs become paths.
     */
    static List<String> arguments(String[] args) {
        List<String> list = new ArrayList<>();
        for (String arg : args) {
            if (arg.isBlank() || arg.startsWith("-")) continue;
            list.add(arg.startsWith("file://") ? URI.create(arg).getPath() : arg.strip());
        }
        return list;
    }
}
