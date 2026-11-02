package com.hyeja.userservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test") // 테스트를 돌릴 때 테스트 프로필 사용
class UserServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
