package fr.cdaacademy.certificate;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import fr.cdaacademy.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Attestations", description = "Attestations par niveau et par parcours, certification globale")
public class CertificateController {

    private final CertificateService certificates;

    public CertificateController(CertificateService certificates) {
        this.certificates = certificates;
    }

    @GetMapping("/api/certificates")
    @Operation(summary = "Mes attestations et l'avancement vers les suivantes (délivre celles qui sont méritées)")
    public CertificateService.Overview overview() {
        return certificates.overview(CurrentUser.id());
    }

    @GetMapping("/api/certificates/{code}")
    @Operation(summary = "Une de mes attestations, pour l'affichage imprimable")
    public CertificateService.CertificateView mine(@PathVariable String code) {
        return certificates.mine(CurrentUser.id(), code);
    }

    @GetMapping("/api/public/certificates/{code}")
    @Operation(summary = "Vérification publique d'une attestation à partir de son code")
    public CertificateService.PublicView verify(@PathVariable String code) {
        return certificates.verify(code);
    }
}
