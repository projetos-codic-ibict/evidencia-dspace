/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.disseminate;

import java.io.IOException;
import java.io.StringWriter;
import java.text.Normalizer;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import de.undercouch.citeproc.CSL;
import de.undercouch.citeproc.csl.CSLDate;
import de.undercouch.citeproc.csl.CSLDateBuilder;
import de.undercouch.citeproc.csl.CSLItemData;
import de.undercouch.citeproc.csl.CSLItemDataBuilder;
import de.undercouch.citeproc.csl.CSLName;
import de.undercouch.citeproc.csl.CSLNameBuilder;
import de.undercouch.citeproc.csl.CSLType;
import jakarta.inject.Named;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataSchema;
import org.dspace.content.MetadataValue;
import org.dspace.util.MultiFormatDateParser;
import org.jbibtex.BibTeXDatabase;
import org.jbibtex.BibTeXEntry;
import org.jbibtex.BibTeXFormatter;
import org.jbibtex.Key;
import org.jbibtex.StringValue;

/**
 * Generate bibliographic descriptions for DSpace objects in various formats.
 * *
 *
 * @author Jesiel Viana
 * @see https://citationstyles.org
 */
@Named
public class CSLBibliographyGenerator {
    /**
     * Map DSpace item types to CSL types.
     */
    private final Map<String, String> documentTypeMap;

    /**
     * Map DSpace metadata fields to CSL fields.
     */
    private final Map<String, String> metadataFieldMap;

    /**
     * Map DSpace metadata fields to BibTeX-only fields (no CSL equivalent).
     */
    private final Map<String, String> bibtexFieldMap;

    /**
     * Map CSL styles.
     */
    private final List<String> bibliographyStyles;

    /**
     * Repository name, used as the {@code howpublished} of every BibTeX entry.
     */
    private final String repositoryName;

    /**
     * CSL type of any work with an unmapped DSpace type.
     */
    private final String defaultType;

    /**
     * Logging context.
     */
    private static final Logger LOG = LogManager.getLogger();

    /**
     * Field types which hold human names (needing special formatting).
     */
    private static final List<String> cslNameTypes
            = Arrays.asList("author", "editor", "translator", "recipient",
            "interviewer", "publisher", "composer", "original-publisher",
            "original-author", "container-author", "collection-editor");

    /**
     * Field types which hold dates (needing special treatment).
     */
    private static final List<String> cslDateTypes
            = Arrays.asList("accessed", "container", "event-date", "issued",
            "original-date", "submitted");

    /**
     * Estilo tratado à parte — howpublished/organization/address não são
     * variáveis CSL (são exclusivas do BibTeX), então esse estilo não passa
     * pelo citeproc-java, é montado direto via JBibTeX a partir do metadado bruto.
     */
    private static final String BIBTEX_STYLE_KEY = "bibtex";

    /**
     * Logical names of the BibTeX-only fields, as configured in {@code bibtex-fields}.
     */
    private static final String BIBTEX_ORGANIZATION = "organization";
    private static final String BIBTEX_ADDRESS_CITY = "address-city";
    private static final String BIBTEX_ADDRESS_STATE = "address-state";


    /**
     * Configure an instance with document type and field mappings.
     *
     * @param documentTypes  map DSpace object types to CSL types.
     * @param metadataFields map DSpace metadata fields to CSL fields.
     * @param defaultType    default CSL type
     * @param bibtexFields   map DSpace metadata fields to BibTeX-only fields.
     * @param repositoryName repository name, used as the BibTeX {@code howpublished}.
     */
    public CSLBibliographyGenerator(Map<String, String> documentTypes,
                                    Map<String, String> metadataFields,
                                    List<String> bibliographyStyles,
                                    String defaultType,
                                    Map<String, String> bibtexFields,
                                    String repositoryName) {
        this.documentTypeMap = documentTypes;
        this.metadataFieldMap = metadataFields;
        this.bibliographyStyles = bibliographyStyles;
        this.defaultType = defaultType.toUpperCase();
        this.bibtexFieldMap = bibtexFields;
        this.repositoryName = repositoryName;
    }


    /**
     * Do not use.
     */
    private CSLBibliographyGenerator() {
        this.documentTypeMap = null;
        this.metadataFieldMap = null;
        this.bibliographyStyles = null;
        this.defaultType = null;
        this.bibtexFieldMap = null;
        this.repositoryName = null;
    }


