package alerts.health

import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class HealthController {
  @GetMapping("/ping")
  fun ping(): ResponseEntity<String> =
    ResponseEntity.ok("pong")
}
