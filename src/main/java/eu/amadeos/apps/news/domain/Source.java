package eu.amadeos.apps.news.domain;

/** What the article list shows: a smart view, a folder or one feed. */
public sealed interface Source {
    /** True if the article of the feed belongs here. */
    boolean includes(Article article, Feed feed);

    record All() implements Source {
        @Override
        public boolean includes(Article article, Feed feed) {
            return true;
        }
    }

    record Unread() implements Source {
        @Override
        public boolean includes(Article article, Feed feed) {
            return !article.read();
        }
    }

    record Starred() implements Source {
        @Override
        public boolean includes(Article article, Feed feed) {
            return article.starred();
        }
    }

    record Later() implements Source {
        @Override
        public boolean includes(Article article, Feed feed) {
            return article.later();
        }
    }

    record Folder(String name) implements Source {
        @Override
        public boolean includes(Article article, Feed feed) {
            return feed != null && feed.folder().equals(name);
        }
    }

    record OneFeed(String feedId) implements Source {
        @Override
        public boolean includes(Article article, Feed feed) {
            return article.feedId().equals(feedId);
        }
    }
}