    /**
     * Generates a citation/bibliography string for the given metadata using
     * the specified CSL style and output format.
     *
     * <p>This method converts the provided list of {@link MetadataValue} objects
     * into a {@link CSLItemData}, then produces an ad-hoc bibliography through
     * the CSL processor.</p>
     *
     * @param metadata     the list of metadata values describing the item to cite
     * @param outputFormat the desired output format (e.g. {@code OutputFormat.HTML}, {@code OutputFormat.TEXT})
     * @return the formatted bibliography string for the given metadata
     * @throws IOException if an error occurs while generating the bibliography
     */
    public List<CSLBibliography> getBibliographies(List<MetadataValue> metadata, OutputFormat outputFormat)
            throws IOException {
        CSLItemData cslItemData = buildItem(metadata);

        List<CSLBibliography> bibliographies = new ArrayList<>();

        for (String style : bibliographyStyles) {
            String bibliography = BIBTEX_STYLE_KEY.equals(style)
                    ? buildCustomBibtex(cslItemData, metadata)
                    : CSL.makeAdhocBibliography(style, outputFormat.value, cslItemData).makeString();
            bibliographies.add(new CSLBibliography(style, bibliography));
        }
        return bibliographies;
    }

    /**
     * Build a BibTeX entry through JBibTeX instead of the CSL processor.
     *
     * <p>Author, title, year and organization come from the {@link CSLItemData} already
     * built for every other style, so the metadata is parsed and normalized only once.
     * Only the fields with no CSL equivalent -- {@code howpublished}, {@code organization}
     * and {@code address} -- are read from the raw metadata, through the configurable
     * {@code bibtex-fields} map.</p>
     *
     * @param item     the item data shared with the CSL styles.
     * @param metadata the raw metadata, for the BibTeX-only fields.
     * @return the formatted BibTeX entry.
     */
    private String buildCustomBibtex(CSLItemData item, List<MetadataValue> metadata) throws IOException {
        Map<String, String> bibtexOnly = extractBibtexOnlyFields(metadata);

        String year = extractYear(item.getIssued());
        String authors = formatBibtexAuthors(item.getAuthor());

        // The institution that produced the work; falls back to the CSL publisher.
        String organization = bibtexOnly.getOrDefault(BIBTEX_ORGANIZATION, item.getPublisher());
        String address = formatAddress(bibtexOnly.get(BIBTEX_ADDRESS_CITY),
                bibtexOnly.get(BIBTEX_ADDRESS_STATE));

        BibTeXEntry entry = new BibTeXEntry(BibTeXEntry.TYPE_MISC, new Key(buildBibtexKey(item, year)));

        addBibtexField(entry, BibTeXEntry.KEY_AUTHOR, authors);
        addBibtexField(entry, BibTeXEntry.KEY_TITLE, item.getTitle());
        addBibtexField(entry, BibTeXEntry.KEY_HOWPUBLISHED, repositoryName);
        addBibtexField(entry, BibTeXEntry.KEY_ORGANIZATION, organization);
        addBibtexField(entry, BibTeXEntry.KEY_ADDRESS, address);
        addBibtexField(entry, BibTeXEntry.KEY_YEAR, year);

        BibTeXDatabase database = new BibTeXDatabase();
        database.addObject(entry);

        BibTeXFormatter formatter = new BibTeXFormatter();
        formatter.setIndent("  ");
        StringWriter writer = new StringWriter();
        formatter.format(database, writer);
        return writer.toString().trim();
    }

    /**
     * Collect the metadata values mapped in {@code bibtex-fields}, keeping the first
     * value of each field.
     */
    private Map<String, String> extractBibtexOnlyFields(List<MetadataValue> metadata) {
        Map<String, String> values = new HashMap<>();

        for (MetadataValue datum : metadata) {
            MetadataField field = datum.getMetadataField();
            MetadataSchema schema = field.getMetadataSchema();
            String dsFieldName = getMetadataFieldNameFrom(schema.getName(), field.getElement(), field.getQualifier());

            String bibtexFieldName = bibtexFieldMap.get(dsFieldName);
            if (null == bibtexFieldName) {
                continue;
            }
            LOG.debug("Map DSpace {} to BibTeX {}", dsFieldName, bibtexFieldName);
            values.putIfAbsent(bibtexFieldName, datum.getValue());
        }
        return values;
    }

    /**
     * Format CSL names the way BibTeX expects them: {@code Family, Given and Family, Given}.
     */
    private String formatBibtexAuthors(CSLName[] names) {
        if (null == names || names.length == 0) {
            return null;
        }
        List<String> formatted = new ArrayList<>();
        for (CSLName name : names) {
            if (null == name) {
                continue;
            }
            String family = name.getFamily();
            String given = name.getGiven();
            if (null == family || family.isBlank()) {
                continue;
            }
            formatted.add((null == given || given.isBlank()) ? family : family + ", " + given);
        }
        return formatted.isEmpty() ? null : String.join(" and ", formatted);
    }

