/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.validation;

import static org.dspace.validation.service.ValidationService.OPERATION_PATH_SECTIONS;
import static org.dspace.validation.util.ValidationUtils.addError;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.dspace.app.util.SubmissionStepConfig;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.InProgressSubmission;
import org.dspace.content.Item;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.dspace.validation.model.ValidationError;

/**
 * Upload step validation for the RDAPP. On top of the required file check of {@link UploadValidator}, it rejects
 * files whose extension is not accepted for the document type of the item (HU001, CA03) and empty files.
 * The accepted extensions come from the {@code rdapp.upload.extensions.*} properties.
 */
public class RdappUploadValidator extends UploadValidator {

    private static final String ERROR_VALIDATION_EXTENSION = "error.validation.upload.extension";

    private static final String ERROR_VALIDATION_EMPTY_FILE = "error.validation.upload.emptyfile";

    private static final String EXTENSIONS_PROPERTY_PREFIX = "rdapp.upload.extensions.";

    private ConfigurationService configurationService;

    @Override
    public List<ValidationError> validate(Context context, InProgressSubmission<?> obj, SubmissionStepConfig config) {
        List<ValidationError> errors = super.validate(context, obj, config);
        String path = "/" + OPERATION_PATH_SECTIONS + "/" + config.getId();

        Item item = obj.getItem();
        String[] allowedExtensions = getAllowedExtensions(item);
        boolean invalidExtension = false;
        boolean emptyFile = false;

        for (Bundle bundle : item.getBundles(Constants.CONTENT_BUNDLE_NAME)) {
            for (Bitstream bitstream : bundle.getBitstreams()) {
                emptyFile = emptyFile || bitstream.getSizeBytes() == 0;
                invalidExtension = invalidExtension
                    || !hasAllowedExtension(bitstream.getName(), allowedExtensions);
            }
        }

        if (invalidExtension) {
            addError(errors, ERROR_VALIDATION_EXTENSION, path);
        }
        if (emptyFile) {
            addError(errors, ERROR_VALIDATION_EMPTY_FILE, path);
        }
        return errors;
    }

    private String[] getAllowedExtensions(Item item) {
        String[] defaults = configurationService.getArrayProperty(EXTENSIONS_PROPERTY_PREFIX + "default");
        String documentType = getItemService().getMetadataFirstValue(item, "local", "tipodocumento", null, Item.ANY);
        if (documentType == null) {
            return defaults;
        }
        return configurationService.getArrayProperty(EXTENSIONS_PROPERTY_PREFIX + toSlug(documentType), defaults);
    }

    private boolean hasAllowedExtension(String fileName, String[] allowedExtensions) {
        if (allowedExtensions.length == 0) {
            return true;
        }
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        String extension = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return Arrays.stream(allowedExtensions)
                     .map(allowed -> allowed.trim().replaceFirst("^\\.", "").toLowerCase(Locale.ROOT))
                     .anyMatch(extension::equals);
    }

    private String toSlug(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                         .replaceAll("\\p{M}", "")
                         .toLowerCase(Locale.ROOT)
                         .replaceAll("[^a-z0-9]+", "-")
                         .replaceAll("^-|-$", "");
    }

    public ConfigurationService getConfigurationService() {
        return configurationService;
    }

    public void setConfigurationService(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

}
