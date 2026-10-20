/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.validation;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.dspace.app.util.SubmissionStepConfig;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.InProgressSubmission;
import org.dspace.content.Item;
import org.dspace.content.service.ItemService;
import org.dspace.core.Constants;
import org.dspace.services.ConfigurationService;
import org.dspace.submit.model.UploadConfiguration;
import org.dspace.submit.model.UploadConfigurationService;
import org.dspace.validation.model.ValidationError;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link RdappUploadValidator}.
 */
public class RdappUploadValidatorTest {

    private static final String EXTENSION_ERROR = "error.validation.upload.extension";

    private static final String EMPTY_FILE_ERROR = "error.validation.upload.emptyfile";

    private static final String PREFIX = "rdapp.upload.extensions.";

    private final ItemService itemService = mock(ItemService.class);

    private final ConfigurationService configurationService = mock(ConfigurationService.class);

    private final Item item = mock(Item.class);

    private final Bundle bundle = mock(Bundle.class);

    private final InProgressSubmission<?> submission = mock(InProgressSubmission.class);

    private final SubmissionStepConfig stepConfig = mock(SubmissionStepConfig.class);

    private RdappUploadValidator validator;

    @Before
    public void setUp() throws Exception {
        UploadConfiguration uploadConfiguration = mock(UploadConfiguration.class);
        when(uploadConfiguration.isRequired()).thenReturn(false);
        UploadConfigurationService uploadConfigurationService = mock(UploadConfigurationService.class);
        when(uploadConfigurationService.getMap()).thenReturn(Map.of("upload", uploadConfiguration));

        when(stepConfig.getId()).thenReturn("upload");
        when(submission.getItem()).thenReturn(item);
        when(item.getBundles(Constants.CONTENT_BUNDLE_NAME)).thenReturn(List.of(bundle));

        String[] defaults = { "pdf" };
        when(configurationService.getArrayProperty(PREFIX + "default")).thenReturn(defaults);
        // Without a key of its own, a document type falls back to the default passed as second argument.
        when(configurationService.getArrayProperty(any(String.class), any(String[].class)))
            .thenAnswer(invocation -> invocation.getArgument(1));
        when(configurationService.getArrayProperty(eq(PREFIX + "mapa-de-evidencias"), any(String[].class)))
            .thenReturn(new String[] { "pdf", "xlsx", "xls", "xlsm", "xlsb" });

        validator = new RdappUploadValidator();
        validator.setName("upload");
        validator.setItemService(itemService);
        validator.setUploadConfigurationService(uploadConfigurationService);
        validator.setConfigurationService(configurationService);
    }

    @Test
    public void acceptsPdfForDefaultType() {
        givenDocumentType("Relatório de avaliação");
        givenFiles(file("relatorio.pdf", 10));

        assertThat(validate(), empty());
    }

    @Test
    public void extensionIsComparedIgnoringCase() {
        givenDocumentType("Relatório de avaliação");
        givenFiles(file("RELATORIO.PDF", 10));

        assertThat(validate(), empty());
    }

    @Test
    public void rejectsExtensionNotAllowedForType() {
        givenDocumentType("Relatório de avaliação");
        givenFiles(file("dados.txt", 10));

        assertThat(messages(validate()), contains(EXTENSION_ERROR));
    }

    @Test
    public void acceptsSpreadsheetForEvidenceMap() {
        givenDocumentType("Mapa de evidências");
        givenFiles(file("mapa.xlsx", 10), file("resumo.pdf", 10));

        assertThat(validate(), empty());
    }

    @Test
    public void rejectsFileWithoutExtension() {
        givenDocumentType("Relatório de avaliação");
        givenFiles(file("relatorio", 10));

        assertThat(messages(validate()), contains(EXTENSION_ERROR));
    }

    @Test
    public void rejectsEmptyFile() {
        givenDocumentType("Relatório de avaliação");
        givenFiles(file("vazio.pdf", 0));

        assertThat(messages(validate()), contains(EMPTY_FILE_ERROR));
    }

    @Test
    public void reportsEachKindOfErrorOnlyOnce() {
        givenDocumentType("Relatório de avaliação");
        givenFiles(file("a.txt", 0), file("b.txt", 0));

        List<ValidationError> errors = validate();

        assertThat(errors, hasSize(2));
        assertThat(messages(errors), contains(EXTENSION_ERROR, EMPTY_FILE_ERROR));
        assertThat(errors.get(0).getPaths(), contains("/sections/upload"));
    }

    @Test
    public void skipsExtensionCheckWhenNoDefaultIsConfigured() {
        when(configurationService.getArrayProperty(PREFIX + "default")).thenReturn(new String[0]);
        givenDocumentType("Relatório de avaliação");
        givenFiles(file("dados.txt", 10));

        assertThat(validate(), empty());
    }

    private void givenDocumentType(String documentType) {
        when(itemService.getMetadataFirstValue(item, "local", "tipodocumento", null, Item.ANY))
            .thenReturn(documentType);
    }

    private void givenFiles(Bitstream... bitstreams) {
        when(bundle.getBitstreams()).thenReturn(List.of(bitstreams));
    }

    private Bitstream file(String name, long sizeBytes) {
        Bitstream bitstream = mock(Bitstream.class);
        when(bitstream.getName()).thenReturn(name);
        when(bitstream.getSizeBytes()).thenReturn(sizeBytes);
        return bitstream;
    }

    private List<ValidationError> validate() {
        return validator.validate(null, submission, stepConfig);
    }

    private List<String> messages(List<ValidationError> errors) {
        return errors.stream().map(ValidationError::getMessage).collect(Collectors.toList());
    }
}