    /**
     * The BibTeX {@code year} field holds a year, not a full date.
     */
    private String extractYear(CSLDate issued) {
        if (null == issued) {
            return null;
        }
        int[][] dateParts = issued.getDateParts();
        if (null != dateParts && dateParts.length > 0 && dateParts[0].length > 0) {
            return String.valueOf(dateParts[0][0]);
        }
        String raw = issued.getRaw();
        return (null != raw && raw.length() >= 4) ? raw.substring(0, 4) : raw;
    }

    private String formatAddress(String city, String state) {
        if (null != city && null != state) {
            return city + "/" + state;
        }
        return null != city ? city : state;
    }

    private void addBibtexField(BibTeXEntry entry, Key key, String value) {
        if (null != value && !value.isBlank()) {
            entry.addField(key, new StringValue(value, StringValue.Style.BRACED));
        }
    }

    /**
     * Build the citation key from the first author's family name plus the year.
     */
    private String buildBibtexKey(CSLItemData item, String year) {
        CSLName[] names = item.getAuthor();
        String family = (null != names && names.length > 0 && null != names[0])
                ? names[0].getFamily()
                : null;
        String slug = (null == family) ? "" : Normalizer.normalize(family, Normalizer.Form.NFD)
                .toLowerCase()
                .replaceAll("[^a-z0-9]", "");
        return slug + (null != year ? year : "");
    }


    /**
     * Discover which bibliographic styles are supported.
     *
     * @return paths to defined styles, ordered alphabetically by name.
     * @throws java.io.IOException passed through.
     */
    static public Set<String> getStyles()
            throws IOException {
        return new TreeSet<>(CSL.getSupportedStyles());
    }

