package sb.broker.bff.catalog.dto;

import sb.broker.bff.workflow.api.catalog.CatalogDictionary;

import java.util.Map;

public record CatalogKey(
        CatalogDictionary dictionary,
        Map<String, String> params) {
}