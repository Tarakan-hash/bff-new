package sb.broker.bff.profile;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import sb.broker.bff.profile.dto.ProfileInfoDTO;
import sb.broker.security.AuthenticatedUser;

@Tag(name = "Текущий пользователь")
@RestController
@RequestMapping("/api/v1/profile")
public class ProfileInfoController {

    private final AuthenticatedUser user;

    public ProfileInfoController(AuthenticatedUser user) {
        this.user = user;
    }

    @Operation(summary = "Данные пользователя из JWT",
            description = "Только данные проверенного токена актуальные на момент его выдачи.")
    @GetMapping("/info")
    public ResponseEntity<ProfileInfoDTO> info(@AuthenticationPrincipal Jwt jwt) {
        String id = user.subject();
        String email = user.email();
        String lastName = jwt.getClaims().get("family_name") instanceof String text && !text.isBlank() ? text : null;
        String firstName = jwt.getClaims().get("given_name") instanceof String text && !text.isBlank() ? text : null;
        String secondName = jwt.getClaims().get("father_name") instanceof String text && !text.isBlank() ? text : null;
        String phone = jwt.getClaims().get("phone") instanceof String text && !text.isBlank() ? text : null;
        String inn = jwt.getClaims().get("inn") instanceof String text && !text.isBlank() ? text : null;

        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ProfileInfoDTO(id, email, firstName, lastName, secondName, inn, phone));
    }
}