    /**
     * Construct a CSLItemData from DSpace Item metadata.
     *
     * @param metadata list of metadata.
     * @return holder of data for a new entry in the bibliography.
     */
    private CSLItemData buildItem(List<MetadataValue> metadata) {
        CSLItemDataBuilder builder = new CSLItemDataBuilder();

        // Accumulators for multivalued fields.
        List<String> categories = new ArrayList<>();
        List<CSLName> authors = new ArrayList<>();
        List<CSLName> collectionEditors = new ArrayList<>();
        List<CSLName> composers = new ArrayList<>();
        List<CSLName> containerAuthors = new ArrayList<>();
        List<CSLName> directors = new ArrayList<>();
        List<CSLName> editors = new ArrayList<>();
        List<CSLName> editorialDirectors = new ArrayList<>();
        List<CSLName> interviewers = new ArrayList<>();
        List<CSLName> illustrators = new ArrayList<>();
        List<CSLName> originalAuthors = new ArrayList<>();
        List<CSLName> recipients = new ArrayList<>();
        List<CSLName> reviewedAuthors = new ArrayList<>();
        List<CSLName> translators = new ArrayList<>();


        CSLName nameValue = null;
        CSLDate dateValue = null;

        for (MetadataValue datum : metadata) {
            MetadataField field = datum.getMetadataField();
            MetadataSchema schema = field.getMetadataSchema();

            String schemaName = schema.getName();
            String element = field.getElement();
            String qualifier = field.getQualifier();

            String dsFieldName = getMetadataFieldNameFrom(schemaName, element, qualifier);

            // Map to CSL field
            String cslFieldName = metadataFieldMap.get(dsFieldName);
            LOG.debug("Map DSpace {} to CSL {}", dsFieldName, cslFieldName);

            // Skip this field if not mapped.
            if (null == cslFieldName) {
                continue;
            }

            String value = datum.getValue();

            // Figure out the CSL work type.
            if ("type".equals(cslFieldName)) {
                String key = value.replaceAll(" ", "_");
                if (documentTypeMap.containsKey(key)) {
                    value = documentTypeMap.get(key)
                            .replaceAll("[ -]", "_")
                            .toUpperCase();
                } else {
                    LOG.warn("No CSL work type for {} -- using default {}.",
                            key, defaultType);
                    value = defaultType;
                }
            } else if (cslNameTypes.contains(cslFieldName)) {
                // If this is a name then format
                nameValue = buildCSLName(value);
            } else if (cslDateTypes.contains(cslFieldName)) {
                // If this is a date then format
                dateValue = buildCSLDate(value);
            }

            // Store in builder; multivalued fields are accumulated instead.
            switch (cslFieldName) {
                // id?
                case "type":
                    builder.type(CSLType.valueOf(value));
                    break;
                case "categories":
                    categories.add(value);
                    break;
                case "author":
                    authors.add(nameValue);
                    break;
                case "collectionEditor":
                    collectionEditors.add(nameValue);
                    break;
                case "composer":
                    composers.add(nameValue);
                    break;
                case "containerAuthor":
                    containerAuthors.add(nameValue);
                    break;
                case "director":
                    directors.add(nameValue);
                    break;
                case "editor":
                    editors.add(nameValue);
                    break;
                case "interviewer":
                    interviewers.add(nameValue);
                    break;
                case "editorialDirector":
                    editorialDirectors.add(nameValue);
                    break;
                case "illustrator":
                    illustrators.add(nameValue);
                    break;
                case "originalAuthor":
                    originalAuthors.add(nameValue);
                    break;
                case "recipient":
                    recipients.add(nameValue);
                    break;
                case "reviewedAuthor":
                    reviewedAuthors.add(nameValue);
                    break;
                case "translator":
                    translators.add(nameValue);
                    break;
                case "accessed":
                    builder.accessed(dateValue);
                    break;
                case "container":
                    builder.container(dateValue);
                    break;
                case "eventDate":
                    builder.eventDate(dateValue);
                    break;
                case "issued":
                    builder.issued(dateValue);
                    break;
                case "originalDate":
                    builder.originalDate(dateValue);
                    break;
                case "submitted":
                    builder.submitted(dateValue);
                    break;
                case "annote":
                    builder.annote(value);
                    break;
                case "abstract":
                    builder.abstrct(value);
                    break;
                case "archive":
                    builder.archive(value);
                    break;
                case "archiveLocation":
                    builder.archiveLocation(value);
                    break;
                case "archivePlace":
                    builder.archivePlace(value);
                    break;
                case "authority":
                    builder.authority(value);
                    break;
                case "callNumber":
                    builder.callNumber(value);
                    break;
                case "chapterNumber":
                    builder.chapterNumber(value);
                    break;
                case "citationNumber":
                    builder.citationNumber(value);
                    break;
                case "citationLabel":
                    builder.citationLabel(value);
                    break;
                case "collectionNumber":
                    builder.collectionNumber(value);
                    break;
                case "collectionTitle":
                    builder.collectionTitle(value);
                    break;
                case "containerTitle":
                    builder.containerTitle(value);
                    break;
                case "containerTitleShort":
                    builder.containerTitleShort(value);
                    break;
                case "dimensions":
                    builder.dimensions(value);
                    break;
                case "DOI":
                    builder.DOI(value);
                    break;
                case "edition":
                    builder.edition(value);
                    break;
                case "event":
                    builder.event(value);
                    break;
                case "eventPlace":
                    builder.eventPlace(value);
                    break;
                case "firstReferenceNoteNumber":
                    builder.firstReferenceNoteNumber(value);
                    break;
                case "genre":
                    builder.genre(value);
                    break;
                case "ISBN":
                    builder.ISBN(value);
                    break;
                case "ISSN":
                    builder.ISSN(value);
                    break;
                case "issue":
                    builder.issue(value);
                    break;
                case "jurisdiction":
                    builder.jurisdiction(value);
                    break;
                case "keyword":
                    builder.keyword(value);
                    break;
                case "locator":
                    builder.locator(value);
                    break;
                case "medium":
                    builder.medium(value);
                    break;
                case "note":
                    builder.note(value);
                    break;
                case "number":
                    builder.number(value);
                    break;
                case "numberOfPages":
                    builder.numberOfPages(value);
                    break;
                case "numberOfVolumes":
                    builder.numberOfVolumes(value);
                    break;
                case "originalPublisher":
                    builder.originalPublisher(value);
                    break;
                case "originalPublisherPlace":
                    builder.originalPublisherPlace(value);
                    break;
                case "originalTitle":
                    builder.originalTitle(value);
                    break;
                case "page":
                    builder.page(value);
                    break;
                case "pageFirst":
                    // builder.pageFirst(value);
                    System.out.println("pageFirst");
                    break;
                case "PMCID":
                    builder.PMCID(value);
                    break;
                case "PMID":
                    builder.PMID(value);
                    break;
                case "publisher":
                    builder.publisher(value);
                    break;
                case "publisherPlace":
                    builder.publisherPlace(value);
                    break;
                case "references":
                    builder.references(value);
                    break;
                case "reviewedTitle":
                    builder.reviewedTitle(value);
                    break;
                case "scale":
                    builder.scale(value);
                    break;
                case "section":
                    builder.section(value);
                    break;
                case "source":
                    builder.source(value);
                    break;
                case "status":
                    builder.status(value);
                    break;
                case "title":
                    builder.title(value);
                    break;
                case "titleShort":
                    builder.titleShort(value);
                    break;
                case "URL":
                    builder.URL(value);
                    break;
                case "version":
                    builder.version(value);
                    break;
                case "volume":
                    builder.volume(value);
                    break;
                case "yearSuffix":
                    builder.yearSuffix(value);
                    break;
                default:
                    builder.note(value);
                    break;
            }
        }

        // Store accumulated multiple-valued fields.
        if (!categories.isEmpty()) {
            builder.categories(categories.toArray(String[]::new));
        }
        if (!authors.isEmpty()) {
            builder.author(authors.toArray(CSLName[]::new));
        }
        if (!collectionEditors.isEmpty()) {
            builder.collectionEditor(collectionEditors.toArray(CSLName[]::new));
        }
        if (!composers.isEmpty()) {
            builder.composer(composers.toArray(CSLName[]::new));
        }
        if (!containerAuthors.isEmpty()) {
            builder.containerAuthor(containerAuthors.toArray(CSLName[]::new));
        }
        if (!directors.isEmpty()) {
            builder.director(directors.toArray(CSLName[]::new));
        }
        if (!editors.isEmpty()) {
            builder.editor(editors.toArray(CSLName[]::new));
        }
        if (!editorialDirectors.isEmpty()) {
            builder.editorialDirector(editorialDirectors.toArray(CSLName[]::new));
        }
        if (!interviewers.isEmpty()) {
            builder.interviewer(interviewers.toArray(CSLName[]::new));
        }
        if (!illustrators.isEmpty()) {
            builder.illustrator(illustrators.toArray(CSLName[]::new));
        }
        if (!originalAuthors.isEmpty()) {
            builder.originalAuthor(originalAuthors.toArray(CSLName[]::new));
        }
        if (!recipients.isEmpty()) {
            builder.recipient(recipients.toArray(CSLName[]::new));
        }
        if (!reviewedAuthors.isEmpty()) {
            builder.reviewedAuthor(reviewedAuthors.toArray(CSLName[]::new));
        }
        if (!translators.isEmpty()) {
            builder.translator(translators.toArray(CSLName[]::new));
        }
        return builder.build();
    }

