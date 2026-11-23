package sb.broker.bff.catalog;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sb.broker.bff.access.ConsentPolicy;
import sb.broker.bff.access.LkAccessService;
import sb.broker.bff.catalog.dto.CatalogKey;
import sb.broker.bff.config.LocalCaches;
import sb.broker.bff.config.properties.CacheProperties;
import sb.broker.bff.temporal.LkWorkflows;
import sb.broker.bff.temporal.WorkflowGateway;
import sb.broker.bff.web.ErrorType;
import sb.broker.bff.web.ServiceException;
import sb.broker.bff.workflow.api.catalog.CatalogDictionary;
import sb.broker.bff.workflow.api.catalog.ReadProductCatalogInput;
import sb.broker.bff.workflow.api.catalog.ReadProductCatalogResult;
import sb.broker.security.AuthenticatedUser;

import java.util.Map;
import java.util.Optional;

/**
 * Справочники Product Catalog.
 *
 * <p>REGIONS доступен без авторизации.
 * Остальные справочники требуют допуска, но не обязательного согласия.
 *
 * <p>При промахе кэша проверку допуска для закрытых справочников
 * выполняет ReadProductCatalogWorkflow.
 * При попадании в кэш — LkAccessService.
 */
@Slf4j
@Service
public class ProductCatalogService {

    private final AuthenticatedUser authenticatedUser;
    private final LkAccessService access;
    private final WorkflowGateway workflows;
    private final Optional<Cache<CatalogKey, Object>> entries;

    public ProductCatalogService(
            AuthenticatedUser authenticatedUser,
            LkAccessService access,
            WorkflowGateway workflows,
            CacheProperties caches,
            LocalCaches localCaches) {
        this.authenticatedUser = authenticatedUser;
        this.access = access;
        this.workflows = workflows;
        this.entries = localCaches.create("bff.catalog", caches.catalog());
    }

    /**
     * @return ответ справочника без изменений
     * @throws CatalogEntryNotFoundException записи справочника нет
     */
    public Object read(CatalogDictionary dictionary, Map<String, String> params) {
        boolean publicDictionary = dictionary == CatalogDictionary.REGIONS;

        CatalogKey key = new CatalogKey(dictionary, params);
        Optional<Object> cached = entries.map(cache -> cache.getIfPresent(key));

        if (cached.isPresent()) {
            log.info("Catalog read: dictionary={}, cached=true", dictionary);

            if (!publicDictionary) {
                access.require(ConsentPolicy.IGNORE);
            }

            return cached.get();
        }

        log.info("Catalog read: dictionary={}, cached=false, workflow start", dictionary);

        String email = publicDictionary ? null : authenticatedUser.email();

        ReadProductCatalogResult result = workflows.execute(
                LkWorkflows.READ_PRODUCT_CATALOG,
                new ReadProductCatalogInput(email, dictionary, params)
        );

        log.info("Catalog workflow result: dictionary={}, outcome={}",
                dictionary, result.outcome());

        return switch (result.outcome()) {
            case FOUND -> {
                entries.ifPresent(cache -> cache.put(key, result.data()));
                yield result.data();
            }
            case NOT_FOUND -> throw new CatalogEntryNotFoundException(dictionary);
            case INVALID_REQUEST -> throw new ServiceException(ErrorType.INVALID_REQUEST);
            case UNAUTHORIZED -> throw new ServiceException(ErrorType.UNAUTHORIZED);
        };
    }
}