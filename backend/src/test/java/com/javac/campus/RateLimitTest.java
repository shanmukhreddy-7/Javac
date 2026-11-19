package com.javac.campus;

import static org.assertj.core.api.Assertions.*;

import com.javac.campus.security.AuthRateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

/**
 * Small isolated servlet-filter test: constructs AuthRateLimitFilter directly with mock
 * requests/responses instead of booting the database or full application.
 */
class RateLimitTest {
  /**
   * Sends three auth POSTs from one address with a limit of two. The first two proceed and the
   * third receives HTTP 429, exercising the filter's admission boundary.
   */
  @Test
  void sensitiveEndpointsAreRateLimited() throws Exception {
    var filter = new AuthRateLimitFilter(2);
    for (int i = 0; i < 3; i++) {
      var request = new MockHttpServletRequest("POST", "/api/auth/login");
      request.setServletPath("/api/auth/login");
      request.setRemoteAddr("127.0.0.1");
      var response = new MockHttpServletResponse();
      filter.doFilter(request, response, new MockFilterChain());
      assertThat(response.getStatus()).isEqualTo(i < 2 ? 200 : 429);
    }
  }
}
