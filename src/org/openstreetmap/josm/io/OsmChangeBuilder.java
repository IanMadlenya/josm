// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.io;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;

import org.openstreetmap.josm.data.osm.Changeset;
import org.openstreetmap.josm.data.osm.IPrimitive;

/**
 * Creates an OsmChange document from JOSM edits.
 * See http://wiki.openstreetmap.org/index.php/OsmChange for a documentation of the OsmChange format.
 * @since 1071
 */
public class OsmChangeBuilder {
    /** Default OSM API version */
    public static final String DEFAULT_API_VERSION = "0.6";

    private String currentMode;
    private final PrintWriter writer;
    private final StringWriter swriter;
    private final OsmWriter osmwriter;
    private String apiVersion = DEFAULT_API_VERSION;
    private boolean prologWritten;

    /**
     * Constructs a new {@code OsmChangeBuilder}.
     * @param changeset changeset
     */
    public OsmChangeBuilder(Changeset changeset) {
        this(changeset, null /* default api version */);
    }

    /**
     * Constructs a new {@code OsmChangeBuilder}.
     * @param changeset changeset
     * @param apiVersion OSM API version
     */
    public OsmChangeBuilder(Changeset changeset, String apiVersion) {
        this.apiVersion = apiVersion == null ? DEFAULT_API_VERSION : apiVersion;
        swriter = new StringWriter();
        writer = new PrintWriter(swriter);
        osmwriter = OsmWriterFactory.createOsmWriter(writer, false, apiVersion);
        osmwriter.setChangeset(changeset);
        osmwriter.setIsOsmChange(true);
    }

    protected void write(IPrimitive p) {
        if (p.isDeleted()) {
            switchMode("delete");
            osmwriter.setWithBody(false);
            p.accept(osmwriter);
        } else {
            switchMode(p.isNew() ? "create" : "modify");
            osmwriter.setWithBody(true);
            p.accept(osmwriter);
        }
    }

    private void switchMode(String newMode) {
        if ((newMode != null && !newMode.equals(currentMode)) || (newMode == null && currentMode != null)) {
            if (currentMode != null) {
                writer.print("</");
                writer.print(currentMode);
                writer.println(">");
            }
            if (newMode != null) {
                writer.print("<");
                writer.print(newMode);
                writer.println(">");
            }
            currentMode = newMode;
        }
    }

    /**
     * Writes the prolog of the OsmChange document
     *
     * @throws IllegalStateException if the prologs has already been written
     */
    public void start() {
        if (prologWritten)
            throw new IllegalStateException(tr("Prolog of OsmChange document already written. Please write only once."));
        writer.print("<osmChange version=\"");
        writer.print(apiVersion);
        writer.println("\" generator=\"JOSM\">");
        prologWritten = true;
    }

    /**
     * Appends a collection of Primitives to the OsmChange document.
     *
     * @param primitives the collection of primitives. Ignored if null.
     * @throws IllegalStateException if the prologs has not been written yet
     * @see #start()
     * @see #append(IPrimitive)
     */
    public void append(Collection<? extends IPrimitive> primitives) {
        if (primitives == null)
            return;
        checkProlog();
        for (IPrimitive p : primitives) {
            write(p);
        }
    }

    private void checkProlog() {
        if (!prologWritten)
            throw new IllegalStateException(tr("Prolog of OsmChange document not written yet. Please write first."));
    }

    /**
     * Appends an Primitive to the OsmChange document.
     *
     * @param p the primitive. Ignored if null.
     * @throws IllegalStateException if the prologs has not been written yet
     * @see #start()
     * @see #append(Collection)
     */
    public void append(IPrimitive p) {
        if (p == null)
            return;
        checkProlog();
        write(p);
    }

    /**
     * Writes the epilog of the OsmChange document
     *
     * @throws IllegalStateException if the prologs has not been written yet
     */
    public void finish() {
        checkProlog();
        if (currentMode != null) {
            writer.print("</");
            writer.print(currentMode);
            writer.println(">");
        }
        writer.println("</osmChange>");
    }

    /**
     * Returns XML document.
     * @return XML document
     */
    public String getDocument() {
        return swriter.toString();
    }
}