    private String getMetadataFieldNameFrom(String schema, String element, String qualifier) {
        StringBuilder sb = new StringBuilder(schema);
        sb.append('.').append(element);
        if (null != qualifier) {
            sb.append('.').append(qualifier);
        }
        return sb.toString();
    }


    private CSLName buildCSLName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        CSLNameBuilder builder = new CSLNameBuilder();
        String trimmedName = name.trim();

        if (trimmedName.contains(",")) {
            // Format: Family, Given
            String[] parts = trimmedName.split(",", 2);
            String family = parts[0].trim();
            String given = parts.length > 1 ? parts[1].trim() : null;

            builder.family(family);
            if (given != null && !given.isEmpty()) {
                builder.given(given);
            }
        } else {
            // Format: Given [Middle ...] Family
            String[] parts = trimmedName.split("\\s+");
            if (parts.length == 1) {
                // Single name: family only
                builder.family(parts[0]);
            } else {
                // Last word = family, the rest = given
                String family = parts[parts.length - 1];
                String given = String.join(" ", java.util.Arrays.copyOf(parts, parts.length - 1));

                builder.family(family);
                if (!given.isEmpty()) {
                    builder.given(given);
                }
            }
        }

        return builder.build();
    }



    private CSLDate buildCSLDate(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }

        // If it's just a year
        if (date.matches("\\d{4}")) {
            int year = Integer.parseInt(date);
            return new CSLDateBuilder()
                    .dateParts(year)
                    .build();
        }

        // Otherwise, try parsing a full date
        ZonedDateTime zonedDateTime = MultiFormatDateParser.parse(date);
        if (zonedDateTime == null) {
            return null;
        }

        return new CSLDateBuilder()
                .dateParts(
                        zonedDateTime.getYear(),
                        zonedDateTime.getMonthValue(),
                        zonedDateTime.getDayOfMonth()
                )
                .build();
    }

    /**
     * String constants for citation output formats accept in CSL class.
     */
    public static enum OutputFormat {
        HTML("html"),
        MARKDOWN("markdown"),
        TEXT("text"),
        ASCIIDOC("asciidoc"),
        FO("fo"),
        RTF("rtf");

        private final String value;

        OutputFormat(String format) {
            this.value = format;
        }

        public String getValue() {
            return value;
        }
    }

}