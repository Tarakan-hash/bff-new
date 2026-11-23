package sb.broker.bff.access;

import com.github.benmanes.caffeine.cache.Cache;

import org.springframework.stereotype.Service;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import sb.broker.bff.config.LocalCaches;
import sb.broker.bff.config.properties.CacheProperties;
import sb.broker.bff.temporal.LkWorkflows;
import sb.broker.bff.temporal.WorkflowGateway;
import sb.broker.bff.web.ErrorType;
import sb.broker.bff.web.Outcome;
import sb.broker.bff.web.OutcomeException;
import sb.broker.bff.web.ServiceException;
import sb.broker.bff.workflow.api.access.CheckLkAccessInput;
import sb.broker.bff.workflow.api.access.CheckLkAccessResult;
import sb.broker.security.AuthenticatedUser;

/**
 * Допуск к методам кабинета через {@code CheckLkAccessWorkflow}.
 *
 * <p>Выданный допуск кэшируется в поде на {@code bff.cache.access.ttl}: запросы одной сессии (страница
 * кабинета делает их пачкой) не запускают workflow повторно. Отказы не кэшируются — проставленное согласие
 * действует сразу. Отозванное согласие действует сразу в этом поде и не позже TTL в остальных.
 */
@Slf4j
@Service
public class LkAccessService {

    private final AuthenticatedUser authenticatedUser;
    private final WorkflowGateway workflows;
    private final Optional<Cache<AccessKey, LkUser>> granted;

    public LkAccessService(AuthenticatedUser authenticatedUser, WorkflowGateway workflows, CacheProperties caches, LocalCaches localCaches) {
        this.authenticatedUser = authenticatedUser;
        this.workflows = workflows;
        this.granted = localCaches.create("bff.access", caches.access());
    }

    /**
     * @return пользователь кабинета
     * @throws ServiceException {@link ErrorType#UNAUTHORIZED}, если email не принадлежит пользователю кабинета
     * @throws OutcomeException {@link Outcome#CONSENTS_REQUIRED}, если нет обязательного согласия
     */
    public LkUser require(ConsentPolicy policy) {
        String email = authenticatedUser.email();
        Optional<LkUser> cached = granted.map(cache -> cache.getIfPresent(new AccessKey(email, policy)));
        if(cached.isEmpty()) {
            log.info("Access check (cache miss): policy={}", policy);
        }
        return cached.orElseGet(() -> check(email, policy));
    }

    /**
     * Сбрасывает допуск текущего пользователя в этом поде (например, после отзыва согласия).
     * Ключ — email из JWT, как и при записи в кэш.
     */
    public void evictCurrentUser() {
        String email = authenticatedUser.email();
        granted.ifPresent(cache -> {
            for (ConsentPolicy policy : ConsentPolicy.values()) {
                cache.invalidate(new AccessKey(email, policy));
            }
            log.info("Access cache evicted");
        });
    }

    private LkUser check(String email, ConsentPolicy policy) {
        CheckLkAccessInput input = new CheckLkAccessInput(email, policy == ConsentPolicy.ENFORCE);
        CheckLkAccessResult result = workflows.execute(LkWorkflows.CHECK_LK_ACCESS, input);
        return switch (result.outcome()) {
            case GRANTED -> remember(email, policy, new LkUser(result.clientId(), result.email()));
            case UNAUTHORIZED -> throw new ServiceException(ErrorType.UNAUTHORIZED);
            case CONSENTS_REQUIRED -> throw new OutcomeException(Outcome.CONSENTS_REQUIRED);
        };
    }

    private LkUser remember(String email, ConsentPolicy policy, LkUser user) {
        granted.ifPresent(cache -> {
            cache.put(new AccessKey(email, policy), user);
            // допуск с проверкой согласий покрывает допуск без неё
            if (policy == ConsentPolicy.ENFORCE) {
                cache.put(new AccessKey(email, ConsentPolicy.IGNORE), user);
            }
        });
        return user;
    }

    private record AccessKey(String email, ConsentPolicy policy) {
    }
}
