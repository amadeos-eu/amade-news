package eu.amadeos.apps.news.ui.main;

import eu.amadeos.commons.swing.ToolIcon;

import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/** News' own toolbar icons, next to the {@link eu.amadeos.commons.swing.StandardGlyph}s. */
public enum NewsGlyph implements ToolIcon.Glyph {
    ADD_FEED, FEED, EUROPE, MARK_READ, STAR, LATER, WEB;

    @Override
    public void paint(ToolIcon.Pen pen) {
        switch (this) {
            case ADD_FEED -> {
                feed(pen);
                pen.fill(new Rectangle2D.Double(10, 9, 6, 7), pen.paper());
                pen.bold(1.8f);
                pen.line(13, 10, 13, 15);
                pen.line(10.5, 12.5, 15.5, 12.5);
                pen.thin();
            }
            case FEED -> feed(pen);
            case EUROPE -> europe(pen);
            case MARK_READ -> {
                pen.page(2.5, 1.5, 10, 13);
                pen.bold(2f);
                Path2D tick = new Path2D.Double();
                tick.moveTo(4.5, 8.5);
                tick.lineTo(7, 11.5);
                tick.lineTo(14.5, 3);
                pen.draw(tick);
                pen.thin();
            }
            case STAR -> pen.fillAndDraw(star(8, 8.3, 7, 2.9), pen.light());
            case LATER -> {
                pen.fillAndDraw(new Ellipse2D.Double(1.5, 1.5, 13, 13), pen.paper());
                pen.bold(1.5f);
                pen.line(8, 8, 8, 4);
                pen.line(8, 8, 11, 10);
                pen.thin();
            }
            case WEB -> {
                pen.fillAndDraw(new Ellipse2D.Double(1.5, 1.5, 13, 13), pen.light());
                pen.draw(new Ellipse2D.Double(5, 1.5, 6, 13));
                pen.line(1.5, 8, 14.5, 8);
                pen.line(2.6, 4.8, 13.4, 4.8);
                pen.line(2.6, 11.2, 13.4, 11.2);
            }
        }
    }

    /** The feed sign: a dot and two arcs. */
    private static void feed(ToolIcon.Pen pen) {
        pen.fill(new Ellipse2D.Double(1.5, 11.5, 3.5, 3.5), pen.ink());
        pen.bold(2f);
        pen.draw(new Arc2D.Double(-4.5, 5, 14, 14, 0, 90, Arc2D.OPEN));
        pen.draw(new Arc2D.Double(-9.5, 0, 24, 24, 0, 90, Arc2D.OPEN));
        pen.thin();
    }

    /** A circle of twelve small stars. */
    private static void europe(ToolIcon.Pen pen) {
        pen.fillAndDraw(new Rectangle2D.Double(0.5, 2.5, 15, 11), pen.light());
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            pen.fill(star(8 + Math.cos(a) * 3.8, 8 + Math.sin(a) * 3.8, 1.3, 0.55), pen.paper());
        }
    }

    private static Path2D star(double cx, double cy, double outer, double inner) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double r = i % 2 == 0 ? outer : inner;
            double a = -Math.PI / 2 + Math.PI * i / 5;
            double x = cx + Math.cos(a) * r, y = cy + Math.sin(a) * r;
            if (i == 0) p.moveTo(x, y);
            else p.lineTo(x, y);
        }
        p.closePath();
        return p;
    }
}
